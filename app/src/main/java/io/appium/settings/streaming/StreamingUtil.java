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

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import java.util.regex.Pattern;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import io.appium.settings.recorder.RecorderUtil;

public class StreamingUtil {
    private static final String TAG = "StreamingUtil";
    private static final Pattern VALID_SOCKET_NAME = Pattern.compile("^[a-zA-Z0-9._-]{1,200}$");

    private StreamingUtil() {
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public static boolean areJpegStreamPermissionsGranted(Context context) {
        return RecorderUtil.isMediaProjectionPermissionGranted(context);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public static boolean areVideoStreamPermissionsGranted(Context context, boolean requiresAudio) {
        if (!RecorderUtil.isMediaProjectionPermissionGranted(context)) {
            return false;
        }
        if (!requiresAudio) {
            return true;
        }
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isValidSocketName(String socketName) {
        return socketName != null && VALID_SOCKET_NAME.matcher(socketName).matches();
    }

    public static int getIntExtra(Intent intent, String name, int defaultValue, int min, int max) {
        if (!intent.hasExtra(name)) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(intent.getStringExtra(name));
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException | NullPointerException e) {
            Log.e(TAG, "Invalid value passed for extra " + name + ", using default", e);
            return defaultValue;
        }
    }

    public static boolean getBooleanExtra(Intent intent, String name, boolean defaultValue) {
        if (!intent.hasExtra(name)) {
            return defaultValue;
        }
        return Boolean.parseBoolean(intent.getStringExtra(name));
    }

    public static String getVideoCodecMime(Intent intent) {
        String codec = intent.getStringExtra(StreamingConstant.EXTRA_CODEC);
        return StreamingConstant.CODEC_HEVC.equalsIgnoreCase(codec)
                ? MediaFormat.MIMETYPE_VIDEO_HEVC
                : MediaFormat.MIMETYPE_VIDEO_AVC;
    }
}
