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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Writer for the streaming wire protocol: a fixed 28-byte, big-endian header
 * followed by the raw payload bytes.
 *
 * <pre>
 * offset  size  field
 * 0       4     magic ("APST")
 * 4       1     version (1)
 * 5       1     track (0=JPEG, 1=VIDEO, 2=AUDIO)
 * 6       1     flags (bit0 CONFIG, bit1 KEYFRAME, bit2 END_OF_STREAM)
 * 7       1     reserved
 * 8       8     sequence (monotonic per track)
 * 16      8     timestampMicros (since session start)
 * 24      4     payloadLength
 * </pre>
 */
public class StreamProtocol {
    private static final byte[] MAGIC = {'A', 'P', 'S', 'T'};
    private static final byte VERSION = 1;
    private static final int HEADER_SIZE = 28;

    private StreamProtocol() {
    }

    public static void writeFrame(OutputStream out, Frame frame) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN);
        header.put(MAGIC)
                .put(VERSION)
                .put(frame.track.value)
                .put(frame.flags)
                .put((byte) 0)
                .putLong(frame.sequence)
                .putLong(frame.timestampMicros)
                .putInt(frame.payload.length);
        out.write(header.array());
        out.write(frame.payload);
        out.flush();
    }
}
