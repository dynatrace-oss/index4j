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
        value = 5,
        jvmArgs = {"-Xms4096m", "-Xmx32g", "-Djdk.attach.allowAttachSelf=true"})
@Warmup(iterations = 5, batchSize = 1)
@Measurement(iterations = 10, batchSize = 1)
@Timeout(time = 3, timeUnit = TimeUnit.HOURS)
public class RrrVectorThroughputBenchmark {

    /*

    Random distribution
    Benchmark              (bitLength)  (sampleRate)   Mode  Cnt        Score        Error  Units
    RRRVectorRankQueries     10000000            16  thrpt   50  7,158,903.332 ± 369682.230  ops/s
    RRRVectorRankQueries     10000000            32  thrpt   50  6,118,638.871 ± 226343.186  ops/s
    RRRVectorRankQueries     10000000            64  thrpt   50  4,490,328.019 ± 139650.638  ops/s
    RRRVectorRankQueries     10000000           256  thrpt   50  1,688,929.851 ± 58220.105  ops/s

    1% sparseness distribution
    Benchmark              (bitLength)  (sampleRate)   Mode  Cnt        Score        Error  Units
    RRRVectorRankQueries     10000000            16  thrpt   50  8,661,903.949 ± 291315.418  ops/s
    RRRVectorRankQueries     10000000            32  thrpt   50  7,164,674.624 ±  95533.421  ops/s
    RRRVectorRankQueries     10000000            64  thrpt   50  5,201,654.881 ± 105306.641  ops/s
    RRRVectorRankQueries     10000000           256  thrpt   50  2,018,335.391 ±  28715.900  ops/s

    FYI: using the broadword approach of:
       Vigna, Sebastiano. "Broadword implementation of rank/select queries." In International Workshop on
       Experimental and Efficient Algorithms, pp. 154-168. Berlin, Heidelberg: Springer Berlin Heidelberg, 2008.
    is up to ~6x faster at the expense of ~2x memory:

    Benchmark             (bitLength)     Mode    Cnt  Score            Error       Units
    Rank9VectorRankQueries   10000000     thrpt   50   31,761,175.641 ± 344,422.032  ops/s

    RRR bytes in 10,000,000 uniform bit sequence  : 48644136
    Rank9 bytes in 10,000,000 uniform bit sequence: 90000152

    */

    @Benchmark
    public void rrrVectorRankQueries(
            RrrVectorThroughputState throughputState, Blackhole blackhole) {
        blackhole.consume(throughputState.rankSupport.rankOnes(throughputState.getNextQuery()));
    }
}
