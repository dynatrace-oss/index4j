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

import com.dynatrace.serialization.Serialization;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class IntVectorTest {

    private static final int N_TESTS = 1_000;

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000})
    void shouldCompareIntVectorToIntArray(int bound) {
        Random random = new Random(42);
        int[] values = random.ints(1_000_000, 0, bound).toArray();
        int bitsNeededPerValue = minimumNumberOfBits(Arrays.stream(values).max().getAsInt());
        IntVector iv = new IntVector(values, bitsNeededPerValue);

        // assert we are taking less space
        assertThat(values.length * Integer.BYTES).isGreaterThan(iv.getWords().length * Long.BYTES);

        // assert values are properly encoded
        for (int i = 0; i < N_TESTS; i++) {
            int pos = random.nextInt(values.length);
            assertThat(values[pos]).isEqualTo(iv.getValue(pos, bitsNeededPerValue));
        }
    }

    @Test
    void shouldCreateSingleWordIntVectorAndReplaceIt() {
        IntVector iv = new IntVector(new int[] {5}, 3);
        assertThat(iv.getValue(0, 3)).isEqualTo(5);
        iv.setWord(0, 2);
        assertThat(iv.getValue(0, 3)).isEqualTo(2);
    }

    @Test
    void shouldTestSerialization() throws IOException {
        Random random = new Random(42);
        int[] values = random.ints(1_000_000, 0, 10_000).toArray();
        int bitsNeededPerValue = minimumNumberOfBits(Arrays.stream(values).max().getAsInt());
        IntVector iv = new IntVector(values, bitsNeededPerValue);

        byte[] serializedIntVector = Serialization.writeToByteArray(IntVector::write, iv);
        IntVector deserializedIntVector =
                Serialization.readFromByteArray(IntVector::read, serializedIntVector);

        assertThat(deserializedIntVector.hashCode()).isEqualTo(iv.hashCode());
    }
}
