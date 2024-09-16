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

import static com.dynatrace.util.Util.HDFS_2k_CHAR;
import static com.dynatrace.util.Util.HDFS_2k_STR;
import static com.dynatrace.util.Util.assertLocationsAreTheSame;
import static com.dynatrace.util.Util.findExpectedLocationsWithOverlap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dynatrace.serialization.Serialization;
import java.io.IOException;
import java.util.Random;
import org.junit.jupiter.api.Test;

public class SuffixArrayTest {

    private final Random random = new Random(42);
    private static final int N_TESTS = 1_000;

    @Test
    void testCountingFromLogs() {
        SuffixArray sa = new SuffixArray(HDFS_2k_STR);
        sa.construct();
        for (int i = 0; i < N_TESTS; i++) {
            int start = random.nextInt(HDFS_2k_STR.length() - 32);
            String substring = HDFS_2k_STR.substring(start, start + random.nextInt(1, 32));
            int count = sa.count(substring);
            assertThat(count)
                    .isEqualTo(
                            findExpectedLocationsWithOverlap(HDFS_2k_CHAR, substring.toCharArray())
                                    .size());
        }
    }

    @Test
    void testSubstringSearchFromLogs() {
        SuffixArray sa = new SuffixArray(HDFS_2k_STR);
        sa.construct();
        int[] offsets = new int[100_000];

        for (int i = 0; i < N_TESTS; i++) {
            int start = random.nextInt(HDFS_2k_STR.length() - 32);
            String substring = HDFS_2k_STR.substring(start, start + random.nextInt(1, 32));
            int count = sa.locate(substring, offsets);
            assertLocationsAreTheSame(count, offsets, substring.toCharArray(), HDFS_2k_CHAR);
        }
    }

    @Test
    void testExceedNumberOfMatches() {
        SuffixArray sa = new SuffixArray(HDFS_2k_STR);
        sa.construct();
        int[] offsets = new int[100];
        int count = sa.locate("INFO", offsets);
        assertThat(count).isEqualTo(offsets.length);
    }

    @Test
    void shouldCheckThatSuffixArrayHasSameLengthAsInputPlusSentinel() {
        SuffixArray sa = new SuffixArray(HDFS_2k_STR);
        sa.construct();
        assertThat(sa.getSuffixArray().length).isEqualTo(HDFS_2k_STR.length() + 1);
    }

    @Test
    void testSubstringSearchFromLogsAfterSerializing() throws IOException {
        SuffixArray sa = new SuffixArray(HDFS_2k_STR);
        sa.construct();

        // serialize
        byte[] serialized = Serialization.writeToByteArray(SuffixArray::write, sa);
        SuffixArray deserialized = Serialization.readFromByteArray(SuffixArray::read, serialized);

        assertEquals(deserialized.hashCode(), sa.hashCode());

        int[] offsets = new int[100_000];

        for (int i = 0; i < N_TESTS; i++) {
            int start = random.nextInt(HDFS_2k_STR.length() - 32);
            String substring = HDFS_2k_STR.substring(start, start + random.nextInt(1, 32));
            int count = sa.locate(substring, offsets);
            assertLocationsAreTheSame(count, offsets, substring.toCharArray(), HDFS_2k_CHAR);
        }
    }
}
