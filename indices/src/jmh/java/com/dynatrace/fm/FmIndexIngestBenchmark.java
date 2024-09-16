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
public class FmIndexIngestBenchmark {

    /*

    (data) = Android.log (logpai repository) (~184 MB, ~1100 different characters)
    Benchmark             (data)  (sampleRate)  Mode  Cnt   Score   Error  Units
    ingestBenchmark  Android.log             1  avgt   15  76.298 ± 3.482   s/op
    ingestBenchmark  Android.log             2  avgt   15  75.457 ± 2.207   s/op
    ingestBenchmark  Android.log             4  avgt   15  71.284 ± 2.189   s/op
    ingestBenchmark  Android.log             8  avgt   15  75.269 ± 4.026   s/op
    ingestBenchmark  Android.log            16  avgt   15  73.317 ± 1.969   s/op
    ingestBenchmark  Android.log            32  avgt   15  70.544 ± 4.448   s/op
    ingestBenchmark  Android.log            64  avgt   15  67.728 ± 0.327   s/op
    ingestBenchmark  Android.log           128  avgt   15  68.675 ± 4.894   s/op
    ingestBenchmark  Android.log           256  avgt   15  70.083 ± 6.244   s/op
     */

    @Benchmark
    public void ingestBenchmark(FmIndexIngestState state, Blackhole blackhole) {
        FmIndex fmIndex = new FmIndex(state.text, state.sampleRate, true);
        blackhole.consume(fmIndex.getAlphabetLength());
    }
}
