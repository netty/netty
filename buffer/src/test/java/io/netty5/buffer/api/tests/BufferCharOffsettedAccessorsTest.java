/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.buffer.api.tests;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class BufferCharOffsettedAccessorsTest extends BufferTestSupport {
    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfCharMustBoundsCheckOnNegativeOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.getChar(-1));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfCharReadOnlyMustBoundsCheckOnNegativeOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().getChar(-1));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfCharMustNotBoundsCheckWhenReadOffsetAndSizeIsEqualToWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            char value = 0x0102;
            buf.writeChar(value);
            assertEquals(value, buf.getChar(0));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfCharMustReadWithDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            char value = 0x0102;
            buf.writeChar(value);
            buf.setByte(0, (byte) 0x10);
            assertEquals(0x1002, buf.getChar(0));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfCharMustNotBoundsCheckWhenReadOffsetAndSizeIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            char value = 0x0102;
            buf.writeChar(value);
            buf.getChar(1);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfCharMustBoundsCheckWhenReadOffsetAndSizeIsGreaterThanCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.getChar(7));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfCharReadOnlyMustNotBoundsCheckWhenReadOffsetAndSizeIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            char value = 0x0102;
            buf.writeChar(value);
            buf.makeReadOnly().getChar(1);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfCharReadOnlyMustBoundsCheckWhenReadOffsetAndSizeIsGreaterThanCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().getChar(7));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfCharMustNotBoundsCheckWhenReadOffsetIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.getChar(0);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfCharMustBoundsCheckWhenReadOffsetIsGreaterThanCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.getChar(8));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfCharReadOnlyMustNotBoundsCheckWhenReadOffsetIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.makeReadOnly().getChar(0);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfCharReadOnlyMustBoundsCheckWhenReadOffsetIsGreaterThanCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().getChar(8));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedSetOfCharMustBoundsCheckWhenWriteOffsetIsNegative(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(Long.BYTES, buf.capacity());
            char value = 0x0102;
            assertThrows(IndexOutOfBoundsException.class, () -> buf.setChar(-1, value));
            buf.writerOffset(Long.BYTES);
            // Verify contents are unchanged.
            assertEquals(0, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedSetOfCharMustBoundsCheckWhenWriteOffsetAndSizeIsBeyondCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(Long.BYTES, buf.capacity());
            char value = 0x0102;
            assertThrows(IndexOutOfBoundsException.class, () -> buf.setChar(7, value));
            buf.writerOffset(Long.BYTES);
            // Verify contents are unchanged.
            assertEquals(0, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedSetOfCharMustHaveDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            char value = 0x0102;
            buf.setChar(0, value);
            buf.writerOffset(Long.BYTES);
            assertEquals((byte) 0x01, buf.readByte());
            assertEquals((byte) 0x02, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
        }
    }
}
