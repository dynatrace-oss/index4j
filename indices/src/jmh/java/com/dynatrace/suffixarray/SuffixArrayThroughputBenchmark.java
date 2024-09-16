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

import com.dynatrace.metrics.ThroughputMetrics;
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

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(
        value = 3,
        jvmArgs = {"-Xms4096m", "-Xmx32g", "-Djdk.attach.allowAttachSelf=true"})
@Warmup(iterations = 3, batchSize = 1)
@Measurement(iterations = 5, batchSize = 1)
@Timeout(time = 3, timeUnit = TimeUnit.HOURS)
public class SuffixArrayThroughputBenchmark {

    /*

    (data) = Android.log (logpai repository) (~184 MB, ~1100 different characters)
    Benchmark                               (data)  (differentQueries)  (maxExtractionLength)  (maxMatches)  (maxQueryLength)  (minQueryLength)   Mode  Cnt             Score       Error  Units
    countBenchmark                     Android.log               20000                     64          1000                32                 8  thrpt   15        137793.019 ± 11453.560  ops/s
    countBenchmark:matches             Android.log               20000                     64          1000                32                 8  thrpt   15  181237415796.000                  #
    countBenchmark:queries             Android.log               20000                     64          1000                32                 8  thrpt   15      20669533.000                  #
    countNonIndexedBenchmark           Android.log               20000                     64          1000                32                 8  thrpt   15        264788.702 ± 26007.445  ops/s
    countNonIndexedBenchmark:matches   Android.log               20000                     64          1000                32                 8  thrpt   15               ≈ 0                  #
    countNonIndexedBenchmark:queries   Android.log               20000                     64          1000                32                 8  thrpt   15      39719453.000                  #
    locateAndExtractBenchmark          Android.log               20000                     64          1000                32                 8  thrpt   15         10662.111 ±   511.402  ops/s
    locateAndExtractBenchmark:matches  Android.log               20000                     64          1000                32                 8  thrpt   15     926819024.000                  #
    locateAndExtractBenchmark:queries  Android.log               20000                     64          1000                32                 8  thrpt   15       1599374.000                  #
    locateBenchmark                    Android.log               20000                     64          1000                32                 8  thrpt   15        132399.421 ±  4382.363  ops/s
    locateBenchmark:matches            Android.log               20000                     64          1000                32                 8  thrpt   15   11502367964.000                  #
    locateBenchmark:queries            Android.log               20000                     64          1000                32                 8  thrpt   15      19860522.000                  #

    */

    @Benchmark
    public void countBenchmark(
            SuffixArrayThroughputState state, Blackhole blackhole, ThroughputMetrics metrics) {
        String pattern = state.getNextQuery();
        int matches = state.suffixArray.count(pattern);
        metrics.trackMatches(matches);
        metrics.trackQueries();
        blackhole.consume(matches);
    }

    @Benchmark
    public void countNonIndexedBenchmark(
            SuffixArrayThroughputState state, Blackhole blackhole, ThroughputMetrics metrics) {
        String pattern = state.getNextNonIndexedQuery();
        int matches = state.suffixArray.count(pattern);
        metrics.trackMatches(matches);
        metrics.trackQueries();
        blackhole.consume(matches);
    }

    @Benchmark
    public void locateBenchmark(
            SuffixArrayThroughputState state, Blackhole blackhole, ThroughputMetrics metrics) {
        String pattern = state.getNextQuery();
        int matches = state.suffixArray.locate(pattern, state.locations);
        metrics.trackMatches(matches);
        metrics.trackQueries();
        blackhole.consume(matches);
    }

    @Benchmark
    public void locateAndExtractBenchmark(
            SuffixArrayThroughputState state, Blackhole blackhole, ThroughputMetrics metrics) {
        String pattern = state.getNextQuery();
        int matches = state.suffixArray.locate(pattern, state.locations);
        for (int i = 0; i < Math.min(matches, state.maxMatches); i++) {
            int k = 0;
            for (int j = state.locations[i];
                    j
                            < Math.min(
                                    state.input.length,
                                    state.locations[i] + state.maxExtractionLength);
                    j++) {

                state.destination[k++] = state.input[j];
            }
            blackhole.consume(state.destination);
            metrics.trackMatches(1);
        }
        metrics.trackQueries();
        blackhole.consume(matches);
    }
}
