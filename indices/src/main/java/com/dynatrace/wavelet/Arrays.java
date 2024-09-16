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

/** Straightforward implementations for finding the maximum value of an array of different types. */
final class Arrays {

    private Arrays() {
        // Utility class
    }

    static int maxValueOfArray(char[] array) {
        int max = Integer.MIN_VALUE;
        for (int v : array) {
            if (max < v) {
                max = v;
            }
        }
        return max;
    }

    static int maxValueOfArray(short[] array) {
        int max = Integer.MIN_VALUE;
        for (int v : array) {
            if (max < v) {
                max = v;
            }
        }
        return max;
    }
}
