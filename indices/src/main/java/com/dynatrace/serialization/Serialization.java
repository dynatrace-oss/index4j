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
package com.dynatrace.serialization;

import static java.util.Objects.requireNonNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Locale;

/** This class implements the serialization logic for the library. */
public final class Serialization {

    private Serialization() {
        // Utility class
    }

    private static final String INCOMPATIBLE_SERIAL_VERSION_MSG =
            "Incompatible serial versions! Expected version %d but was %d.";

    /**
     * Checks that two serial versions are the same.
     *
     * @param expectedSerialVersion The expected serial version
     * @param currentSerialVersion The current serial version
     * @throws IOException If they are not equal
     */
    public static void checkSerialVersion(byte expectedSerialVersion, byte currentSerialVersion)
            throws IOException {
        if (expectedSerialVersion != currentSerialVersion) {
            throw new IOException(
                    String.format(
                            Locale.ROOT,
                            INCOMPATIBLE_SERIAL_VERSION_MSG,
                            expectedSerialVersion & 0xFF,
                            currentSerialVersion & 0xFF));
        }
    }

    /**
     * Serializes a given object to a byte array.
     *
     * @param <T> the type to be serialized
     * @param serializationWriter the serialization writer
     * @param data the data to be serialized
     * @return a byte array
     * @throws IOException if an I/O error occurs
     */
    public static <T> byte[] writeToByteArray(
            SerializationWriter<T> serializationWriter, final T data) throws IOException {
        requireNonNull(serializationWriter);
        try (final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            try (final ObjectOutput objectOutputStream =
                    new ObjectOutputStream(byteArrayOutputStream)) {
                serializationWriter.write(data, objectOutputStream);
                objectOutputStream.close();
                return byteArrayOutputStream.toByteArray();
            }
        }
    }

    /**
     * Deserializes an object from a given byte array.
     *
     * @param <T> the type to be deserialized
     * @param byteArray the byte array
     * @param serializationReader the serialization reader
     * @return the deserialized data
     * @throws IOException if an I/O error occurs
     */
    public static <T> T readFromByteArray(
            SerializationReader<T> serializationReader, final byte[] byteArray) throws IOException {
        requireNonNull(serializationReader);
        requireNonNull(byteArray);
        try (final ByteArrayInputStream byteArrayInputStream =
                new ByteArrayInputStream(byteArray)) {
            try (final ObjectInput objectInputStream =
                    new ObjectInputStream(byteArrayInputStream)) {
                return serializationReader.read(objectInputStream);
            }
        }
    }
}
