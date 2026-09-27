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
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Build;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

// Base for a live streaming session: accepts a single client on a local abstract socket
// (off the main thread), then drains a FrameQueue to it on a dedicated writer thread
// while the subclass-specific capture pipeline runs.
public abstract class StreamingSession implements Runnable {
    private static final String TAG = "StreamingSession";

    protected final Context appContext;
    protected final String socketName;
    protected final FrameQueue queue = new FrameQueue(StreamingConstant.MAX_BUFFERED_FRAMES);

    protected volatile boolean stopped = false;
    protected volatile boolean hasAsyncError = false;

    private LocalServerSocket serverSocket;
    private LocalSocket clientSocket;
    private Thread writerThread;
    private volatile Listener listener;

    /**
     * Notified when the captured content's dimensions change - a device rotation, most
     * commonly. Always delivered on the Handler thread passed to SizeChangeMonitor.
     */
    protected interface SizeChangeListener {
        void onCapturedSizeChanged(int newWidth, int newHeight);
    }

    // API 34+: caller forwards MediaProjection.Callback#onCapturedContentResize() in here.
    // Below that: DisplayManager.DisplayListener fallback, diffing dimensions since it also fires for unrelated events.
    protected static final class SizeChangeMonitor {
        private final Context appContext;
        private final Handler handler;
        private final SizeChangeListener listener;
        private int lastWidth;
        private int lastHeight;
        private DisplayManager.DisplayListener displayListener;

        SizeChangeMonitor(Context appContext, Handler handler, int initialWidth, int initialHeight,
                           SizeChangeListener listener) {
            this.appContext = appContext;
            this.handler = handler;
            this.listener = listener;
            this.lastWidth = initialWidth;
            this.lastHeight = initialHeight;
        }

        void onCapturedContentResize(int width, int height) {
            notifyIfChanged(width, height);
        }

        void start() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // The caller forwards MediaProjection.Callback#onCapturedContentResize into
                // onCapturedContentResize() above instead - no fallback needed on API 34+.
                return;
            }
            DisplayManager displayManager =
                    (DisplayManager) appContext.getSystemService(Context.DISPLAY_SERVICE);
            if (displayManager == null) {
                return;
            }
            displayListener = new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    DisplayMetrics metrics = appContext.getResources().getDisplayMetrics();
                    notifyIfChanged(metrics.widthPixels, metrics.heightPixels);
                }
            };
            displayManager.registerDisplayListener(displayListener, handler);
        }

        void stop() {
            if (displayListener == null) {
                return;
            }
            DisplayManager displayManager =
                    (DisplayManager) appContext.getSystemService(Context.DISPLAY_SERVICE);
            if (displayManager != null) {
                displayManager.unregisterDisplayListener(displayListener);
            }
            displayListener = null;
        }

        private void notifyIfChanged(int width, int height) {
            if (width == lastWidth && height == lastHeight) {
                return;
            }
            lastWidth = width;
            lastHeight = height;
            listener.onCapturedSizeChanged(width, height);
        }
    }

    /**
     * Notified once this session's background thread has fully exited, whether stopped
     * explicitly or ended on its own (client disconnect, capture error, accept timeout).
     */
    public interface Listener {
        void onSessionEnded();
    }

    protected StreamingSession(Context context, String socketName) {
        this.appContext = context.getApplicationContext();
        this.socketName = socketName;
    }

    /**
     * Registers a callback fired exactly once, after this session's background thread has
     * exited for any reason. The owning Service uses this to stop itself when a session ends
     * on its own, since stopSession() alone does not cover that case.
     *
     * @param listener The listener to notify; replaces any previously set listener
     */
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void startSession() {
        stopped = false;
        new Thread(this, getSessionThreadName()).start();
    }

    public void stopSession() {
        stopped = true;
        unblockAccept();
        closeQuietly(clientSocket);
    }

    // LocalServerSocket#close() does not reliably interrupt a thread already parked in
    // accept() on Android (unlike java.net.ServerSocket). Connect a throwaway client first
    // so a pending accept() completes and returns - run() discards it once it observes
    // `stopped`. Only then close the server socket; must be this order, since closing it
    // first unbinds the socket name and the connect() below would just fail instead.
    private void unblockAccept() {
        try {
            LocalSocket unblocker = new LocalSocket();
            unblocker.connect(new LocalSocketAddress(socketName));
            closeQuietly(unblocker);
        } catch (IOException ignored) {
            // Nothing was blocked in accept() (already accepted, or never bound).
        }
        closeQuietly(serverSocket);
    }

    public boolean isSessionRunning() {
        return !stopped;
    }

    protected abstract String getSessionThreadName();

    /**
     * Sets up the capture pipeline (VirtualDisplay, encoder(s)) and blocks until
     * the session is stopped or an async error occurs.
     */
    protected abstract void configureAndCapture() throws Exception;

    /**
     * Releases all capture-pipeline resources (VirtualDisplay, encoders, MediaProjection).
     */
    protected abstract void releaseCaptureResources();

    @Override
    public void run() {
        try {
            serverSocket = new LocalServerSocket(socketName);

            Thread watchdog = new Thread(() -> {
                try {
                    Thread.sleep(StreamingConstant.ACCEPT_TIMEOUT_MS);
                } catch (InterruptedException e) {
                    return;
                }
                if (clientSocket == null) {
                    Log.w(TAG, "No client connected within timeout, closing streaming session");
                    stopSession();
                }
            }, "streaming-accept-watchdog");
            watchdog.start();

            clientSocket = serverSocket.accept();
            watchdog.interrupt();

            if (stopped) {
                // stopSession() (explicitly, or via the watchdog above) had to connect a
                // throwaway client to unblock accept() - there is no real client to serve.
                closeQuietly(clientSocket);
                return;
            }

            final OutputStream out = clientSocket.getOutputStream();

            // Detect the client going away even when nothing is being written (e.g. a JPEG
            // stream on an unchanged screen): the client never sends data, so a blocking read
            // here only returns once the peer closes the connection.
            Thread disconnectWatcher = new Thread(() -> {
                try {
                    clientSocket.getInputStream().read();
                } catch (IOException ignored) {
                    // Local socket closed already, by stopSession() or a writer error.
                }
                stopSession();
            }, "streaming-disconnect-watcher");
            disconnectWatcher.setDaemon(true);
            disconnectWatcher.start();

            writerThread = new Thread(() -> {
                try {
                    while (!stopped) {
                        Frame frame = queue.take();
                        StreamProtocol.writeFrame(out, frame);
                    }
                } catch (Exception e) {
                    if (!stopped) {
                        Log.e(TAG, "Streaming writer thread error", e);
                        hasAsyncError = true;
                    }
                }
            }, "streaming-writer");
            writerThread.start();

            configureAndCapture();
        } catch (Exception e) {
            Log.e(TAG, "Streaming session terminated", e);
        } finally {
            stopped = true;
            releaseCaptureResources();
            if (writerThread != null) {
                writerThread.interrupt();
            }
            closeQuietly(clientSocket);
            closeQuietly(serverSocket);
            if (listener != null) {
                listener.onSessionEnded();
            }
        }
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }
}
