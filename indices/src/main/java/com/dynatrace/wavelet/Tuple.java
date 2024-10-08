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
package com.dynatrace.wavelet;

/**
 * Convenience class implementing tuples with generic data types.
 *
 * @param <K> The type of the first element of the tuple
 * @param <V> The type of the second element of the tuple
 */
final class Tuple<K, V> {

    public K key;
    public V value;

    public Tuple(K key, V value) {
        this.key = key;
        this.value = value;
    }
}
