package io.appium.settings.streaming;

/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Writes an IVF file.
 *
 * IVF format is a simple container format for VP8 encoded frames.
 */
public class IvfWriter {
    /**
     * Makes a 32 byte file header for IVF format.
     *
     * Timebase fraction is in format scale/rate, e.g. 1/1000
     *
     * @param frameCount total number of frames file contains
     * @param width      frame width
     * @param height     frame height
     * @param scale      timebase scale (or numerator of the timebase fraction)
     * @param rate       timebase rate (or denominator of the timebase fraction)
     */
    public static byte[] makeIvfHeader(int frameCount, int width, int height, int scale, int rate){
        byte[] ivfHeader = new byte[32];
        ivfHeader[0] = 'D';
        ivfHeader[1] = 'K';
        ivfHeader[2] = 'I';
        ivfHeader[3] = 'F';
        lay16Bits(ivfHeader, 4, 0);  // version
        lay16Bits(ivfHeader, 6, 32);  // header size
        ivfHeader[8] = 'V';  // fourcc
        ivfHeader[9] = 'P';
        ivfHeader[10] = '8';
        ivfHeader[11] = '0';
        lay16Bits(ivfHeader, 12, width);
        lay16Bits(ivfHeader, 14, height);
        lay32Bits(ivfHeader, 16, rate);  // scale/rate
        lay32Bits(ivfHeader, 20, scale);
        lay32Bits(ivfHeader, 24, frameCount);
        lay32Bits(ivfHeader, 28, 0);  // unused
        return ivfHeader;
    }
    /**
     * Makes a 12 byte header for an encoded frame.
     *
     * @param size      frame size
     * @param timestamp presentation timestamp of the frame
     */
    public static byte[] makeIvfFrameHeader(int size, long timestamp){
        byte[] frameHeader = new byte[12];
        lay32Bits(frameHeader, 0, size);
        lay64bits(frameHeader, 4, timestamp);
        return frameHeader;
    }
    /**
     * Lays least significant 16 bits of an int into 2 items of a byte array.
     *
     * Note that ordering is little-endian.
     *
     * @param array     the array to be modified
     * @param index     index of the array to start laying down
     * @param value     the integer to use least significant 16 bits
     */
    private static void lay16Bits(byte[] array, int index, int value){
        array[index] = (byte) (value);
        array[index + 1] = (byte) (value >> 8);
    }
    /**
     * Lays an int into 4 items of a byte array.
     *
     * Note that ordering is little-endian.
     *
     * @param array     the array to be modified
     * @param index     index of the array to start laying down
     * @param value     the integer to use
     */
    private static void lay32Bits(byte[] array, int index, int value){
        for (int i = 0; i < 4; i++){
            array[index + i] = (byte) (value >> (i * 8));
        }
    }
    /**
     * Lays a long int into 8 items of a byte array.
     *
     * Note that ordering is little-endian.
     *
     * @param array     the array to be modified
     * @param index     index of the array to start laying down
     * @param value     the integer to use
     */
    private static void lay64bits(byte[] array, int index, long value){
        for (int i = 0; i < 8; i++){
            array[index + i] = (byte) (value >> (i * 8));
        }
    }
}
