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
package com.dynatrace.bitsequence;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dynatrace.serialization.Serialization;
import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.sux4j.bits.Rank;
import it.unimi.dsi.sux4j.bits.Rank9;
import java.io.IOException;
import java.util.Random;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class RrrVectorTest {

    private static final int N_TESTS = 1_000;
    private final Random random = new Random(42);

    private String integersToBinaryString(int[] values) {
        StringBuilder binary = new StringBuilder();
        for (int v : values) {
            String s = String.format("%32s", Integer.toBinaryString(v)).replace(' ', '0');
            binary.append(new StringBuilder(s).reverse().toString());
        }
        return binary.toString();
    }

    private int rank0(int[] bitSequence) {
        int rank = 0;
        String s = integersToBinaryString(bitSequence);
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '0') {
                ++rank;
            }
        }
        return rank;
    }

    private int rank1(int[] bitSequence) {
        int rank = 0;
        String s = integersToBinaryString(bitSequence);
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '1') {
                ++rank;
            }
        }
        return rank;
    }

    @Test
    void testEncodeSmallBitVectorAndAnswerQueries() {

        BitVector bv = LongArrayBitVector.getInstance().length(1024);
        // 101000000001000000111000
        bv.set(0, true);
        bv.set(2, true);
        bv.set(11, true);
        bv.set(18, true);
        bv.set(19, true);
        bv.set(20, true);
        bv.set(199, true);
        bv.set(512, true);

        RrrVector rrr = new RrrVector(bv, 32);
        assertThat(rrr.access(0)).isEqualTo(true);
        assertThat(rrr.access(1)).isEqualTo(false);
        assertThat(rrr.access(2)).isEqualTo(true);
        assertThat(rrr.access(15)).isEqualTo(false);
        assertThat(rrr.access(19)).isEqualTo(true);
        assertThat(rrr.access(199)).isEqualTo(true);
        assertThat(rrr.access(512)).isEqualTo(true);

        assertThat(rrr.rankOnes(0)).isEqualTo(0);
        assertThat(rrr.rankOnes(1)).isEqualTo(1);
        assertThat(rrr.rankOnes(2)).isEqualTo(1);
        assertThat(rrr.rankOnes(3)).isEqualTo(2);

        assertThat(rrr.rankZeroes(0)).isEqualTo(0);
        assertThat(rrr.rankZeroes(1)).isEqualTo(0);
        assertThat(rrr.rankZeroes(2)).isEqualTo(1);
        assertThat(rrr.rankZeroes(3)).isEqualTo(1);
    }

    @Test
    void testCornerCases() {
        int[] bitSequence = new int[2];
        // 1010000000000000000000000000000010000000000000000000000000000000
        bitSequence[0] = 5;
        bitSequence[1] = 1;
        int bitLen = bitSequence.length * Integer.BYTES * 8;
        RrrVector rrr = new RrrVector(bitSequence, 32);

        Assertions.assertThat(rrr.rankZeroes(0)).isEqualTo(0);
        Assertions.assertThat(rrr.rankOnes(0)).isEqualTo(0);

        Assertions.assertThat(rrr.rankZeroes(1)).isEqualTo(0);
        Assertions.assertThat(rrr.rankOnes(1)).isEqualTo(1);

        Assertions.assertThat(rrr.rankZeroes(bitLen)).isEqualTo(61);
        Assertions.assertThat(rrr.rankOnes(bitLen)).isEqualTo(3);
    }

    @Test
    void testOutOfBoundsAccess() {
        int[] bitSequence = new int[1];
        bitSequence[0] = 5;
        RrrVector rrr = new RrrVector(bitSequence, 32);
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> {
                            rrr.access(9999);
                        });
        assertThat(exception.getMessage())
                .isEqualTo("Out of range access. Requested 9999 when range is [0, 32)");

        exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> {
                            rrr.access(-1);
                        });
        assertThat(exception.getMessage())
                .isEqualTo("Out of range access. Requested -1 when range is [0, 32)");
    }

    @Test
    void testBitSequenceRrrAndAccessIt() {
        int[] bitSequence = new int[2];
        bitSequence[0] = 5;
        bitSequence[1] = 1;
        int bitLen = bitSequence.length * Integer.BYTES * 8;
        RrrVector rrr = new RrrVector(bitSequence, 32);
        String binary = integersToBinaryString(bitSequence);

        for (int i = 0; i < bitLen; i++) {
            Assertions.assertThat(rrr.access(i)).isEqualTo(binary.charAt(i) == '1');
        }
    }

    @Test
    void testBitSequenceRrrAndRankIt() {
        int[] bitSequence = new int[2];
        bitSequence[0] = 5;
        bitSequence[1] = 1;
        int bitLen = bitSequence.length * Integer.BYTES * 8;
        RrrVector rrr = new RrrVector(bitSequence, 32);

        Assertions.assertThat(rrr.rankZeroes(bitLen)).isEqualTo(rank0(bitSequence));
        Assertions.assertThat(rrr.rankOnes(bitLen)).isEqualTo(rank1(bitSequence));
        Assertions.assertThat(rrr.rankZeroes(-1)).isEqualTo(0);
        Assertions.assertThat(rrr.rankOnes(-1)).isEqualTo(0);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100, 1_000, 10_000})
    void testBitSequenceRrrAndRankItLargeScaleWithSampleRate(int integers) {
        int[] bitSequence = new int[integers];
        int bitLen = bitSequence.length * Integer.BYTES * 8;
        BitVector bv = LongArrayBitVector.getInstance().length(bitLen);
        for (int i = 0; i < integers; i++) {
            bitSequence[i] = random.nextInt();
            for (int j = 0; j < 32; j++) {
                int mask = 1 << j;
                bv.set(i * 32L + j, (bitSequence[i] & mask) != 0);
            }
        }

        for (int samplingRate = 4; samplingRate <= 256; samplingRate <<= 1) {
            RrrVector rrr = new RrrVector(bitSequence, samplingRate);
            RrrVector rrrFromBv = new RrrVector(bv, samplingRate);
            Rank r9 = new Rank9(bv);

            Assertions.assertThat(rrr.rankOnes(bitLen)).isEqualTo(r9.rank(bitLen));
            for (int i = 0; i < N_TESTS; i++) {
                int p = random.nextInt(bitLen);
                Assertions.assertThat(rrr.rankOnes(p)).isEqualTo(r9.rank(p));
                Assertions.assertThat(rrrFromBv.rankOnes(p)).isEqualTo(r9.rank(p));
                Assertions.assertThat(rrr.rankZeroes(p)).isEqualTo(r9.rankZero(p));
                Assertions.assertThat(rrrFromBv.rankZeroes(p)).isEqualTo(r9.rankZero(p));
                Assertions.assertThat(rrr.access(p)).isEqualTo(bv.getBoolean(p));
                Assertions.assertThat(rrrFromBv.access(p)).isEqualTo(bv.getBoolean(p));
            }
        }
    }

    @Test
    void testSerializeBitRrr() throws IOException {
        int integers = 1_000_000;
        int[] bitSequence = new int[integers];
        int bitLen = bitSequence.length * Integer.BYTES * 8;
        BitVector bv = LongArrayBitVector.getInstance().length(bitLen);
        for (int i = 0; i < integers; i++) {
            bitSequence[i] = random.nextInt();
            for (int j = 0; j < 32; j++) {
                int mask = 1 << j;
                bv.set(i * 32L + j, (bitSequence[i] & mask) != 0);
            }
        }
        RrrVector rrr = new RrrVector(bitSequence, 32);

        byte[] serialized = Serialization.writeToByteArray(RrrVector::write, rrr);
        RrrVector deserialized = Serialization.readFromByteArray(RrrVector::read, serialized);

        assertEquals(deserialized.hashCode(), rrr.hashCode());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8, 16, 32, 64})
    void testEncodeLargeBitVector(int sampleRate) {

        int bitLen = 1_000_000;
        Random r = new Random(42);
        BitVector bv = LongArrayBitVector.getInstance().length(bitLen);
        for (int i = 0; i < bitLen; i++) {
            if (r.nextGaussian() > 0.66) {
                bv.set(i, true);
            }
        }

        RrrVector rrr = new RrrVector(bv, sampleRate);
        Rank9 rank = new Rank9(bv);
        for (int i = 0; i < bitLen; i++) {
            assertThat(rrr.access(i)).isEqualTo(bv.getBoolean(i));
            assertThat(rrr.rankOnes(i)).isEqualTo(rank.rank(i));
            assertThat(rrr.rankZeroes(i)).isEqualTo(rank.rankZero(i));
        }
        assertThat(rrr.rankOnes(bitLen)).isEqualTo(rank.rank(bitLen));
    }
}
