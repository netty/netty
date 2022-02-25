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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class BufferSplitTest extends BufferTestSupport {

    @ParameterizedTest
    @MethodSource("allocators")
    void readSplit(Fixture fixture) {
        readSplit(fixture, 3, 3, 1, 1);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void readSplitWriteOffsetLessThanCapacity(Fixture fixture) {
        readSplit(fixture, 5, 4, 2, 1);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void readSplitOffsetZero(Fixture fixture) {
        readSplit(fixture, 3, 3, 1, 0);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void readSplitOffsetToWriteOffset(Fixture fixture) {
        readSplit(fixture, 3, 3, 1, 2);
    }

    private void readSplit(Fixture fixture, int capacity, int writeBytes, int readBytes, int offset) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(capacity)) {
            writeRandomBytes(buf, writeBytes);
            assertEquals(writeBytes, buf.writerOffset());

            for (int i = 0; i < readBytes; i++) {
                buf.readByte();
            }
            assertEquals(readBytes, buf.readerOffset());

            try (Buffer split = buf.readSplit(offset)) {
                assertEquals(readBytes + offset, split.capacity());
                assertEquals(split.capacity(), split.writerOffset());
                assertEquals(readBytes, split.readerOffset());

                assertEquals(capacity - split.capacity(), buf.capacity());
                assertEquals(writeBytes - split.capacity(), buf.writerOffset());
                assertEquals(0, buf.readerOffset());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void writeSplit(Fixture fixture) {
        writeSplit(fixture, 5, 3, 1, 1);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void writeSplitWriteOffsetLessThanCapacity(Fixture fixture) {
        writeSplit(fixture, 5, 2, 2, 2);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void writeSplitOffsetZero(Fixture fixture) {
        writeSplit(fixture, 3, 3, 1, 0);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void writeSplitOffsetToCapacity(Fixture fixture) {
        writeSplit(fixture, 3, 1, 1, 2);
    }

    private void writeSplit(Fixture fixture, int capacity, int writeBytes, int readBytes, int offset) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(capacity)) {
            writeRandomBytes(buf, writeBytes);
            assertEquals(writeBytes, buf.writerOffset());
            for (int i = 0; i < readBytes; i++) {
                buf.readByte();
            }
            assertEquals(readBytes, buf.readerOffset());

            try (Buffer split = buf.writeSplit(offset)) {
                assertEquals(writeBytes + offset, split.capacity());
                assertEquals(writeBytes, split.writerOffset());
                assertEquals(readBytes, split.readerOffset());

                assertEquals(capacity - split.capacity(), buf.capacity());
                assertEquals(0, buf.writerOffset());
                assertEquals(0, buf.readerOffset());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void splitPostFull(Fixture fixture) {
        splitPostFullOrRead(fixture, false);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void splitPostFullAndRead(Fixture fixture) {
        splitPostFullOrRead(fixture, true);
    }

    private static void splitPostFullOrRead(Fixture fixture, boolean read) {
        final int capacity = 3;
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(capacity)) {
            writeRandomBytes(buf, capacity);
            assertEquals(buf.capacity(), buf.writerOffset());
            if (read) {
                for (int i = 0; i < capacity; i++) {
                    buf.readByte();
                }
            }
            assertEquals(read ? buf.capacity() : 0, buf.readerOffset());

            try (Buffer split = buf.split()) {
                assertEquals(capacity, split.capacity());
                assertEquals(split.capacity(), split.writerOffset());
                assertEquals(read ? split.capacity() : 0, split.readerOffset());

                assertEquals(0, buf.capacity());
                assertEquals(0, buf.writerOffset());
                assertEquals(0, buf.readerOffset());
            }
        }
    }
}
