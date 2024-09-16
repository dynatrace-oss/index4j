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

import static com.dynatrace.intsequence.Common.minimumNumberOfBits;
import static com.dynatrace.serialization.Serialization.checkSerialVersion;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import org.junit.jupiter.api.Test;

public class UtilTest {

    @Test
    void shouldComputeMinimumNumberOfBits() {
        for (long v = 0; v < 1_000; v++) {
            int minBits = minimumNumberOfBits(v);
            assertThat(v >>> minBits).isEqualTo(0);
        }
    }

    @Test
    void shouldComplainFromIncompatibleSerialVersions() {
        byte version1 = 0;
        byte version2 = 5;
        Exception exception =
                assertThrows(
                        IOException.class,
                        () -> {
                            checkSerialVersion(version1, version2);
                        });
        assertThat(exception.getMessage())
                .isEqualTo("Incompatible serial versions! Expected version 0 but was 5.");
    }
}
