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
package com.dynatrace.metrics;

import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
@AuxCounters(value = AuxCounters.Type.EVENTS)
public class ThroughputMetrics {

    private long matches;
    private long queries;

    @Setup(Level.Iteration)
    public void clean() {
        matches = 0;
        queries = 0;
    }

    public void trackMatches(int matches) {
        this.matches += matches;
    }

    public void trackQueries() {
        ++this.queries;
    }

    public long matches() {
        return matches;
    }

    public long queries() {
        return queries;
    }
}
