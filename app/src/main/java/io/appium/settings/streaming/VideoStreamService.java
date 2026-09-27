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

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import io.appium.settings.helpers.NotificationHelpers;

public class VideoStreamService extends Service {
    private static final String TAG = "VideoStreamService";

    private static VideoStreamSession session;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, "onDestroy called: Stopping video stream");
        stopSession();
        super.onDestroy();
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            Log.e(TAG, "onStartCommand: Unable to retrieve streaming intent/action");
            stopSession();
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        if (!StreamingConstant.ACTION_VIDEO_STREAM_START.equals(action)) {
            Log.v(TAG, "onStartCommand: Received streaming stop/unknown intent with action: " + action);
            stopSession();
            return START_NOT_STICKY;
        }

        if (session != null && session.isSessionRunning()) {
            Log.v(TAG, "Video streaming is already running, exiting");
            return START_STICKY;
        }

        // Since Android 14 (API 34), MediaProjectionManager.getMediaProjection() throws
        // SecurityException unless this service is already a MEDIA_PROJECTION-typed
        // foreground service, so startForeground() must run before it, not after.
        startForeground(NotificationHelpers.APPIUM_VIDEO_STREAM_NOTIFICATION_ID,
                NotificationHelpers.getNotification(this, "Appium video screen streaming"));

        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (manager == null) {
            Log.e(TAG, "onStartCommand: Unable to retrieve MediaProjectionManager instance");
            stopSession();
            return START_NOT_STICKY;
        }

        int resultCode = intent.getIntExtra(StreamingConstant.EXTRA_RESULT_CODE, 0);
        MediaProjection projection = manager.getMediaProjection(resultCode, intent);
        if (projection == null) {
            Log.e(TAG, "onStartCommand: Unable to retrieve MediaProjection instance");
            stopSession();
            return START_NOT_STICKY;
        }

        String socketName = intent.getStringExtra(StreamingConstant.EXTRA_SOCKET_NAME);
        if (!StreamingUtil.isValidSocketName(socketName)) {
            Log.e(TAG, "onStartCommand: Invalid or missing socket name");
            stopSession();
            return START_NOT_STICKY;
        }

        boolean audioEnabled = StreamingUtil.getBooleanExtra(intent, StreamingConstant.EXTRA_AUDIO, false);
        String codecMime = StreamingUtil.getVideoCodecMime(intent);
        int fps = StreamingUtil.getIntExtra(intent, StreamingConstant.EXTRA_FPS,
                StreamingConstant.VIDEO_FPS_DEFAULT, 1, 60);
        int bitrate = StreamingUtil.getIntExtra(intent, StreamingConstant.EXTRA_BITRATE,
                StreamingConstant.VIDEO_BITRATE_DEFAULT,
                StreamingConstant.VIDEO_BITRATE_MIN, StreamingConstant.VIDEO_BITRATE_MAX);

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        String resolutionMode = intent.getStringExtra(StreamingConstant.EXTRA_RESOLUTION);

        // Resolution is deliberately NOT resolved here: RecorderUtil.getRecordingResolution()
        // creates a throwaway encoder to probe capabilities, which is slow enough to delay
        // startSession() (and thus the LocalServerSocket bind) and race the client's
        // connection attempt. VideoStreamSession resolves it lazily on its own thread instead.
        session = new VideoStreamSession(getApplicationContext(), projection, socketName,
                metrics.widthPixels, metrics.heightPixels, resolutionMode, metrics.densityDpi,
                codecMime, fps, bitrate, audioEnabled);
        VideoStreamSession startedSession = session;
        startedSession.setListener(() -> onSessionEnded(startedSession));
        startedSession.startSession();
        return START_STICKY;
    }

    private void stopSession() {
        if (session != null) {
            session.stopSession();
            session = null;
        }
        stopForeground(true);
        stopSelf();
    }

    // Invoked from the session's own background thread once it exits on its own (client
    // disconnect, capture error, accept timeout). stopSession() above only covers an
    // explicit ACTION_STOP; without this, the service - and isRunning() - would keep
    // reporting as active even though capture already ended.
    private void onSessionEnded(VideoStreamSession endedSession) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (session == endedSession) {
                session = null;
                stopForeground(true);
                stopSelf();
            }
        });
    }
}
