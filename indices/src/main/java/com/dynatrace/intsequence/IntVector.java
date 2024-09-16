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
import static com.dynatrace.serialization.Serialization.checkSerialVersion;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;

/**
 * This class implements a vector of integers of custom width. While it is aimed at storing integers
 * of a fixed bit size, the reading methods actually enable to read a variable bit size.
 */
public final class IntVector {

    private static final byte SERIAL_VERSION_V0 = 0;
    private static final int WORD_SIZE = Long.BYTES * 8;
    private final long[] data;
    private final int length; // number of elements that we will store
    private final int elementWidth; // the length of each element in bits

    /**
     * Creates an empty vector to store {@code length} elements each of {@code width} size (in
     * bits).
     *
     * @param length The number of elements
     * @param width The size in bits of each element
     */
    public IntVector(int length, int width) {
        long bitsRequired = ((long) length * (long) width);
        if (bitsRequired % WORD_SIZE == 0) {
            this.data = new long[(int) (bitsRequired / WORD_SIZE)];
        } else {
            this.data = new long[(int) (bitsRequired / WORD_SIZE + 1)];
        }
        this.length = length;
        this.elementWidth = width;
    }

    /**
     * Builds a vector containing the values of {@code array} but each using only width bits. This
     * means a one-to-one mapping of {@code array} but simply using the specified number of bits.
     *
     * @param array The array of values that will be stored
     * @param width The width in bits used to store each value
     */
    public IntVector(int[] array, int width) {
        long bitsRequired = ((long) array.length * (long) width);
        if (bitsRequired % WORD_SIZE == 0) {
            this.data = new long[(int) (bitsRequired / WORD_SIZE)];
        } else {
            this.data = new long[(int) (bitsRequired / WORD_SIZE + 1)];
        }
        this.length = array.length;
        this.elementWidth = width;
        for (int i = 0; i < array.length; i++) {
            setValue(i, array[i]);
        }
    }

    private IntVector(int length, int width, long[] words) {
        this.length = length;
        this.elementWidth = width;
        this.data = words;
    }

    /**
     * Sets the integer value of {@code value} using {@code width} bits specified in the constructor
     * at index {@code position}.
     *
     * @param position The absolute position in counts of the width size
     * @param value The value to write
     */
    public void setValue(int position, long value) {
        long bitPosition =
                (long) position * elementWidth; // the starting position from which to read
        int wordIndex =
                (int) (bitPosition >>> 6); // the index of the current word in counts of 64 bits
        int offset =
                (int) (bitPosition & 63); // offset modulo 63 to see if we cross a word boundary

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
     * Gets an absolute integer value at position {@code position} by reading {@code length} bits in
     * counts of {@code width}.
     *
     * @param position The element that we want to retrieve in absolute counts of {@code width}
     * @param length The number of bits that we want to read
     * @return The value
     */
    public long getValue(int position, int length) {
        long bitPosition = (long) position * elementWidth;
        int wordIndex = (int) (bitPosition >>> 6);
        int offset = (int) (bitPosition & 63);

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
     * Returns the number of elements, counting in increments of {@code width} bits each.
     *
     * @return The number of elements stored (not the bit length)
     */
    public int getLength() {
        return this.length;
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
     * Gets the number of bits used to represent each encoded element.
     *
     * @return the number of bits
     */
    public int getElementWidth() {
        return this.elementWidth;
    }

    /**
     * Serializes this object to an {@code ObjectOutput} stream.
     *
     * @param objectOutput The stream to which the object will be written
     */
    public void write(ObjectOutput objectOutput) throws IOException {
        objectOutput.writeByte(SERIAL_VERSION_V0);
        objectOutput.writeInt(length);
        objectOutput.writeInt(elementWidth);
        for (long l : data) {
            objectOutput.writeLong(l);
        }
    }

    /**
     * Deserializes an {@code IntVector} from an {@code ObjectInput} stream.
     *
     * @param objectInput The stream from which to read from
     * @return The deserialized instance of the object
     */
    public static IntVector read(ObjectInput objectInput) throws IOException {
        checkSerialVersion(SERIAL_VERSION_V0, objectInput.readByte());
        int length = objectInput.readInt();
        int width = objectInput.readInt();
        long bitsRequired = ((long) length * (long) width);
        long[] words;
        if (bitsRequired % 64 == 0) {
            words = new long[(int) (bitsRequired / 64)];
        } else {
            words = new long[(int) (bitsRequired / 64 + 1)];
        }
        for (int i = 0; i < words.length; i++) {
            words[i] = objectInput.readLong();
        }

        return new IntVector(length, width, words);
    }

    @Override
    public int hashCode() {
        return this.length + this.elementWidth + Arrays.hashCode(this.data);
    }
}
