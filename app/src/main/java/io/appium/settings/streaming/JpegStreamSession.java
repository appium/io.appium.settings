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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
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

    // Reused across frames (all touched only from the capture handler thread) to avoid
    // allocating fresh Bitmaps/buffers on every encode.
    private Bitmap captureBitmap;
    private Bitmap croppedBitmap;
    private Canvas croppedCanvas;
    private Bitmap scaledBitmap;
    private Canvas scaledCanvas;
    private Paint scalePaint;
    private final ByteArrayOutputStream jpegBuffer = new ByteArrayOutputStream();

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
                byte[] jpeg = encodeToJpeg(image);
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

        if (captureBitmap != null) {
            captureBitmap.recycle();
            captureBitmap = null;
        }
        if (croppedBitmap != null) {
            croppedBitmap.recycle();
            croppedBitmap = null;
            croppedCanvas = null;
        }
        if (scaledBitmap != null) {
            scaledBitmap.recycle();
            scaledBitmap = null;
            scaledCanvas = null;
        }
    }

    // Only ever called from the capture handler thread (the ImageReader listener), so the
    // reused Bitmap/Canvas/stream fields need no synchronization.
    private byte[] encodeToJpeg(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int strideWidth = width + (rowStride - pixelStride * width) / pixelStride;

        if (captureBitmap == null || captureBitmap.getWidth() != strideWidth
                || captureBitmap.getHeight() != height) {
            captureBitmap = Bitmap.createBitmap(strideWidth, height, Bitmap.Config.ARGB_8888);
        }
        captureBitmap.copyPixelsFromBuffer(buffer);

        Bitmap cropped;
        if (strideWidth == width) {
            cropped = captureBitmap;
        } else {
            if (croppedBitmap == null || croppedBitmap.getWidth() != width
                    || croppedBitmap.getHeight() != height) {
                croppedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                croppedCanvas = new Canvas(croppedBitmap);
            }
            // The canvas is bounded to width x height, so this naturally drops the
            // right-hand row-stride padding columns from captureBitmap.
            croppedCanvas.drawBitmap(captureBitmap, 0, 0, null);
            cropped = croppedBitmap;
        }

        Bitmap toEncode = cropped;
        if (scale != 100) {
            int scaledWidth = Math.max(1, width * scale / 100);
            int scaledHeight = Math.max(1, height * scale / 100);
            if (scaledBitmap == null || scaledBitmap.getWidth() != scaledWidth
                    || scaledBitmap.getHeight() != scaledHeight) {
                scaledBitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
                scaledCanvas = new Canvas(scaledBitmap);
                scalePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
            }
            scaledCanvas.drawBitmap(cropped, new Rect(0, 0, width, height),
                    new Rect(0, 0, scaledWidth, scaledHeight), scalePaint);
            toEncode = scaledBitmap;
        }

        jpegBuffer.reset();
        toEncode.compress(Bitmap.CompressFormat.JPEG, quality, jpegBuffer);
        return jpegBuffer.toByteArray();
    }
}
