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

import static java.util.Objects.requireNonNull;

import com.dynatrace.serialization.SerializationWriter;
import com.google.common.io.CountingOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

public class Util {

    public static <T> long countSerializedSize(
            SerializationWriter<T> serializationWriter, final T data) throws IOException {

        requireNonNull(serializationWriter);
        // An output stream that does not allocate any memory
        OutputStream emptyByteArrayOutputStream =
                new OutputStream() {
                    @Override
                    public void write(int b) {}

                    @Override
                    public void write(byte[] b) {}

                    @Override
                    public void write(byte[] b, int off, int len) {}
                };

        CountingOutputStream counter = new CountingOutputStream(emptyByteArrayOutputStream);
        ObjectOutputStream oos = new ObjectOutputStream(counter);

        serializationWriter.write(data, oos);
        oos.close();
        return counter.getCount();
    }
}
