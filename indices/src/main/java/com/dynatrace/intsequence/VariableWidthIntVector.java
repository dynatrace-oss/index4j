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

import static com.dynatrace.intsequence.Common.HIGH_BITS_SET;
import static com.dynatrace.intsequence.Common.LOW_BITS_SET;
import static com.dynatrace.intsequence.Common.minimumNumberOfBits;
import static com.dynatrace.serialization.Serialization.checkSerialVersion;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;

/** This class implements a vector of integers of variable width. */
public final class VariableWidthIntVector {

    private static final byte SERIAL_VERSION_V0 = 0;
    private static final int WORD_SIZE = Long.BYTES * 8;
    private final long[] data;

    /**
     * Creates an empty vector that stores {@code bitsSize} bits and that can be filled with
     * variable width values.
     *
     * @param bitsSize The total size of the bit sequence
     */
    public VariableWidthIntVector(long bitsSize) {
        if (bitsSize % 64 == 0) {
            this.data = new long[(int) (bitsSize / 64)];
        } else {
            this.data = new long[(int) (bitsSize / 64 + 1)];
        }
    }

    private VariableWidthIntVector(long[] words) {
        this.data = words;
    }

    /**
     * Sets the value at {@code position} with {@value} using the minimum number of bits possible.
     *
     * @param position The absolute position in bit counts
     * @param value The value to write
     */
    public void setValue(long position, long value) {
        int elementWidth = minimumNumberOfBits(value);
        int wordIndex =
                (int) (position >>> 6); // the index of the current word in counts of 64 bits
        int offset = (int) (position & 63); // offset modulo 63 to see if we cross a word boundary

        // remove unwanted bits
        value = value & LOW_BITS_SET[elementWidth];

        // check if we fit in this word or if we need the next one as well
        if (offset + elementWidth < WORD_SIZE) {
            // in case we are overwriting values
            data[wordIndex] =
                    data[wordIndex]
                            & ((0xFFFFFFFFFFFFFFFFL << (offset + elementWidth))
                                    | LOW_BITS_SET[offset]);
            data[wordIndex] = data[wordIndex] | (value << offset);
        } else {
            data[wordIndex] = data[wordIndex] & LOW_BITS_SET[offset];
            data[wordIndex] = data[wordIndex] | (value << offset);
            if (((offset + elementWidth) & 63) > 0) {
                offset = (offset + elementWidth) & 63;
                data[wordIndex + 1] = data[wordIndex + 1] & HIGH_BITS_SET[offset];
                data[wordIndex + 1] = data[wordIndex + 1] | (value >>> (elementWidth - offset));
            }
        }
    }

    /**
     * Sets the value at {@code position} with {@value} using the given number of bits.
     *
     * @param position The absolute position in bit counts
     * @param value The value to write
     * @param bits The number of bits to use
     */
    public void setValue(long position, long value, int bits) {
        int wordIndex =
                (int) (position >>> 6); // the index of the current word in counts of 64 bits
        int offset = (int) (position & 63); // offset modulo 63 to see if we cross a word boundary

        // remove unwanted bits
        value = value & LOW_BITS_SET[bits];

        // check if we fit in this word or if we need the next one as well
        if (offset + bits < WORD_SIZE) {
            // in case we are overwriting values
            data[wordIndex] =
                    data[wordIndex]
                            & ((0xFFFFFFFFFFFFFFFFL << (offset + bits)) | LOW_BITS_SET[offset]);
            data[wordIndex] = data[wordIndex] | (value << offset);
        } else {
            data[wordIndex] = data[wordIndex] & LOW_BITS_SET[offset];
            data[wordIndex] = data[wordIndex] | (value << offset);
            if (((offset + bits) & 63) > 0) {
                offset = (offset + bits) & 63;
                data[wordIndex + 1] = data[wordIndex + 1] & HIGH_BITS_SET[offset];
                data[wordIndex + 1] = data[wordIndex + 1] | (value >>> (bits - offset));
            }
        }
    }

    /**
     * Gets a value from an absolute bit position.
     *
     * @param position The element that we want to retrieve in bit counts
     * @param length The number of bits that we want to read
     * @return The value
     */
    public long getValue(long position, int length) {
        int wordIndex = (int) (position >>> 6);
        int offset = (int) (position & 63);

        long leftFromBoundary = data[wordIndex] >>> offset;
        if ((offset + length) > WORD_SIZE) {
            long rightFromBoundary =
                    ((data[wordIndex + 1]) & LOW_BITS_SET[(offset + length) & 63])
                            << (WORD_SIZE - offset);
            return leftFromBoundary | rightFromBoundary;
        } else {
            return leftFromBoundary & LOW_BITS_SET[length];
        }
    }

    /**
     * Returns a reference to the underlying words structure.
     *
     * @return Reference to the words
     */
    public long[] getWords() {
        return this.data;
    }

    /**
     * Directly changes the value of a whole word of 8 bytes.
     *
     * @param position The position (addressed in words of 8 bytes) where to set the value
     * @param word The 8-byte word value
     */
    public void setWord(int position, long word) {
        this.data[position] = word;
    }

    /**
     * Gets the number of bytes used by the underlying array of longs.
     *
     * @return The size in bytes used by the vector
     */
    public int getSizeInBytes() {
        return this.data.length * Long.BYTES;
    }

    /**
     * Serializes this object to an {@code ObjectOutput} stream.
     *
     * @param objectOutput The stream to which the object will be written
     */
    public void write(ObjectOutput objectOutput) throws IOException {
        objectOutput.writeByte(SERIAL_VERSION_V0);
        objectOutput.writeInt(data.length);
        for (long l : data) {
            objectOutput.writeLong(l);
        }
    }

    /**
     * Deserializes a {@code VariableWidthIntVector} from a {@code ObjectInput} stream.
     *
     * @param objectInput The stream from which to read from
     * @return The deserialized instance of this object
     */
    public static VariableWidthIntVector read(ObjectInput objectInput) throws IOException {
        checkSerialVersion(SERIAL_VERSION_V0, objectInput.readByte());
        int length = objectInput.readInt();
        long[] words = new long[length];
        for (int i = 0; i < words.length; i++) {
            words[i] = objectInput.readLong();
        }

        return new VariableWidthIntVector(words);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.data);
    }
}
