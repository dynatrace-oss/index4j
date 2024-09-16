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

import static com.dynatrace.util.Util.PATH;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public class RunnerTest {

    @Test
    void shouldRunMainSuccessfully() throws IOException {
        String[] args = new String[] {Path.of(PATH, "HDFS_2k_multichar.log").toString(), "32"};
        Runner.main(args);
    }

    @Test
    void shouldRunMainAndThrowException() {
        String[] args =
                new String[] {
                    Path.of(PATH, "HDFS_2k_multichar.log").toString(),
                };

        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> Runner.main(args));
        assertThat(exception.getMessage())
                .isEqualTo("Error: Please use parameters:\n<input file> <sample rate>");
    }
}
