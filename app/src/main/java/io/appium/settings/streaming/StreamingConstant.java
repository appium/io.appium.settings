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

import io.appium.settings.BuildConfig;

public class StreamingConstant {
    public static final String ACTION_STREAMING_BASE = BuildConfig.APPLICATION_ID + ".streaming";
    public static final String ACTION_STREAMING_JPEG_BASE = ACTION_STREAMING_BASE + ".jpeg";
    public static final String ACTION_STREAMING_VIDEO_BASE = ACTION_STREAMING_BASE + ".video";

    public static final String ACTION_JPEG_STREAM_START = ACTION_STREAMING_JPEG_BASE + ".ACTION_START";
    public static final String ACTION_JPEG_STREAM_STOP = ACTION_STREAMING_JPEG_BASE + ".ACTION_STOP";
    public static final String ACTION_VIDEO_STREAM_START = ACTION_STREAMING_VIDEO_BASE + ".ACTION_START";
    public static final String ACTION_VIDEO_STREAM_STOP = ACTION_STREAMING_VIDEO_BASE + ".ACTION_STOP";

    public static final int REQUEST_CODE_JPEG_STREAM_CAPTURE = 124;
    public static final int REQUEST_CODE_VIDEO_STREAM_CAPTURE = 125;

    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_SOCKET_NAME = "socket_name";
    public static final String EXTRA_FPS = "fps";
    public static final String EXTRA_QUALITY = "quality";
    public static final String EXTRA_SCALE = "scale";
    public static final String EXTRA_CODEC = "codec";
    public static final String EXTRA_BITRATE = "bitrate";
    public static final String EXTRA_RESOLUTION = "resolution";
    public static final String EXTRA_AUDIO = "audio";

    public static final String CODEC_H264 = "h264";
    public static final String CODEC_HEVC = "hevc";

    public static final int JPEG_FPS_DEFAULT = 60;
    public static final int JPEG_QUALITY_DEFAULT = 80;
    public static final int JPEG_SCALE_DEFAULT = 100;

    public static final int VIDEO_FPS_DEFAULT = 30;
    public static final int VIDEO_BITRATE_DEFAULT = 4_000_000;
    public static final int VIDEO_BITRATE_MIN = 100_000;
    public static final int VIDEO_BITRATE_MAX = 50_000_000;

    public static final int AUDIO_SAMPLE_RATE_HZ = 44100;
    public static final int AUDIO_CHANNEL_COUNT = 1;
    public static final int AUDIO_BITRATE_DEFAULT = 64000;

    public static final int MAX_BUFFERED_FRAMES = 60;
    public static final long ACCEPT_TIMEOUT_MS = 10_000;
    public static final long DRAIN_TIMEOUT_US = 10_000;

    private StreamingConstant() {
    }
}
