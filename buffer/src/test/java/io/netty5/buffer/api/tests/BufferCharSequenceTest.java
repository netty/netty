/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty5.buffer.api.tests;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.util.AsciiString;
import org.checkerframework.checker.units.qual.A;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;

import static java.nio.charset.StandardCharsets.US_ASCII;

public class BufferCharSequenceTest extends BufferTestSupport {

    @ParameterizedTest
    @MethodSource("allocators")
    void readCharSequence(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(32)) {
            String data = "Hello World";
            buf.writeBytes(data.getBytes(US_ASCII));
            assertEquals(data.length(), buf.writerOffset());
            assertEquals(0, buf.readerOffset());

            final CharSequence charSequence = buf.readCharSequence(data.length(), US_ASCII);
            Assertions.assertEquals(data, charSequence.toString());
            assertEquals(data.length(), buf.writerOffset());
            assertEquals(data.length(), buf.readerOffset());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void writeCharSequence(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(32)) {
            AsciiString data = new AsciiString("Hello world".getBytes(US_ASCII));
            buf.writeCharSequence(data, US_ASCII);
            assertEquals(data.length(), buf.writerOffset());
            assertEquals(0, buf.readerOffset());

            final byte[] read = readByteArray(buf);
            Assertions.assertEquals(data.toString(), new String(read, US_ASCII));
            assertEquals(data.length(), buf.writerOffset());
            assertEquals(data.length(), buf.readerOffset());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void readAndWriteCharSequence(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(32)) {
            AsciiString data = new AsciiString("Hello world".getBytes(US_ASCII));
            buf.writeCharSequence(data, US_ASCII);
            assertEquals(data.length(), buf.writerOffset());
            assertEquals(0, buf.readerOffset());

            final CharSequence read = buf.readCharSequence(data.length(), US_ASCII);
            Assertions.assertEquals(data.toString(), read.toString());
            assertEquals(data.length(), buf.writerOffset());
            assertEquals(data.length(), buf.readerOffset());
        }
    }
}
