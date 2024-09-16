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
public class FmIndexThroughputBenchmark {

    /*

    (data) = Android.log (logpai repository) (~184 MB, ~1100 different characters)
    Benchmark                      (data)  (differentQueries)  (maxExtractionLength)  (maxMatches)  (maxQueryLength)  (minQueryLength)  (sampleRate)   Mode  Cnt            Score       Error  Units
    locateBenchmark          Android.log               20000                     64             1                32                 8             1  thrpt   15        57443.665 ±  1945.283  ops/s
    locateBenchmark:matches  Android.log               20000                     64             1                32                 8             1  thrpt   15      8616376.000                  #
    locateBenchmark:queries  Android.log               20000                     64             1                32                 8             1  thrpt   15      8616801.000                  #
    locateBenchmark          Android.log               20000                     64             1                32                 8             2  thrpt   15        52601.402 ±  3040.314  ops/s
    locateBenchmark:matches  Android.log               20000                     64             1                32                 8             2  thrpt   15      7890438.000                  #
    locateBenchmark:queries  Android.log               20000                     64             1                32                 8             2  thrpt   15      7890438.000                  #
    locateBenchmark          Android.log               20000                     64             1                32                 8             4  thrpt   15        51338.112 ±  1696.775  ops/s
    locateBenchmark:matches  Android.log               20000                     64             1                32                 8             4  thrpt   15      7700950.000                  #
    locateBenchmark:queries  Android.log               20000                     64             1                32                 8             4  thrpt   15      7700950.000                  #
    locateBenchmark          Android.log               20000                     64             1                32                 8             8  thrpt   15        44362.852 ±  2271.924  ops/s
    locateBenchmark:matches  Android.log               20000                     64             1                32                 8             8  thrpt   15      6654640.000                  #
    locateBenchmark:queries  Android.log               20000                     64             1                32                 8             8  thrpt   15      6654640.000                  #
    locateBenchmark          Android.log               20000                     64             1                32                 8            16  thrpt   15        32223.541 ±  3444.519  ops/s
    locateBenchmark:matches  Android.log               20000                     64             1                32                 8            16  thrpt   15      4833693.000                  #
    locateBenchmark:queries  Android.log               20000                     64             1                32                 8            16  thrpt   15      4833693.000                  #
    locateBenchmark          Android.log               20000                     64             1                32                 8            32  thrpt   15        26031.428 ±  1204.622  ops/s
    locateBenchmark:matches  Android.log               20000                     64             1                32                 8            32  thrpt   15      3904841.000                  #
    locateBenchmark:queries  Android.log               20000                     64             1                32                 8            32  thrpt   15      3904841.000                  #
    locateBenchmark          Android.log               20000                     64             1                32                 8            64  thrpt   15        13749.283 ±   532.575  ops/s
    locateBenchmark:matches  Android.log               20000                     64             1                32                 8            64  thrpt   15      2062456.000                  #
    locateBenchmark:queries  Android.log               20000                     64             1                32                 8            64  thrpt   15      2062456.000                  #
    locateBenchmark          Android.log               20000                     64             1                32                 8           128  thrpt   15         5864.187 ±   467.737  ops/s
    locateBenchmark:matches  Android.log               20000                     64             1                32                 8           128  thrpt   15       879691.000                  #
    locateBenchmark:queries  Android.log               20000                     64             1                32                 8           128  thrpt   15       879691.000                  #
    locateBenchmark          Android.log               20000                     64             1                32                 8           256  thrpt   15         2242.963 ±    53.965  ops/s
    locateBenchmark:matches  Android.log               20000                     64             1                32                 8           256  thrpt   15       336463.000                  #
    locateBenchmark:queries  Android.log               20000                     64             1                32                 8           256  thrpt   15       336463.000                  #
    locateBenchmark          Android.log               20000                     64            10                32                 8             1  thrpt   15        51050.241 ± 5172.646  ops/s

    Benchmark                     (data)  (differentQueries)  (maxExtractionLength)  (maxMatches)  (maxQueryLength)  (minQueryLength)  (sampleRate)   Mode  Cnt            Score       Error  Units
    locateBenchmark:matches  Android.log               20000                     64            10                32                 8             1  thrpt   15  66046240.000                 #
    locateBenchmark:queries  Android.log               20000                     64            10                32                 8             1  thrpt   15   7657921.000                 #
    locateBenchmark          Android.log               20000                     64            10                32                 8             2  thrpt   15     45082.744 ± 2001.523  ops/s
    locateBenchmark:matches  Android.log               20000                     64            10                32                 8             2  thrpt   15  58327260.000                 #
    locateBenchmark:queries  Android.log               20000                     64            10                32                 8             2  thrpt   15   6762602.000                 #
    locateBenchmark          Android.log               20000                     64            10                32                 8             4  thrpt   15     33420.877 ± 3833.099  ops/s
    locateBenchmark:matches  Android.log               20000                     64            10                32                 8             4  thrpt   15  43240647.000                 #
    locateBenchmark:queries  Android.log               20000                     64            10                32                 8             4  thrpt   15   5013369.000                 #
    locateBenchmark          Android.log               20000                     64            10                32                 8             8  thrpt   15     23666.442 ± 1537.343  ops/s
    locateBenchmark:matches  Android.log               20000                     64            10                32                 8             8  thrpt   15  30619355.000                 #
    locateBenchmark:queries  Android.log               20000                     64            10                32                 8             8  thrpt   15   3550082.000                 #
    locateBenchmark          Android.log               20000                     64            10                32                 8            16  thrpt   15     14778.522 ±  876.492  ops/s
    locateBenchmark:matches  Android.log               20000                     64            10                32                 8            16  thrpt   15  19119623.000                 #
    locateBenchmark:queries  Android.log               20000                     64            10                32                 8            16  thrpt   15   2216851.000                 #
    locateBenchmark          Android.log               20000                     64            10                32                 8            32  thrpt   15      7222.887 ±  229.592  ops/s
    locateBenchmark:matches  Android.log               20000                     64            10                32                 8            32  thrpt   15   9344630.000                 #
    locateBenchmark:queries  Android.log               20000                     64            10                32                 8            32  thrpt   15   1083473.000                 #
    locateBenchmark          Android.log               20000                     64            10                32                 8            64  thrpt   15      2925.678 ±   41.863  ops/s
    locateBenchmark:matches  Android.log               20000                     64            10                32                 8            64  thrpt   15   3785856.000                 #
    locateBenchmark:queries  Android.log               20000                     64            10                32                 8            64  thrpt   15    438874.000                 #
    locateBenchmark          Android.log               20000                     64            10                32                 8           128  thrpt   15       954.472 ±   47.485  ops/s
    locateBenchmark:matches  Android.log               20000                     64            10                32                 8           128  thrpt   15   1235243.000                 #
    locateBenchmark:queries  Android.log               20000                     64            10                32                 8           128  thrpt   15    143184.000                 #
    locateBenchmark          Android.log               20000                     64            10                32                 8           256  thrpt   15       286.423 ±   25.775  ops/s
    locateBenchmark:matches  Android.log               20000                     64            10                32                 8           256  thrpt   15    369718.000                 #
    locateBenchmark:queries  Android.log               20000                     64            10                32                 8           256  thrpt   15     42988.000                 #

    Benchmark                     (data)  (differentQueries)  (maxExtractionLength)  (maxMatches)  (maxQueryLength)  (minQueryLength)  (sampleRate)   Mode  Cnt            Score       Error  Units
    locateBenchmark          Android.log               20000                     64           100                32                 8             1  thrpt   15      45059.255 ± 1613.843  ops/s
    locateBenchmark:matches  Android.log               20000                     64           100                32                 8             1  thrpt   15  503315671.000                 #
    locateBenchmark:queries  Android.log               20000                     64           100                32                 8             1  thrpt   15    6759093.000                 #
    locateBenchmark          Android.log               20000                     64           100                32                 8             2  thrpt   15      26351.090 ±  555.081  ops/s
    locateBenchmark:matches  Android.log               20000                     64           100                32                 8             2  thrpt   15  294363063.000                 #
    locateBenchmark:queries  Android.log               20000                     64           100                32                 8             2  thrpt   15    3952786.000                 #
    locateBenchmark          Android.log               20000                     64           100                32                 8             4  thrpt   15      13835.212 ±  618.698  ops/s
    locateBenchmark:matches  Android.log               20000                     64           100                32                 8             4  thrpt   15  154544879.000                 #
    locateBenchmark:queries  Android.log               20000                     64           100                32                 8             4  thrpt   15    2075348.000                 #
    locateBenchmark          Android.log               20000                     64           100                32                 8             8  thrpt   15       6649.783 ±  344.263  ops/s
    locateBenchmark:matches  Android.log               20000                     64           100                32                 8             8  thrpt   15   74310189.000                 #
    locateBenchmark:queries  Android.log               20000                     64           100                32                 8             8  thrpt   15     997506.000                 #
    locateBenchmark          Android.log               20000                     64           100                32                 8            16  thrpt   15       2917.506 ±  116.410  ops/s
    locateBenchmark:matches  Android.log               20000                     64           100                32                 8            16  thrpt   15   32620261.000                 #
    locateBenchmark:queries  Android.log               20000                     64           100                32                 8            16  thrpt   15     437645.000                 #
    locateBenchmark          Android.log               20000                     64           100                32                 8            32  thrpt   15       1119.843 ±   42.726  ops/s
    locateBenchmark:matches  Android.log               20000                     64           100                32                 8            32  thrpt   15   12521844.000                 #
    locateBenchmark:queries  Android.log               20000                     64           100                32                 8            32  thrpt   15     167991.000                 #
    locateBenchmark          Android.log               20000                     64           100                32                 8            64  thrpt   15        396.158 ±   13.233  ops/s
    locateBenchmark:matches  Android.log               20000                     64           100                32                 8            64  thrpt   15    4405730.000                 #
    locateBenchmark:queries  Android.log               20000                     64           100                32                 8            64  thrpt   15      59438.000                 #
    locateBenchmark          Android.log               20000                     64           100                32                 8           128  thrpt   15        123.140 ±    4.675  ops/s
    locateBenchmark:matches  Android.log               20000                     64           100                32                 8           128  thrpt   15    1379613.000                 #
    locateBenchmark:queries  Android.log               20000                     64           100                32                 8           128  thrpt   15      18481.000                 #
    locateBenchmark          Android.log               20000                     64           100                32                 8           256  thrpt   15         39.493 ±    1.404  ops/s
    locateBenchmark:matches  Android.log               20000                     64           100                32                 8           256  thrpt   15     448511.000                 #
    locateBenchmark:queries  Android.log               20000                     64           100                32                 8           256  thrpt   15       5933.000                 #

    Benchmark                     (data)  (differentQueries)  (maxExtractionLength)  (maxMatches)  (maxQueryLength)  (minQueryLength)  (sampleRate)   Mode  Cnt            Score       Error  Units
    locateBenchmark          Android.log               20000                     64          1000                32                 8             1  thrpt   15        19131.850 ±   271.529  ops/s
    locateBenchmark:matches  Android.log               20000                     64          1000                32                 8             1  thrpt   15   1662399662.000                  #
    locateBenchmark:queries  Android.log               20000                     64          1000                32                 8             1  thrpt   15      2869872.000                  #
    locateBenchmark          Android.log               20000                     64          1000                32                 8             2  thrpt   15         6659.694 ±    48.909  ops/s
    locateBenchmark:matches  Android.log               20000                     64          1000                32                 8             2  thrpt   15    579581551.000                  #
    locateBenchmark:queries  Android.log               20000                     64          1000                32                 8             2  thrpt   15       998991.000                  #
    locateBenchmark          Android.log               20000                     64          1000                32                 8             4  thrpt   15         2874.059 ±    72.174  ops/s
    locateBenchmark:matches  Android.log               20000                     64          1000                32                 8             4  thrpt   15    250438090.000                  #
    locateBenchmark:queries  Android.log               20000                     64          1000                32                 8             4  thrpt   15       431139.000                  #
    locateBenchmark          Android.log               20000                     64          1000                32                 8             8  thrpt   15         1214.470 ±    25.408  ops/s
    locateBenchmark:matches  Android.log               20000                     64          1000                32                 8             8  thrpt   15    105813004.000                  #
    locateBenchmark:queries  Android.log               20000                     64          1000                32                 8             8  thrpt   15       182195.000                  #
    locateBenchmark          Android.log               20000                     64          1000                32                 8            16  thrpt   15          462.193 ±     4.688  ops/s
    locateBenchmark:matches  Android.log               20000                     64          1000                32                 8            16  thrpt   15     40806937.000                  #
    locateBenchmark:queries  Android.log               20000                     64          1000                32                 8            16  thrpt   15        69343.000                  #
    locateBenchmark          Android.log               20000                     64          1000                32                 8            32  thrpt   15          172.831 ±     2.984  ops/s
    locateBenchmark:matches  Android.log               20000                     64          1000                32                 8            32  thrpt   15     15252412.000                  #
    locateBenchmark:queries  Android.log               20000                     64          1000                32                 8            32  thrpt   15        25940.000                  #
    locateBenchmark          Android.log               20000                     64          1000                32                 8            64  thrpt   15           54.955 ±     0.965  ops/s
    locateBenchmark:matches  Android.log               20000                     64          1000                32                 8            64  thrpt   15      5183278.000                  #
    locateBenchmark:queries  Android.log               20000                     64          1000                32                 8            64  thrpt   15         8254.000                  #
    locateBenchmark          Android.log               20000                     64          1000                32                 8           128  thrpt   15           16.246 ±     0.181  ops/s
    locateBenchmark:matches  Android.log               20000                     64          1000                32                 8           128  thrpt   15      1615425.000                  #
    locateBenchmark:queries  Android.log               20000                     64          1000                32                 8           128  thrpt   15         2448.000                  #
    locateBenchmark          Android.log               20000                     64          1000                32                 8           256  thrpt   15            4.746 ±     0.044  ops/s
    locateBenchmark:matches  Android.log               20000                     64          1000                32                 8           256  thrpt   15       471071.000                  #
    locateBenchmark:queries  Android.log               20000                     64          1000                32                 8           256  thrpt   15          721.000                  #

    Benchmark                     (data)  (differentQueries)  (maxExtractionLength)  (maxMatches)  (maxQueryLength)  (minQueryLength)  (sampleRate)   Mode  Cnt            Score       Error  Units
    extractBenchmark         Android.log               20000                     64             1                32                 8             1  thrpt   15        43004.338 ±  4181.193  ops/s
    extractBenchmark:matches Android.log               20000                     64             1                32                 8             1  thrpt   15              ≈ 0                  #
    extractBenchmark:queries Android.log               20000                     64             1                32                 8             1  thrpt   15      6450854.000                  #
    extractBenchmark         Android.log               20000                     64             1                32                 8             2  thrpt   15        43707.940 ±  1714.259  ops/s
    extractBenchmark:matches Android.log               20000                     64             1                32                 8             2  thrpt   15              ≈ 0                  #
    extractBenchmark:queries Android.log               20000                     64             1                32                 8             2  thrpt   15      6556384.000                  #
    extractBenchmark         Android.log               20000                     64             1                32                 8             4  thrpt   15        41857.283 ±  1606.903  ops/s
    extractBenchmark:matches Android.log               20000                     64             1                32                 8             4  thrpt   15              ≈ 0                  #
    extractBenchmark:queries Android.log               20000                     64             1                32                 8             4  thrpt   15      6278789.000                  #
    extractBenchmark         Android.log               20000                     64             1                32                 8             8  thrpt   15        38385.295 ±   597.421  ops/s
    extractBenchmark:matches Android.log               20000                     64             1                32                 8             8  thrpt   15              ≈ 0                  #
    extractBenchmark:queries Android.log               20000                     64             1                32                 8             8  thrpt   15      5757967.000                  #
    extractBenchmark         Android.log               20000                     64             1                32                 8            16  thrpt   15        26995.239 ±  2079.389  ops/s
    extractBenchmark:matches Android.log               20000                     64             1                32                 8            16  thrpt   15              ≈ 0                  #
    extractBenchmark:queries Android.log               20000                     64             1                32                 8            16  thrpt   15      4049420.000                  #
    extractBenchmark         Android.log               20000                     64             1                32                 8            32  thrpt   15        19544.639 ±  1720.193  ops/s
    extractBenchmark:matches Android.log               20000                     64             1                32                 8            32  thrpt   15              ≈ 0                  #
    extractBenchmark:queries Android.log               20000                     64             1                32                 8            32  thrpt   15      2931790.000                  #
    extractBenchmark         Android.log               20000                     64             1                32                 8            64  thrpt   15        12450.568 ±   849.899  ops/s
    extractBenchmark:matches Android.log               20000                     64             1                32                 8            64  thrpt   15              ≈ 0                  #
    extractBenchmark:queries Android.log               20000                     64             1                32                 8            64  thrpt   15      1867653.000                  #
    extractBenchmark         Android.log               20000                     64             1                32                 8           128  thrpt   15         7204.733 ±   185.763  ops/s
    extractBenchmark:matches Android.log               20000                     64             1                32                 8           128  thrpt   15              ≈ 0                  #
    extractBenchmark:queries Android.log               20000                     64             1                32                 8           128  thrpt   15      1080754.000                  #
    extractBenchmark         Android.log               20000                     64             1                32                 8           256  thrpt   15         2482.106 ±   171.496  ops/s
    extractBenchmark:matches Android.log               20000                     64             1                32                 8           256  thrpt   15              ≈ 0                  #
    extractBenchmark:queries Android.log               20000                     64             1                32                 8           256  thrpt   15       372334.000                  #


     */

    @Benchmark
    public void countBenchmark(
            FmIndexThroughputState state, Blackhole blackhole, ThroughputMetrics metrics) {
        char[] pattern = state.getNextQuery();
        int matches = state.fmIndex.count(pattern);
        metrics.trackMatches(matches);
        metrics.trackQueries();
        blackhole.consume(matches);
    }

    @Benchmark
    public void countNonIndexedBenchmark(
            FmIndexThroughputState state, Blackhole blackhole, ThroughputMetrics metrics) {
        char[] pattern = state.getNextNonIndexedQuery();
        int matches = state.fmIndex.count(pattern);
        metrics.trackMatches(matches);
        metrics.trackQueries();
        blackhole.consume(matches);
    }

    @Benchmark
    public void locateBenchmark(
            FmIndexThroughputState state, Blackhole blackhole, ThroughputMetrics metrics) {
        char[] pattern = state.getNextQuery();
        int matches =
                state.fmIndex.locate(pattern, 0, pattern.length, state.locations, state.maxMatches);
        metrics.trackMatches(matches);
        metrics.trackQueries();
        blackhole.consume(matches);
    }

    @Benchmark
    public void extractBenchmark(
            FmIndexThroughputState state, Blackhole blackhole, ThroughputMetrics metrics) {
        int start = state.getNextExtraction();
        blackhole.consume(
                state.fmIndex.extract(start, start + state.maxQueryLength, state.destination, 0));
        metrics.trackQueries();
    }

    @Benchmark
    public void locateAndExtractBenchmark(
            FmIndexThroughputState state, Blackhole blackhole, ThroughputMetrics metrics) {
        char[] pattern = state.getNextQuery();
        int matches =
                state.fmIndex.locate(pattern, 0, pattern.length, state.locations, state.maxMatches);
        for (int i = 0; i < matches; i++) {
            blackhole.consume(
                    state.fmIndex.extract(
                            state.locations[i],
                            Math.min(
                                    state.fmIndex.getInputLength(),
                                    state.locations[i] + state.maxExtractionLength),
                            state.destination,
                            0));
            metrics.trackMatches(1);
        }
        metrics.trackQueries();
    }
}
