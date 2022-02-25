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

public class BufferByteOffsettedAccessorsTest extends BufferTestSupport {

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfByteMustBoundsCheckOnNegativeOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.getByte(-1));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfByteReadOnlyMustBoundsCheckOnNegativeOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().getByte(-1));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfByteMustNotBoundsCheckWhenReadOffsetAndSizeIsEqualToWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            byte value = 0x01;
            buf.writeByte(value);
            assertEquals(value, buf.getByte(0));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfByteMustReadWithDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            byte value = 0x01;
            buf.writeByte(value);
            buf.setByte(0, (byte) 0x10);
            assertEquals(0x10, buf.getByte(0));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfByteMustBoundsCheckWhenReadOffsetAndSizeIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            byte value = 0x01;
            buf.writeByte(value);
            assertThrows(IndexOutOfBoundsException.class, () -> buf.getByte(-1));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfByteReadOnlyMustBoundsCheckWhenReadOffsetAndSizeIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            byte value = 0x01;
            buf.writeByte(value);
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().getByte(-1));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfByteMustNotBoundsCheckWhenReadOffsetIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.getByte(0);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfByteMustBoundsCheckWhenReadOffsetIsGreaterThanCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.getByte(8));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfByteReadOnlyMustNotBoundsCheckWhenReadOffsetIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.makeReadOnly().getByte(0);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfByteReadOnlyMustBoundsCheckWhenReadOffsetIsGreaterThanCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().getByte(8));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedByteMustBoundsCheckOnNegativeOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.getUnsignedByte(-1));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedByteReadOnlyMustBoundsCheckOnNegativeOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().getUnsignedByte(-1));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedByteMustNotBoundsCheckWhenReadOffsetAndSizeIsEqualToWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            int value = 0x01;
            buf.writeUnsignedByte(value);
            assertEquals(value, buf.getUnsignedByte(0));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedByteMustReadWithDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            int value = 0x01;
            buf.writeUnsignedByte(value);
            buf.setByte(0, (byte) 0x10);
            assertEquals(0x10, buf.getUnsignedByte(0));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedByteMustNotBoundsCheckWhenReadOffsetAndSizeIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            int value = 0x01;
            buf.writeUnsignedByte(value);
            buf.getUnsignedByte(1);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedByteMustBoundsCheckWhenReadOffsetAndSizeIsGreaterThanCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.getUnsignedByte(8));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedByteReadOnlyMustNotBoundsCheckWhenReadOffsetAndSizeIsGreaterThanWriteOffset(
            Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            int value = 0x01;
            buf.writeUnsignedByte(value);
            buf.makeReadOnly().getUnsignedByte(1);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedByteReadOnlyMustBoundsCheckWhenReadOffsetAndSizeIsGreaterThanCapacity(
            Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().getUnsignedByte(8));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedByteMustNotBoundsCheckWhenReadOffsetIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.getUnsignedByte(0);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedByteMustBoundsCheckWhenReadOffsetIsGreaterThanCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.getUnsignedByte(8));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedByteReadOnlyMustNotBoundsCheckWhenReadOffsetIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.makeReadOnly().getUnsignedByte(0);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedByteReadOnlyMustBoundsCheckWhenReadOffsetIsGreaterThanCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().getUnsignedByte(8));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedSetOfByteMustBoundsCheckWhenWriteOffsetIsNegative(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(Long.BYTES, buf.capacity());
            byte value = 0x01;
            assertThrows(IndexOutOfBoundsException.class, () -> buf.setByte(-1, value));
            buf.writerOffset(Long.BYTES);
            // Verify contents are unchanged.
            assertEquals(0, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedSetOfByteMustBoundsCheckWhenWriteOffsetAndSizeIsBeyondCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(Long.BYTES, buf.capacity());
            byte value = 0x01;
            assertThrows(IndexOutOfBoundsException.class, () -> buf.setByte(8, value));
            buf.writerOffset(Long.BYTES);
            // Verify contents are unchanged.
            assertEquals(0, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedSetOfByteMustHaveDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            byte value = 0x01;
            buf.setByte(0, value);
            buf.writerOffset(Long.BYTES);
            assertEquals((byte) 0x01, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
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
    void offsettedSetOfUnsignedByteMustBoundsCheckWhenWriteOffsetIsNegative(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(Long.BYTES, buf.capacity());
            int value = 0x01;
            assertThrows(IndexOutOfBoundsException.class, () -> buf.setUnsignedByte(-1, value));
            buf.writerOffset(Long.BYTES);
            // Verify contents are unchanged.
            assertEquals(0, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedSetOfUnsignedByteMustBoundsCheckWhenWriteOffsetAndSizeIsBeyondCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(Long.BYTES, buf.capacity());
            int value = 0x01;
            assertThrows(IndexOutOfBoundsException.class, () -> buf.setUnsignedByte(8, value));
            buf.writerOffset(Long.BYTES);
            // Verify contents are unchanged.
            assertEquals(0, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedSetOfUnsignedByteMustHaveDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            int value = 0x01;
            buf.setUnsignedByte(0, value);
            buf.writerOffset(Long.BYTES);
            assertEquals((byte) 0x01, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
        }
    }
}
