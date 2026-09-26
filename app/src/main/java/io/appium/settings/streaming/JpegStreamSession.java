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

import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

import androidx.annotation.RequiresApi;

/**
 * Live JPEG frame streaming session: captures the device screen via a
 * MediaProjection-backed VirtualDisplay/ImageReader pair and JPEG-encodes
 * each frame (throttled to the requested fps) onto the session's frame queue.
 */
public class JpegStreamSession extends StreamingSession {
    private static final String TAG = "JpegStreamSession";

    private final MediaProjection mediaProjection;
    private final int width;
    private final int height;
    private final int dpi;
    private final int fps;
    private final int quality;
    private final int scale;

    private HandlerThread handlerThread;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private MediaProjection.Callback mediaProjectionCallback;

    public JpegStreamSession(MediaProjection mediaProjection, String socketName,
                              int width, int height, int dpi,
                              int fps, int quality, int scale) {
        super(socketName);
        this.mediaProjection = mediaProjection;
        this.width = width;
        this.height = height;
        this.dpi = dpi;
        this.fps = fps;
        this.quality = quality;
        this.scale = scale;
    }

    @Override
    protected String getSessionThreadName() {
        return "jpeg-stream-session";
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    protected void configureAndCapture() throws Exception {
        handlerThread = new HandlerThread("jpeg-stream-capture");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);

        long frameIntervalNanos = 1_000_000_000L / Math.max(1, fps);
        long[] lastEmittedNanos = {0};
        AtomicLong sequence = new AtomicLong(0);
        long sessionStartNanos = System.nanoTime();

        imageReader.setOnImageAvailableListener(reader -> {
            long now = System.nanoTime();
            try (Image image = reader.acquireLatestImage()) {
                if (image == null) {
                    return;
                }
                if (now - lastEmittedNanos[0] < frameIntervalNanos) {
                    return;
                }
                byte[] jpeg = encodeToJpeg(image, width, height, quality, scale);
                lastEmittedNanos[0] = now;
                long timestampMicros = (now - sessionStartNanos) / 1000;
                queue.offer(new Frame(Frame.Track.JPEG, (byte) 0, sequence.getAndIncrement(),
                        timestampMicros, jpeg));
            } catch (Exception e) {
                Log.e(TAG, "Failed to encode JPEG frame", e);
                hasAsyncError = true;
            }
        }, handler);

        mediaProjectionCallback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                super.onStop();
                if (!stopped) {
                    hasAsyncError = true;
                }
            }
        };
        mediaProjection.registerCallback(mediaProjectionCallback, handler);

        virtualDisplay = mediaProjection.createVirtualDisplay("Appium Jpeg Stream",
                width, height, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, handler);

        while (!stopped && !hasAsyncError) {
            Thread.sleep(200);
        }
    }

    @Override
    protected void releaseCaptureResources() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (mediaProjectionCallback != null) {
            mediaProjection.unregisterCallback(mediaProjectionCallback);
            mediaProjectionCallback = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (handlerThread != null) {
            handlerThread.quitSafely();
            handlerThread = null;
        }
        mediaProjection.stop();
    }

    private static byte[] encodeToJpeg(Image image, int width, int height, int quality,
                                        int scalePercent) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int rowPadding = rowStride - pixelStride * width;

        Bitmap raw = Bitmap.createBitmap(width + rowPadding / pixelStride, height,
                Bitmap.Config.ARGB_8888);
        raw.copyPixelsFromBuffer(buffer);

        Bitmap cropped = rowPadding == 0 ? raw : Bitmap.createBitmap(raw, 0, 0, width, height);
        if (cropped != raw) {
            raw.recycle();
        }

        Bitmap toEncode = cropped;
        if (scalePercent != 100) {
            int scaledWidth = Math.max(1, width * scalePercent / 100);
            int scaledHeight = Math.max(1, height * scalePercent / 100);
            toEncode = Bitmap.createScaledBitmap(cropped, scaledWidth, scaledHeight, true);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        toEncode.compress(Bitmap.CompressFormat.JPEG, quality, baos);

        if (toEncode != cropped) {
            toEncode.recycle();
        }
        cropped.recycle();

        return baos.toByteArray();
    }
}
