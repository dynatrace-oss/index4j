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
package com.dynatrace.fm;

import static com.dynatrace.intsequence.Common.minimumNumberOfBits;
import static com.dynatrace.serialization.Serialization.checkSerialVersion;

import com.dynatrace.bitsequence.RrrVector;
import com.dynatrace.intsequence.IntVector;
import com.dynatrace.wavelet.WaveletFixedBlockBoosting;
import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.LongArrayBitVector;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.concurrent.ThreadSafe;

/**
 * An FM-Index is a compressed full-text substring index based on suffix arrays, bit vectors,
 * wavelet trees and the Burrows–Wheeler transform. It can be used to find the number of occurrences
 * of a pattern within compressed text, locate the position of each occurrence and retrieve the
 * original string. The query time and the required storage space has a sublinear complexity with
 * respect to the size of the input data. Its memory requirements are sensitive to the alphabet
 * size, meaning that the less different symbols in the input text, the less memory the index will
 * require. Storage will increase linearly with the size of the input text.
 *
 * <p>For easier construction, take a look at the {@link FmIndexBuilder}. Note that this
 * implementation only supports UNICODE values up to the Java default for {@code
 * Character.MAX_VALUE} (which is {@code u"\U0000FFFF"}). If more is required, then consider mapping
 * your alphabet to a sequence of monotonic integers first, where the maximum value is {@code
 * SIZE_WCHAR}.
 *
 * <p>This implementation of the FM-Index makes use of Fixed-Block boosting wavelet trees (see
 * {@link WaveletFixedBlockBoosting}). The currently supported operations are:
 *
 * <ul>
 *   <li>{@link FmIndex#count(char[])}: enables to count the number of occurrences of a given
 *       pattern.
 *   <li>{@link FmIndex#locate(char[], int, int, int[], int)}: enables to find all the leftmost
 *       starting positions of a given pattern. Its speed will depend on the parameter {@code
 *       sampleRate} which trades off space for speed. A {@code sampleRate} of {@code 4} means to
 *       store an additional integer every four input symbols but also reduces the number of
 *       iterations per query to a maximum of {@code 4}. Therefore, more position integers require
 *       more space but reduce the number of searches.
 *   <li>{@link FmIndex#extract(int, int, char[], int)}: enables retrieving the original input text
 *       from the compressed index between given positions. The {@code sampleRate} parameter has the
 *       same impact as with the locate query.
 *   <li>{@link FmIndex#extractUntilBoundary(int, char[], int, char)}: enables retrieving arbitrary
 *       text from a start position until a given character is found. Useful when looking for
 *       example for record boundaries with the newline character as a record separator starting
 *       from a starting match previously found with the locate query.
 *   <li>{@link FmIndex#extractUntilBoundaryLeft(int, char[], int, char)}: Same as {@code
 *       extractUntilBoundary} but only recovers the original input to the left of the starting
 *       point until matching the delimiter. Useful for extracting key-value pairs.
 *   <li>{@link FmIndex#extractUntilBoundaryRight(int, char[], int, char)}: Same as {@code
 *       extractUntilBoundary} but only recovers the original input to the right of the starting
 *       point until matching the delimiter. Useful for extracting key-value pairs.
 * </ul>
 *
 * <p>The code here present is based on the original FM-Index article by Ferragina, Paolo, and
 * Giovanni Manzini. "Opportunistic data structures with applications." In Proceedings 41st annual
 * symposium on foundations of computer science, pp. 390-398. IEEE, 2000.
 */
@ThreadSafe
public final class FmIndex {

    private static final byte SERIAL_VERSION_V0 = 0;
    // the maximum alphabet size (256 * 256) as defined by the Char.MAX_VALUE constant
    private static final int MAX_ALPHABET = Character.MAX_VALUE;
    // the trade-off parameter for space and runtime. Higher sample rate means less integer
    // positions
    // corresponding to the suffixes are stored, therefore less memory is used. However, both
    // locating
    // and counting will require iterating until the next stored position
    private final int sampleRate;
    // monotonicMap holds the mapping for each symbol to the number in order of appearance
    // we can look up the numeric value of every symbol as follows:
    // short c = monotonicMap.getOrDefault((int) 'x', (short) 0) where c is the numeric value
    private final Map<Integer, Short> monotonicMap;
    // boolean to indicate whether we want to enable input extraction - if the application does not
    // need it,
    // we can save some memory
    private final boolean enableExtract;
    // cumulative counts of each letter (mapped)
    private int[] cumulativeCounts;
    // monotonicLookUp holds the symbol that corresponds to each mapping in order of appearance
    private int[] monotonicLookUp;
    // the positions corresponding to the entries in the suffix array - the smaller the sample rate,
    // the more suffixes we need to store.
    private IntVector suffixes;
    // same as before but for the entries / order of the input text. This is only required if
    // displaying
    // or extracting (decompressing) text
    private IntVector positions;
    // the bit width that we are using to store the positions. For example if the maximum value that
    // we
    // need to store is, e.g. 7, then 3 bits are enough to represent all values from 0 to 7
    // inclusive.
    private int bitWidthSuffixes;
    // same as before but for the order of the text
    private int bitWidthPositions;
    // bit vector representing which positions are sampled in the suffix array and which are not -
    // needed
    // as stopping condition when locating or reconstructing
    private RrrVector sampledSuffixes;
    // the wavelet tree (at this point, it is an array, rather) that represents the burrows-wheeler
    // transform
    // of the input sequence. It uses huffman encoding and fixed block boosting to reduce memory
    // consumption
    // compared to wavelet trees or wavelet matrices
    private final WaveletFixedBlockBoosting waveletFixedBlockBoosting;
    // the length of the input corpus text
    private final int length;

    /**
     * Builds an FM-Index over the text {@code input} using the given {@code sampleRate}. The {@code
     * sampleRate} controls the trade-off between space consumption and speed of locate and extract
     * queries. A sample rate of {@code 8} means that an additional integer will be stored for every
     * 8-th symbol in the input text, and that in order to retrieve the position of a symbol, we
     * will need to iterate a maximum of {@code 8} times per symbol. Therefore, a larger {@code
     * sampleRate} results in less space, but more iterations. Using a sample rate of one
     * essentially results in the whole structure becoming a map from all symbols to all locations
     * and will require an additional space of {@code 4} bytes times the length of the input text.
     * Also note that if the recovery of the original text is enabled, then each location will also
     * require an additional integer.
     *
     * <p>It is recommended to use the builder to create an instance of the FM-Index. See {@link
     * FmIndexBuilder}.
     *
     * @param input A char array containing the input text
     * @param sampleRate The sample rate to control the trade-off between space and locate speed.
     *     Typically, a value of {@code 64} is reasonable.
     * @param enableExtract If the index should enable retrieving the original text, rather than
     *     just the count or locations of substrings. If that is the case, setting this parameter to
     *     {@code false} will result in saving some space.
     */
    public FmIndex(char[] input, int sampleRate, boolean enableExtract) {
        this.sampleRate = sampleRate;
        this.enableExtract = enableExtract;
        this.cumulativeCounts = new int[MAX_ALPHABET + 1]; // this is later resized to alphabet size
        this.monotonicMap = new HashMap<Integer, Short>();
        // Add the terminating $ character that is lexicographically smaller than any other
        char[] sentinelTerminatedInput = addSentinelTerminatingCharacter(input);
        this.length = sentinelTerminatedInput.length;
        // Map the alphabet to a continuous, monotonically-increasing integer sequence
        // example: if input is "bab\0" then X will be "2120" (a -> 1, b -> 2, \0 -> 0) (symbol
        // ordered)
        short[] mappedSequence = mapToMonotonicSequence(sentinelTerminatedInput);
        // Create cumulative counts
        fillCumulativeCounts(mappedSequence);
        // Build suffix array and sample positions
        int[] suffixArray = buildSuffixArrayAndSample(mappedSequence);
        // Create Burrows-Wheeler Transform from the suffix array
        short[] bwt = burrowsWheelerTransform(mappedSequence, suffixArray);
        waveletFixedBlockBoosting = new WaveletFixedBlockBoosting(bwt, sampleRate);
    }

    /**
     * Builds an FM-Index over the text {@code input} using the given {@code sampleRate}. The {@code
     * sampleRate} controls the trade-off between space consumption and speed of locate and extract
     * queries. A sample rate of {@code 8} means that an additional integer will be stored for every
     * 8-th symbol in the input text, and that in order to retrieve the position of a symbol, we
     * will need to iterate a maximum of {@code 8} times per symbol. Therefore, a larger {@code
     * sampleRate} results in less space, but more iterations. Using a sample rate of one
     * essentially results in the whole structure becoming a map from all symbols to all locations
     * and will require an additional space of {@code 4} bytes times the length of the input text.
     *
     * <p>It is recommended to use the builder to create an instance of the FM-Index. See {@link
     * FmIndexBuilder}.
     *
     * @param input A char array containing the input text
     * @param sampleRate The sample rate to control the trade-off between space and locate speed.
     *     Typically, a value of {@code 64} is reasonable.
     */
    public FmIndex(char[] input, int sampleRate) {
        this(input, sampleRate, true);
    }

    private FmIndex(
            int sampleRate,
            boolean enableExtract,
            int bitWidthSuffixes,
            int bitWidthPositions,
            int length,
            Map<Integer, Short> monotonicMap,
            int[] cumulativeCounts,
            int[] monotonicLookup,
            IntVector suffixes,
            IntVector positions,
            RrrVector sampledSuffixes,
            WaveletFixedBlockBoosting wavelet) {

        this.sampleRate = sampleRate;
        this.enableExtract = enableExtract;
        this.bitWidthSuffixes = bitWidthSuffixes;
        this.bitWidthPositions = bitWidthPositions;
        this.length = length;
        this.monotonicMap = monotonicMap;
        this.cumulativeCounts = cumulativeCounts;
        this.monotonicLookUp = monotonicLookup;
        this.suffixes = suffixes;
        this.positions = positions;
        this.sampledSuffixes = sampledSuffixes;
        this.waveletFixedBlockBoosting = wavelet;
    }

    /**
     * Converts a UTF-8 encoded byte pattern into a char pattern and enables to use any of the
     * queries that employ the {@code char} datatype. Note that this implementation only supports
     * UNICODE values up to the Java default for {@code Character.MAX_VALUE} (which is {@code
     * u"\U0000FFFF"}). If more is required, then consider mapping your alphabet to a sequence of
     * monotonic integers first (see function {@link FmIndex#mapToMonotonicSequence(char[])} to see
     * an example).
     *
     * @param pattern The byte pattern to convert to char array
     * @param offset Byte position from which to start converting
     * @param length How many bytes to convert, counting from offset
     * @param destination The char array to which the byte pattern will be written to
     * @return The number of resulting chars
     */
    protected static int convertBytePatternToCharPattern(
            byte[] pattern, int offset, int length, char[] destination) {
        int pos = offset;
        int i = 0;
        char nextChar;
        while (pos < length + offset) {
            byte firstByte = pattern[pos];
            if (firstByte < 0b0000_0000) {

                if (((firstByte & 0b1111_0000) >>> 3) == 30) {
                    // 4 byte char
                    byte secondByte = pattern[pos + 1];
                    byte thirdByte = pattern[pos + 2];
                    byte fourthByte = pattern[pos + 3];
                    pos += 4;
                    int beforeConversion =
                            ((((firstByte & 0b0000_0111) << 18)
                                            | ((secondByte & 0b0011_1111) << 12)
                                            | ((thirdByte & 0b0011_1111) << 6)
                                            | (fourthByte & 0b0011_1111))
                                    & 0b111_111111_111111_111111);
                    if (beforeConversion > Short.MAX_VALUE) {
                        throw new RuntimeException(
                                "Found a character that exceeds ("
                                        + (int) Short.MAX_VALUE
                                        + "): it was "
                                        + beforeConversion);
                    }
                    nextChar = (char) beforeConversion;

                } else if (((firstByte & 0b1110_0000) >>> 4) == 14) {
                    // 3 byte char
                    byte secondByte = pattern[pos + 1];
                    byte thirdByte = pattern[pos + 2];
                    pos += 3;
                    nextChar =
                            (char)
                                    ((((firstByte & 0b00001111) << 12)
                                                    | ((secondByte & 0b00111111) << 6)
                                                    | (thirdByte & 0b00111111))
                                            & 0b1111_111111_111111);
                } else {
                    // 2 byte char
                    byte secondByte = pattern[pos + 1];
                    pos += 2;
                    nextChar =
                            (char)
                                    ((((firstByte & 0b00011111) << 6) | (secondByte & 0b00111111))
                                            & 0b11111111111);
                }
            } else {
                // single byte char
                ++pos;
                nextChar = (char) firstByte;
            }

            destination[i++] = nextChar;
        }
        return i;
    }

    private char[] addSentinelTerminatingCharacter(char[] input) {
        char[] sentinel = new char[input.length + 1];
        System.arraycopy(input, 0, sentinel, 0, input.length);
        sentinel[input.length] = 0;
        return sentinel;
    }

    private void fillCumulativeCounts(short[] mappedSequence) {
        // example: if input is "bab\0" then the cumulative counts will be {0, 1, 2, 4, repeat 4
        // until
        // end of alphabet}
        // which is the cumulative sum of alphabet counts, '\0' increases from 0 to 1, 'a' increases
        // from
        // 1 to 2, 'b' increases by two from 2 to 4 and so it stays
        for (short value : mappedSequence) {
            cumulativeCounts[value]++;
        }
        int offset = cumulativeCounts[0];
        cumulativeCounts[0] = 0;

        for (int i = 1; i < monotonicLookUp.length; i++) {
            int previousSum = cumulativeCounts[i];
            cumulativeCounts[i] = cumulativeCounts[i - 1] + offset;
            offset = previousSum;
        }
        cumulativeCounts = Arrays.copyOfRange(cumulativeCounts, 0, monotonicLookUp.length + 1);
        cumulativeCounts[monotonicLookUp.length] = this.length;
    }

    private int[] buildSuffixArrayAndSample(short[] mappedSequence) {
        // Build the suffix array using Yuta Mori's div suf sort algorithm
        // DivSufSort dss = new DivSufSort(monotonicLookUp.length); // this is the alphabet length
        org.jsuffixarrays.DivSufSort dss =
                new org.jsuffixarrays.DivSufSort(
                        monotonicLookUp.length); // this is the alphabet length
        int[] mappedSequenceAsInts = new int[mappedSequence.length];
        for (int i = 0; i < mappedSequence.length; i++) {
            mappedSequenceAsInts[i] = mappedSequence[i];
        }
        // int[] suffixArray = dss.buildSuffixArray(mappedSequence, 0, mappedSequence.length);
        int[] suffixArray =
                dss.buildSuffixArray(mappedSequenceAsInts, 0, mappedSequenceAsInts.length);
        // Sample positions
        bitWidthSuffixes = minimumNumberOfBits(mappedSequence.length);
        suffixes = new IntVector(mappedSequence.length / sampleRate + 1, bitWidthSuffixes);
        BitVector whichPositionIsSampled =
                LongArrayBitVector.getInstance().length(mappedSequence.length);
        int samplingIndex = 0;
        for (int i = 0; i < mappedSequence.length; i++) {
            if (suffixArray[i] % sampleRate == 0) {
                suffixes.setValue(samplingIndex, suffixArray[i]);
                whichPositionIsSampled.set(i, true);
                samplingIndex++;
            } else {
                whichPositionIsSampled.set(i, false);
            }
        }
        this.sampledSuffixes = new RrrVector(whichPositionIsSampled, sampleRate);
        // Sample also positions in case we need to extract / decompress
        if (enableExtract) {
            this.bitWidthPositions = bitWidthSuffixes;
            positions = new IntVector(mappedSequence.length / sampleRate + 2, bitWidthPositions);
            for (int i = 0; i < mappedSequence.length; i++) {
                if (suffixArray[i] % sampleRate == 0) {
                    positions.setValue(suffixArray[i] / sampleRate, i);
                }
            }
            positions.setValue(
                    (mappedSequence.length - 1) / sampleRate + 1,
                    positions.getValue(0, bitWidthPositions));
        }
        return suffixArray;
    }

    private short[] burrowsWheelerTransform(short[] mappedSequence, int[] suffixArray) {
        // This basically takes all rotations, sorts them into lexical order and then
        // takes the last column. E.g., for "baba$" the output would be "abba$":
        // rotations    sort    last    output
        // baba$        $baba   a       abba$
        // $baba        a$bab   b
        // a$baba       aba$b   b
        // ba$ba        ba$ba   a
        // aba$b        baba$   $
        // note that the output "abba" contains the sequence of "b" together, making it
        // easier to compress in the wavelet tree
        short[] bwt = new short[mappedSequence.length];
        for (int i = 0; i < mappedSequence.length; i++) {
            if (suffixArray[i] == 0) {
                bwt[i] = mappedSequence[mappedSequence.length - 1];
            } else {
                bwt[i] = mappedSequence[suffixArray[i] - 1];
            }
        }
        return bwt;
    }

    private short[] mapToMonotonicSequence(char[] inputSentinel) {
        Set<Character> alphabet = new HashSet<>();
        int otherTerminating = 0;
        for (char c : inputSentinel) {
            alphabet.add(c);
            if (c == '\0') {
                ++otherTerminating;
            }
        }

        int mappedValue = 0;
        if (otherTerminating != 1) {
            mappedValue = 1;
        }

        monotonicLookUp = new int[alphabet.size() + 1];
        // Add the sentinel value manually
        monotonicMap.put((int) '\0', (short) mappedValue);
        monotonicLookUp[mappedValue] = 0;
        mappedValue++;
        // And add the rest
        for (char symbol : inputSentinel) {
            if (null == monotonicMap.putIfAbsent((int) symbol, (short) mappedValue)) {
                monotonicLookUp[mappedValue++] = symbol;
            }
        }

        if (monotonicMap.size() > Short.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Input has more than " + Short.MAX_VALUE + " different symbols");
        }

        // Do the actual mapping
        short[] mappedSequence = new short[inputSentinel.length];
        for (int i = 0; i < inputSentinel.length - 1; i++) {
            mappedSequence[i] = monotonicMap.get((int) inputSentinel[i]);
        }
        mappedSequence[inputSentinel.length - 1] = 0;
        return mappedSequence;
    }

    /**
     * Counts the number of times a given pattern is found in the indexed input.
     *
     * @param pattern The substring to search in the index
     * @return The number of overlapping matches
     */
    public int count(char[] pattern) {
        return count(pattern, 0, pattern.length);
    }

    /**
     * Counts the number of times a given pattern is found in the indexed input.
     *
     * @param pattern The substring to search in the index
     * @param offset The offset in the substring from which to start using the pattern
     * @param length The number of chars from the offset used for the pattern
     * @return The number of overlapping matches
     */
    public int count(char[] pattern, int offset, int length) {
        int i = (offset + length) - 1;
        short c = monotonicMap.getOrDefault((int) pattern[i], (short) 0);
        if (c == 0) {
            return 0;
        }
        int start = cumulativeCounts[c];
        int end = cumulativeCounts[c + 1];

        while (start < end && i >= offset + 1) {
            c = monotonicMap.getOrDefault((int) pattern[--i], (short) 0);
            if (c == 0) {
                return 0;
            }
            start = (int) (cumulativeCounts[c] + waveletFixedBlockBoosting.rank(start, c));
            end = (int) (cumulativeCounts[c] + waveletFixedBlockBoosting.rank(end, c));
        }

        return Math.max(0, end - start);
    }

    /**
     * Locates all occurrences of the given pattern and stores the positions in the array locations.
     * Note that the user is responsible for allocating a large enough array, or to otherwise,
     * indicate the maximum number of matches to find using the alternative function call {@link
     * FmIndex#locate(char[], int, int, int[], int)}.
     *
     * @param pattern The char pattern to search in the index
     * @param locations An array where the found locations will be written to
     * @return The number of occurrences of the pattern, similar as with {@link
     *     FmIndex#count(char[], int, int)}
     */
    public int locate(char[] pattern, int[] locations) {
        return locate(pattern, 0, pattern.length, locations, -1);
    }

    /**
     * Locates all occurrences of the given pattern and stores the positions in the array locations.
     * Note that the user is responsible for allocating a large enough array, or to otherwise,
     * indicate the maximum number of matches to find.
     *
     * @param pattern The char pattern to search in the index
     * @param offset Where to start matching within the pattern
     * @param length How many chars, from the offset, to use for the pattern
     * @param locations An array where the found locations will be written to
     * @param maxMatches The maximum number of occurrences. Once reached, the search will stop
     * @return The number of occurrences of the pattern, similar as with {@link
     *     FmIndex#count(char[], int, int)}
     */
    public int locate(char[] pattern, int offset, int length, int[] locations, int maxMatches) {

        int i = (offset + length) - 1;
        short c = monotonicMap.getOrDefault((int) pattern[i], (short) 0);
        if (c == 0) {
            return 0;
        }
        int start = cumulativeCounts[c];
        int end = cumulativeCounts[c + 1];
        int matchesPosition = 0;

        // first determine range as in count
        while (start < end && i >= (offset + 1)) {
            c = monotonicMap.getOrDefault((int) pattern[--i], (short) 0);
            if (c == 0) {
                return 0;
            }
            start = (int) (cumulativeCounts[c] + waveletFixedBlockBoosting.rank(start, c));
            end = (int) (cumulativeCounts[c] + waveletFixedBlockBoosting.rank(end, c));
        }

        // now extract locations with the sampled suffixes
        if (start < end) {
            i = start + 1;
            while (i <= end) {
                int j = i;
                int distance = 0;
                while (!(sampledSuffixes.access(j - 1))) {
                    long tuple = (waveletFixedBlockBoosting.inverseSelect(j - 1));
                    c = (short) tuple;
                    int rank = (int) waveletFixedBlockBoosting.rank(j, c);
                    j = cumulativeCounts[c] + rank;
                    ++distance;
                }
                locations[matchesPosition] =
                        (int)
                                (suffixes.getValue(
                                                (sampledSuffixes.rankOnes(j) - 1), bitWidthSuffixes)
                                        + distance);
                ++matchesPosition;
                if (matchesPosition == maxMatches) {
                    break;
                }
                ++i;
            }
        }

        return matchesPosition;
    }

    /**
     * Extracts the original string from the index in the range {@code [start, stop)} and stores it
     * in the given array, starting from the given offset.
     *
     * @param start The inclusive starting position of the original slice of the string
     * @param stop The exclusive ending position of the original slice of the string
     * @param destination The array where to store the extracted slice
     * @param offset The offset to shift the extracted string in the destination array
     * @return The number of symbols extracted, which should be {@code stop - start}
     */
    public int extract(int start, int stop, char[] destination, int offset) {

        if (!enableExtract) {
            throw new RuntimeException("Text recovery not enabled at build time");
        }

        if (start < 0) {
            throw new RuntimeException("Requested position less than 0");
        }

        if (stop >= length) {
            throw new RuntimeException("Stop position longer than index string");
        }

        // calculate position of backward search
        int samplePosition =
                (int) (positions.getValue((stop / sampleRate) + 1, bitWidthPositions) + 1);
        // number of letters to skip
        int skipUntilNextSampled = sampleRate - (stop) % sampleRate;

        // special case: we are at the last position
        if ((stop / sampleRate) == positions.getLength() - 2) {
            skipUntilNextSampled = length - stop;
        }

        // backwards search
        int range = stop - start;
        if (destination.length - offset < range) {
            throw new RuntimeException("Supplied destination is not large enough");
        }
        int remaining = range;
        int distance = 0;
        while (remaining > 0) {
            short c = (short) waveletFixedBlockBoosting.inverseSelect(samplePosition - 1);
            samplePosition =
                    (int) (cumulativeCounts[c] + waveletFixedBlockBoosting.rank(samplePosition, c));

            if (distance >= skipUntilNextSampled) {
                destination[remaining - 1 + offset] = (char) monotonicLookUp[c];
                remaining--;
            }
            distance++;
        }
        return range;
    }

    private void checkBoundsForExtraction(int from, char[] destination) {
        if (!enableExtract) {
            throw new RuntimeException("Text recovery not enabled at build time");
        }

        if (from < 0) {
            throw new RuntimeException("Requested position less than 0");
        }

        if (from >= length) {
            throw new RuntimeException("Requested position longer than index string");
        }

        if (destination.length == 0) {
            throw new IllegalArgumentException("Supplied destination for extraction has size zero");
        }
    }

    /**
     * Extracts the original string starting from the given position until the boundary characters
     * are found, and then stores it in the given array, starting from the given offset. If the
     * supplied destination is not large enough, an exception will be thrown.
     *
     * @param from The starting position of the original string from which to extract left and right
     * @param destination The array where to store the extracted slice
     * @param offset The offset to shift the extracted string in the destination array
     * @param boundary The symbol at which to stop extracting, both left and right from the starting
     *     position
     * @return The number of symbols extracted
     */
    public int extractUntilBoundary(int from, char[] destination, int offset, char boundary) {

        checkBoundsForExtraction(from, destination);

        // calculate position of backward search
        int samplePosition =
                (int) (positions.getValue((from / sampleRate) + 1, bitWidthPositions) + 1);
        // number of letters to skip at the start
        int skipUntilNextSampled = sampleRate - (from) % sampleRate;

        // special case: we are at the last position
        if ((from / sampleRate) == positions.getLength() - 2) {
            skipUntilNextSampled = length - from;
        }

        int downStreamPos = destination.length - 1;

        // start the backwards search
        short mappedBoundary = monotonicMap.getOrDefault((int) boundary, (short) 0);
        if (mappedBoundary == 0) {
            throw new IllegalArgumentException("Boundary does not exist");
        }

        int remaining = destination.length;
        int distance = 0;
        while (remaining > 0) {

            short c = (short) waveletFixedBlockBoosting.inverseSelect(samplePosition - 1);
            samplePosition =
                    (int) (cumulativeCounts[c] + waveletFixedBlockBoosting.rank(samplePosition, c));

            if (distance >= skipUntilNextSampled) {

                // this will exit if we find the left boundary
                if (c == mappedBoundary) {
                    break;
                }
                // special break: we are the very beginning
                if (c == 0) {
                    break;
                }

                destination[downStreamPos--] = (char) monotonicLookUp[c];
                remaining--;
            }
            distance++;
        }

        // Shift the downstream to begin at 0 so that we can copy the upstream directly afterwards
        int downStreamLength = destination.length - (downStreamPos + 1);
        System.arraycopy(destination, downStreamPos + 1, destination, offset, downStreamLength);

        // Do incremental (+4) searches
        int step = 4;
        int upStreamPos;
        int finalPos = -1;
        int timesUpStream = 1;
        while (finalPos == -1) {

            int prevFrom = from;
            from += step;
            from = Math.min(from, this.length - 1);
            remaining = from - prevFrom;
            upStreamPos = (timesUpStream - 1) * step + remaining - 1;

            samplePosition =
                    (int) (positions.getValue((from / sampleRate) + 1, bitWidthPositions) + 1);
            skipUntilNextSampled = sampleRate - (from) % sampleRate;
            if ((from / sampleRate) == positions.getLength() - 2) {
                skipUntilNextSampled = length - from;
            }
            distance = 0;
            while (remaining > 0) {

                short c = (short) waveletFixedBlockBoosting.inverseSelect(samplePosition - 1);
                samplePosition =
                        (int)
                                (cumulativeCounts[c]
                                        + waveletFixedBlockBoosting.rank(samplePosition, c));

                // check if we are at the snippet
                if (distance >= skipUntilNextSampled) {

                    // this will exit if we find the left boundary
                    if (c == mappedBoundary) {
                        if (upStreamPos == 0) {
                            // this actually means the first char was a boundary
                            return 0;
                        }
                        finalPos = upStreamPos;
                    }

                    if (offset + downStreamLength + (upStreamPos) >= destination.length) {
                        throw new RuntimeException(
                                "Extraction does not fit in the supplied destination. "
                                        + "Currently extracted: "
                                        + (offset + downStreamLength + (upStreamPos)));
                    }
                    destination[offset + downStreamLength + (upStreamPos--)] =
                            (char) monotonicLookUp[c];
                    remaining--;
                }
                distance++;
            }
            // exit if we reached the end
            if (from == this.length - 1) {
                // If we found the EOF of the string in the first upStream segment, then when
                // we reach here it will be -1. But if we reached here, in the worst case we
                // only add a single char - thats where the 1 comes from. Otherwise, we add
                // whatever upStreamPos was incremented to.
                finalPos = (upStreamPos < 0) ? 1 : upStreamPos + from - prevFrom;
                break;
            }

            // update offset
            ++timesUpStream;
        }

        return downStreamLength + finalPos;
    }

    /**
     * Extracts the original string starting from the given position only to the left until the
     * boundary character is found, and then stores it in the given array, starting from the given
     * offset. If the supplied destination is not large enough, an exception will be thrown.
     *
     * @param from The starting position of the original string from which to extract left
     * @param destination The array where to store the extracted slice
     * @param offset The offset to shift the extracted string in the destination array
     * @param boundary The symbol at which to stop extracting, left from the starting position
     * @return The number of symbols extracted
     */
    public int extractUntilBoundaryLeft(int from, char[] destination, int offset, char boundary) {

        ++from; // include the "from" character
        checkBoundsForExtraction(from, destination);

        // calculate position of backward search
        int samplePosition =
                (int) (positions.getValue((from / sampleRate) + 1, bitWidthPositions) + 1);
        // number of letters to skip at the start
        int skipUntilNextSampled = sampleRate - (from) % sampleRate;

        // special case: we are at the last position
        if ((from / sampleRate) == positions.getLength() - 2) {
            skipUntilNextSampled = length - from;
        }

        int downStreamPos = destination.length - 1;

        // start the backwards search
        short mappedBoundary = monotonicMap.getOrDefault((int) boundary, (short) 0);
        if (mappedBoundary == 0) {
            throw new IllegalArgumentException("Boundary does not exist");
        }

        int distance = 0;
        while (true) {

            short c = (short) waveletFixedBlockBoosting.inverseSelect(samplePosition - 1);
            samplePosition =
                    (int) (cumulativeCounts[c] + waveletFixedBlockBoosting.rank(samplePosition, c));

            if (distance >= skipUntilNextSampled) {

                // this will exit if we find the left boundary
                if (c == mappedBoundary) {
                    break;
                }
                // special break: we are the very beginning
                if (c == 0) {
                    break;
                }

                destination[downStreamPos--] = (char) monotonicLookUp[c];

                if (downStreamPos == offset) {
                    throw new RuntimeException(
                            "Extraction does not fit in the supplied destination. "
                                    + "Currently extracted: "
                                    + (destination.length - offset));
                }
            }
            distance++;
        }

        // Shift the downstream to begin at 0 so that we can copy the upstream directly afterwards
        int downStreamLength = destination.length - (downStreamPos + 1);
        System.arraycopy(destination, downStreamPos + 1, destination, offset, downStreamLength);

        return downStreamLength;
    }

    /**
     * Extracts the original string starting from the given position only to the right until the
     * boundary character is found, and then stores it in the given array, starting from the given
     * offset. If the supplied destination is not large enough, an exception will be thrown.
     *
     * @param from The starting position of the original string from which to extract right
     * @param destination The array where to store the extracted slice
     * @param offset The offset to shift the extracted string in the destination array
     * @param boundary The symbol at which to stop extracting, right from the starting position
     * @return The number of symbols extracted
     */
    public int extractUntilBoundaryRight(int from, char[] destination, int offset, char boundary) {

        checkBoundsForExtraction(from, destination);

        short mappedBoundary = monotonicMap.getOrDefault((int) boundary, (short) 0);
        if (mappedBoundary == 0) {
            throw new IllegalArgumentException("Boundary does not exist");
        }

        // Do incremental (+4) searches
        int step = 4;
        int upStreamPos;
        int finalPos = -1;
        int timesUpStream = 1;
        while (finalPos == -1) {

            int prevFrom = from;
            from += step;
            from = Math.min(from, this.length - 1);
            int remaining = from - prevFrom;
            upStreamPos = (timesUpStream - 1) * step + remaining - 1;

            int samplePosition =
                    (int) (positions.getValue((from / sampleRate) + 1, bitWidthPositions) + 1);
            int skipUntilNextSampled = sampleRate - (from) % sampleRate;
            if ((from / sampleRate) == positions.getLength() - 2) {
                skipUntilNextSampled = length - from;
            }
            int distance = 0;
            while (remaining > 0) {

                short c = (short) waveletFixedBlockBoosting.inverseSelect(samplePosition - 1);
                samplePosition =
                        (int)
                                (cumulativeCounts[c]
                                        + waveletFixedBlockBoosting.rank(samplePosition, c));

                // check if we are at the snippet
                if (distance >= skipUntilNextSampled) {

                    // this will exit if we find the left boundary
                    if (c == mappedBoundary) {
                        if (upStreamPos == 0) {
                            // this actually means the first char was a boundary
                            return 0;
                        }
                        finalPos = upStreamPos;
                    }

                    if (offset + (upStreamPos) >= destination.length) {
                        throw new RuntimeException(
                                "Extraction does not fit in the supplied destination. "
                                        + "Currently extracted: "
                                        + (offset + (upStreamPos)));
                    }
                    // This is because our range is (from, boundary]
                    if (upStreamPos > 0) {
                        destination[offset + (upStreamPos--) - 1] = (char) monotonicLookUp[c];
                    }
                    remaining--;
                }
                distance++;
            }
            // exit if we reached the end
            if (from == this.length - 1) {
                // If we found the EOF of the string in the first upStream segment, then when
                // we reach here it will be -1. But if we reached here, in the worst case we
                // only add a single char - thats where the 1 comes from. Otherwise, we add
                // whatever upStreamPos was incremented to.
                finalPos = upStreamPos + from - prevFrom;
                break;
            }

            // update offset
            ++timesUpStream;
        }

        return finalPos - 1;
    }

    /**
     * Returns the size of the original string indexed.
     *
     * @return The size of the original string indexed
     */
    public int getInputLength() {
        return this.length;
    }

    /**
     * Gets sigma, the size of the alphabet of the original string, i.e., the number of different
     * symbols.
     *
     * @return The size of the alphabet
     */
    public int getAlphabetLength() {
        return this.monotonicMap.size();
    }

    /**
     * Serializes this object to an {@code ObjectOutput} stream.
     *
     * @param objectOutput The stream to which the object will be written
     */
    public void write(ObjectOutput objectOutput) throws IOException {
        objectOutput.writeByte(SERIAL_VERSION_V0);
        objectOutput.writeInt(sampleRate);
        objectOutput.writeBoolean(enableExtract);
        objectOutput.writeInt(bitWidthSuffixes);
        objectOutput.writeInt(bitWidthPositions);
        objectOutput.writeInt(length);

        objectOutput.writeInt(monotonicMap.keySet().size());
        for (int v : monotonicMap.keySet()) {
            objectOutput.writeInt(v);
            objectOutput.writeShort(monotonicMap.get(v));
        }
        objectOutput.writeInt(cumulativeCounts.length);
        for (int v : cumulativeCounts) {
            objectOutput.writeInt(v);
        }
        objectOutput.writeInt(monotonicLookUp.length);
        for (int v : monotonicLookUp) {
            objectOutput.writeInt(v);
        }
        suffixes.write(objectOutput);
        if (enableExtract) {
            positions.write(objectOutput);
        }
        sampledSuffixes.write(objectOutput);
        waveletFixedBlockBoosting.write(objectOutput);
    }

    /**
     * Deserializes an {@code FM-index} from an {@code ObjectInput} stream.
     *
     * @param objectInput The stream from which to read from
     * @return The deserialized instance of this object
     */
    public static FmIndex read(ObjectInput objectInput) throws IOException {
        checkSerialVersion(SERIAL_VERSION_V0, objectInput.readByte());

        int sampleRate = objectInput.readInt();
        boolean enableExtract = objectInput.readBoolean();
        int bitWidthSuffixes = objectInput.readInt();
        int bitWidthPositions = objectInput.readInt();
        int length = objectInput.readInt();

        int numKeys = objectInput.readInt();
        Map<Integer, Short> monotonicMap = new HashMap<>();
        for (int i = 0; i < numKeys; i++) {
            int key = objectInput.readInt();
            short value = objectInput.readShort();
            monotonicMap.put(key, value);
        }
        int[] cumulativeCounts = new int[objectInput.readInt()];
        for (int i = 0; i < cumulativeCounts.length; i++) {
            cumulativeCounts[i] = objectInput.readInt();
        }
        int[] monotonicLookup = new int[objectInput.readInt()];
        for (int i = 0; i < monotonicLookup.length; i++) {
            monotonicLookup[i] = objectInput.readInt();
        }
        IntVector suffixes = IntVector.read(objectInput);
        IntVector positions = (enableExtract) ? IntVector.read(objectInput) : null;
        RrrVector sampledSuffixes = RrrVector.read(objectInput);
        WaveletFixedBlockBoosting wavelet = WaveletFixedBlockBoosting.read(objectInput);

        return new FmIndex(
                sampleRate,
                enableExtract,
                bitWidthSuffixes,
                bitWidthPositions,
                length,
                monotonicMap,
                cumulativeCounts,
                monotonicLookup,
                suffixes,
                positions,
                sampledSuffixes,
                wavelet);
    }

    @Override
    public int hashCode() {
        return sampleRate
                + ((enableExtract) ? 1 : 0)
                + bitWidthSuffixes
                + bitWidthPositions
                + length
                + monotonicMap.hashCode()
                + Arrays.hashCode(cumulativeCounts)
                + Arrays.hashCode(monotonicLookUp)
                + suffixes.hashCode()
                + ((positions != null) ? positions.hashCode() : 0)
                + sampledSuffixes.hashCode()
                + waveletFixedBlockBoosting.hashCode();
    }

    @Override
    public String toString() {
        return "FMIndex-sampleRate:" + this.sampleRate + "-extract:" + this.enableExtract;
    }
}
