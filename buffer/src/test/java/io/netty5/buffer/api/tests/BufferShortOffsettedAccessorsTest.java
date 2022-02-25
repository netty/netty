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

public class BufferShortOffsettedAccessorsTest extends BufferTestSupport {
    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfShortMustBoundsCheckOnNegativeOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.getShort(-1));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfShortReadOnlyMustBoundsCheckOnNegativeOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().getShort(-1));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfShortMustNotBoundsCheckWhenReadOffsetAndSizeIsEqualToWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            short value = 0x0102;
            buf.writeShort(value);
            assertEquals(value, buf.getShort(0));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfShortMustReadWithDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            short value = 0x0102;
            buf.writeShort(value);
            buf.setByte(0, (byte) 0x10);
            assertEquals(0x1002, buf.getShort(0));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfShortMustNotBoundsCheckWhenReadOffsetAndSizeIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            short value = 0x0102;
            buf.writeShort(value);
            buf.getShort(1);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfShortMustBoundsCheckWhenReadOffsetAndSizeIsGreaterThanCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.getShort(7));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfShortReadOnlyMustNotBoundsCheckWhenReadOffsetAndSizeIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            short value = 0x0102;
            buf.writeShort(value);
            buf.makeReadOnly().getShort(1);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfShortReadOnlyMustBoundsCheckWhenReadOffsetAndSizeIsGreaterThanCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().getShort(7));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfShortMustNotBoundsCheckWhenReadOffsetIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.getShort(0);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfShortMustBoundsCheckWhenReadOffsetIsGreaterThanCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.getShort(7));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfShortReadOnlyMustNotBoundsCheckWhenReadOffsetIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.makeReadOnly().getShort(0);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfShortReadOnlyMustBoundsCheckWhenReadOffsetIsGreaterThanCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().getShort(7));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedShortMustBoundsCheckOnNegativeOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.getUnsignedShort(-1));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedShortReadOnlyMustBoundsCheckOnNegativeOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().getUnsignedShort(-1));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedShortMustNotBoundsCheckWhenReadOffsetAndSizeIsEqualToWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            int value = 0x0102;
            buf.writeUnsignedShort(value);
            assertEquals(value, buf.getUnsignedShort(0));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedShortMustReadWithDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            int value = 0x0102;
            buf.writeUnsignedShort(value);
            buf.setByte(0, (byte) 0x10);
            assertEquals(0x1002, buf.getUnsignedShort(0));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedShortMustNotBoundsCheckWhenReadOffsetAndSizeIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            int value = 0x0102;
            buf.writeUnsignedShort(value);
            buf.getUnsignedShort(1);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedShortMustBoundsCheckWhenReadOffsetAndSizeIsGreaterThanCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.getUnsignedShort(7));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedShortReadOnlyMustNotBoundsCheckWhenReadOffsetAndSizeIsGreaterThanWriteOffset(
            Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            int value = 0x0102;
            buf.writeUnsignedShort(value);
            buf.makeReadOnly().getUnsignedShort(1);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedShortReadOnlyMustBoundsCheckWhenReadOffsetAndSizeIsGreaterThanCapacity(
            Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().getUnsignedShort(7));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedShortMustNotBoundsCheckWhenReadOffsetIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.getUnsignedShort(0);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedShortMustBoundsCheckWhenReadOffsetIsGreaterThanCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.getUnsignedShort(7));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedShortReadOnlyMustNotBoundsCheckWhenReadOffsetIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.makeReadOnly().getUnsignedShort(0);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedShortReadOnlyMustBoundsCheckWhenReadOffsetIsGreaterThanCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().getUnsignedShort(7));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedSetOfShortMustBoundsCheckWhenWriteOffsetIsNegative(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(Long.BYTES, buf.capacity());
            short value = 0x0102;
            assertThrows(IndexOutOfBoundsException.class, () -> buf.setShort(-1, value));
            buf.writerOffset(Long.BYTES);
            // Verify contents are unchanged.
            assertEquals(0, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedSetOfShortMustBoundsCheckWhenWriteOffsetAndSizeIsBeyondCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(Long.BYTES, buf.capacity());
            short value = 0x0102;
            assertThrows(IndexOutOfBoundsException.class, () -> buf.setShort(7, value));
            buf.writerOffset(Long.BYTES);
            // Verify contents are unchanged.
            assertEquals(0, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedSetOfShortMustHaveDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            short value = 0x0102;
            buf.setShort(0, value);
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

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedSetOfUnsignedShortMustBoundsCheckWhenWriteOffsetIsNegative(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(Long.BYTES, buf.capacity());
            int value = 0x0102;
            assertThrows(IndexOutOfBoundsException.class, () -> buf.setUnsignedShort(-1, value));
            buf.writerOffset(Long.BYTES);
            // Verify contents are unchanged.
            assertEquals(0, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedSetOfUnsignedShortMustBoundsCheckWhenWriteOffsetAndSizeIsBeyondCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(Long.BYTES, buf.capacity());
            int value = 0x0102;
            assertThrows(IndexOutOfBoundsException.class, () -> buf.setUnsignedShort(7, value));
            buf.writerOffset(Long.BYTES);
            // Verify contents are unchanged.
            assertEquals(0, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedSetOfUnsignedShortMustHaveDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            int value = 0x0102;
            buf.setUnsignedShort(0, value);
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
