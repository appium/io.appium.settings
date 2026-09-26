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

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

// Base for a live streaming session: accepts a single client on a local abstract socket
// (off the main thread), then drains a FrameQueue to it on a dedicated writer thread
// while the subclass-specific capture pipeline runs.
public abstract class StreamingSession implements Runnable {
    private static final String TAG = "StreamingSession";

    protected final String socketName;
    protected final FrameQueue queue = new FrameQueue(StreamingConstant.MAX_BUFFERED_FRAMES);

    protected volatile boolean stopped = false;
    protected volatile boolean hasAsyncError = false;

    private LocalServerSocket serverSocket;
    private LocalSocket clientSocket;
    private Thread writerThread;

    protected StreamingSession(String socketName) {
        this.socketName = socketName;
    }

    public void startSession() {
        stopped = false;
        new Thread(this, getSessionThreadName()).start();
    }

    public void stopSession() {
        stopped = true;
        closeQuietly(serverSocket);
        closeQuietly(clientSocket);
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
                    closeQuietly(serverSocket);
                }
            }, "streaming-accept-watchdog");
            watchdog.start();

            clientSocket = serverSocket.accept();
            watchdog.interrupt();

            final OutputStream out = clientSocket.getOutputStream();
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
