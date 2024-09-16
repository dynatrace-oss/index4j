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

import com.dynatrace.metrics.IngestMetrics;
import com.dynatrace.metrics.Util;
import java.io.IOException;
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

@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(
        value = 1,
        jvmArgs = {"-Xms4096m", "-Xmx32g", "-Djdk.attach.allowAttachSelf=true"})
@Warmup(iterations = 0, batchSize = 1)
@Measurement(iterations = 1, batchSize = 1)
@Timeout(time = 3, timeUnit = TimeUnit.HOURS)
public class SuffixArraySerializedSizeBenchmark {

    /*

    (data) = Android.log (logpai repository) (~184 MB, ~1100 different characters)
    Benchmark                          (data)  Mode  Cnt          Score   Error  Units
    serializedSizeBenchmark       Android.log    ss             111.885           s/op
    serializedSizeBenchmark:size  Android.log    ss       964057202.000              #

     */

    @Benchmark
    public void serializedSizeBenchmark(
            SuffixArrayIngestState state, Blackhole blackhole, IngestMetrics metrics)
            throws IOException {

        SuffixArray nsa = new SuffixArray(state.text);
        nsa.construct();

        long bytes = Util.countSerializedSize(SuffixArray::write, nsa);
        metrics.trackSerializedSize(bytes);
        blackhole.consume(bytes);
    }
}
