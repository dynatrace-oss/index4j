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
package com.dynatrace.run;

import com.dynatrace.fm.FmIndex;
import com.dynatrace.fm.FmIndexBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Runner {

    private Runner() {
        // Main class
    }

    public static void main(String[] args) throws IOException {

        if (args.length < 2) {
            throw new IllegalArgumentException(
                    "Error: Please use parameters:\n" + "<input file> <sample rate>");
        }

        String input = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
        int sampleRate = Integer.parseInt(args[1]);
        FmIndex fmi =
                new FmIndexBuilder()
                        .setSampleRate(sampleRate)
                        .setEnableExtraction(true)
                        .build(input.toCharArray());

        System.out.println("Alphabet size: " + fmi.getAlphabetLength()); // how many symbols
        System.out.println(
                "Input length : " + fmi.getInputLength()); // the length of the input `text`

        String pattern = "INFO";
        System.out.println("Occurrences of " + pattern + ": " + fmi.count(pattern.toCharArray()));
    }
}
