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

package io.appium.settings.media;

import android.annotation.SuppressLint;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Build;

import java.io.IOException;

import androidx.annotation.RequiresApi;

import io.appium.settings.recorder.RecorderConstant;

// Shared MediaCodec/AudioRecord configuration helpers, used by both the file-based
// RecorderThread and the live streaming sessions. Only the non-trivial codec/AudioRecord
// configuration is shared here; the drain loops differ enough (muxer write vs. queue push)
// that they are not extracted.
public class MediaCodecFactory {

    private MediaCodecFactory() {
    }

    public static MediaFormat createVideoEncoderFormat(String videoMime, int videoWidth,
                                                         int videoHeight, int videoBitrate,
                                                         int videoFrameRate) {
        MediaFormat encoderFormat = MediaFormat.createVideoFormat(videoMime, videoWidth,
                videoHeight);
        encoderFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        encoderFormat.setInteger(MediaFormat.KEY_BIT_RATE, videoBitrate);
        encoderFormat.setInteger(MediaFormat.KEY_FRAME_RATE, videoFrameRate);
        encoderFormat.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER,
                RecorderConstant.AUDIO_CODEC_REPEAT_PREV_FRAME_AFTER_MS);
        encoderFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,
                RecorderConstant.AUDIO_CODEC_I_FRAME_INTERVAL_MS);
        return encoderFormat;
    }

    public static MediaCodec createConfiguredVideoEncoder(String videoMime, MediaFormat format)
            throws IOException {
        MediaCodec encoder = MediaCodec.createEncoderByType(videoMime);
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        return encoder;
    }

    public static int calculateVideoBitrate(int width, int height, int frameRate) {
        return (int) (RecorderConstant.BITRATE_MULTIPLIER * frameRate * width * height);
    }

    public static MediaCodec createAacEncoder(int sampleRate, int channelCount, int bitrate)
            throws IOException {
        MediaFormat encoderFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate, channelCount);
        encoderFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);

        MediaCodec audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        audioEncoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        return audioEncoder;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission")
    public static AudioRecord createPlaybackCaptureAudioRecord(MediaProjection mediaProjection,
                                                                 int sampleRate) {
        int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        AudioFormat audioFormat = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build();

        AudioPlaybackCaptureConfiguration apcc =
                new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .build();

        return new AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(4 * minBufferSize)
                .setAudioPlaybackCaptureConfig(apcc)
                .build();
    }
}
