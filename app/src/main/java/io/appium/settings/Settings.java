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
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import java.io.File;
import java.nio.file.Paths;

import io.appium.settings.recorder.RecorderService;
import io.appium.settings.recorder.RecorderUtil;
import io.appium.settings.streaming.JpegStreamService;
import io.appium.settings.streaming.StreamingConstant;
import io.appium.settings.streaming.StreamingUtil;
import io.appium.settings.streaming.VideoStreamService;

import static io.appium.settings.recorder.RecorderConstant.ACTION_RECORDING_BASE;
import static io.appium.settings.recorder.RecorderConstant.ACTION_RECORDING_FILENAME;
import static io.appium.settings.recorder.RecorderConstant.ACTION_RECORDING_MAX_DURATION;
import static io.appium.settings.recorder.RecorderConstant.ACTION_RECORDING_PRIORITY;
import static io.appium.settings.recorder.RecorderConstant.ACTION_RECORDING_RESOLUTION;
import static io.appium.settings.recorder.RecorderConstant.ACTION_RECORDING_RESULT_CODE;
import static io.appium.settings.recorder.RecorderConstant.ACTION_RECORDING_ROTATION;
import static io.appium.settings.recorder.RecorderConstant.ACTION_RECORDING_START;
import static io.appium.settings.recorder.RecorderConstant.ACTION_RECORDING_STOP;
import static io.appium.settings.recorder.RecorderConstant.NO_PATH_SET;
import static io.appium.settings.recorder.RecorderConstant.NO_RESOLUTION_MODE_SET;
import static io.appium.settings.recorder.RecorderConstant.RECORDING_MAX_DURATION_DEFAULT_MS;
import static io.appium.settings.recorder.RecorderConstant.RECORDING_PRIORITY_DEFAULT;
import static io.appium.settings.recorder.RecorderConstant.RECORDING_ROTATION_DEFAULT_DEGREE;
import static io.appium.settings.recorder.RecorderConstant.REQUEST_CODE_SCREEN_CAPTURE;

// MediaProjection consent trampoline for recording, jpeg-stream and video-stream;
// singleInstance means a second am start while a dialog is pending needs onNewIntent, not just onCreate.
public class Settings extends Activity {
    private static final String TAG = "APPIUM SETTINGS";

    private String recordingOutputPath = NO_PATH_SET;
    private int recordingRotation = RECORDING_ROTATION_DEFAULT_DEGREE;
    private int recordingPriority = RECORDING_PRIORITY_DEFAULT;
    private int recordingMaxDuration = RECORDING_MAX_DURATION_DEFAULT_MS;
    private String recordingResolutionMode = NO_RESOLUTION_MODE_SET;

    private Intent pendingJpegStreamIntent;
    private Intent pendingVideoStreamIntent;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Log.d(TAG, "Entering the app");
        dispatch(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        dispatch(intent);
    }

    private void dispatch(Intent intent) {
        if (intent == null) {
            Log.e(TAG, "dispatch: Unable to retrieve intent instance");
            finishActivity();
            return;
        }

        String action = intent.getAction();
        boolean isMediaAction = action != null && (
                action.startsWith(ACTION_RECORDING_BASE)
                        || action.startsWith(StreamingConstant.ACTION_STREAMING_BASE));

        if (isMediaAction) {
            Log.d(TAG, "Skip starting foreground service");
        } else {
            // https://developer.android.com/about/versions/oreo/background-location-limits
            // The ForegroundService handles SettingsReceivers registration.
            startForegroundService(ForegroundService.getForegroundServiceIntent(Settings.this));
        }

        if (action != null && action.startsWith(StreamingConstant.ACTION_STREAMING_JPEG_BASE)) {
            handleJpegStreaming(intent);
        } else if (action != null && action.startsWith(StreamingConstant.ACTION_STREAMING_VIDEO_BASE)) {
            handleVideoStreaming(intent);
        } else {
            handleRecording(intent);
        }
    }

    private void handleRecording(Intent intent) {
        String recordingAction = intent.getAction();
        if (recordingAction == null) {
            Log.e(TAG, "handleRecording: Unable to retrieve intent.action instance");
            finishActivity();
            return;
        }

        if (!recordingAction.startsWith(ACTION_RECORDING_BASE)) {
            Log.i(TAG, "handleRecording: Received different intent with action: "
                    + recordingAction);
            finishActivity();
            return;
        }

        if (RecorderUtil.isLowerThanQ()) {
            Log.e(TAG, "handleRecording: Current Android OS Version is lower than Q");
            finishActivity();
            return;
        }

        if (!RecorderUtil.areRecordingPermissionsGranted(getApplicationContext())) {
            Log.e(TAG, "handleRecording: Required permissions are not granted");
            finishActivity();
            return;
        }

        if (recordingAction.equals(ACTION_RECORDING_START)) {
            String recordingFilename = intent.getStringExtra(ACTION_RECORDING_FILENAME);
            if (!RecorderUtil.isValidFileName(recordingFilename)) {
                Log.e(TAG, "handleRecording: Invalid filename passed by user: "
                        + recordingFilename);
                finishActivity();
                return;
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
                finishActivity();
                return;
            }

            recordingOutputPath = Paths
                    .get(externalStorageFile.getAbsolutePath(), recordingFilename)
                    .toAbsolutePath()
                    .toString();

            recordingRotation = RecorderUtil.getDeviceRotationInDegree(getApplicationContext());

            recordingPriority = RecorderUtil.getRecordingPriority(intent);

            recordingMaxDuration = RecorderUtil.getRecordingMaxDuration(intent);

            recordingResolutionMode = RecorderUtil.getRecordingResolutionMode(intent);

            // start record
            final MediaProjectionManager manager
                    = (MediaProjectionManager) getSystemService(
                    Context.MEDIA_PROJECTION_SERVICE);

            if (manager == null) {
                Log.e(TAG, "handleRecording: " +
                        "Unable to retrieve MediaProjectionManager instance");
                finishActivity();
                return;
            }

            final Intent permissionIntent = manager.createScreenCaptureIntent();

            startActivityForResult(permissionIntent, REQUEST_CODE_SCREEN_CAPTURE);
        } else if (recordingAction.equals(ACTION_RECORDING_STOP)) {
            // stop record
            final Intent recorderIntent = new Intent(this, RecorderService.class);
            recorderIntent.setAction(ACTION_RECORDING_STOP);
            startService(recorderIntent);

            finishActivity();
        } else {
            Log.e(TAG, "handleRecording: Unknown recording intent with action:"
                    + recordingAction);
            finishActivity();
        }
    }

    private void handleJpegStreaming(Intent intent) {
        String action = intent.getAction();

        if (StreamingConstant.ACTION_JPEG_STREAM_START.equals(action)) {
            if (RecorderUtil.isLowerThanQ()) {
                Log.e(TAG, "handleJpegStreaming: Current Android OS Version is lower than Q");
                finishActivity();
                return;
            }

            if (!StreamingUtil.areJpegStreamPermissionsGranted(getApplicationContext())) {
                Log.e(TAG, "handleJpegStreaming: Required permissions are not granted");
                finishActivity();
                return;
            }

            String socketName = intent.getStringExtra(StreamingConstant.EXTRA_SOCKET_NAME);
            if (!StreamingUtil.isValidSocketName(socketName)) {
                Log.e(TAG, "handleJpegStreaming: Invalid or missing socket name");
                finishActivity();
                return;
            }

            final MediaProjectionManager manager
                    = (MediaProjectionManager) getSystemService(
                    Context.MEDIA_PROJECTION_SERVICE);
            if (manager == null) {
                Log.e(TAG, "handleJpegStreaming: " +
                        "Unable to retrieve MediaProjectionManager instance");
                finishActivity();
                return;
            }

            pendingJpegStreamIntent = intent;

            startActivityForResult(manager.createScreenCaptureIntent(),
                    StreamingConstant.REQUEST_CODE_JPEG_STREAM_CAPTURE);
        } else if (StreamingConstant.ACTION_JPEG_STREAM_STOP.equals(action)) {
            final Intent stopIntent = new Intent(this, JpegStreamService.class);
            stopIntent.setAction(StreamingConstant.ACTION_JPEG_STREAM_STOP);
            startService(stopIntent);

            finishActivity();
        } else {
            Log.e(TAG, "handleJpegStreaming: Unknown streaming intent with action:" + action);
            finishActivity();
        }
    }

    private void handleVideoStreaming(Intent intent) {
        String action = intent.getAction();

        if (StreamingConstant.ACTION_VIDEO_STREAM_START.equals(action)) {
            if (RecorderUtil.isLowerThanQ()) {
                Log.e(TAG, "handleVideoStreaming: Current Android OS Version is lower than Q");
                finishActivity();
                return;
            }

            boolean audioEnabled = StreamingUtil.getBooleanExtra(intent,
                    StreamingConstant.EXTRA_AUDIO, false);
            if (!StreamingUtil.areVideoStreamPermissionsGranted(getApplicationContext(), audioEnabled)) {
                Log.e(TAG, "handleVideoStreaming: Required permissions are not granted");
                finishActivity();
                return;
            }

            String socketName = intent.getStringExtra(StreamingConstant.EXTRA_SOCKET_NAME);
            if (!StreamingUtil.isValidSocketName(socketName)) {
                Log.e(TAG, "handleVideoStreaming: Invalid or missing socket name");
                finishActivity();
                return;
            }

            final MediaProjectionManager manager
                    = (MediaProjectionManager) getSystemService(
                    Context.MEDIA_PROJECTION_SERVICE);
            if (manager == null) {
                Log.e(TAG, "handleVideoStreaming: " +
                        "Unable to retrieve MediaProjectionManager instance");
                finishActivity();
                return;
            }

            pendingVideoStreamIntent = intent;

            startActivityForResult(manager.createScreenCaptureIntent(),
                    StreamingConstant.REQUEST_CODE_VIDEO_STREAM_CAPTURE);
        } else if (StreamingConstant.ACTION_VIDEO_STREAM_STOP.equals(action)) {
            final Intent stopIntent = new Intent(this, VideoStreamService.class);
            stopIntent.setAction(StreamingConstant.ACTION_VIDEO_STREAM_STOP);
            startService(stopIntent);

            finishActivity();
        } else {
            Log.e(TAG, "handleVideoStreaming: Unknown streaming intent with action:" + action);
            finishActivity();
        }
    }

    private void finishActivity() {
        Log.d(TAG, "Closing the app");
        Handler handler = new Handler();
        handler.postDelayed(Settings.this::finish, 0);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_CODE_SCREEN_CAPTURE:
                handleRecordingActivityResult(resultCode, data);
                break;
            case StreamingConstant.REQUEST_CODE_JPEG_STREAM_CAPTURE:
                handleJpegStreamActivityResult(resultCode, data);
                break;
            case StreamingConstant.REQUEST_CODE_VIDEO_STREAM_CAPTURE:
                handleVideoStreamActivityResult(resultCode, data);
                break;
            default:
                Log.e(TAG, "onActivityResult: Received unknown request with code: " + requestCode);
                finishActivity();
        }
    }

    private void handleRecordingActivityResult(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            Log.e(TAG, "handleRecording: onActivityResult: " +
                    "MediaProjection permission is not granted, " +
                    "Did you apply appops adb command?");
            finishActivity();
            return;
        }

        final Intent intent = new Intent(this, RecorderService.class);
        intent.setAction(ACTION_RECORDING_START);
        intent.putExtra(ACTION_RECORDING_RESULT_CODE, resultCode);
        intent.putExtra(ACTION_RECORDING_FILENAME, recordingOutputPath);
        intent.putExtra(ACTION_RECORDING_ROTATION, recordingRotation);
        intent.putExtra(ACTION_RECORDING_PRIORITY, recordingPriority);
        intent.putExtra(ACTION_RECORDING_MAX_DURATION, recordingMaxDuration);
        intent.putExtra(ACTION_RECORDING_RESOLUTION, recordingResolutionMode);
        intent.putExtras(data);

        startService(intent);

        finishActivity();
    }

    private void handleJpegStreamActivityResult(int resultCode, Intent data) {
        final Intent pending = pendingJpegStreamIntent;
        pendingJpegStreamIntent = null;

        if (resultCode != Activity.RESULT_OK || pending == null) {
            Log.e(TAG, "handleJpegStreaming: onActivityResult: " +
                    "MediaProjection permission is not granted, " +
                    "Did you apply appops adb command?");
            finishActivity();
            return;
        }

        final Intent intent = new Intent(this, JpegStreamService.class);
        intent.setAction(StreamingConstant.ACTION_JPEG_STREAM_START);
        intent.putExtra(StreamingConstant.EXTRA_RESULT_CODE, resultCode);
        intent.putExtras(pending);
        intent.putExtras(data);

        startService(intent);

        finishActivity();
    }

    private void handleVideoStreamActivityResult(int resultCode, Intent data) {
        final Intent pending = pendingVideoStreamIntent;
        pendingVideoStreamIntent = null;

        if (resultCode != Activity.RESULT_OK || pending == null) {
            Log.e(TAG, "handleVideoStreaming: onActivityResult: " +
                    "MediaProjection permission is not granted, " +
                    "Did you apply appops adb command?");
            finishActivity();
            return;
        }

        final Intent intent = new Intent(this, VideoStreamService.class);
        intent.setAction(StreamingConstant.ACTION_VIDEO_STREAM_START);
        intent.putExtra(StreamingConstant.EXTRA_RESULT_CODE, resultCode);
        intent.putExtras(pending);
        intent.putExtras(data);

        startService(intent);

        finishActivity();
    }
}
