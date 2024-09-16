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
package com.dynatrace.suffixarray;

import static com.dynatrace.serialization.Serialization.checkSerialVersion;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.annotation.concurrent.ThreadSafe;
import org.jsuffixarrays.SuffixArrays;

/**
 * This class extends the Suffix Array implementation described in <a
 * href="https://github.com/carrotsearch/jsuffixarrays/tree/master">https://github.com/carrotsearch/jsuffixarrays/tree/master</a>
 * with a search method.
 */
@ThreadSafe
public final class SuffixArray {

    private static final byte SERIAL_VERSION_V0 = 0;
    private final CharSequence cs;
    private int[] suffixArray = null;

    /**
     * Creates a wrapper for a Suffix Array from a string. Note that this constructor does not build
     * yet the suffix array data structure. To do so, use the {@link SuffixArray#construct()}
     * method.
     *
     * @param input The string encoded in UTF-8.
     */
    public SuffixArray(CharSequence input) {
        this.cs = input;
    }

    private SuffixArray(CharSequence input, int[] suffixes) {
        this.cs = input;
        this.suffixArray = suffixes;
    }

    private static int compareTo(
            CharSequence s1, int from1, int to1, CharSequence s2, int from2, int to2) {
        int lim = Math.min(to1 - from1, to2 - from2);
        for (int k = 0; k < lim; k++) {

            char c1 = s1.charAt(from1 + k);
            char c2 = s2.charAt(from2 + k);
            if (c1 != c2) {
                return c1 - c2;
            }
        }
        return (to1 - from1) - (to2 - from2);
    }

    private static boolean startsWith(
            CharSequence s1, int from1, int to1, CharSequence s2, int from2, int to2) {
        int len1 = to1 - from1;
        int len2 = to2 - from2;
        if (len2 > len1) {
            return false;
        }
        int lim = Math.min(len1, len2);
        int k = 0;
        while (k < lim) {
            if (s1.charAt(from1 + k) != s2.charAt(from2 + k)) {
                return false;
            }
            ++k;
        }
        return true;
    }

    /** Method to build the suffix array using the QSufSort method. */
    public void construct() {
        this.suffixArray = SuffixArrays.create(cs);
    }

    /**
     * Finds all matches of a given pattern in a suffix array previously built with {@link
     * SuffixArray#construct()}.
     *
     * @param pattern The string encoded in UTF-8 to be queried
     * @return The number of matches found
     */
    public int count(CharSequence pattern) {
        int left = searchUpperInterval(pattern);
        int right = searchLowerInterval(pattern, left);
        return right - left;
    }

    /**
     * Finds all locations of a given pattern in a suffix array previously built with {@link
     * SuffixArray#construct()}.
     *
     * @param pattern The string encoded in UTF-8 to be queried
     * @param offsets An array where the matches will be written to. Only as many matches as fit in
     *     the array will be written.
     * @return The number of matches found or the length of the provided offsets array if more
     *     matches are found
     */
    public int locate(CharSequence pattern, int[] offsets) {
        int left = searchUpperInterval(pattern);
        int right = searchLowerInterval(pattern, left);
        int matches = right - left;
        int k = 0;
        for (int i = left; i < right; i++) {
            if (k == offsets.length) {
                return k;
            }
            offsets[k] = this.suffixArray[i];
            ++k;
        }
        return matches;
    }

    private int searchUpperInterval(CharSequence p) {
        int l = 0;
        int r = this.suffixArray.length - 1;
        while (l < r) {
            int mid = Math.floorDiv((l + r), 2);
            if (compareTo(p, 0, p.length(), cs, this.suffixArray[mid], cs.length()) > 0) {
                l = mid + 1;
            } else {
                r = mid;
            }
        }
        return l;
    }

    private int searchLowerInterval(CharSequence p, int upperInterval) {
        int r = this.suffixArray.length - 1;
        while (upperInterval < r) {
            int mid = Math.floorDiv((upperInterval + r), 2);
            if (startsWith(cs, this.suffixArray[mid], cs.length(), p, 0, p.length())) {
                upperInterval = mid + 1;
            } else {
                r = mid;
            }
        }
        return r;
    }

    /**
     * Gets the actual suffix integer array.
     *
     * @return The suffix array
     */
    public int[] getSuffixArray() {
        return this.suffixArray;
    }

    /**
     * Serializes this object to an {@code ObjectOutput} stream.
     *
     * @param objectOutput The stream to which the object will be written
     */
    public void write(ObjectOutput objectOutput) throws IOException {
        objectOutput.writeByte(SERIAL_VERSION_V0);
        byte[] bytes = cs.toString().getBytes(StandardCharsets.UTF_8);
        objectOutput.writeInt(bytes.length);
        objectOutput.write(bytes);
        objectOutput.writeInt(suffixArray.length);
        for (int v : suffixArray) {
            objectOutput.writeInt(v);
        }
    }

    /**
     * Deserializes a {@code SuffixArray} from a {@code ObjectInput} stream.
     *
     * @param objectInput The stream from which to read from
     * @return The deserialized instance of this object
     */
    public static SuffixArray read(ObjectInput objectInput) throws IOException {
        checkSerialVersion(SERIAL_VERSION_V0, objectInput.readByte());
        byte[] bytes = new byte[objectInput.readInt()];
        objectInput.readFully(bytes, 0, bytes.length);
        int[] suffixes = new int[objectInput.readInt()];
        for (int i = 0; i < suffixes.length; i++) {
            suffixes[i] = objectInput.readInt();
        }

        return new SuffixArray(new String(bytes), suffixes);
    }

    @Override
    public int hashCode() {
        return this.cs.hashCode() + Arrays.hashCode(this.suffixArray);
    }
}
