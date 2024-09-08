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

package io.appium.settings;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import io.appium.settings.receivers.AnimationSettingReceiver;
import io.appium.settings.receivers.BluetoothConnectionSettingReceiver;
import io.appium.settings.receivers.ClipboardReceiver;
import io.appium.settings.receivers.DataConnectionSettingReceiver;
import io.appium.settings.receivers.HasAction;
import io.appium.settings.receivers.LocaleSettingReceiver;
import io.appium.settings.receivers.LocalesReader;
import io.appium.settings.receivers.LocationInfoReceiver;
import io.appium.settings.receivers.MediaScannerReceiver;
import io.appium.settings.receivers.NotificationsReceiver;
import io.appium.settings.receivers.SmsReader;
import io.appium.settings.receivers.UnpairBluetoothDevicesReceiver;
import io.appium.settings.receivers.WiFiConnectionSettingReceiver;
import io.appium.settings.recorder.RecorderService;
import io.appium.settings.helpers.RecorderUtil;
import io.appium.settings.streaming.StreamingService;

import static io.appium.settings.helpers.RecorderConstant.ACTION_RECORDING_BASE;
import static io.appium.settings.helpers.RecorderConstant.ACTION_RECORDING_FILENAME;
import static io.appium.settings.helpers.RecorderConstant.ACTION_RECORDING_MAX_DURATION;
import static io.appium.settings.helpers.RecorderConstant.ACTION_STREAMING_PORT;
import static io.appium.settings.helpers.RecorderConstant.ACTION_RECORDING_PRIORITY;
import static io.appium.settings.helpers.RecorderConstant.ACTION_RECORDING_RESOLUTION;
import static io.appium.settings.helpers.RecorderConstant.ACTION_RECORDING_RESULT_CODE;
import static io.appium.settings.helpers.RecorderConstant.ACTION_RECORDING_ROTATION;
import static io.appium.settings.helpers.RecorderConstant.ACTION_RECORDING_START;
import static io.appium.settings.helpers.RecorderConstant.ACTION_RECORDING_STOP;
import static io.appium.settings.helpers.RecorderConstant.ACTION_STREAMING_BASE;
import static io.appium.settings.helpers.RecorderConstant.ACTION_STREAMING_START;
import static io.appium.settings.helpers.RecorderConstant.ACTION_STREAMING_STOP;
import static io.appium.settings.helpers.RecorderConstant.NO_PATH_SET;
import static io.appium.settings.helpers.RecorderConstant.NO_RESOLUTION_MODE_SET;
import static io.appium.settings.helpers.RecorderConstant.RECORDING_MAX_DURATION_DEFAULT_MS;
import static io.appium.settings.helpers.RecorderConstant.RECORDING_PRIORITY_DEFAULT;
import static io.appium.settings.helpers.RecorderConstant.RECORDING_ROTATION_DEFAULT_DEGREE;
import static io.appium.settings.helpers.RecorderConstant.REQUEST_CODE_SCREEN_CAPTURE;
import static io.appium.settings.helpers.RecorderConstant.REQUEST_CODE_SCREEN_STREAM;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Settings extends Activity {
    private static final String TAG = "APPIUM SETTINGS";

    private String recordingOutputPath = NO_PATH_SET;
    private int recordingRotation = RECORDING_ROTATION_DEFAULT_DEGREE;
    private int recordingPriority = RECORDING_PRIORITY_DEFAULT;
    private int recordingMaxDuration = RECORDING_MAX_DURATION_DEFAULT_MS;
    private int streamingPort = 0;
    private String recordingResolutionMode = NO_RESOLUTION_MODE_SET;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Log.d(TAG, "Entering the app");

        registerSettingsReceivers(Arrays.asList(
                WiFiConnectionSettingReceiver.class,
                AnimationSettingReceiver.class,
                DataConnectionSettingReceiver.class,
                LocaleSettingReceiver.class,
                LocalesReader.class,
                LocationInfoReceiver.class,
                ClipboardReceiver.class,
                BluetoothConnectionSettingReceiver.class,
                UnpairBluetoothDevicesReceiver.class,
                NotificationsReceiver.class,
                SmsReader.class,
                MediaScannerReceiver.class
        ));

        // https://developer.android.com/about/versions/oreo/background-location-limits
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(ForegroundService.getForegroundServiceIntent(Settings.this));
        } else {
            LocationTracker.getInstance().start(this);
        }

        if (!handleScreenRecordingIfRequested(getIntent())
                && !handleScreenStreamingIfRequested(getIntent())) {
            finishActivity();
        }
    }

    private boolean handleScreenRecordingIfRequested(Intent intent) {
        String recordingAction = checkIntent(intent, ACTION_RECORDING_BASE);
        if (recordingAction == null) {
            return false;
        }
        if (recordingAction.equals(ACTION_RECORDING_START)) {
            String recordingFilename = intent.getStringExtra(ACTION_RECORDING_FILENAME);
            if (!RecorderUtil.isValidFileName(recordingFilename)) {
                Log.e(TAG, "handleRecording: Invalid filename passed by user: "
                        + recordingFilename);
                return false;
            }

            /*
             External Storage File Directory for app
             (i.e /storage/emulated/0/Android/data/io.appium.settings/files) may not be created
             so we need to call getExternalFilesDir() method twice
             source:https://www.androidbugfix.com/2021/10/getexternalfilesdirnull-returns-null-in.html
            */
            File externalStorageFile = getExternalFilesDir(null);
            if (externalStorageFile == null) {
                externalStorageFile = getExternalFilesDir(null);
            }
            // if path is still null despite calling method twice, early exit
            if (externalStorageFile == null) {
                Log.e(TAG, "handleRecording: Unable to retrieve external storage file path");
                return false;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                recordingOutputPath = Paths
                        .get(externalStorageFile.getAbsolutePath(), recordingFilename)
                        .toAbsolutePath()
                        .toString();
            }
            recordingRotation = RecorderUtil.getDeviceRotationInDegree(getApplicationContext());
            recordingPriority = RecorderUtil.getRecordingPriority(intent);
            recordingMaxDuration = RecorderUtil.getRecordingMaxDuration(intent);
            recordingResolutionMode = RecorderUtil.getRecordingResolutionMode(intent);

            // start record
            final MediaProjectionManager manager = (MediaProjectionManager) getSystemService(
                    Context.MEDIA_PROJECTION_SERVICE
            );
            if (manager == null) {
                Log.e(TAG, "handleRecording: " +
                        "Unable to retrieve MediaProjectionManager instance");
                return false;
            }
            final Intent permissionIntent = manager.createScreenCaptureIntent();
            startActivityForResult(permissionIntent, REQUEST_CODE_SCREEN_CAPTURE);
            return true;
        } else if (recordingAction.equals(ACTION_RECORDING_STOP)) {
            // stop record
            final Intent recorderIntent = new Intent(this, RecorderService.class);
            recorderIntent.setAction(ACTION_RECORDING_STOP);
            startService(recorderIntent);
        } else {
            Log.e(TAG, "handleRecording: Unknown recording intent with action:"
                    + recordingAction);
        }
        return false;
    }

    private boolean handleScreenStreamingIfRequested(Intent intent) {
        String streamingAction = checkIntent(intent, ACTION_STREAMING_BASE);
        if (streamingAction == null) {
            return false;
        }
        if (streamingAction.equals(ACTION_STREAMING_START)) {
            streamingPort = intent.getIntExtra(ACTION_STREAMING_PORT, 0);
            if (streamingPort <= 0) {
                Log.e(TAG, "handleStreaming: Invalid port provided by user: " + streamingPort);
                return false;
            }
            recordingMaxDuration = RecorderUtil.getRecordingMaxDuration(intent);
            recordingResolutionMode = RecorderUtil.getRecordingResolutionMode(intent);

            // start record
            final MediaProjectionManager manager = (MediaProjectionManager) getSystemService(
                    Context.MEDIA_PROJECTION_SERVICE
            );
            if (manager == null) {
                Log.e(TAG, "handleStreaming: " +
                        "Unable to retrieve MediaProjectionManager instance");
                return false;
            }
            final Intent permissionIntent = manager.createScreenCaptureIntent();
            startActivityForResult(permissionIntent, REQUEST_CODE_SCREEN_STREAM);
            return true;
        } else if (streamingAction.equals(ACTION_STREAMING_STOP)) {
            // stop record
            final Intent streamingIntent = new Intent(this, StreamingService.class);
            streamingIntent.setAction(ACTION_STREAMING_STOP);
            startService(streamingIntent);
        } else {
            Log.e(TAG, "handleStreaming: Unknown recording intent with action:"
                    + streamingAction);
        }
        return false;
    }

    private void finishActivity() {
        Log.d(TAG, "Closing the app");
        Handler handler = new Handler();
        handler.postDelayed(Settings.this::finish, 0);
    }

    private @Nullable String checkIntent(Intent intent, String action) {
        if (intent == null) {
            Log.e(TAG, "Unable to retrieve intent instance");
            return null;
        }
        String result = intent.getAction();
        if (result == null) {
            Log.e(TAG, "Unable to retrieve intent.action instance");
            return null;
        }
        if (!result.startsWith(action)) {
            Log.i(TAG, "Received different intent with action: "
                    + result);
            return null;
        }
        if (RecorderUtil.isLowerThanQ()) {
            Log.e(TAG, "Current Android OS Version is lower than Q");
            return null;
        }
        if (!RecorderUtil.areRecordingPermissionsGranted(getApplicationContext())) {
            Log.e(TAG, "Required permissions are not granted");
            return null;
        }
        return result;
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        if (REQUEST_CODE_SCREEN_CAPTURE != requestCode && REQUEST_CODE_SCREEN_STREAM != requestCode) {
            Log.e(TAG, "onActivityResult: Received unknown request with code: " + requestCode);
            finishActivity();
            return;
        }

        if (resultCode != Activity.RESULT_OK) {
            Log.e(TAG, "onActivityResult: " +
                    "MediaProjection permission is not granted, " +
                    "Did you apply appops adb command?");
            finishActivity();
            return;
        }

        Intent intent = REQUEST_CODE_SCREEN_CAPTURE == requestCode
                ? createScreenRecordingIntent(resultCode)
                : createScreenStreamingIntent(resultCode);
        intent.putExtras(data);
        startService(intent);

        finishActivity();
    }

    private @NonNull Intent createScreenRecordingIntent(int resultCode) {
        final Intent intent = new Intent(this, RecorderService.class);
        intent.setAction(ACTION_RECORDING_START);
        intent.putExtra(ACTION_RECORDING_RESULT_CODE, resultCode);
        intent.putExtra(ACTION_RECORDING_FILENAME, recordingOutputPath);
        intent.putExtra(ACTION_RECORDING_ROTATION, recordingRotation);
        intent.putExtra(ACTION_RECORDING_PRIORITY, recordingPriority);
        intent.putExtra(ACTION_RECORDING_MAX_DURATION, recordingMaxDuration);
        intent.putExtra(ACTION_RECORDING_RESOLUTION, recordingResolutionMode);
        return intent;
    }

    private @NonNull Intent createScreenStreamingIntent(int resultCode) {
        final Intent intent = new Intent(this, StreamingService.class);
        intent.setAction(ACTION_STREAMING_START);
        intent.putExtra(ACTION_RECORDING_RESULT_CODE, resultCode);
        intent.putExtra(ACTION_STREAMING_PORT, streamingPort);
        intent.putExtra(ACTION_RECORDING_MAX_DURATION, recordingMaxDuration);
        intent.putExtra(ACTION_RECORDING_RESOLUTION, recordingResolutionMode);
        return intent;
    }

    private void registerSettingsReceivers(List<Class<? extends BroadcastReceiver>> receiverClasses)
    {
        for (Class<? extends BroadcastReceiver> receiverClass: receiverClasses) {
            try {
                final BroadcastReceiver receiver = receiverClass.newInstance();
                IntentFilter filter = new IntentFilter(((HasAction) receiver).getAction());
                getApplicationContext().registerReceiver(receiver, filter);
            } catch (IllegalAccessException | InstantiationException e) {
                e.printStackTrace();
            }
        }
    }
}
