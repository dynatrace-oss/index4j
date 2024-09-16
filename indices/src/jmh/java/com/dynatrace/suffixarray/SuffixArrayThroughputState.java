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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class SuffixArrayThroughputState {
    @Param("data")
    public String data;

    @Param("8")
    public int minQueryLength = 8;

    @Param("32")
    public int maxQueryLength = 32;

    @Param("64")
    public int maxExtractionLength = 64;

    @Param("1000")
    public int maxMatches = 1000;

    @Param("20000")
    public int differentQueries = 20000;

    public SuffixArray suffixArray;
    public int[] locations;
    public char[] destination;
    public char[] input;
    private List<String> queries;
    private List<String> nonIndexedQueries;
    private int nextQuery;

    @Setup(Level.Trial)
    public void setupIndex() throws IOException {
        // Create input
        String input = Files.readString(Path.of(data));
        this.input = input.toCharArray();
        this.suffixArray = new SuffixArray(input);
        this.suffixArray.construct();

        // Gather patterns to query
        Random r = new Random(42);
        queries = new ArrayList<>(differentQueries);
        nonIndexedQueries = new ArrayList<>(differentQueries);
        for (int i = 0; i < differentQueries; i++) {
            int start = r.nextInt(0, input.length() - maxQueryLength);
            int size = r.nextInt(minQueryLength, maxQueryLength);
            queries.add(input.substring(start, start + size));
            nonIndexedQueries.add(generateRandomPattern(size, r));
        }

        locations = new int[maxMatches];
        destination = new char[Math.max(maxQueryLength, maxExtractionLength)];
    }

    @Setup(Level.Iteration)
    public void setupIteration() {
        nextQuery = 0;
    }

    public String getNextQuery() {
        return queries.get(nextQuery++ % queries.size());
    }

    public String getNextNonIndexedQuery() {
        return nonIndexedQueries.get(nextQuery++ % queries.size());
    }

    private String generateRandomPattern(int length, Random random) {
        char[] pattern = new char[length];
        for (int i = 0; i < length; i++) {
            pattern[i] = (char) (random.nextInt(26) + 97);
        }
        return String.valueOf(pattern);
    }
}
