/*
 * Copyright 2024 Dynatrace LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dynatrace.intsequence;

/** This class contains some lookup tables and bit manipulation functions. */
public final class Common {

    private Common() {
        // Utility class
    }

    /** Table containing all increasing binary values which have all lower order bits set. */
    public static final long[] LOW_BITS_SET = {
        0x0000000000000000L,
        0x0000000000000001L,
        0x0000000000000003L,
        0x0000000000000007L,
        0x000000000000000FL,
        0x000000000000001FL,
        0x000000000000003FL,
        0x000000000000007FL,
        0x00000000000000FFL,
        0x00000000000001FFL,
        0x00000000000003FFL,
        0x00000000000007FFL,
        0x0000000000000FFFL,
        0x0000000000001FFFL,
        0x0000000000003FFFL,
        0x0000000000007FFFL,
        0x000000000000FFFFL,
        0x000000000001FFFFL,
        0x000000000003FFFFL,
        0x000000000007FFFFL,
        0x00000000000FFFFFL,
        0x00000000001FFFFFL,
        0x00000000003FFFFFL,
        0x00000000007FFFFFL,
        0x0000000000FFFFFFL,
        0x0000000001FFFFFFL,
        0x0000000003FFFFFFL,
        0x0000000007FFFFFFL,
        0x000000000FFFFFFFL,
        0x000000001FFFFFFFL,
        0x000000003FFFFFFFL,
        0x000000007FFFFFFFL,
        0x00000000FFFFFFFFL,
        0x00000001FFFFFFFFL,
        0x00000003FFFFFFFFL,
        0x00000007FFFFFFFFL,
        0x0000000FFFFFFFFFL,
        0x0000001FFFFFFFFFL,
        0x0000003FFFFFFFFFL,
        0x0000007FFFFFFFFFL,
        0x000000FFFFFFFFFFL,
        0x000001FFFFFFFFFFL,
        0x000003FFFFFFFFFFL,
        0x000007FFFFFFFFFFL,
        0x00000FFFFFFFFFFFL,
        0x00001FFFFFFFFFFFL,
        0x00003FFFFFFFFFFFL,
        0x00007FFFFFFFFFFFL,
        0x0000FFFFFFFFFFFFL,
        0x0001FFFFFFFFFFFFL,
        0x0003FFFFFFFFFFFFL,
        0x0007FFFFFFFFFFFFL,
        0x000FFFFFFFFFFFFFL,
        0x001FFFFFFFFFFFFFL,
        0x003FFFFFFFFFFFFFL,
        0x007FFFFFFFFFFFFFL,
        0x00FFFFFFFFFFFFFFL,
        0x01FFFFFFFFFFFFFFL,
        0x03FFFFFFFFFFFFFFL,
        0x07FFFFFFFFFFFFFFL,
        0x0FFFFFFFFFFFFFFFL,
        0x1FFFFFFFFFFFFFFFL,
        0x3FFFFFFFFFFFFFFFL,
        0x7FFFFFFFFFFFFFFFL,
        0xFFFFFFFFFFFFFFFFL
    };

    /** Table containing all increasing binary values which have all lower order bits set. */
    public static final long[] HIGH_BITS_SET = {
        ~0x0000000000000000L,
        ~0x0000000000000001L,
        ~0x0000000000000003L,
        ~0x0000000000000007L,
        ~0x000000000000000FL,
        ~0x000000000000001FL,
        ~0x000000000000003FL,
        ~0x000000000000007FL,
        ~0x00000000000000FFL,
        ~0x00000000000001FFL,
        ~0x00000000000003FFL,
        ~0x00000000000007FFL,
        ~0x0000000000000FFFL,
        ~0x0000000000001FFFL,
        ~0x0000000000003FFFL,
        ~0x0000000000007FFFL,
        ~0x000000000000FFFFL,
        ~0x000000000001FFFFL,
        ~0x000000000003FFFFL,
        ~0x000000000007FFFFL,
        ~0x00000000000FFFFFL,
        ~0x00000000001FFFFFL,
        ~0x00000000003FFFFFL,
        ~0x00000000007FFFFFL,
        ~0x0000000000FFFFFFL,
        ~0x0000000001FFFFFFL,
        ~0x0000000003FFFFFFL,
        ~0x0000000007FFFFFFL,
        ~0x000000000FFFFFFFL,
        ~0x000000001FFFFFFFL,
        ~0x000000003FFFFFFFL,
        ~0x000000007FFFFFFFL,
        ~0x00000000FFFFFFFFL,
        ~0x00000001FFFFFFFFL,
        ~0x00000003FFFFFFFFL,
        ~0x00000007FFFFFFFFL,
        ~0x0000000FFFFFFFFFL,
        ~0x0000001FFFFFFFFFL,
        ~0x0000003FFFFFFFFFL,
        ~0x0000007FFFFFFFFFL,
        ~0x000000FFFFFFFFFFL,
        ~0x000001FFFFFFFFFFL,
        ~0x000003FFFFFFFFFFL,
        ~0x000007FFFFFFFFFFL,
        ~0x00000FFFFFFFFFFFL,
        ~0x00001FFFFFFFFFFFL,
        ~0x00003FFFFFFFFFFFL,
        ~0x00007FFFFFFFFFFFL,
        ~0x0000FFFFFFFFFFFFL,
        ~0x0001FFFFFFFFFFFFL,
        ~0x0003FFFFFFFFFFFFL,
        ~0x0007FFFFFFFFFFFFL,
        ~0x000FFFFFFFFFFFFFL,
        ~0x001FFFFFFFFFFFFFL,
        ~0x003FFFFFFFFFFFFFL,
        ~0x007FFFFFFFFFFFFFL,
        ~0x00FFFFFFFFFFFFFFL,
        ~0x01FFFFFFFFFFFFFFL,
        ~0x03FFFFFFFFFFFFFFL,
        ~0x07FFFFFFFFFFFFFFL,
        ~0x0FFFFFFFFFFFFFFFL,
        ~0x1FFFFFFFFFFFFFFFL,
        ~0x3FFFFFFFFFFFFFFFL,
        ~0x7FFFFFFFFFFFFFFFL,
        ~0xFFFFFFFFFFFFFFFFL
    };

    /**
     * Computes the minimum number of bits required to store a long value.
     *
     * @param value The largest value that we need to represent
     * @return The minimum number of bits
     */
    public static int minimumNumberOfBits(long value) {
        if (value == 0) {
            return 1;
        }
        int flooredLog2 = log2LookupTable(value);
        return flooredLog2 + 1;
    }

    private static final int[] TAB64 = {
        63, 0, 58, 1, 59, 47, 53, 2,
        60, 39, 48, 27, 54, 33, 42, 3,
        61, 51, 37, 40, 49, 18, 28, 20,
        55, 30, 34, 11, 43, 14, 22, 4,
        62, 57, 46, 52, 38, 26, 32, 41,
        50, 36, 17, 19, 29, 10, 13, 21,
        56, 45, 25, 31, 35, 16, 9, 12,
        44, 24, 15, 8, 23, 7, 6, 5
    };

    /**
     * Computes the logarithm base two of a long value.
     *
     * @param value The value for which we want to compute the logarithm base two
     * @return The logarithm base two of the value
     */
    public static int log2LookupTable(long value) {
        value |= value >>> 1;
        value |= value >>> 2;
        value |= value >>> 4;
        value |= value >>> 8;
        value |= value >>> 16;
        value |= value >>> 32;
        return TAB64[(int) ((((value - (value >>> 1)) * 0x07EDD5E59A4E28C2L)) >>> 58)];
    }
}
