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
package com.dynatrace.wavelet;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.sux4j.bits.Rank;
import it.unimi.dsi.sux4j.bits.Rank9;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * A wavelet tree implementation in Java. This data structure can be used to perform rank and access
 * queries on arbitrary ranges of an input string in logarithmic time in respect to the alphabet
 * size.
 *
 * <p>Note that it can still be optimized to avoid the partial rescans of the input text at every
 * level of the tree. See: <a
 * href="https://dl.acm.org/doi/fullHtml/10.1145/3457197">https://dl.acm.org/doi/fullHtml/10.1145/3457197</a>
 *
 * <p>For a faster and storage optimized version, see {@link WaveletFixedBlockBoosting}.
 */
@ThreadSafe
public final class WaveletTree implements Serializable {

    private final List<Character> alphabetOrdered;
    private final Set<Character> alphabetSet;
    private final WaveletTreeOpt wt;
    private final int inputLength;

    /**
     * Builds a Wavelet tree using a standard tree structure with pointers.
     *
     * @param text The input text
     */
    public WaveletTree(String text) {

        // Get the alphabet and the ordered alphabet
        this.alphabetSet = new HashSet<>();
        for (int i = 0; i < text.length(); i++) {
            this.alphabetSet.add(text.charAt(i));
        }
        this.alphabetOrdered = new ArrayList<>(alphabetSet);
        this.alphabetOrdered.sort(Character::compare);
        this.inputLength = text.length();

        // Generate the wavelet tree
        this.wt =
                new WaveletTreeOpt(
                        text.toCharArray(),
                        text.length(),
                        alphabetOrdered,
                        0,
                        alphabetOrdered.size());
    }

    /**
     * Builds a Wavelet tree using a standard tree structure with pointers.
     *
     * @param text The input text
     */
    public WaveletTree(char[] text) {

        // Get the alphabet and the ordered alphabet
        this.alphabetSet = new HashSet<>();
        for (int i = 0; i < text.length; i++) {
            this.alphabetSet.add(text[i]);
        }
        this.alphabetOrdered = new ArrayList<>(alphabetSet);
        this.alphabetOrdered.sort(Character::compare);
        this.inputLength = text.length;

        // Generate the wavelet tree
        this.wt = new WaveletTreeOpt(text, text.length, alphabetOrdered, 0, alphabetOrdered.size());
    }

    /**
     * Computes the rank of symbol {@code symbol} for the range {@code [0, position)}.
     *
     * @param position The exclusive ending position of the range for the rank calculation
     * @param symbol The symbol already mapped to the alphabet
     * @return The number of appearances of {@code symbol} in the given range
     */
    public int rank(int position, char symbol) {
        if (position < 0) {
            return 0;
        }
        if (alphabetSet.contains(symbol)) {
            return this.wt.rank(position, symbol);
        }
        return 0;
    }

    /**
     * Returns the symbol at position {@code position}.
     *
     * @param position The position for which we want to know the symbol
     * @return The symbol or null if it is out of bounds
     */
    public @Nullable Character access(int position) {
        if (position < 0 || position >= inputLength) {
            return null;
        }
        return this.wt.access(position);
    }

    private static final class WaveletTreeOpt implements Serializable {

        private final Character mid;
        private final Character leafLeftSymbol;
        private final Character leafRightSymbol;
        private final Rank rank;
        private final WaveletTreeOpt left;
        private final WaveletTreeOpt right;

        public WaveletTreeOpt(
                char[] s, int l, List<Character> sortedAlphabet, int fromAlphabet, int toAlphabet) {

            int midAlphabet = (fromAlphabet + toAlphabet) / 2;
            this.mid = sortedAlphabet.get(midAlphabet);
            BitVector map = LongArrayBitVector.getInstance().length(l);
            int indexLeft = 0;
            int indexRight = 0;
            char[] aux1 = new char[s.length];
            char[] aux2 = new char[s.length];
            for (int i = 0; i < l; i++) {
                if (s[i] < mid) {
                    map.set(i, false);
                    aux1[indexLeft++] = s[i];
                } else {
                    map.set(i, true);
                    aux2[indexRight++] = s[i];
                }
            }
            this.rank = new Rank9(map);

            int leftSize = midAlphabet - fromAlphabet;
            int rightSize = toAlphabet - midAlphabet;
            if (leftSize >= 2) {
                this.left =
                        new WaveletTreeOpt(
                                aux1, indexLeft, sortedAlphabet, fromAlphabet, midAlphabet);
            } else {
                this.left = null;
            }
            if (rightSize >= 2) {
                this.right =
                        new WaveletTreeOpt(
                                aux2, indexRight, sortedAlphabet, midAlphabet, toAlphabet);
            } else {
                this.right = null;
            } // otherwise we are done, as we only have two symbols

            // For access operation. This could also be done keeping a reference
            // to the sorted alphabet and just comparing the zeroOrOne value with
            // the middle value, but this way it is just very simple
            leafLeftSymbol = sortedAlphabet.get(fromAlphabet);
            leafRightSymbol = sortedAlphabet.get(toAlphabet - 1);
        }

        public int rank(int pos, char symbol) {

            int zeroOrOne = (symbol < mid) ? (0) : (1);

            int num;
            if (zeroOrOne == 0) {
                num = (int) rank.rankZero(pos);
                if (left != null) {
                    return left.rank(num, symbol);
                }
            } else {
                num = (int) rank.rank(pos);
                if (right != null) {
                    return right.rank(num, symbol);
                }
            }
            return num;
        }

        public Character access(int pos) {
            int zeroOrOne = rank.bitVector().getBoolean(pos) ? 1 : 0;
            if (zeroOrOne == 0) {
                if (left != null) {
                    return left.access((int) rank.rankZero(pos));
                }
                return leafLeftSymbol;
            } else {
                if (right != null) {
                    return right.access((int) rank.rank(pos));
                }
                return leafRightSymbol;
            }
        }
    }
}
