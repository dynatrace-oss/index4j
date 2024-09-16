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

import static com.dynatrace.util.Util.HDFS_2k_CHAR;
import static com.dynatrace.util.Util.HDFS_2k_STR;
import static com.dynatrace.util.Util.assertLocationsAreTheSame;
import static com.dynatrace.util.Util.extractUntilBoundary;
import static com.dynatrace.util.Util.extractUntilBoundaryLeft;
import static com.dynatrace.util.Util.extractUntilBoundaryRight;
import static com.dynatrace.util.Util.findExpectedMatchesWithOverlap;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dynatrace.serialization.Serialization;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class FmIndexTest {

    private final Random random = new Random(42);
    private static final int N_TESTS = 100;

    @Test
    void shouldCount() {
        char[] text = "This is a long string\0".toCharArray();
        char[] substring = "is".toCharArray();

        FmIndex fmi = new FmIndexBuilder().setEnableExtraction(false).build(text);
        int matches = fmi.count(substring);
        assertThat(matches).isEqualTo(findExpectedMatchesWithOverlap(text, substring));
    }

    @Test
    void shouldCountWithMultipleSentinels() {
        char[] text = "This \0is a \0long string\0".toCharArray();
        char[] substring = "is".toCharArray();

        FmIndex fmi = new FmIndex(text, 4);
        int matches = fmi.count(substring);
        assertThat(matches).isEqualTo(findExpectedMatchesWithOverlap(text, substring));

        substring = "\0".toCharArray();
        matches = fmi.count(substring);
        assertThat(matches).isEqualTo(findExpectedMatchesWithOverlap(text, "\0".toCharArray()));
    }

    @Test
    void shouldCountPartialString() {
        char[] text = "This is a long string\0".toCharArray();
        char[] substring = "is a long".toCharArray();

        FmIndex fmi = new FmIndexBuilder().build(text);
        int matches = fmi.count(substring, 0, 2); // "is" should appear twice
        assertThat(matches)
                .isEqualTo(
                        findExpectedMatchesWithOverlap(text, Arrays.copyOfRange(substring, 0, 2)));
    }

    @Test
    void shouldCountAndLocatePartiallyExistingPattern() {
        char[] text = "This is a long string\0".toCharArray();
        char[] substring = "baaa".toCharArray();

        FmIndex fmi = new FmIndexBuilder().build(text);
        int matches = fmi.count(substring);
        assertThat(matches).isEqualTo(0);

        matches = fmi.locate(substring, new int[0]);
        assertThat(matches).isEqualTo(0);
    }

    @Test
    void shouldCountSlicedString() {
        char[] text = "This is a long string\0".toCharArray();
        char[] substring = "is a long".toCharArray();

        FmIndex fmi = new FmIndexBuilder().build(text);
        int matches = fmi.count(substring, 2, 1); // the first space
        assertThat(matches)
                .isEqualTo(
                        findExpectedMatchesWithOverlap(text, Arrays.copyOfRange(substring, 2, 3)));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8, 16})
    void shouldCountFromLogFile(int sampleRate) {
        FmIndex fmi = new FmIndexBuilder().setSampleRate(sampleRate).build(HDFS_2k_CHAR);
        for (int i = 0; i < N_TESTS; i++) {
            int start = random.nextInt(HDFS_2k_STR.length() - 32);
            char[] substring =
                    HDFS_2k_STR.substring(start, start + random.nextInt(1, 32)).toCharArray();
            int count = fmi.count(substring);
            assertThat(count).isEqualTo(findExpectedMatchesWithOverlap(HDFS_2k_CHAR, substring));
        }
    }

    @Test
    void shouldCountAndLocateNonExistingPattern() {
        char[] text = "This is a long string\0".toCharArray();
        FmIndex fmi = new FmIndexBuilder().build(text);
        char[] pat1 = "does not exist here".toCharArray();
        char[] pat2 = "never seen".toCharArray();
        assertThat(fmi.count(pat1)).isEqualTo(0);
        assertThat(fmi.count(pat2)).isEqualTo(0);
        assertThat(fmi.count(pat1, 0, pat1.length)).isEqualTo(0);
        assertThat(fmi.count(pat2, 0, pat2.length)).isEqualTo(0);
        assertThat(fmi.locate(pat1, new int[1])).isEqualTo(0);
        assertThat(fmi.locate(pat2, new int[1])).isEqualTo(0);
    }

    @Test
    void shouldConvertFourByteUtf8() {
        byte[] pattern = new byte[6];
        pattern[0] = 'a';
        pattern[1] = (byte) 0b11110_000;
        pattern[2] = (byte) 0b10_000000;
        pattern[3] = (byte) 0b10_000000;
        pattern[4] = (byte) 0b10_000000;
        pattern[5] = 'c';
        char[] destination = new char[3];
        int charCount =
                FmIndex.convertBytePatternToCharPattern(pattern, 0, pattern.length, destination);
        assertThat(charCount).isEqualTo(3);
    }

    @Test
    void shouldComplainFromTooBigChar() {
        byte[] pattern = new byte[6];
        pattern[0] = 'a';
        pattern[1] = (byte) 0b11110_111;
        pattern[2] = (byte) 0b10_111000;
        pattern[3] = (byte) 0b10_111000;
        pattern[4] = (byte) 0b10_111000;
        pattern[5] = 'c';
        Exception exception =
                assertThrows(
                        RuntimeException.class,
                        () -> {
                            FmIndex.convertBytePatternToCharPattern(
                                    pattern, 0, pattern.length, new char[3]);
                        });
        assertThat(exception.getMessage())
                .isEqualTo("Found a character that exceeds (32767): it was 2068024");
    }

    @Test
    void shouldExceedCharsetLimit() {
        char[] textWithTooManySymbols = new char[Short.MAX_VALUE + 1];
        for (int i = 0; i < textWithTooManySymbols.length; i++) {
            textWithTooManySymbols[i] = (char) i;
        }
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> {
                            new FmIndex(textWithTooManySymbols, 32);
                        });
        assertThat(exception.getMessage()).isEqualTo("Input has more than 32767 different symbols");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8, 16})
    void shouldLocateFromLogFile(int sampleRate) {
        FmIndex fmi = new FmIndexBuilder().setSampleRate(sampleRate).build(HDFS_2k_CHAR);
        int[] locations = new int[10_000];
        for (int i = 0; i < N_TESTS; i++) {
            int start = random.nextInt(0, HDFS_2k_STR.length() - 32);
            char[] substring =
                    HDFS_2k_STR.substring(start, start + random.nextInt(16, 32)).toCharArray();
            int count = fmi.locate(substring, 0, substring.length, locations, 10_000);
            assertLocationsAreTheSame(count, locations, substring, HDFS_2k_CHAR);
        }
    }

    @Test
    void shouldLocateMaxNumberOfMatches() {
        FmIndex fmi = new FmIndexBuilder().build(HDFS_2k_CHAR);
        int count = fmi.locate("INFO".toCharArray(), 0, 4, new int[100], 100);
        assertThat(count).isEqualTo(100);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8, 16})
    void shouldLocateFromLogFileWithMultipleSentinels(int sampleRate) {
        char[] modified = Arrays.copyOf(HDFS_2k_CHAR, HDFS_2k_CHAR.length);
        for (int i = 0; i < 1_000; i++) {
            modified[random.nextInt(0, modified.length - 2)] = '\0';
        }
        FmIndex fmi = new FmIndexBuilder().setSampleRate(sampleRate).build(modified);
        int[] locations = new int[100_000];
        for (int i = 0; i < N_TESTS; i++) {
            int start = random.nextInt(0, modified.length - 32);
            char[] substring = Arrays.copyOfRange(modified, start, start + random.nextInt(1, 32));
            int count = fmi.locate(substring, 0, substring.length, locations, -1);
            assertLocationsAreTheSame(count, locations, substring, modified);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8, 16})
    void shouldSerializeAndLocateFromLogFile(int sampleRate) throws IOException {

        FmIndex fmi =
                new FmIndexBuilder()
                        .setSampleRate(sampleRate)
                        .setEnableExtraction(false)
                        .build(HDFS_2k_CHAR);

        byte[] serialized = Serialization.writeToByteArray(FmIndex::write, fmi);
        FmIndex deserialized = Serialization.readFromByteArray(FmIndex::read, serialized);

        assertEquals(deserialized.hashCode(), fmi.hashCode());

        int[] locations = new int[100_000];
        for (int i = 0; i < N_TESTS; i++) {
            int start = random.nextInt(0, HDFS_2k_STR.length() - 32);
            char[] substring =
                    HDFS_2k_STR.substring(start, start + random.nextInt(1, 32)).toCharArray();
            int count = deserialized.locate(substring, 0, substring.length, locations, -1);
            assertLocationsAreTheSame(count, locations, substring, HDFS_2k_CHAR);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8, 16})
    void shouldLocateSlicedSubstringFromLogFile(int sampleRate) {
        FmIndex fmi = new FmIndexBuilder().setSampleRate(sampleRate).build(HDFS_2k_CHAR);
        int[] locations = new int[10_000];
        for (int i = 0; i < N_TESTS; i++) {
            int start = random.nextInt(0, HDFS_2k_STR.length() - 32);
            char[] substring =
                    HDFS_2k_STR.substring(start, start + random.nextInt(32, 64)).toCharArray();
            int subStart = random.nextInt(0, 8);
            int subEnd = random.nextInt(subStart + 16, substring.length - subStart);
            int count = fmi.locate(substring, subStart, subEnd - subStart, locations, 10_000);

            assertLocationsAreTheSame(
                    count,
                    locations,
                    new String(substring, subStart, subEnd - subStart).toCharArray(),
                    HDFS_2k_CHAR);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8, 16})
    void shouldLocateBytePatternFromLogFile(int sampleRate) {
        FmIndex fmi = new FmIndexBuilder().setSampleRate(sampleRate).build(HDFS_2k_CHAR);
        char[] bytesToPattern = new char[32];
        int[] locations = new int[50_000];
        for (int i = 0; i < N_TESTS; i++) {
            int start = random.nextInt(0, HDFS_2k_STR.length() - 32);
            String aux = HDFS_2k_STR.substring(start, start + random.nextInt(1, 32));
            char[] substring = aux.toCharArray();
            byte[] byteString = aux.getBytes(StandardCharsets.UTF_8);
            int size =
                    FmIndex.convertBytePatternToCharPattern(
                            byteString, 0, byteString.length, bytesToPattern);
            int count = fmi.locate(bytesToPattern, 0, size, locations, -1);
            assertLocationsAreTheSame(count, locations, substring, HDFS_2k_CHAR);
        }
    }

    @Test
    void shouldAttemptExtractionWithoutEnabled() {
        FmIndex fmi = new FmIndexBuilder().setEnableExtraction(false).build(HDFS_2k_CHAR);
        Exception exception =
                assertThrows(
                        RuntimeException.class,
                        () -> {
                            fmi.extract(50, 100, new char[50], 0);
                        });
        assertThat(exception.getMessage()).isEqualTo("Text recovery not enabled at build time");

        exception =
                assertThrows(
                        RuntimeException.class,
                        () -> {
                            fmi.extractUntilBoundary(50, new char[50], 0, '\n');
                        });
        assertThat(exception.getMessage()).isEqualTo("Text recovery not enabled at build time");
    }

    @Test
    void shouldTestOutOfBoundsExtraction() {
        FmIndex fmi = new FmIndexBuilder().build(HDFS_2k_CHAR);
        Exception exception =
                assertThrows(
                        RuntimeException.class,
                        () -> {
                            fmi.extract(-5, 100, new char[50], 0);
                        });
        assertThat(exception.getMessage()).isEqualTo("Requested position less than 0");

        exception =
                assertThrows(
                        RuntimeException.class,
                        () -> {
                            fmi.extract(
                                    HDFS_2k_CHAR.length, HDFS_2k_CHAR.length + 50, new char[50], 0);
                        });
        assertThat(exception.getMessage()).isEqualTo("Stop position longer than index string");

        exception =
                assertThrows(
                        RuntimeException.class,
                        () -> {
                            fmi.extract(50, 100, new char[10], 0);
                        });
        assertThat(exception.getMessage()).isEqualTo("Supplied destination is not large enough");

        exception =
                assertThrows(
                        RuntimeException.class,
                        () -> {
                            fmi.extractUntilBoundary(-5, new char[50], 0, '\n');
                        });
        assertThat(exception.getMessage()).isEqualTo("Requested position less than 0");

        exception =
                assertThrows(
                        RuntimeException.class,
                        () -> {
                            fmi.extractUntilBoundary(
                                    HDFS_2k_CHAR.length + 1, new char[50], 0, '\n');
                        });
        assertThat(exception.getMessage()).isEqualTo("Requested position longer than index string");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8, 16})
    void shouldExtractFullTextFromLogFile(int sampleRate) {
        FmIndex fmi = new FmIndexBuilder().setSampleRate(sampleRate).build(HDFS_2k_CHAR);
        char[] destination = new char[HDFS_2k_CHAR.length];
        int extracted = fmi.extract(0, HDFS_2k_CHAR.length, destination, 0);
        assertThat(extracted).isEqualTo(HDFS_2k_CHAR.length);
        assertThat(new String(destination, 0, extracted)).isEqualTo(new String(HDFS_2k_CHAR));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8, 16})
    void shouldExtractPatternFromLogFile(int sampleRate) {
        FmIndex fmi = new FmIndexBuilder().setSampleRate(sampleRate).build(HDFS_2k_CHAR);
        char[] destination = new char[100];
        for (int i = 0; i < N_TESTS; i++) {
            int start = random.nextInt(HDFS_2k_CHAR.length - 100);
            int end = start + random.nextInt(100);

            int extracted = fmi.extract(start, end, destination, 0);
            assertThat(extracted).isEqualTo(end - start);
            assertThat(new String(destination, 0, end - start))
                    .isEqualTo(new String(HDFS_2k_CHAR, start, end - start));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 14, 66})
    void shouldExtractUntilBoundaryCornerCases(int seed) {
        String s = "What a string!\nNow this is long, indeed\nBut others could be longer.";
        char[] text = s.toCharArray();
        for (int sampleRate = 1; sampleRate <= 256; sampleRate <<= 1) {
            FmIndex fmi = new FmIndexBuilder().setSampleRate(sampleRate).build(s.toCharArray());
            char[] destination = new char[100];

            int letters = fmi.extractUntilBoundary(seed, destination, 0, '\n');
            String actual = new String(destination, 0, letters);
            String expected = extractUntilBoundary(text, seed, '\n');
            assertThat(actual).isEqualTo(expected);

            letters = fmi.extractUntilBoundaryLeft(seed, destination, 0, '\n');
            actual = new String(destination, 0, letters);
            expected = extractUntilBoundaryLeft(text, seed, '\n');
            assertThat(actual).isEqualTo(expected);

            letters = fmi.extractUntilBoundaryRight(seed, destination, 0, '\n');
            actual = new String(destination, 0, letters);
            expected = extractUntilBoundaryRight(text, seed, '\n');
            assertThat(actual).isEqualTo(expected);
        }
    }

    @Test
    void shouldAttemptExtractionWithNonExistingBoundary() {
        FmIndex fmi = new FmIndexBuilder().build(HDFS_2k_CHAR);
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> {
                            fmi.extractUntilBoundary(50, new char[50], 0, '이');
                        });
        assertThat(exception.getMessage()).isEqualTo("Boundary does not exist");

        exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> {
                            fmi.extractUntilBoundaryLeft(50, new char[50], 0, '이');
                        });
        assertThat(exception.getMessage()).isEqualTo("Boundary does not exist");

        exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> {
                            fmi.extractUntilBoundaryRight(50, new char[50], 0, '이');
                        });
        assertThat(exception.getMessage()).isEqualTo("Boundary does not exist");
    }

    @Test
    void shouldAttemptExtractionForTooLongOfAString() {
        FmIndex fmi = new FmIndexBuilder().build(HDFS_2k_CHAR);

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> {
                            fmi.extractUntilBoundary(50, new char[0], 0, '\n');
                        });
        assertThat(exception.getMessage())
                .isEqualTo("Supplied destination for extraction has size zero");

        exception =
                assertThrows(
                        RuntimeException.class,
                        () -> {
                            fmi.extractUntilBoundary(50, new char[10], 0, '\n');
                        });
        assertThat(exception.getMessage())
                .isEqualTo(
                        "Extraction does not fit in the supplied destination. Currently "
                                + "extracted: 13");

        exception =
                assertThrows(
                        RuntimeException.class,
                        () -> {
                            fmi.extractUntilBoundaryLeft(50, new char[10], 0, '\n');
                        });
        assertThat(exception.getMessage())
                .isEqualTo(
                        "Extraction does not fit in the supplied destination. Currently "
                                + "extracted: 10");

        exception =
                assertThrows(
                        RuntimeException.class,
                        () -> {
                            fmi.extractUntilBoundaryRight(50, new char[10], 0, '\n');
                        });
        assertThat(exception.getMessage())
                .isEqualTo(
                        "Extraction does not fit in the supplied destination. Currently "
                                + "extracted: 11");
    }

    @Test
    void shouldExtractTwoFirstLogLines() {
        FmIndex fmi = new FmIndexBuilder().build(HDFS_2k_CHAR);
        char[] destination = new char[300];
        char boundary = '\n';

        // extract first log line
        int extracted = fmi.extractUntilBoundary(5, destination, 0, boundary);
        destination[extracted++] = boundary; // append boundary
        // and second
        extracted += fmi.extractUntilBoundary(extracted + 2, destination, extracted, boundary);
        String expected =
                "081109 203533 44 INFO root: this file should have 2061 unique characters, i"
                        + "ncluding 3 and 4 byte UTF8 encoded";
        expected +=
                "\n"
                        + "081109 203615 148 INFO dfs.DataNode$PacketResponder: PacketRe"
                        + "sponder 1 for block blk_38865049064139660 由电画留當疾療発 terminating";
        assertThat(new String(destination, 0, extracted)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8, 16})
    void shouldExtractUntilBoundaryFromLogFile(int sampleRate) {
        FmIndex fmi = new FmIndexBuilder().setSampleRate(sampleRate).build(HDFS_2k_CHAR);
        char[] destination = new char[1 << 15];

        for (int i = 0; i < N_TESTS; i++) {
            int seed = random.nextInt(HDFS_2k_CHAR.length - 100);
            int extracted = fmi.extractUntilBoundary(seed, destination, 0, '\n');
            String actual = new String(destination, 0, extracted).replace('\r', ' ');
            String expected = extractUntilBoundary(HDFS_2k_CHAR, seed, '\n').replace('\r', ' ');
            assertThat(actual).isEqualTo(expected);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8, 16})
    void shouldExtractLeftUntilBoundaryFromLogFile(int sampleRate) {
        FmIndex fmi = new FmIndexBuilder().setSampleRate(sampleRate).build(HDFS_2k_CHAR);
        char[] destination = new char[1 << 15];

        for (int i = 0; i < N_TESTS; i++) {
            int seed = random.nextInt(HDFS_2k_CHAR.length - 100);
            int extracted = fmi.extractUntilBoundaryLeft(seed, destination, 0, '\n');
            String actual = new String(destination, 0, extracted).replace('\r', ' ');
            String expected = extractUntilBoundaryLeft(HDFS_2k_CHAR, seed, '\n').replace('\r', ' ');
            assertThat(actual).isEqualTo(expected);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8, 16})
    void shouldExtractRightUntilBoundaryFromLogFile(int sampleRate) {
        FmIndex fmi = new FmIndexBuilder().setSampleRate(sampleRate).build(HDFS_2k_CHAR);
        char[] destination = new char[1 << 15];

        for (int i = 0; i < N_TESTS; i++) {
            int seed = random.nextInt(HDFS_2k_CHAR.length - 100);
            int extracted = fmi.extractUntilBoundaryRight(seed, destination, 0, '\n');
            String actual = new String(destination, 0, extracted).replace('\r', ' ');
            String expected =
                    extractUntilBoundaryRight(HDFS_2k_CHAR, seed, '\n').replace('\r', ' ');
            assertThat(actual).isEqualTo(expected);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8, 16})
    void shouldSerializeAndExtractUntilBoundaryFromLogFile(int sampleRate) throws IOException {
        FmIndex fmi = new FmIndexBuilder().setSampleRate(sampleRate).build(HDFS_2k_CHAR);
        char[] destination = new char[1 << 15];

        byte[] serialized = Serialization.writeToByteArray(FmIndex::write, fmi);
        FmIndex deserialized = Serialization.readFromByteArray(FmIndex::read, serialized);

        assertEquals(deserialized.hashCode(), fmi.hashCode());

        for (int i = 0; i < N_TESTS; i++) {
            int seed = random.nextInt(HDFS_2k_CHAR.length - 100);
            int extracted = deserialized.extractUntilBoundary(seed, destination, 0, '\n');
            String actual = new String(destination, 0, extracted).replace('\r', ' ');
            String expected = extractUntilBoundary(HDFS_2k_CHAR, seed, '\n').replace('\r', ' ');
            assertThat(actual).isEqualTo(expected);
        }
    }

    @Test
    void shouldTestConvenienceMethods() {
        FmIndex fmi = new FmIndexBuilder().build(HDFS_2k_CHAR);
        assertThat(fmi.getInputLength()).isEqualTo(HDFS_2k_CHAR.length + 1); // sentinel char
        assertThat(fmi.getAlphabetLength())
                .isEqualTo(
                        HDFS_2k_STR.chars()
                                        .mapToObj(e -> Character.toString((char) e))
                                        .distinct()
                                        .toList()
                                        .size()
                                + 1 // sentinel char
                        );
        assertThat(fmi.toString()).isEqualTo("FMIndex-sampleRate:32-extract:true");
    }
}
