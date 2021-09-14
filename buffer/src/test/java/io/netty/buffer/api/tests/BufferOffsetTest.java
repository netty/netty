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
package io.netty.buffer.api.tests;

import io.netty.buffer.api.Buffer;
import io.netty.buffer.api.BufferAllocator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class BufferOffsetTest extends BufferTestSupport {

    @ParameterizedTest
    @MethodSource("allocators")
    void accumulateReaderOffset(Fixture fixture) {
        accumulateReaderOffset(fixture, 32, 16, 8, 8);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void accumulateReaderOffsetNegative(Fixture fixture) {
        accumulateReaderOffset(fixture, 16, 8, 4, -2);
    }

    private void accumulateReaderOffset(Fixture fixture, int capacity, int writeBytes, int readBytes, int offset) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            try (Buffer buf = allocator.allocate(capacity)) {
                writeRandomBytes(buf, writeBytes);

                for (int i = 0; i < readBytes; i++) {
                    buf.readByte();
                }

                buf.accumulateReaderOffset(offset);
                assertEquals(readBytes + offset, buf.readerOffset());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void accumulateWriterOffset(Fixture fixture) {
        accumulateWriterOffset(fixture, 32, 16, 8);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void accumulateWriterOffsetNegative(Fixture fixture) {
        accumulateWriterOffset(fixture, 16, 8, -2);
    }

    private void accumulateWriterOffset(Fixture fixture, int capacity, int writeBytes, int offset) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            try (Buffer buf = allocator.allocate(capacity)) {
                writeRandomBytes(buf, writeBytes);

                buf.accumulateWriterOffset(offset);
                assertEquals(writeBytes + offset, buf.writerOffset());
            }
        }
    }
}
