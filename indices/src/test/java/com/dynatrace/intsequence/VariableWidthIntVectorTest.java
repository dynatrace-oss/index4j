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

import static com.dynatrace.intsequence.Common.minimumNumberOfBits;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dynatrace.serialization.Serialization;
import java.io.IOException;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class VariableWidthIntVectorTest {

    @ParameterizedTest
    @ValueSource(ints = {10, 100, 1_000, 10_000, 100_000, 1_000_000})
    void shouldCompareVariableWidthIntVectorToIntArray(int bound) {
        Random random = new Random(42);
        int[] values = random.ints(100, 0, bound).toArray();
        int[] offsets = new int[values.length];
        VariableWidthIntVector vector =
                new VariableWidthIntVector(values.length * Integer.BYTES * 8L); // overshoot
        int currentBitsUsed = 0;
        for (int i = 0; i < values.length; i++) {
            int bitsNeeded = minimumNumberOfBits(values[i]);
            vector.setValue(currentBitsUsed, values[i], bitsNeeded);
            offsets[i] = bitsNeeded;
            currentBitsUsed += bitsNeeded;
        }

        // assert values are properly encoded
        int currentBitPosition = 0;
        for (int i = 0; i < values.length; i++) {
            assertThat(vector.getValue(currentBitPosition, offsets[i])).isEqualTo(values[i]);
            currentBitPosition += offsets[i];
        }
    }

    @Test
    void shouldCreateSingleWordVectorAndReplaceIt() {
        VariableWidthIntVector iv = new VariableWidthIntVector(3);
        iv.setValue(0, 5, 3);
        assertThat(iv.getValue(0, 3)).isEqualTo(5);
        iv.setWord(0, 2);
        assertThat(iv.getWords()[0]).isEqualTo(2);
    }

    @Test
    void shouldSetValuesWithLeastNumberOfBits() {
        VariableWidthIntVector iv =
                new VariableWidthIntVector(3); // lets fit two values: 0b1 and 0b10
        iv.setValue(0, 1);
        iv.setValue(1, 2);
        assertThat(iv.getWords()[0]).isEqualTo(5); // 0b101

        // same but between words
        iv = new VariableWidthIntVector(1_000_000);
        iv.setValue(0, Long.MAX_VALUE); // takes 63 bits
        iv.setValue(minimumNumberOfBits(Long.MAX_VALUE), 0b11);
        assertThat(iv.getValue(0, 64)).isEqualTo(-1L);
        assertThat(iv.getValue(64, 1)).isEqualTo(1L);

        // same but between multiple words
        int bitPos = 65;
        for (int i = 0; i < 100; i++) {
            bitPos += minimumNumberOfBits(i);
            iv.setValue(bitPos, i);
            assertThat(iv.getValue(bitPos, minimumNumberOfBits(i))).isEqualTo(i);
        }
    }

    @Test
    void shouldTestSerialization() throws IOException {
        Random random = new Random(42);
        int[] values = random.ints(100, 0, 10_000).toArray();
        VariableWidthIntVector vector =
                new VariableWidthIntVector(values.length * Integer.BYTES * 8L); // overshoot
        int currentBitsUsed = 0;
        for (int value : values) {
            int bitsNeeded = minimumNumberOfBits(value);
            vector.setValue(currentBitsUsed, value, bitsNeeded);
            currentBitsUsed += bitsNeeded;
        }

        byte[] serializedVector =
                Serialization.writeToByteArray(VariableWidthIntVector::write, vector);
        VariableWidthIntVector deserializedVector =
                Serialization.readFromByteArray(VariableWidthIntVector::read, serializedVector);

        assertEquals(deserializedVector.hashCode(), vector.hashCode());
    }
}
