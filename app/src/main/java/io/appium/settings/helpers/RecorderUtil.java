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

package io.appium.settings.helpers;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.os.Build;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import java.io.IOException;
import java.util.Objects;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import static android.content.Context.WINDOW_SERVICE;
import static io.appium.settings.helpers.RecorderConstant.ACTION_RECORDING_MAX_DURATION;
import static io.appium.settings.helpers.RecorderConstant.ACTION_RECORDING_PRIORITY;
import static io.appium.settings.helpers.RecorderConstant.ACTION_RECORDING_RESOLUTION;
import static io.appium.settings.helpers.RecorderConstant.NO_RESOLUTION_MODE_SET;
import static io.appium.settings.helpers.RecorderConstant.RECORDING_DEFAULT_VIDEO_MIME_TYPE;
import static io.appium.settings.helpers.RecorderConstant.RECORDING_MAX_DURATION_DEFAULT_MS;
import static io.appium.settings.helpers.RecorderConstant.RECORDING_PRIORITY_DEFAULT;
import static io.appium.settings.helpers.RecorderConstant.RECORDING_PRIORITY_MAX;
import static io.appium.settings.helpers.RecorderConstant.RECORDING_PRIORITY_MIN;
import static io.appium.settings.helpers.RecorderConstant.RECORDING_PRIORITY_NORM;
import static io.appium.settings.helpers.RecorderConstant.RECORDING_RESOLUTION_480P;
import static io.appium.settings.helpers.RecorderConstant.RECORDING_RESOLUTION_DEFAULT;
import static io.appium.settings.helpers.RecorderConstant.RECORDING_RESOLUTION_FULL_HD;
import static io.appium.settings.helpers.RecorderConstant.RECORDING_RESOLUTION_HD;
import static io.appium.settings.helpers.RecorderConstant.RECORDING_RESOLUTION_LIST;
import static io.appium.settings.helpers.RecorderConstant.RECORDING_RESOLUTION_QCIF;
import static io.appium.settings.helpers.RecorderConstant.RECORDING_RESOLUTION_QVGA;
import static io.appium.settings.helpers.RecorderConstant.RECORDING_ROTATION_DEFAULT_DEGREE;

public class RecorderUtil {
    private static final String TAG = "RecorderUtil";

    public static boolean isLowerThanQ() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public static boolean areRecordingPermissionsGranted(Context context) {
        // Check if we have required permission
        int permissionAudio = ActivityCompat.checkSelfPermission(context,
                Manifest.permission.RECORD_AUDIO);

        return permissionAudio == PackageManager.PERMISSION_GRANTED
                && isMediaProjectionPermissionGranted(context);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public static boolean isMediaProjectionPermissionGranted(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();
            ApplicationInfo applicationInfo =
                    packageManager.getApplicationInfo(context.getPackageName(), 0);
            AppOpsManager appOpsManager =
                    (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            // AppOpsManager.OPSTR_PROJECT_MEDIA == "android:project_media" is a hidden field value,
            // so directly taken from https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/app/AppOpsManager.java#1532
            int mode = appOpsManager.unsafeCheckOpNoThrow("android:project_media",
                    applicationInfo.uid, applicationInfo.packageName);
            return (mode == AppOpsManager.MODE_ALLOWED);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Exception while checking media projection permission", e);
            return false;
        }
    }

    public static boolean isValidFileName(String filename) {
        if (filename == null || !filename.endsWith(".mp4")) {
            return false;
        }
        if (filename.length() >= 255) {
            return false;
        }
        for (char c : filename.toCharArray()) {
            if (!isValidFilenameChar(c)){
                return false;
            }
        }
        return true;
    }

    // taken from https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/os/FileUtils.java#995
    private static boolean isValidFilenameChar(char c) {
        switch (c) {
            case '"':
            case '*':
            case '/':
            case ':':
            case '<':
            case '>':
            case '?':
            case '\\':
            case '|':
            case '\0':
                return false;
            default:
                return true;
        }
    }

    public static int getDeviceRotationInDegree(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(WINDOW_SERVICE);
        if (wm == null) {
            return RECORDING_ROTATION_DEFAULT_DEGREE;
        }
        Display display = wm.getDefaultDisplay();
        if (display == null) {
            return RECORDING_ROTATION_DEFAULT_DEGREE;
        }
        int deviceRotation = display.getRotation();

        switch(deviceRotation) {
            case Surface.ROTATION_0:
                // portrait
                return 0;
            case Surface.ROTATION_90:
                // landscape left
                return 90;
            case Surface.ROTATION_180:
                // flipped portrait
                return 180;
            case Surface.ROTATION_270:
                // landscape right
                return 270;
            default:
                break;
        }
        return RECORDING_ROTATION_DEFAULT_DEGREE;
    }

    public static int getRecordingPriority(Intent intent) {
        if (intent.hasExtra(ACTION_RECORDING_PRIORITY)) {
            String userRequestedRecordingPriority =
                    intent.getStringExtra(ACTION_RECORDING_PRIORITY);

            if (userRequestedRecordingPriority == null) {
                Log.e(TAG, "Unable to retrieve recording priority from intent extras");
                return RECORDING_PRIORITY_DEFAULT;
            }

            switch(userRequestedRecordingPriority) {
                case RECORDING_PRIORITY_MAX:
                    return Thread.MAX_PRIORITY;
                case RECORDING_PRIORITY_MIN:
                    return Thread.MIN_PRIORITY;
                case RECORDING_PRIORITY_NORM:
                    return Thread.NORM_PRIORITY;
                default:
                    Log.e(TAG, "Invalid recording priority passed by user");
                    break;
            }
        } else {
            Log.e(TAG, "Unable to retrieve recording priority from intent");
        }
        return RECORDING_PRIORITY_DEFAULT;
    }

    public static int getRecordingMaxDuration(Intent intent) {
        if (intent.hasExtra(ACTION_RECORDING_MAX_DURATION)) {
            try {
                int userRequestedMaxDurationInSecond = Integer.parseInt(
                        Objects.requireNonNull(intent.getStringExtra(ACTION_RECORDING_MAX_DURATION))
                );
                if (userRequestedMaxDurationInSecond <= 0) {
                    Log.e(TAG, "Maximum recording duration must be greater than 0 second");
                    return RECORDING_MAX_DURATION_DEFAULT_MS;
                }
                // Convert it to millisecond and return
                return userRequestedMaxDurationInSecond * 1000;
            } catch (NumberFormatException e) {
                Log.e(TAG, "Exception while retrieving recording max duration", e);
            }
        } else {
            Log.e(TAG, "Unable to retrieve recording max duration");
        }
        return RECORDING_MAX_DURATION_DEFAULT_MS;
    }

    public static String getRecordingResolutionMode(Intent intent) {
        if (intent.hasExtra(ACTION_RECORDING_RESOLUTION)) {
            String userRequestedResolutionMode =
                    intent.getStringExtra(ACTION_RECORDING_RESOLUTION);
            if (userRequestedResolutionMode == null) {
                Log.e(TAG, "Unable to retrieve resolution mode from intent extras, " +
                        "using max supported resolution");
                return NO_RESOLUTION_MODE_SET;
            }
            return userRequestedResolutionMode;
        } else {
            Log.v(TAG, "Unable to retrieve resolution mode from intent, " +
                    "using max supported resolution");
        }
        return NO_RESOLUTION_MODE_SET;
    }

    public static Size getRecordingResolution(String userRequestedResolutionMode) {
        if (userRequestedResolutionMode == null) {
            Log.e(TAG, "Unable to retrieve resolution mode, " +
                    "using max supported resolution");
            return getSupportedMaxResolution();
        }
        if (userRequestedResolutionMode.isEmpty()) {
            Log.v(TAG, "Unable to retrieve resolution mode, " +
                    "using max supported resolution");
            return getSupportedMaxResolution();
        }
        // Split resolution mode (e.g 1920x1080) to it's width and height values,
        // lowercase 'x' or uppercase 'X' are both accepted/valid separator
        String[] resolutionWidthHeight = userRequestedResolutionMode
                .toLowerCase()
                .split("x");
        if (resolutionWidthHeight.length != 2) {
            Log.e(TAG, "Invalid resolution mode passed by user, " +
                    "using max supported resolution");
            return getSupportedMaxResolution();
        }

        try {
            int requestedResolutionWidth =
                    Integer.parseInt(resolutionWidthHeight[0]);
            int requestedResolutionHeight =
                    Integer.parseInt(resolutionWidthHeight[1]);

            Size requestedResolution =
                    new Size(requestedResolutionWidth, requestedResolutionHeight);

            // android.util.Size's equality check (equals(Object) method) also accounts width/height equality
            if (requestedResolution.equals(RECORDING_RESOLUTION_FULL_HD)) {
                return RECORDING_RESOLUTION_FULL_HD;
            } else if (requestedResolution.equals(RECORDING_RESOLUTION_HD)) {
                return RECORDING_RESOLUTION_HD;
            } else if (requestedResolution.equals(RECORDING_RESOLUTION_480P)) {
                return RECORDING_RESOLUTION_480P;
            } else if (requestedResolution.equals(RECORDING_RESOLUTION_QVGA)) {
                return RECORDING_RESOLUTION_QVGA;
            } else if (requestedResolution.equals(RECORDING_RESOLUTION_QCIF)) {
                return RECORDING_RESOLUTION_QCIF;
            } else {
                Log.e(TAG, "Invalid resolution mode passed by user, " +
                        "using max supported resolution");
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Exception while parsing resolution mode argument, " +
                    "using max supported resolution", e);
        }
        return getSupportedMaxResolution();
    }

    public static Size getSupportedMaxResolution() {
        try {
            MediaCodec videoEncoder =
                    MediaCodec.createEncoderByType(RECORDING_DEFAULT_VIDEO_MIME_TYPE);
            MediaCodecInfo.VideoCapabilities videoEncoderCapabilities = videoEncoder
                    .getCodecInfo().getCapabilitiesForType(RECORDING_DEFAULT_VIDEO_MIME_TYPE)
                    .getVideoCapabilities();

            for(Size resolution: RECORDING_RESOLUTION_LIST) {
                if (videoEncoderCapabilities.isSizeSupported(
                        resolution.getWidth(), resolution.getHeight())) {
                    return resolution;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Exception while retrieving default supported recording resolution", e);
        }

        return RECORDING_RESOLUTION_DEFAULT;
    }
}
