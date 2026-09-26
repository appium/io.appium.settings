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
 * Wraps raw AAC-LC access units (as produced by MediaCodec) with a 7-byte ADTS
 * header, so concatenated audio payloads form a standalone, decodable ADTS stream
 * on the wire (no separate out-of-band codec config needed for the audio track).
 */
public class AdtsHelper {
    private static final int[] SAMPLE_RATES = {
            96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050,
            16000, 12000, 11025, 8000, 7350
    };
    private static final int AAC_LC_PROFILE = 2;

    private AdtsHelper() {
    }

    public static byte[] addAdtsHeader(byte[] aacPayload, int sampleRate, int channelCount) {
        int packetLength = aacPayload.length + 7;
        byte[] packet = new byte[packetLength];
        int freqIdx = getSampleRateIndex(sampleRate);

        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9; // MPEG-4, Layer 0, no CRC
        packet[2] = (byte) (((AAC_LC_PROFILE - 1) << 6) + (freqIdx << 2) + (channelCount >> 2));
        packet[3] = (byte) (((channelCount & 3) << 6) + (packetLength >> 11));
        packet[4] = (byte) ((packetLength & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLength & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;

        System.arraycopy(aacPayload, 0, packet, 7, aacPayload.length);
        return packet;
    }

    private static int getSampleRateIndex(int sampleRate) {
        for (int i = 0; i < SAMPLE_RATES.length; i++) {
            if (SAMPLE_RATES[i] == sampleRate) {
                return i;
            }
        }
        return 4; // default: 44100 Hz
    }
}
