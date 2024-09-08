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

import static io.appium.settings.helpers.RecorderConstant.BPS_IN_MBPS;
import static io.appium.settings.helpers.RecorderConstant.VIDEO_CODEC_DEFAULT_FRAME_RATE;
import static io.appium.settings.streaming.StreamingServer.ON_CLOSE;
import static io.appium.settings.streaming.StreamingServer.ON_CONNECT;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.java_websocket.WebSocket;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import io.appium.settings.helpers.RecorderConstant;

public class StreamingThread implements Runnable, IEventHandler<WebSocket> {

    private static final String TAG = "RecorderThread";

    private final MediaProjection mediaProjection;
    private final int videoWidth;
    private final int videoHeight;
    private final int videoDpi;
    private final int recordingMaxDuration;
    private byte[] ivfHeader;
    private final EventEmitter<WebSocket> connectionHandler;
    private final List<Client> connectedClients = new ArrayList<>();

    private volatile Semaphore stopped;

    private final VirtualDisplay.Callback displayCallback = new VirtualDisplay.Callback() {
        @Override
        public void onPaused() {
            super.onPaused();
            Log.v(TAG, "VirtualDisplay callback: Display streaming paused");
        }

        @Override
        public void onStopped() {
            super.onStopped();
            if (stopped != null) {
                stopped.release();
            }
        }
    };

    public StreamingThread(
            MediaProjection mediaProjection, EventEmitter<WebSocket> connectionHandler,
            int videoWidth, int videoHeight, int videoDpi, int recordingMaxDuration
    ) {
        this.mediaProjection = mediaProjection;
        this.connectionHandler = connectionHandler;
        connectionHandler.subscribe(this);
        this.videoWidth = videoWidth;
        this.videoHeight = videoHeight;
        this.videoDpi = videoDpi;
        this.recordingMaxDuration = recordingMaxDuration;
    }

    public void start() {
        if (stopped != null) {
            return;
        }
        stopped = new Semaphore(1);
        try {
            stopped.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        new Thread(this).start();
    }

    public void stop() {
        if (stopped == null) {
            return;
        }
        stopped.release();
        connectionHandler.unsubscribe(this);
    }

    public boolean isStreaming() {
        return stopped != null && stopped.hasQueuedThreads();
    }

    private MediaFormat initVideoEncoderFormat(String videoMime, int videoWidth,
                                               int videoHeight, int videoBitrate,
                                               int videoFrameRate) {
        MediaFormat encoderFormat = MediaFormat.createVideoFormat(videoMime, videoWidth,
                videoHeight);
        encoderFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        encoderFormat.setInteger(MediaFormat.KEY_BIT_RATE, videoBitrate);
        encoderFormat.setInteger(MediaFormat.KEY_FRAME_RATE, videoFrameRate);
        encoderFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 0);
        encoderFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        return encoderFormat;
    }

    private VirtualDisplay initVirtualDisplay(MediaProjection mediaProjection,
                                              Surface surface, Handler handler,
                                              int videoWidth, int videoHeight, int videoDpi) {
        return mediaProjection.createVirtualDisplay("Appium Screen Streamer",
                videoWidth, videoHeight, videoDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface, displayCallback, handler);
    }

    private int calculateBitRate(int width, int height, int frameRate) {
        return (int) (RecorderConstant.BITRATE_MULTIPLIER *
                frameRate * width * height);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public void run() {
        VirtualDisplay virtualDisplay = null;
        MediaCodec videoEncoder = null;
        Surface surface = null;
        try {
            videoEncoder = MediaCodec.createEncoderByType("OMX.google.vp8.encoder");

            MediaCodecInfo.VideoCapabilities videoEncoderCapabilities = videoEncoder
                    .getCodecInfo().getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_VP8)
                    .getVideoCapabilities();

            int videoFrameRate = Math.min(VIDEO_CODEC_DEFAULT_FRAME_RATE,
                    videoEncoderCapabilities.getSupportedFrameRates().getUpper());

            int videoBitrate = videoEncoderCapabilities.getBitrateRange()
                    .clamp(calculateBitRate(this.videoWidth, this.videoHeight, videoFrameRate));

            Log.i(TAG, String.format("Recording starting with frame rate = %d FPS " +
                            "and bitrate = %5.2f Mbps",
                    videoFrameRate, videoBitrate / BPS_IN_MBPS));

            MediaFormat videoEncoderFormat =
                    initVideoEncoderFormat(MediaFormat.MIMETYPE_VIDEO_VP8,
                            this.videoWidth, this.videoHeight, videoBitrate, videoFrameRate);

            this.ivfHeader = IvfWriter.makeIvfHeader(
                    0, this.videoWidth, this.videoHeight, 1, videoBitrate
            );
            videoEncoder.setCallback(new MediaCodec.Callback() {
                @Override
                public void onInputBufferAvailable(@NonNull MediaCodec codec, int inputBufIndex) {
                }

                @Override
                public void onOutputBufferAvailable(
                        @NonNull MediaCodec codec, int outputBufferId, @NonNull MediaCodec.BufferInfo info
                ) {
                    ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferId);
                    if (info.size > 0 && outputBuffer != null) {
                        outputBuffer.position(info.offset);
                        outputBuffer.limit(info.offset + info.size);

                        byte[] header = IvfWriter.makeIvfFrameHeader(outputBuffer.remaining(), info.presentationTimeUs);
                        byte[] b = new byte[outputBuffer.remaining()];
                        outputBuffer.get(b);

                        sendFrameToConnectedClients(header, b);
                    }
                    codec.releaseOutputBuffer(outputBufferId, false);
                }

                @Override
                public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
                    e.printStackTrace();
                }

                @Override
                public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
                    Log.i(TAG, "onOutputFormatChanged. CodecInfo:" + codec.getCodecInfo() + " MediaFormat:" + format.toString());
                }
            });
            videoEncoder.configure(
                    videoEncoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE
            );
            surface = videoEncoder.createInputSurface();
            videoEncoder.start();

            Handler handler = new Handler(Looper.getMainLooper());
            virtualDisplay = initVirtualDisplay(this.mediaProjection, surface, handler,
                    this.videoWidth, this.videoHeight, this.videoDpi);

            if (stopped.tryAcquire(this.recordingMaxDuration, TimeUnit.MILLISECONDS)) {
                Log.v(TAG, "Streaming has been stopped");
            }
        } catch (Exception mainException) {
            Log.e(TAG, "run: Exception occurred during recording", mainException);
        } finally {
            disconnectAllClients();

            if (virtualDisplay != null) {
                virtualDisplay.release();
            }

            if (surface != null) {
                surface.release();
            }

            if (videoEncoder != null) {
                videoEncoder.stop();
                videoEncoder.release();
            }

            mediaProjection.stop();
        }
    }

    @Override
    public void onEvent(String name, WebSocket socket) {
        switch (name) {
            case ON_CONNECT:
                synchronized (connectedClients) {
                    connectedClients.add(new Client(socket, false));
                }
                break;
            case ON_CLOSE:
                synchronized (connectedClients) {
                    int i = 0;
                    while (i < connectedClients.size()) {
                        Client client = connectedClients.get(i);
                        if (client.socket.isOpen()) {
                            i++;
                        } else {
                            client.socket.close();
                            connectedClients.remove(client);
                        }
                    }
                }
                break;
        }
    }

    private void disconnectAllClients() {
        synchronized (connectedClients) {
            int i = 0;
            while (i < connectedClients.size()) {
                Client client = connectedClients.get(i);
                if (client.socket.isOpen()) {
                    client.socket.close();
                }
                connectedClients.remove(client);
            }
        }
    }

    private void sendFrameToConnectedClients(byte[] header, byte[] data) {
        byte[] payload = new byte[header.length + data.length];
        System.arraycopy(header, 0, payload, 0, header.length);
        System.arraycopy(data, 0, payload, header.length, data.length);
        int i = 0;
        synchronized (connectedClients) {
            while (i < connectedClients.size()) {
                Client client = connectedClients.get(i);
                if (!client.socket.isOpen()) {
                    connectedClients.remove(i);
                    continue;
                }
                if (!client.didReceiveIvfHeader) {
                    client.socket.send(ivfHeader);
                    client.didReceiveIvfHeader = true;
                }
                client.socket.send(payload);
                i++;
            }
        }
    }

    private static class Client {
        public final WebSocket socket;
        public boolean didReceiveIvfHeader;

        public Client(WebSocket socket, boolean didReceiveIvfHeader) {
            this.socket = socket;
            this.didReceiveIvfHeader = didReceiveIvfHeader;
        }
    }
}