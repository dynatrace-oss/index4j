/*
 * Copyright 2020-2024 Dynatrace LLC
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
package com.dynatrace.serialization;

import java.io.IOException;
import java.io.ObjectInput;

/**
 * A deserializer for a given type.
 *
 * @param <T> the type to be deserialized
 */
@FunctionalInterface
public interface SerializationReader<T> {

    /**
     * Deserializes an object by reading from a given {@link ObjectInput}.
     *
     * @param objectInput the data input
     * @return the deserialized object
     * @throws IOException if an I/O error occurs.
     */
    T read(ObjectInput objectInput) throws IOException;
}
