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
package com.dynatrace.encoding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.concurrent.ThreadSafe;
import org.jsuffixarrays.DivSufSort;

/** This class contains a simple implementation to compute the Burrows-Wheeler transform. */
@ThreadSafe
public final class BurrowsWheelerTransform {

    private BurrowsWheelerTransform() {
        // Utility class
    }

    /**
     * Creates the Burrows-Wheeler transform over the given text. Note that the BWT can be different
     * from other sources since this depends on the lexicographic order of the symbols used by
     * DivSufSort.
     *
     * @param text The text to transform
     * @return An array containing the BWT of the given text
     */
    public static char[] createBurrowsWheelerTransform(char[] text) {

        char[] sentinel = new char[text.length + 1];
        System.arraycopy(text, 0, sentinel, 0, text.length);
        sentinel[text.length] = '\0';

        Set<Character> alphabet = new HashSet<>();
        for (char c : sentinel) {
            alphabet.add(c);
        }
        List<Character> sortedAlphabet = new ArrayList<>(alphabet);
        sortedAlphabet.sort(Character::compareTo);

        Map<Integer, Short> monotonicMap = new HashMap<>();
        int mappedValue = 0;
        for (char symbol : sortedAlphabet) {
            monotonicMap.put((int) symbol, (short) mappedValue);
            ++mappedValue;
        }

        if (monotonicMap.size() > Short.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Charset has more than " + Short.MAX_VALUE + " different characters.");
        }

        // Do the actual mapping
        int[] mappedSequence = new int[sentinel.length];
        for (int i = 0; i < sentinel.length; i++) {
            mappedSequence[i] = monotonicMap.get((int) sentinel[i]);
        }

        // Compute suffix array
        DivSufSort divSufSorter = new DivSufSort(alphabet.size());
        int[] suffixArray = divSufSorter.buildSuffixArray(mappedSequence, 0, sentinel.length);

        // Create BWT
        short[] bwt = new short[mappedSequence.length];
        for (int i = 0; i < mappedSequence.length; i++) {
            if (suffixArray[i] == 0) {
                bwt[i] = 0;
            } else {
                bwt[i] = (short) mappedSequence[suffixArray[i] - 1];
            }
        }

        char[] bwtWithChars = new char[bwt.length];
        for (int i = 0; i < bwt.length; i++) {
            bwtWithChars[i] = sortedAlphabet.get(bwt[i]);
        }

        return bwtWithChars;
    }

    /**
     * Measures the redundancy of a string of symbols by computing the length divided by the average
     * runs of the same symbol.
     *
     * @param input Input string of symbols
     * @return The {@code n/r} measure of redundancy, where higher means more redundant
     */
    public static double computeRedundancyOfText(char[] input) {
        // Measure n / r
        int r = 1;
        char previous = input[0];
        for (int i = 1; i < input.length; i++) {
            if (previous != input[i]) {
                ++r;
                previous = input[i];
            }
        }

        return (input.length / (double) r);
    }

    /**
     * Measures the redundancy of a string of symbols by computing the length divided by the average
     * runs of the same symbol.
     *
     * @param input Input string of symbols
     * @return The {@code n/r} measure of redundancy, where higher means more redundant
     */
    public static double computeRedundancyOfText(short[] input) {
        // Measure n / r
        int r = 1;
        short previous = input[0];
        for (int i = 1; i < input.length; i++) {
            if (previous != input[i]) {
                ++r;
                previous = input[i];
            }
        }

        return (input.length / (double) r);
    }
}
