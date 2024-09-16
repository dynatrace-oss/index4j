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
package com.dynatrace.util;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class includes convenient functions for testing the behavior of the supported data
 * structures.
 */
public class Util {

    /** Path to the resources folder where test data is available. */
    public static final String PATH = String.valueOf(Path.of("src", "test", "resources"));

    /** Some strings for testing purposes. */
    public static final char[] SMALLER_TEXT =
            "aloha what a string this is string is eh".toCharArray();

    public static final char[] LONGER_TEXT =
            ("""
        To Sherlock Holmes she is always the woman. I have seldom heard him
        mention her under any other name. In his eyes she eclipses and
        predominates the whole of her sex. It was not that he felt any
        emotion akin to love for Irene Adler. All emotions, and that one
        particularly, were abhorrent to his cold, precise but admirably
        balanced mind. He was, I take it, the most perfect reasoning and
        observing machine that the world has seen, but as a lover he would
        have placed himself in a false position. He never spoke of the softer
        passions, save with a gibe and a sneer. They were admirable things
        for the observer--excellent for drawing the veil from men's motives
        and actions. But for the trained reasoner to admit such intrusions
        into his own delicate and finely adjusted temperament was to
        introduce a distracting factor which might throw a doubt upon all his
        mental results. Grit in a sensitive instrument, or a crack in one of
        his own high-power lenses, would not be more disturbing than a strong
        emotion in a nature such as his. And yet there was but one woman to
        him, and that woman was the late Irene Adler, of dubious and
        questionable memory.

        I had seen little of Holmes lately. My marriage had drifted us away
        from each other. My own complete happiness, and the home-centred
        interests which rise up around the man who first finds himself master
        of his own establishment, were sufficient to absorb all my attention,
        while Holmes, who loathed every form of society with his whole
        Bohemian soul, remained in our lodgings in Baker Street, buried among
        his old books, and alternating from week to week between cocaine and
        ambition, the drowsiness of the drug, and the fierce energy of his
        own keen nature. He was still, as ever, deeply attracted by the study
        of crime, and occupied his immense faculties and extraordinary powers
        of observation in following out those clues, and clearing up those
        mysteries which had been abandoned as hopeless by the official
        police. From time to time I heard some vague account of his doings:
        of his summons to Odessa in the case of the Trepoff murder, of his
        clearing up of the singular tragedy of the Atkinson brothers at
        Trincomalee, and finally of the mission which he had accomplished so
        delicately and successfully for the reigning family of Holland.
        Beyond these signs of his activity, however, which I merely shared
        with all the readers of the daily press, I knew little of my former
        friend and companion.""")
                    .toCharArray();

    public static String HDFS_2k_STR;
    public static char[] HDFS_2k_CHAR;

    static {
        byte[] data;
        try {
            data = Files.readAllBytes(Path.of(PATH, "HDFS_2k_multichar.log"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        HDFS_2k_STR = new String(data, StandardCharsets.UTF_8);
        HDFS_2k_CHAR = HDFS_2k_STR.toCharArray();
    }

    /** And their integer counterparts. */
    public static final short[] LONGER_TEXT_SHORT = shortArrayFromCharArray(LONGER_TEXT);

    /**
     * Finds the overlapping matches of a literal pattern in a corpus of text.
     *
     * @param text The text to be searched
     * @param pattern The literal pattern (no regex)
     * @return The number of overlapping matches
     */
    public static int findExpectedMatchesWithOverlap(char[] text, char[] pattern) {
        Pattern p = Pattern.compile(Pattern.quote(new String(pattern)));
        Matcher m = p.matcher(new String(text));
        int matches = 0;
        while (m.find()) {
            m.region(m.start() + 1, text.length);
            ++matches;
        }
        return matches;
    }

    /**
     * Finds the overlapping matches and positions of a literal pattern in a corpus of text.
     *
     * @param text The text to be searched
     * @param pattern The literal pattern (no regex)
     * @return A list containing the sorted positions of the overlapping matches
     */
    public static List<Integer> findExpectedLocationsWithOverlap(char[] text, char[] pattern) {
        Pattern p = Pattern.compile(Pattern.quote(new String(pattern)));
        Matcher m = p.matcher(new String(text));
        List<Integer> locations = new ArrayList<>();
        while (m.find()) {
            locations.add(m.start());
            m.region(m.start() + 1, text.length);
        }
        locations.sort(Integer::compare);
        return locations;
    }

    /**
     * Utility function to convert a char array into a short array, assuming each char is not taking
     * more than 2 bytes to encode.
     *
     * @param array The char array to be converted
     * @return A short array with the values of the char array
     */
    public static short[] shortArrayFromCharArray(char[] array) {
        short[] a = new short[array.length];
        for (int i = 0; i < array.length; i++) {
            a[i] = (short) array[i];
        }
        return a;
    }

    /**
     * Returns a String representing a slice {@code [a_i, ..., a_j]} of an input text {@code [a_0,
     * ..., a_n]} where {@code a_i} is the first left appearance of a boundary character starting
     * from {@code seed}, and {@code a_j} is the first right appearance of the character. If no such
     * character is found, the search continues until the start or end of the input text.
     *
     * @param text The input text to slice based on
     * @param seed The position from which to start looking left and right
     * @param boundary The character to start/stop the slice
     * @return The slice of the input text starting and ending with the boundary character
     */
    public static String extractUntilBoundary(char[] text, int seed, char boundary) {

        if (text[seed] == boundary) {
            return "";
        }

        StringBuilder downStream = new StringBuilder();
        int i = seed;
        while (i >= 0) {
            if (text[i] != boundary) {
                downStream.append(text[i]);
            } else {
                break;
            }
            --i;
        }

        StringBuilder upStream = new StringBuilder();
        i = seed + 1;
        while (i < text.length) {
            if (text[i] != boundary) {
                upStream.append(text[i]);
            } else {
                break;
            }
            ++i;
        }

        return downStream.reverse() + upStream.toString();
    }

    /**
     * Returns a String representing a slice {@code [a_i, ..., a_j]} of an input text {@code [a_0,
     * ..., a_n]} where {@code a_i} is the first left appearance of a boundary character starting
     * from {@code seed}. If no such character is found, the search continues until the start or end
     * of the input text.
     *
     * @param text The input text to slice based on
     * @param seed The position from which to start looking left
     * @param boundary The character to start/stop the slice
     * @return The slice of the input text starting and ending with the boundary character
     */
    public static String extractUntilBoundaryLeft(char[] text, int seed, char boundary) {

        if (text[seed] == boundary) {
            return "";
        }

        StringBuilder downStream = new StringBuilder();
        int i = seed;
        while (i >= 0) {
            if (text[i] != boundary) {
                downStream.append(text[i]);
            } else {
                break;
            }
            --i;
        }

        return downStream.reverse().toString();
    }

    /**
     * Returns a String representing a slice {@code [a_i, ..., a_j]} of an input text {@code [a_0,
     * ..., a_n]} where {@code a_j} is the first right appearance of the character starting from
     * {@code seed}. If no such character is found, the search continues until the start or end of
     * the input text.
     *
     * @param text The input text to slice based on
     * @param seed The position from which to start looking right
     * @param boundary The character to start/stop the slice
     * @return The slice of the input text starting and ending with the boundary character
     */
    public static String extractUntilBoundaryRight(char[] text, int seed, char boundary) {

        if (text[seed] == boundary) {
            return "";
        }

        StringBuilder upStream = new StringBuilder();
        int i = seed + 1;
        while (i < text.length) {
            if (text[i] != boundary) {
                upStream.append(text[i]);
            } else {
                break;
            }
            ++i;
        }

        return upStream.toString();
    }

    /**
     * Asserts that the number of locations found and the actual locations (sorted) are correct.
     * Note that locations are found with overlap, i.e., non-greedy policy for consuming matches.
     *
     * @param count The actual number of matches
     * @param locations The actual start of each location (does not need to be sorted)
     * @param substring The substring for which we will find the expected matches
     * @param text The corpus of text in which we will search the substring
     */
    public static void assertLocationsAreTheSame(
            int count, int[] locations, char[] substring, char[] text) {
        List<Integer> expected = findExpectedLocationsWithOverlap(text, substring);
        assertThat(count).isEqualTo(expected.size());
        List<Integer> sortedMatches = new ArrayList<>(count);
        for (int j = 0; j < count; j++) {
            sortedMatches.add(locations[j]);
        }
        sortedMatches.sort(Integer::compare);
        assertThat(sortedMatches).isEqualTo(expected);
    }

    /**
     * Counts how many times a symbol appears in a sequence of symbols until reaching a given end.
     *
     * @param symbol The symbol whose frequency we will count
     * @param temp The sequence of symbols
     * @param until The end point
     * @return The number of occurrences
     */
    public static int countPreviousOccurrencesOfSymbol(char symbol, char[] temp, int until) {
        int counter = 0;
        for (int i = 0; i < until; i++) {
            if (temp[i] == symbol) {
                counter++;
            }
        }
        return counter;
    }

    /**
     * Counts how many times a symbol appears in a sequence of symbols until reaching a given end.
     *
     * @param symbol The symbol whose frequency we will count
     * @param temp The sequence of symbols
     * @param until The end point
     * @return The number of occurrences
     */
    public static int countPreviousOccurrencesOfSymbol(short symbol, short[] temp, int until) {
        int counter = 0;
        for (int i = 0; i < until; i++) {
            if (temp[i] == symbol) {
                counter++;
            }
        }
        return counter;
    }

    /**
     * Returns the mapping of a monotonically increasing alphabet transformation.
     *
     * @param text The input text
     * @return A map to map the input text (from symbol to number)
     */
    public static Map<Character, Short> convertAlphabetToMonotonicSequence(char[] text) {
        Map<Character, Short> map = new HashMap<>();
        short code = 0;
        for (char c : text) {
            if (map.putIfAbsent(c, code) == null) {
                ++code;
            }
        }

        return map;
    }

    /**
     * Returns the reverse mapping of a monotonically increasing alphabet transformation.
     *
     * @param text The input text
     * @return A map to get the inverse mapping (from number to symbol)
     */
    public static Map<Short, Character> getReverseMonotonicAlphabet(char[] text) {
        Map<Character, Short> map = new HashMap<>();
        short code = 0;
        for (char c : text) {
            if (map.putIfAbsent(c, code) == null) {
                ++code;
            }
        }
        Map<Short, Character> reverse = new HashMap<>();
        for (char c : map.keySet()) {
            reverse.putIfAbsent(map.get(c), c);
        }

        return reverse;
    }

    /**
     * Converts an input text of symbols into a representation where all symbols are monotonically
     * increasing by one.
     *
     * @param text The input text
     * @param map The map between symbols and numbers
     * @return The transformed text
     */
    public static short[] transformStringIntoMonotonicWithAlphabet(
            char[] text, Map<Character, Short> map) {
        short[] temp = new short[text.length];
        for (int i = 0; i < text.length; i++) {
            temp[i] = map.get(text[i]);
        }
        return temp;
    }
}
