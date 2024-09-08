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

import static io.appium.settings.helpers.RecorderConstant.ACTION_RECORDING_MAX_DURATION;
import static io.appium.settings.helpers.RecorderConstant.ACTION_STREAMING_PORT;
import static io.appium.settings.helpers.RecorderConstant.ACTION_RECORDING_RESOLUTION;
import static io.appium.settings.helpers.RecorderConstant.ACTION_RECORDING_RESULT_CODE;
import static io.appium.settings.helpers.RecorderConstant.ACTION_RECORDING_START;
import static io.appium.settings.helpers.RecorderConstant.ACTION_RECORDING_STOP;
import static io.appium.settings.helpers.RecorderConstant.RECORDING_MAX_DURATION_DEFAULT_MS;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.java_websocket.WebSocket;

import java.io.IOException;

import io.appium.settings.helpers.NotificationHelpers;
import io.appium.settings.helpers.RecorderUtil;

public class StreamingService extends Service {
    private static final String TAG = "StreamingService";

    private static StreamingThread streamingThread;
    private static StreamingServer streamingServer;

    public StreamingService() {
        super();
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, "onDestroy called: Stopping recorder");
        if (streamingThread != null && streamingThread.isStreaming()) {
            streamingThread.stop();
            streamingThread = null;
        }
        if (streamingServer != null) {
            try {
                streamingServer.stop();
            } catch (InterruptedException e) {
                // ignore
            }
            streamingServer = null;
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        if (intent == null) {
            Log.e(TAG, "onStartCommand: Unable to retrieve recording intent");
            return START_NOT_STICKY;
        }
        final String action = intent.getAction();
        if (action == null) {
            Log.e(TAG, "onStartCommand: Unable to retrieve recording intent:action");
            return START_NOT_STICKY;
        }

        int result = START_STICKY;
        if (ACTION_RECORDING_START.equals(action)) {
            showNotification(); // TODO is this really necessary

            MediaProjectionManager mMediaProjectionManager =
                    (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

            if (mMediaProjectionManager != null) {
                startStreaming(mMediaProjectionManager, intent);
            } else {
                Log.e(TAG, "onStartCommand: " +
                        "Unable to retrieve MediaProjectionManager instance");
                result = START_NOT_STICKY;
            }
        } else if (ACTION_RECORDING_STOP.equals(action)) {
            Log.v(TAG, "onStartCommand: Received recording stop intent, stopping recording");
            stopRecord();
            result = START_NOT_STICKY;
        } else {
            Log.v(TAG, "onStartCommand: Received unknown recording intent with action: "
                    + action);
            result = START_NOT_STICKY;
        }

        return result;
    }

    /**
     * start recording
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void startStreaming(MediaProjectionManager mediaProjectionManager,
                                final Intent intent) {
        if (streamingThread != null) {
            if (streamingThread.isStreaming()) {
                Log.v(TAG, "Recording is already continuing, exiting");
                return;
            } else {
                Log.w(TAG, "Recording is stopped, " +
                        "but recording instance is still alive, starting recording");
                streamingThread = null;
            }
        }

        int resultCode = intent.getIntExtra(ACTION_RECORDING_RESULT_CODE, 0);
        // get MediaProjection
        final MediaProjection projection = mediaProjectionManager.getMediaProjection(resultCode,
                intent);
        if (projection == null) {
            Log.e(TAG, "Recording is stopped, Unable to retrieve MediaProjection instance");
            return;
        }

        int port = intent.getIntExtra(ACTION_STREAMING_PORT, 0);
        if (port == 0) {
            Log.e(TAG, "Recording is stopped, Unable to retrieve the port number");
            return;
        }

        final EventEmitter<WebSocket> connectionHandler = new EventEmitter<>();
        try {
            streamingServer = new StreamingServer(port, connectionHandler);
            streamingServer.start();
        } catch (IOException e) {
            Log.e(TAG, "Cannot start the streaming server on port " + port, e);
            return;
        }

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int rawWidth = metrics.widthPixels;
        int rawHeight = metrics.heightPixels;
        int rawDpi = metrics.densityDpi;

        String recordingResolutionMode = intent.getStringExtra(ACTION_RECORDING_RESOLUTION);

        Size recordingResolution = RecorderUtil.
                getRecordingResolution(recordingResolutionMode);

        int resolutionWidth = recordingResolution.getWidth();
        int resolutionHeight = recordingResolution.getHeight();

        /*
        MediaCodec's tested supported resolutions (as per CTS tests) are for landscape mode as default (1920x1080, 1280x720 etc.)
        but if phone or tablet is in portrait mode (usually it is),
        we need to flip width/height to match it
         */
        if (rawWidth < rawHeight) {
            resolutionWidth = recordingResolution.getHeight();
            resolutionHeight = recordingResolution.getWidth();
        }

        Log.v(TAG, String.format("Starting recording with resolution(widthxheight): (%dx%d)",
                resolutionWidth, resolutionHeight));

        int recordingMaxDuration = intent.getIntExtra(ACTION_RECORDING_MAX_DURATION,
                RECORDING_MAX_DURATION_DEFAULT_MS);

        streamingThread = new StreamingThread(
                projection, connectionHandler, resolutionWidth, resolutionHeight, rawDpi, recordingMaxDuration
        );
        streamingThread.start();
    }

    /**
     * stop recording
     */
    private void stopRecord() {
        if (streamingThread != null) {
            streamingThread.stop();
            streamingThread = null;
        }
        stopSelf();
    }

    private void showNotification() {
        // Set the info for the views that show in the notification panel.
        startForeground(NotificationHelpers.APPIUM_NOTIFICATION_IDENTIFIER,
                NotificationHelpers.getNotification(this));
    }
}