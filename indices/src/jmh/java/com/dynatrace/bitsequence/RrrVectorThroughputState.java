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
package com.dynatrace.bitsequence;

import java.util.Random;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class RrrVectorThroughputState {

    @Param("10000000")
    public int bitLength = 10_000_000;

    @Param({"16", "32", "64"})
    public int sampleRate = 32;

    public RrrVector rankSupport;
    private final Random random = new Random(42);

    @Setup(Level.Trial)
    public void setup() {
        int[] fingerprints = random.ints(bitLength / (Integer.BYTES * 8)).toArray();
        rankSupport = new RrrVector(fingerprints, sampleRate);
    }

    public int getNextQuery() {
        return random.nextInt(bitLength);
    }
}
