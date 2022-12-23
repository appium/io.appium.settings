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

package io.appium.settings.recorder;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

import androidx.annotation.RequiresApi;

import static io.appium.settings.recorder.RecorderConstant.BPS_IN_MBPS;
import static io.appium.settings.recorder.RecorderConstant.NANOSECONDS_IN_MICROSECOND;
import static io.appium.settings.recorder.RecorderConstant.NO_TIMESTAMP_SET;
import static io.appium.settings.recorder.RecorderConstant.NO_TRACK_INDEX_SET;
import static io.appium.settings.recorder.RecorderConstant.RECORDING_DEFAULT_VIDEO_MIME_TYPE;
import static io.appium.settings.recorder.RecorderConstant.VIDEO_CODEC_DEFAULT_FRAME_RATE;

public class RecorderThread implements Runnable {

    private static final String TAG = "RecorderThread";

    private final MediaProjection mediaProjection;
    private final String outputFilePath;
    private final int videoWidth;
    private final int videoHeight;
    private final int videoDpi;
    private final int recordingRotation;
    private final int recordingPriority;
    private final int recordingMaxDuration;

    private boolean muxerStarted = false;
    private boolean isStartTimestampInitialized = false;
    private long startTimestampUs = System.nanoTime() / NANOSECONDS_IN_MICROSECOND;
    private long lastAudioTimestampUs = NO_TIMESTAMP_SET;

    private int videoTrackIndex = NO_TRACK_INDEX_SET;
    private int audioTrackIndex = NO_TRACK_INDEX_SET;

    private volatile boolean stopped = false;
    private volatile boolean audioStopped = false;
    private volatile boolean hasAsyncError = false;

    private final VirtualDisplay.Callback displayCallback = new VirtualDisplay.Callback() {
        @Override
        public void onPaused() {
            super.onPaused();
            Log.v(TAG, "VirtualDisplay callback: Display streaming paused");
        }

        @Override
        public void onStopped() {
            super.onStopped();
            if (!stopped) {
                hasAsyncError = true;
            }
        }
    };

    public RecorderThread(MediaProjection mediaProjection, String outputFilePath,
                          int videoWidth, int videoHeight, int videoDpi, int recordingRotation,
                          int recordingPriority, int recordingMaxDuration) {
        this.mediaProjection = mediaProjection;
        this.outputFilePath = outputFilePath;
        this.videoWidth = videoWidth;
        this.videoHeight = videoHeight;
        this.videoDpi = videoDpi;
        this.recordingRotation = recordingRotation;
        this.recordingPriority = recordingPriority;
        this.recordingMaxDuration = recordingMaxDuration;
    }

    public void startRecording() {
        stopped = false;
        Thread recordingThread = new Thread(this);
        recordingThread.start();
    }

    public void stopRecording() {
        stopped = true;
    }

    public boolean isRecordingRunning() {
        return !stopped;
    }

    private MediaFormat initVideoEncoderFormat(String videoMime, int videoWidth,
                                               int videoHeight, int videoBitrate,
                                               int videoFrameRate) {
        MediaFormat encoderFormat = MediaFormat.createVideoFormat(videoMime, videoWidth,
                videoHeight);
        encoderFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        encoderFormat.setInteger(MediaFormat.KEY_BIT_RATE, videoBitrate);
        encoderFormat.setInteger(MediaFormat.KEY_FRAME_RATE, videoFrameRate);
        encoderFormat.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER,
                RecorderConstant.AUDIO_CODEC_REPEAT_PREV_FRAME_AFTER_MS);
        encoderFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,
                RecorderConstant.AUDIO_CODEC_I_FRAME_INTERVAL_MS);
        return encoderFormat;
    }

    private VirtualDisplay initVirtualDisplay(MediaProjection mediaProjection,
                                              Surface surface, Handler handler,
                                              int videoWidth, int videoHeight, int videoDpi) {
        return mediaProjection.createVirtualDisplay("Appium Screen Recorder",
                videoWidth, videoHeight, videoDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface, displayCallback, handler);
    }

    private MediaCodec initAudioCodec(int sampleRate) throws IOException {
        // TODO set channelCount 2 try stereo quality
        MediaFormat encoderFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate, RecorderConstant.AUDIO_CODEC_CHANNEL_COUNT);
        encoderFormat.setInteger(MediaFormat.KEY_BIT_RATE,
                RecorderConstant.AUDIO_CODEC_DEFAULT_BITRATE);

        MediaCodec audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        audioEncoder.configure(encoderFormat, null, null,
                MediaCodec.CONFIGURE_FLAG_ENCODE);
        return audioEncoder;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private AudioRecord initAudioRecord(MediaProjection mediaProjection, int sampleRate) {
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig,
                AudioFormat.ENCODING_PCM_16BIT);

        AudioFormat audioFormat = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build();

        AudioRecord.Builder audioRecordBuilder = new AudioRecord.Builder();
        AudioPlaybackCaptureConfiguration apcc =
                new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .build();
        return audioRecordBuilder.setAudioFormat(audioFormat)
                .setBufferSizeInBytes(4 * minBufferSize)
                .setAudioPlaybackCaptureConfig(apcc)
                .build();
    }

    private Thread initAudioRecordThread(MediaCodec audioEncoder, final AudioRecord audioRecord,
                                         int priority) {
        return new Thread(() -> {
            Thread.currentThread().setPriority(priority);
            try {
                audioRecord.startRecording();
            } catch (Exception e) {
                hasAsyncError = true;
                e.printStackTrace();
                return;
            }
            try {
                while (!audioStopped) {
                    int index = audioEncoder.dequeueInputBuffer(
                            RecorderConstant.MEDIA_QUEUE_BUFFERING_DEFAULT_TIMEOUT_MS);
                    if (index < 0) {
                        continue;
                    }
                    ByteBuffer inputBuffer = audioEncoder.getInputBuffer(index);
                    if (inputBuffer == null) {
                        if (!stopped) {
                            hasAsyncError = true;
                        }
                        return;
                    }
                    inputBuffer.clear();
                    int read = audioRecord.read(inputBuffer, inputBuffer.capacity());
                    if (read <= 0) {
                        if (!stopped) {
                            hasAsyncError = true;
                        }
                        break;
                    }
                    audioEncoder.queueInputBuffer(index, 0, read,
                            getPresentationTimeUs(), 0);
                }
            } catch (Exception e) {
                if (!stopped) {
                    Log.e(TAG, "Recording stopped, Audio Thread error", e);
                    hasAsyncError = true;
                    e.printStackTrace();
                }
            } finally {
                audioRecord.stop();
                audioRecord.release();
            }
        });
    }

    private long getPresentationTimeUs() {
        if (!isStartTimestampInitialized) {
            startTimestampUs =
                    System.nanoTime() / RecorderConstant.NANOSECONDS_IN_MICROSECOND;
            isStartTimestampInitialized = true;
        }
        return (System.nanoTime() / RecorderConstant.NANOSECONDS_IN_MICROSECOND
                - startTimestampUs);
    }

    private int calculateBitRate(int width, int height, int frameRate) {
        return (int) (RecorderConstant.BITRATE_MULTIPLIER *
                frameRate * width * height);
    }

    private void startMuxerIfSetUp(MediaMuxer muxer) {
        if (audioTrackIndex >= 0 && videoTrackIndex >= 0) {
            muxer.start();
            muxerStarted = true;
        }
    }

    private boolean writeAudioBufferToFile(MediaCodec audioEncoder, MediaMuxer muxer,
                                           MediaCodec.BufferInfo bufferInfo) {
        int encoderStatus;

        encoderStatus = audioEncoder.dequeueOutputBuffer(bufferInfo, 0);
        if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            if (audioTrackIndex > 0) {
                Log.e(TAG, "Recording stopped, audioTrackIndex greater than zero");
                return false;
            }
            audioTrackIndex = muxer.addTrack(audioEncoder.getOutputFormat());
            startMuxerIfSetUp(muxer);
        } else if (encoderStatus < 0 && encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
            Log.w(TAG, "Unexpected result from audio encoder.dequeueOutputBuffer: "
                    + encoderStatus + ", however continuing recording");
        } else if (encoderStatus >= 0) {
            ByteBuffer encodedData = audioEncoder.getOutputBuffer(encoderStatus);
            if (encodedData == null) {
                Log.e(TAG, "Recording stopped, " +
                        "Unable to retrieve output buffer of audio encoder");
                return false;
            }

            if (bufferInfo.presentationTimeUs > this.lastAudioTimestampUs
                    && muxerStarted && bufferInfo.size != 0 &&
                    (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                this.lastAudioTimestampUs = bufferInfo.presentationTimeUs;
                muxer.writeSampleData(audioTrackIndex, encodedData, bufferInfo);
            }

            audioEncoder.releaseOutputBuffer(encoderStatus, false);

            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.v(TAG, "Recording stopped, audio encoder buffer reached end of stream");
                return false;
            }
        }
        return true;
    }

    private boolean writeVideoBufferToFile(MediaCodec videoEncoder, MediaMuxer muxer,
                                           MediaCodec.BufferInfo bufferInfo) {
        int encoderStatus;

        encoderStatus = videoEncoder.dequeueOutputBuffer(bufferInfo,
                RecorderConstant.MEDIA_QUEUE_BUFFERING_DEFAULT_TIMEOUT_MS);
        if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            if (videoTrackIndex > 0) {
                Log.e(TAG, "Recording stopped, videoTrackIndex greater than zero");
                return false;
            }
            videoTrackIndex = muxer.addTrack(videoEncoder.getOutputFormat());
            startMuxerIfSetUp(muxer);
        } else if (encoderStatus < 0 && encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
            Log.w(TAG, "Unexpected result from encoder.dequeueOutputBuffer: "
                    + encoderStatus + ", however continuing recording");
        } else if (encoderStatus >= 0) {
            ByteBuffer encodedData = videoEncoder.getOutputBuffer(encoderStatus);
            if (encodedData == null) {
                Log.w(TAG, "Recording stopped, " +
                        "Unable to retrieve output buffer of videoEncoder");
                return false;
            }

            if (bufferInfo.size != 0 &&
                    (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                bufferInfo.presentationTimeUs = getPresentationTimeUs();
                muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo);
            }

            videoEncoder.releaseOutputBuffer(encoderStatus, false);

            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.v(TAG, "Recording stopped, video encoder buffer reached end of stream");
                return false;
            }
        }
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public void run() {
        VirtualDisplay virtualDisplay = null;
        MediaCodec videoEncoder = null;
        MediaCodec audioEncoder = null;
        Surface surface = null;
        Thread audioRecordThread = null;
        MediaMuxer muxer = null;
        try {
            videoEncoder = MediaCodec.createEncoderByType(RECORDING_DEFAULT_VIDEO_MIME_TYPE);

            MediaCodecInfo.VideoCapabilities videoEncoderCapabilities = videoEncoder
                    .getCodecInfo().getCapabilitiesForType(RECORDING_DEFAULT_VIDEO_MIME_TYPE)
                    .getVideoCapabilities();

            int videoFrameRate = Math.min(VIDEO_CODEC_DEFAULT_FRAME_RATE,
                    videoEncoderCapabilities.getSupportedFrameRates().getUpper());

            int videoBitrate = videoEncoderCapabilities.getBitrateRange()
                    .clamp(calculateBitRate(this.videoWidth, this.videoHeight, videoFrameRate));

            Log.i(TAG, String.format("Recording starting with frame rate = %d FPS " +
                            "and bitrate = %5.2f Mbps",
                    videoFrameRate, videoBitrate / BPS_IN_MBPS));

            MediaFormat videoEncoderFormat =
                    initVideoEncoderFormat(RECORDING_DEFAULT_VIDEO_MIME_TYPE,
                            this.videoWidth, this.videoHeight, videoBitrate, videoFrameRate);

            videoEncoder.configure(videoEncoderFormat, null, null,
                    MediaCodec.CONFIGURE_FLAG_ENCODE);
            surface = videoEncoder.createInputSurface();
            videoEncoder.start();

            Handler handler = new Handler(Looper.getMainLooper());
            virtualDisplay = initVirtualDisplay(this.mediaProjection, surface, handler,
                    this.videoWidth, this.videoHeight, this.videoDpi);

            int sampleRate = RecorderConstant.AUDIO_CODEC_SAMPLE_RATE_HZ;
            audioEncoder = initAudioCodec(sampleRate);
            audioEncoder.start();

            AudioRecord audioRecord = initAudioRecord(this.mediaProjection, sampleRate);

            muxer = new MediaMuxer(this.outputFilePath,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            // set output file orientation info
            // note: this method must be run before muxer.start()
            muxer.setOrientationHint(recordingRotation);

            audioRecordThread = initAudioRecordThread(audioEncoder, audioRecord,
                    this.recordingPriority);
            audioRecordThread.start();

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            lastAudioTimestampUs = NO_TIMESTAMP_SET;

            long recordingStartTime = System.currentTimeMillis();

            while (!stopped && !hasAsyncError) {
                if (!writeAudioBufferToFile(audioEncoder, muxer, bufferInfo)) {
                    break;
                }

                if (videoTrackIndex >= 0 && audioTrackIndex < 0) {
                    continue; // wait for audio config before processing any video data frames
                }

                if (!writeVideoBufferToFile(videoEncoder, muxer, bufferInfo)) {
                    break;
                }

                if ((System.currentTimeMillis() - recordingStartTime) >= this.recordingMaxDuration) {
                    Log.v(TAG, "Recording stopped, reached maximum duration");
                    stopped = true;
                }
            }
        } catch (Exception mainException) {
            Log.e(TAG, "run: Exception occurred during recording", mainException);
        } finally {
            if (muxer != null) {
                muxer.stop();
                muxer.release();
                muxer = null;
            }

            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }

            if (surface != null) {
                surface.release();
                surface = null;
            }

            if (videoEncoder != null) {
                videoEncoder.stop();
                videoEncoder.release();
                videoEncoder = null;
            }

            if (audioRecordThread != null) {
                audioStopped = true;
                try {
                    audioRecordThread.join();
                    audioRecordThread = null;
                } catch (InterruptedException e) {
                    Log.w(TAG, "Error releasing resources, audioRecordThread: ", e);
                }
            }

            if (audioEncoder != null) {
                audioEncoder.stop();
                audioEncoder.release();
                audioEncoder = null;
            }
        }
    }
}