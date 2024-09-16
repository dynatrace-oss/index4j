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

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Timeout;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(
        value = 3,
        jvmArgs = {"-Xms4096m", "-Xmx32g", "-Djdk.attach.allowAttachSelf=true"})
@Warmup(iterations = 3, batchSize = 1)
@Measurement(iterations = 5, batchSize = 1)
@Timeout(time = 3, timeUnit = TimeUnit.HOURS)
public class SuffixArrayIngestBenchmark {

    /*

    (data) = Android.log (logpai repository) (~184 MB, ~1100 different characters)
    Benchmark             (data)  Mode  Cnt   Score   Error  Units
    ingestBenchmark  Android.log  avgt   15  95.042 ± 7.128   s/op

     */

    @Benchmark
    public void ingestBenchmark(SuffixArrayIngestState state, Blackhole blackhole) {
        SuffixArray nsa = new SuffixArray(state.text);
        nsa.construct();
        blackhole.consume(nsa.getSuffixArray().length);
    }
}
