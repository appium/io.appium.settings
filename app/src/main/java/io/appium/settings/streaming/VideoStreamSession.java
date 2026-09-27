/*
  Copyright 2012-present Appium Committers
  <p>
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at
  <p>
  http://www.apache.org/licenses/LICENSE-2.0
  <p>
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */

package io.appium.settings.streaming;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

import androidx.annotation.RequiresApi;

import io.appium.settings.media.MediaCodecFactory;
import io.appium.settings.recorder.RecorderUtil;

// Live H.264/HEVC video streaming session, with an optional interleaved AAC audio track.
// Video is captured via a MediaProjection-backed VirtualDisplay feeding a MediaCodec
// encoder's input Surface; audio (if enabled) mirrors RecorderThread's AudioRecord capture
// approach but pushes encoded access units onto the session's frame queue instead of
// muxing to a file.
public class VideoStreamSession extends StreamingSession {
    private static final String TAG = "VideoStreamSession";

    private final MediaProjection mediaProjection;
    private final int rawWidth;
    private final int rawHeight;
    private final String resolutionMode;
    private final int dpi;
    private final String codecMime;
    private final int fps;
    private final int bitrate;
    private final boolean audioEnabled;
    private final long sessionStartNanos = System.nanoTime();

    private VirtualDisplay virtualDisplay;
    // Reassigned by restartVideoEncoder() on a rotation; drainVideo() compares its own
    // `encoder` argument against the current field to know its generation was superseded.
    private volatile MediaCodec videoEncoder;
    private volatile Thread videoDrainThread;
    private MediaCodec audioEncoder;
    private AudioRecord audioRecord;
    private MediaProjection.Callback mediaProjectionCallback;
    private HandlerThread callbackHandlerThread;
    private Handler callbackHandler;
    private StreamingSession.SizeChangeMonitor sizeChangeMonitor;

    // Never reset across restartVideoEncoder(): a rotation-driven swap looks to the client
    // like a real codec reconfiguration - a fresh CONFIG frame, but a continuous sequence.
    private final AtomicLong videoSequence = new AtomicLong(0);
    private boolean isPortraitOrientation;
    private int currentWidth;
    private int currentHeight;
    private int clampedFps;
    private int clampedBitrate;

    public VideoStreamSession(Context context, MediaProjection mediaProjection, String socketName,
                               int rawWidth, int rawHeight, String resolutionMode, int dpi,
                               String codecMime, int fps, int bitrate, boolean audioEnabled) {
        super(context, socketName);
        this.mediaProjection = mediaProjection;
        this.rawWidth = rawWidth;
        this.rawHeight = rawHeight;
        this.resolutionMode = resolutionMode;
        this.dpi = dpi;
        this.codecMime = codecMime;
        this.fps = fps;
        this.bitrate = bitrate;
        this.audioEnabled = audioEnabled;
    }

    @Override
    protected String getSessionThreadName() {
        return "video-stream-session";
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    protected void configureAndCapture() throws Exception {
        // Deliberately resolved here (on this session's own background thread, after the
        // LocalServerSocket is already bound/accepting) rather than on the Service's main
        // thread before startSession() - RecorderUtil.getRecordingResolution() creates a
        // throwaway encoder to probe capabilities, which is slow enough to otherwise delay
        // the socket bind and race the client's connection attempt.
        Size recordingResolution = RecorderUtil.getRecordingResolution(resolutionMode);
        isPortraitOrientation = rawWidth < rawHeight;
        currentWidth = isPortraitOrientation ? recordingResolution.getHeight() : recordingResolution.getWidth();
        currentHeight = isPortraitOrientation ? recordingResolution.getWidth() : recordingResolution.getHeight();

        MediaCodecInfo.VideoCapabilities capabilities;
        MediaCodec capabilitiesProbe = MediaCodec.createEncoderByType(codecMime);
        try {
            capabilities = capabilitiesProbe.getCodecInfo()
                    .getCapabilitiesForType(codecMime).getVideoCapabilities();
        } finally {
            capabilitiesProbe.release();
        }

        clampedFps = Math.min(fps, capabilities.getSupportedFrameRates().getUpper());
        clampedBitrate = capabilities.getBitrateRange().clamp(bitrate);

        videoEncoder = createVideoEncoder(currentWidth, currentHeight);
        Surface surface = videoEncoder.createInputSurface();
        videoEncoder.start();

        // Dedicated handler thread, not the main looper: a rotation restart does real work
        // here (building a new encoder, joining the old drain thread).
        callbackHandlerThread = new HandlerThread("video-stream-callback");
        callbackHandlerThread.start();
        callbackHandler = new Handler(callbackHandlerThread.getLooper());

        sizeChangeMonitor = new SizeChangeMonitor(appContext, callbackHandler, rawWidth, rawHeight,
                this::onRawSizeChanged);
        sizeChangeMonitor.start();

        mediaProjectionCallback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                super.onStop();
                if (!stopped) {
                    hasAsyncError = true;
                }
            }

            @Override
            public void onCapturedContentResize(int width, int height) {
                super.onCapturedContentResize(width, height);
                sizeChangeMonitor.onCapturedContentResize(width, height);
            }
        };
        mediaProjection.registerCallback(mediaProjectionCallback, callbackHandler);

        virtualDisplay = mediaProjection.createVirtualDisplay("Appium Video Stream",
                currentWidth, currentHeight, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface, null, callbackHandler);

        MediaCodec startingEncoder = videoEncoder;
        videoDrainThread = new Thread(() -> drainVideo(startingEncoder), "video-stream-drain");
        videoDrainThread.start();

        Thread audioDrainThread = null;
        try {
            if (audioEnabled) {
                audioEncoder = MediaCodecFactory.createAacEncoder(StreamingConstant.AUDIO_SAMPLE_RATE_HZ,
                        StreamingConstant.AUDIO_CHANNEL_COUNT, StreamingConstant.AUDIO_BITRATE_DEFAULT);
                audioEncoder.start();
                audioRecord = MediaCodecFactory.createPlaybackCaptureAudioRecord(mediaProjection,
                        StreamingConstant.AUDIO_SAMPLE_RATE_HZ);
                audioDrainThread = new Thread(this::captureAndDrainAudio, "audio-stream-drain");
                audioDrainThread.start();
            }

            // videoDrainThread may be replaced mid-session by restartVideoEncoder() (a
            // rotation), so this can't just join() a single drain thread - poll instead.
            while (!stopped && !hasAsyncError) {
                Thread.sleep(200);
            }
        } finally {
            // Ensure both drain threads stop touching their encoders (even if audio setup
            // above threw) before releaseCaptureResources() stops/releases them elsewhere.
            stopped = true;
            joinQuietly(videoDrainThread);
            joinQuietly(audioDrainThread);
        }
    }

    private static void joinQuietly(Thread thread) {
        if (thread == null) {
            return;
        }
        try {
            thread.join();
        } catch (InterruptedException ignored) {
        }
    }

    private MediaCodec createVideoEncoder(int width, int height) throws IOException {
        MediaFormat format = MediaCodecFactory.createVideoEncoderFormat(codecMime, width, height,
                clampedBitrate, clampedFps);
        return MediaCodecFactory.createConfiguredVideoEncoder(codecMime, format);
    }

    // The target resolution is a fixed supported-resolution choice, independent of raw pixel
    // size, so a rotation only swaps its width/height rather than re-deriving a new one.
    private void onRawSizeChanged(int newRawWidth, int newRawHeight) {
        if (stopped) {
            // Queued right as the session is tearing down - fields may already be released.
            return;
        }
        if (!isPlainRotation(newRawWidth, newRawHeight)) {
            // Not a portrait<->landscape flip of the session's original raw dimensions (e.g.
            // a foldable's screen switch, or multi-window) - leave the resolution as-is.
            return;
        }
        boolean newIsPortrait = newRawWidth < newRawHeight;
        if (newIsPortrait == isPortraitOrientation) {
            return;
        }
        isPortraitOrientation = newIsPortrait;
        restartVideoEncoder(currentHeight, currentWidth);
    }

    // True only if the new raw size is an exact swap of the session's original raw
    // dimensions - a real rotation of the same physical display, not an arbitrary resize.
    private boolean isPlainRotation(int newRawWidth, int newRawHeight) {
        return (newRawWidth == rawWidth && newRawHeight == rawHeight)
                || (newRawWidth == rawHeight && newRawHeight == rawWidth);
    }

    // MediaCodec's input Surface size is fixed at configure() time, so a resolution change
    // means: build a fresh encoder, point the VirtualDisplay at its new Surface, then swap the
    // drain thread over. The old thread notices the swap (its `encoder` no longer matches the
    // videoEncoder field) and exits within one DRAIN_TIMEOUT_US wakeup.
    private void restartVideoEncoder(int newWidth, int newHeight) {
        Log.i(TAG, "Restarting video encoder at " + newWidth + "x" + newHeight);
        MediaCodec newEncoder = null;
        Surface newSurface;
        try {
            newEncoder = createVideoEncoder(newWidth, newHeight);
            newSurface = newEncoder.createInputSurface();
            newEncoder.start();
        } catch (Exception e) {
            Log.e(TAG, "Failed to build a new video encoder after resize", e);
            releaseQuietly(newEncoder);
            hasAsyncError = true;
            return;
        }

        if (stopped) {
            // stopSession() raced this rebuild - virtualDisplay/videoEncoder are being (or
            // have been) released elsewhere, so don't touch them; just discard the new one.
            releaseQuietly(newEncoder);
            return;
        }

        virtualDisplay.resize(newWidth, newHeight, dpi);
        virtualDisplay.setSurface(newSurface);

        MediaCodec oldEncoder = videoEncoder;
        Thread oldDrainThread = videoDrainThread;
        videoEncoder = newEncoder;
        currentWidth = newWidth;
        currentHeight = newHeight;

        joinQuietly(oldDrainThread);
        oldEncoder.stop();
        oldEncoder.release();

        MediaCodec startedEncoder = newEncoder;
        Thread newDrainThread = new Thread(() -> drainVideo(startedEncoder), "video-stream-drain");
        videoDrainThread = newDrainThread;
        newDrainThread.start();
    }

    private static void releaseQuietly(MediaCodec codec) {
        if (codec == null) {
            return;
        }
        try {
            codec.stop();
        } catch (Exception ignored) {
        }
        try {
            codec.release();
        } catch (Exception ignored) {
        }
    }

    private long getPresentationTimeUs() {
        return (System.nanoTime() - sessionStartNanos) / 1000;
    }

    // The video encoder's input Surface is fed by SurfaceFlinger/VirtualDisplay, which
    // stamps buffers using the same CLOCK_MONOTONIC-based clock as System.nanoTime() - but
    // as an absolute time, not one relative to sessionStartNanos like audio's
    // getPresentationTimeUs(). Rebase it here so both tracks share the same origin.
    private long toSessionRelativeUs(long presentationTimeUs) {
        return presentationTimeUs - sessionStartNanos / 1000;
    }

    // `encoder` is this thread's own generation; the loop exits cleanly (no error) once
    // restartVideoEncoder() moves the videoEncoder field on to a newer one.
    private void drainVideo(MediaCodec encoder) {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        try {
            while (!stopped && !hasAsyncError && encoder == videoEncoder) {
                int status = encoder.dequeueOutputBuffer(info, StreamingConstant.DRAIN_TIMEOUT_US);
                if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat outputFormat = encoder.getOutputFormat();
                    byte[] csd0 = toBytes(outputFormat.getByteBuffer("csd-0"));
                    byte[] csd1 = outputFormat.containsKey("csd-1")
                            ? toBytes(outputFormat.getByteBuffer("csd-1")) : new byte[0];
                    byte[] configPayload = new byte[csd0.length + csd1.length];
                    System.arraycopy(csd0, 0, configPayload, 0, csd0.length);
                    System.arraycopy(csd1, 0, configPayload, csd0.length, csd1.length);
                    queue.offer(new Frame(Frame.Track.VIDEO, Frame.FLAG_CONFIG,
                            videoSequence.getAndIncrement(), getPresentationTimeUs(), configPayload));
                    continue;
                }
                if (status < 0) {
                    continue;
                }
                ByteBuffer outputBuffer = encoder.getOutputBuffer(status);
                if (outputBuffer != null && info.size > 0
                        && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    outputBuffer.position(info.offset);
                    outputBuffer.limit(info.offset + info.size);
                    byte[] payload = new byte[info.size];
                    outputBuffer.get(payload);

                    byte flags = 0;
                    if ((info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                        flags |= Frame.FLAG_KEYFRAME;
                    }
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        flags |= Frame.FLAG_EOS;
                    }
                    queue.offer(new Frame(Frame.Track.VIDEO, flags, videoSequence.getAndIncrement(),
                            toSessionRelativeUs(info.presentationTimeUs), payload));
                }
                encoder.releaseOutputBuffer(status, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break;
                }
            }
        } catch (Exception e) {
            if (!stopped && encoder == videoEncoder) {
                Log.e(TAG, "Video drain thread error", e);
                hasAsyncError = true;
            }
        }
    }

    private void captureAndDrainAudio() {
        AtomicLong sequence = new AtomicLong(0);
        try {
            audioRecord.startRecording();
        } catch (Exception e) {
            Log.e(TAG, "Failed to start audio recording", e);
            hasAsyncError = true;
            return;
        }
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        try {
            while (!stopped && !hasAsyncError) {
                int inputIndex = audioEncoder.dequeueInputBuffer(StreamingConstant.DRAIN_TIMEOUT_US);
                if (inputIndex >= 0) {
                    ByteBuffer inputBuffer = audioEncoder.getInputBuffer(inputIndex);
                    if (inputBuffer != null) {
                        inputBuffer.clear();
                        int read = audioRecord.read(inputBuffer, inputBuffer.capacity());
                        if (read < 0) {
                            // Negative return is an AudioRecord error code (e.g. ERROR_DEAD_OBJECT),
                            // not "nothing available yet" - stop rather than loop on empty audio forever.
                            Log.e(TAG, "AudioRecord.read() failed with error code " + read);
                            hasAsyncError = true;
                            break;
                        }
                        audioEncoder.queueInputBuffer(inputIndex, 0, read, getPresentationTimeUs(), 0);
                    }
                }

                int outputIndex = audioEncoder.dequeueOutputBuffer(info, 0);
                if (outputIndex >= 0) {
                    ByteBuffer outputBuffer = audioEncoder.getOutputBuffer(outputIndex);
                    if (outputBuffer != null && info.size > 0
                            && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        outputBuffer.position(info.offset);
                        outputBuffer.limit(info.offset + info.size);
                        byte[] raw = new byte[info.size];
                        outputBuffer.get(raw);
                        byte[] withAdts = AdtsHelper.addAdtsHeader(raw,
                                StreamingConstant.AUDIO_SAMPLE_RATE_HZ,
                                StreamingConstant.AUDIO_CHANNEL_COUNT);
                        queue.offer(new Frame(Frame.Track.AUDIO, (byte) 0, sequence.getAndIncrement(),
                                info.presentationTimeUs, withAdts));
                    }
                    audioEncoder.releaseOutputBuffer(outputIndex, false);
                }
            }
        } catch (Exception e) {
            if (!stopped) {
                Log.e(TAG, "Audio drain thread error", e);
                hasAsyncError = true;
            }
        }
    }

    private static byte[] toBytes(ByteBuffer buffer) {
        if (buffer == null) {
            return new byte[0];
        }
        ByteBuffer duplicate = buffer.duplicate();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return bytes;
    }

    @Override
    protected void releaseCaptureResources() {
        if (sizeChangeMonitor != null) {
            sizeChangeMonitor.stop();
            sizeChangeMonitor = null;
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (mediaProjectionCallback != null) {
            mediaProjection.unregisterCallback(mediaProjectionCallback);
            mediaProjectionCallback = null;
        }
        if (audioRecord != null) {
            try {
                audioRecord.stop();
            } catch (IllegalStateException ignored) {
            }
            audioRecord.release();
            audioRecord = null;
        }
        if (audioEncoder != null) {
            audioEncoder.stop();
            audioEncoder.release();
            audioEncoder = null;
        }
        if (videoEncoder != null) {
            videoEncoder.stop();
            videoEncoder.release();
            videoEncoder = null;
        }
        if (callbackHandlerThread != null) {
            callbackHandlerThread.quitSafely();
            callbackHandlerThread = null;
        }
        mediaProjection.stop();
    }
}
