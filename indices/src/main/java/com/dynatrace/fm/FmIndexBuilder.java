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

/** This class provides a builder for the {@link FmIndex} and includes default parameters. */
public final class FmIndexBuilder {

    private int sampleRate = 32;
    private boolean enableExtraction = true;

    /**
     * Sets the sampling rate for the FM-Index. The lower the value of the sample rate, the higher
     * the memory consumption but the higher the query throughput. The default value is 32. A value
     * of 4 provides speed almost as fast as 1, which is the minimum. Depending on the application,
     * a value around 256 can already be relatively slow.
     *
     * @param sampleRate The length of the interval for which we sample positions in the suffix
     *     array of the FM-Index.
     * @return This builder
     */
    public FmIndexBuilder setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
        return this;
    }

    /**
     * Sets whether we want to enable extraction, i.e., decompression or retrieval of the original
     * indexed text. If the use case for the index is only to count or locate positions, then this
     * can be safely set to false, which will save memory. Default is {@code true}.
     *
     * @param enableExtraction Whether we want to enable transforming the index back to the original
     *     input corpus
     * @return This builder
     */
    public FmIndexBuilder setEnableExtraction(boolean enableExtraction) {
        this.enableExtraction = enableExtraction;
        return this;
    }

    /**
     * Builds the FM-Index over the input corpus of text with the selected parameters, or the
     * default ones otherwise.
     *
     * @param input The text for which we want to build an index to query afterwards
     * @return The built FM-Index
     */
    public FmIndex build(char[] input) {
        return new FmIndex(input, sampleRate, enableExtraction);
    }
}
