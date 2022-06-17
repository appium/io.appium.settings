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
import io.appium.settings.helpers.NotificationHelpers;

import static io.appium.settings.recorder.RecorderConstant.ACTION_RECORDING_FILENAME;
import static io.appium.settings.recorder.RecorderConstant.ACTION_RECORDING_MAX_DURATION;
import static io.appium.settings.recorder.RecorderConstant.ACTION_RECORDING_PRIORITY;
import static io.appium.settings.recorder.RecorderConstant.ACTION_RECORDING_RESOLUTION;
import static io.appium.settings.recorder.RecorderConstant.ACTION_RECORDING_RESULT_CODE;
import static io.appium.settings.recorder.RecorderConstant.ACTION_RECORDING_ROTATION;
import static io.appium.settings.recorder.RecorderConstant.ACTION_RECORDING_START;
import static io.appium.settings.recorder.RecorderConstant.ACTION_RECORDING_STOP;
import static io.appium.settings.recorder.RecorderConstant.RECORDING_MAX_DURATION_DEFAULT_MS;
import static io.appium.settings.recorder.RecorderConstant.RECORDING_PRIORITY_DEFAULT;
import static io.appium.settings.recorder.RecorderConstant.RECORDING_ROTATION_DEFAULT_DEGREE;

public class RecorderService extends Service {
    private static final String TAG = "RecorderService";

    private static RecorderThread recorderThread;

    public RecorderService() {
        super();
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, "onDestroy called: Stopping recorder");
        if (recorderThread != null && recorderThread.isRecordingRunning()) {
            recorderThread.stopRecording();
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
                startRecord(mMediaProjectionManager, intent);
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
    private void startRecord(MediaProjectionManager mediaProjectionManager,
                             final Intent intent) {
        if (recorderThread != null) {
            if (recorderThread.isRecordingRunning()) {
                Log.v(TAG, "Recording is already continuing, exiting");
                return;
            } else {
                Log.w(TAG, "Recording is stopped, " +
                        "but recording instance is still alive, starting recording");
                recorderThread = null;
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

        String outputFilePath = intent.getStringExtra(ACTION_RECORDING_FILENAME);
        if (outputFilePath == null) {
            Log.e(TAG, "Recording is stopped, Unable to retrieve outputFilePath instance");
            return;
        }

        /* TODO we need to rotate frames that comes from virtual screen before writing to file via muxer,
         *  for handling landscape mode properly, we need to find a way to rotate images somehow fast and reliable
         */
        int recordingRotationDegree = intent.getIntExtra(ACTION_RECORDING_ROTATION,
                RECORDING_ROTATION_DEFAULT_DEGREE);

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

        int recordingPriority = intent.getIntExtra(ACTION_RECORDING_PRIORITY,
                RECORDING_PRIORITY_DEFAULT);

        int recordingMaxDuration = intent.getIntExtra(ACTION_RECORDING_MAX_DURATION,
                RECORDING_MAX_DURATION_DEFAULT_MS);

        recorderThread = new RecorderThread(projection, outputFilePath,
                resolutionWidth, resolutionHeight, rawDpi,
                recordingRotationDegree, recordingPriority, recordingMaxDuration);
        recorderThread.startRecording();
    }

    /**
     * stop recording
     */
    private void stopRecord() {
        if (recorderThread != null) {
            recorderThread.stopRecording();
            recorderThread = null;
        }
        stopSelf();
    }

    private void showNotification() {
        // Set the info for the views that show in the notification panel.
        startForeground(NotificationHelpers.APPIUM_NOTIFICATION_IDENTIFIER,
                NotificationHelpers.getNotification(this));
    }
}