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
public class FmIndexSerializedSizeBenchmark {

    /*

    (data) = Android.log (logpai repository) (~184 MB, ~1100 different characters)
    Benchmark                           (data)  (sampleRate)  Mode  Cnt           Score   Error  Units
    serializedSizeBenchmark       Android.log             1    ss               84.554           s/op
    serializedSizeBenchmark:size  Android.log             1    ss       1534371662.000              #
    serializedSizeBenchmark       Android.log             2    ss               78.448           s/op
    serializedSizeBenchmark:size  Android.log             2    ss        807419032.000              #
    serializedSizeBenchmark       Android.log             4    ss               75.185           s/op
    serializedSizeBenchmark:size  Android.log             4    ss        425336257.000              #
    serializedSizeBenchmark       Android.log             8    ss               74.245           s/op
    serializedSizeBenchmark:size  Android.log             8    ss        230493250.000              #
    serializedSizeBenchmark       Android.log            16    ss               74.856           s/op
    serializedSizeBenchmark:size  Android.log            16    ss        131610167.000              #
    serializedSizeBenchmark       Android.log            32    ss               74.621           s/op
    serializedSizeBenchmark:size  Android.log            32    ss         81859813.000              #
    serializedSizeBenchmark       Android.log            64    ss               73.908           s/op
    serializedSizeBenchmark:size  Android.log            64    ss         56929260.000              #
    serializedSizeBenchmark       Android.log           128    ss               68.514           s/op
    serializedSizeBenchmark:size  Android.log           128    ss         44464161.000              #
    serializedSizeBenchmark       Android.log           256    ss               75.806           s/op
    serializedSizeBenchmark:size  Android.log           256    ss         38267297.000              #
    serializedSizeBenchmark       Android.log           512    ss               74.453           s/op
    serializedSizeBenchmark:size  Android.log           512    ss         35166105.000              #
    serializedSizeBenchmark       Android.log          1024    ss               75.957           s/op
    serializedSizeBenchmark:size  Android.log          1024    ss         33616854.000              #
    serializedSizeBenchmark       Android.log          2048    ss               80.895           s/op
    serializedSizeBenchmark:size  Android.log          2048    ss         32845922.000              #
    serializedSizeBenchmark       Android.log          4096    ss               83.128           s/op
    serializedSizeBenchmark:size  Android.log          4096    ss         32461044.000              #
    serializedSizeBenchmark       Android.log          8192    ss               83.448           s/op
    serializedSizeBenchmark:size  Android.log          8192    ss         32268597.000              #

     */

    @Benchmark
    public void serializedSizeBenchmark(
            FmIndexIngestState state, Blackhole blackhole, IngestMetrics metrics)
            throws IOException {
        FmIndex fmIndex = new FmIndex(state.text, state.sampleRate, true);

        long bytes = Util.countSerializedSize(FmIndex::write, fmIndex);
        metrics.trackSerializedSize(bytes);
        blackhole.consume(bytes);
    }
}
