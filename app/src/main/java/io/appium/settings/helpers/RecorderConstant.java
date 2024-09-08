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

import android.media.MediaFormat;
import android.util.Size;

import java.util.Arrays;
import java.util.List;

import io.appium.settings.BuildConfig;

public class RecorderConstant {
    public static final int REQUEST_CODE_SCREEN_CAPTURE = 123;
    public static final int REQUEST_CODE_SCREEN_STREAM = 124;
    public static final String ACTION_RECORDING_BASE = BuildConfig.APPLICATION_ID + ".recording";
    public static final String ACTION_STREAMING_BASE = BuildConfig.APPLICATION_ID + ".streaming";
    public static final String ACTION_RECORDING_START = ACTION_RECORDING_BASE + ".ACTION_START";
    public static final String ACTION_RECORDING_STOP = ACTION_RECORDING_BASE + ".ACTION_STOP";
    public static final String ACTION_STREAMING_START = ACTION_STREAMING_BASE + ".ACTION_START";
    public static final String ACTION_STREAMING_STOP = ACTION_STREAMING_BASE + ".ACTION_STOP";
    public static final String ACTION_RECORDING_RESULT_CODE = "result_code";
    public static final String ACTION_RECORDING_ROTATION = "recording_rotation";
    public static final String ACTION_RECORDING_FILENAME = "filename";
    public static final String ACTION_STREAMING_PORT = "port";
    public static final String ACTION_RECORDING_PRIORITY = "priority";
    public static final String ACTION_RECORDING_MAX_DURATION = "max_duration_sec";
    public static final String ACTION_RECORDING_RESOLUTION = "resolution";
    public static final String RECORDING_DEFAULT_VIDEO_MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;
    public static final Size RECORDING_RESOLUTION_FULL_HD = new Size(1920, 1080);
    public static final Size RECORDING_RESOLUTION_HD = new Size(1280, 720);
    public static final Size RECORDING_RESOLUTION_480P = new Size(720, 480);
    public static final Size RECORDING_RESOLUTION_QVGA = new Size(320, 240);
    public static final Size RECORDING_RESOLUTION_QCIF = new Size(176, 144);
    public static final Size RECORDING_RESOLUTION_DEFAULT = new Size(1920, 1080);
    public static final float BITRATE_MULTIPLIER = 0.25f;
    public static final int AUDIO_CODEC_SAMPLE_RATE_HZ = 44100;
    public static final int AUDIO_CODEC_CHANNEL_COUNT = 1;
    public static final int AUDIO_CODEC_REPEAT_PREV_FRAME_AFTER_MS = 1000000;
    public static final int AUDIO_CODEC_I_FRAME_INTERVAL_MS = 5;
    public static final int AUDIO_CODEC_DEFAULT_BITRATE = 64000;
    public static final int VIDEO_CODEC_DEFAULT_FRAME_RATE = 30;
    public static final long MEDIA_QUEUE_BUFFERING_DEFAULT_TIMEOUT_MS = 10000;
    public static final long NANOSECONDS_IN_MICROSECOND = 1000;
    public static final String NO_PATH_SET = "";
    public static final long NO_TIMESTAMP_SET = -1;
    // Assume 0 degree == portrait as default
    public static final int RECORDING_ROTATION_DEFAULT_DEGREE = 0;
    public static final int NO_TRACK_INDEX_SET = -1;
    public static final String NO_RESOLUTION_MODE_SET = "";
    public static final String RECORDING_PRIORITY_MAX = "high";
    public static final String RECORDING_PRIORITY_NORM = "normal";
    public static final String RECORDING_PRIORITY_MIN = "low";
    public static final int RECORDING_PRIORITY_DEFAULT = Thread.MAX_PRIORITY;
    public static final int RECORDING_MAX_DURATION_DEFAULT_MS = 15 * 60 * 1000; // 15 Minutes, in milliseconds
    /*
     * Note: Reason we limit recording to following resolution list is that
     * android's AVC/H264 video encoder capabilities varies device-to-device (OEM modifications)
     * and with values larger than 1920x1080, isSizeSupported(width, height) method (see https://developer.android.com/reference/android/media/MediaCodecInfo.VideoCapabilities#isSizeSupported(int,%20int))
     * returns false on tested devices and also with arbitrary values between supported range,
     * sometimes MediaEncoder.configure() method crashes with an exception on some phones
     * also default supported resolutions as per CTS tests are limited to following values (values between 176x144 and 1920x1080)
     * see https://android.googlesource.com/platform/cts/+/refs/heads/android12-qpr1-release/tests/tests/media/src/android/media/cts/VideoEncoderTest.java#1766
     * because of these reasons, to support wide variety of devices, we pre-limit resolution modes to the following values
     */
    public static final List<Size> RECORDING_RESOLUTION_LIST =
            Arrays.asList(
                    new Size(1920, 1080),
                    new Size(1280, 720),
                    new Size(720, 480),
                    new Size(320, 240),
                    new Size(176, 144)
            );
    // 1048576 Bps == 1 Mbps (1024*1024)
    public static final float BPS_IN_MBPS = 1048576f;
}
