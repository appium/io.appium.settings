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
    // Mutable: reassigned by onCapturedSizeChanged() on a device rotation. Only ever
    // read/written from the capture handler thread, so no synchronization is needed.
    private int width;
    private int height;
    private final int dpi;
    private final int fps;
    private final int quality;
    private final int scale;

    private HandlerThread handlerThread;
    private Handler captureHandler;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private MediaProjection.Callback mediaProjectionCallback;
    private StreamingSession.SizeChangeMonitor sizeChangeMonitor;

    private long frameIntervalNanos;
    private long sessionStartNanos;
    private long lastEmittedNanos;
    private final AtomicLong sequence = new AtomicLong(0);

    // Reused across frames (all touched only from the capture handler thread) to avoid
    // allocating fresh Bitmaps/buffers on every encode.
    private Bitmap captureBitmap;
    private Bitmap croppedBitmap;
    private Canvas croppedCanvas;
    private Bitmap scaledBitmap;
    private Canvas scaledCanvas;
    private Paint scalePaint;
    private final ByteArrayOutputStream jpegBuffer = new ByteArrayOutputStream();

    public JpegStreamSession(Context context, MediaProjection mediaProjection, String socketName,
                              int width, int height, int dpi,
                              int fps, int quality, int scale) {
        super(context, socketName);
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
        captureHandler = new Handler(handlerThread.getLooper());

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        imageReader.setOnImageAvailableListener(this::onImageAvailable, captureHandler);

        frameIntervalNanos = 1_000_000_000L / Math.max(1, fps);
        sessionStartNanos = System.nanoTime();

        sizeChangeMonitor = new SizeChangeMonitor(appContext, captureHandler, width, height,
                this::onCapturedSizeChanged);
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
                if (sizeChangeMonitor != null) {
                    sizeChangeMonitor.onCapturedContentResize(width, height);
                }
            }
        };
        mediaProjection.registerCallback(mediaProjectionCallback, captureHandler);

        virtualDisplay = mediaProjection.createVirtualDisplay("Appium Jpeg Stream",
                width, height, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, captureHandler);

        while (!stopped && !hasAsyncError) {
            Thread.sleep(200);
        }
    }

    // Runs on the capture handler thread, same as onImageAvailable() below - so swapping
    // imageReader/virtualDisplay's surface here can't race an in-flight callback.
    private void onCapturedSizeChanged(int newWidth, int newHeight) {
        if (stopped) {
            // Queued right as the session is tearing down - fields may already be released.
            return;
        }
        try {
            Log.i(TAG, "Captured content resized to " + newWidth + "x" + newHeight
                    + ", reconfiguring JPEG capture");
            ImageReader oldReader = imageReader;
            ImageReader newReader = ImageReader.newInstance(newWidth, newHeight, PixelFormat.RGBA_8888, 2);
            newReader.setOnImageAvailableListener(this::onImageAvailable, captureHandler);

            virtualDisplay.resize(newWidth, newHeight, dpi);
            virtualDisplay.setSurface(newReader.getSurface());

            imageReader = newReader;
            width = newWidth;
            height = newHeight;
            oldReader.close();
        } catch (Exception e) {
            // Can race a concurrent stopSession() releasing virtualDisplay/imageReader.
            if (!stopped) {
                Log.e(TAG, "Failed to reconfigure JPEG capture after resize", e);
                hasAsyncError = true;
            }
        }
    }

    // Delivered on the capture handler thread. Reads dimensions off `image`, not the
    // width/height fields, so a straggler callback for an already-replaced reader still works.
    private void onImageAvailable(ImageReader reader) {
        long now = System.nanoTime();
        try (Image image = reader.acquireLatestImage()) {
            if (image == null) {
                return;
            }
            if (now - lastEmittedNanos < frameIntervalNanos) {
                return;
            }
            byte[] jpeg = encodeToJpeg(image);
            lastEmittedNanos = now;
            long timestampMicros = (now - sessionStartNanos) / 1000;
            queue.offer(new Frame(Frame.Track.JPEG, (byte) 0, sequence.getAndIncrement(),
                    timestampMicros, jpeg));
        } catch (Exception e) {
            // Can race releaseCaptureResources() closing imageReader/virtualDisplay concurrently.
            if (!stopped) {
                Log.e(TAG, "Failed to encode JPEG frame", e);
                hasAsyncError = true;
            }
        }
    }

    @Override
    protected void releaseCaptureResources() {
        // These fields are otherwise only ever touched on the capture handler thread (image
        // available callbacks, resize handling); marshaling their teardown onto that same
        // thread closes the race an already-queued callback would otherwise have with
        // releasing them from this (session) thread instead.
        if (captureHandler != null) {
            runOnHandlerAndWait(captureHandler, this::releaseCaptureThreadResources);
        } else {
            releaseCaptureThreadResources();
        }
        if (handlerThread != null) {
            handlerThread.quitSafely();
            handlerThread = null;
        }
        mediaProjection.stop();
    }

    private void releaseCaptureThreadResources() {
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
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
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

    // Only ever called from the capture handler thread, so the reused Bitmap/Canvas fields
    // need no synchronization. Sizes come from `image`, not the width/height fields, so a
    // straggler callback for an already-replaced ImageReader still encodes correctly.
    private byte[] encodeToJpeg(Image image) {
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int strideWidth = imageWidth + (rowStride - pixelStride * imageWidth) / pixelStride;

        if (captureBitmap == null || captureBitmap.getWidth() != strideWidth
                || captureBitmap.getHeight() != imageHeight) {
            if (captureBitmap != null) {
                captureBitmap.recycle();
            }
            captureBitmap = Bitmap.createBitmap(strideWidth, imageHeight, Bitmap.Config.ARGB_8888);
        }
        captureBitmap.copyPixelsFromBuffer(buffer);

        Bitmap cropped;
        if (strideWidth == imageWidth) {
            cropped = captureBitmap;
        } else {
            if (croppedBitmap == null || croppedBitmap.getWidth() != imageWidth
                    || croppedBitmap.getHeight() != imageHeight) {
                if (croppedBitmap != null) {
                    croppedBitmap.recycle();
                }
                croppedBitmap = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888);
                croppedCanvas = new Canvas(croppedBitmap);
            }
            // Canvas is bounded to imageWidth x imageHeight, dropping the row-stride padding.
            croppedCanvas.drawBitmap(captureBitmap, 0, 0, null);
            cropped = croppedBitmap;
        }

        Bitmap toEncode = cropped;
        if (scale != 100) {
            int scaledWidth = Math.max(1, imageWidth * scale / 100);
            int scaledHeight = Math.max(1, imageHeight * scale / 100);
            if (scaledBitmap == null || scaledBitmap.getWidth() != scaledWidth
                    || scaledBitmap.getHeight() != scaledHeight) {
                if (scaledBitmap != null) {
                    scaledBitmap.recycle();
                }
                scaledBitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
                scaledCanvas = new Canvas(scaledBitmap);
                scalePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
            }
            scaledCanvas.drawBitmap(cropped, new Rect(0, 0, imageWidth, imageHeight),
                    new Rect(0, 0, scaledWidth, scaledHeight), scalePaint);
            toEncode = scaledBitmap;
        }

        jpegBuffer.reset();
        toEncode.compress(Bitmap.CompressFormat.JPEG, quality, jpegBuffer);
        return jpegBuffer.toByteArray();
    }
}
