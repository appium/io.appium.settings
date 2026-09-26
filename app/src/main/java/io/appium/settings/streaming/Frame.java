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

/**
 * A single unit of streamed data (a JPEG image, a video NAL access unit, or an
 * ADTS-framed AAC audio access unit) queued for delivery to the connected client.
 */
public class Frame {
    public static final byte FLAG_CONFIG = 0x01;
    public static final byte FLAG_KEYFRAME = 0x02;
    public static final byte FLAG_EOS = 0x04;

    public enum Track {
        JPEG((byte) 0),
        VIDEO((byte) 1),
        AUDIO((byte) 2);

        public final byte value;

        Track(byte value) {
            this.value = value;
        }
    }

    public final Track track;
    public final byte flags;
    public final long sequence;
    public final long timestampMicros;
    public final byte[] payload;

    public Frame(Track track, byte flags, long sequence, long timestampMicros, byte[] payload) {
        this.track = track;
        this.flags = flags;
        this.sequence = sequence;
        this.timestampMicros = timestampMicros;
        this.payload = payload;
    }
}
