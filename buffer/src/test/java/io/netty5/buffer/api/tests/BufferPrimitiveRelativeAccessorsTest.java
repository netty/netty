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

public class BufferPrimitiveRelativeAccessorsTest extends BufferTestSupport {

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfByteMustNotBoundsCheckWhenReadOffsetAndSizeIsEqualToWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            byte value = 0x01;
            buf.writeByte(value);
            assertEquals(1, buf.readableBytes());
            assertEquals(7, buf.writableBytes());
            assertEquals(value, buf.readByte());
            assertEquals(0, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfByteMustReadWithDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            byte value = 0x01;
            buf.writeByte(value);
            buf.setByte(0, (byte) 0x10);
            assertEquals(1, buf.readableBytes());
            assertEquals(7, buf.writableBytes());
            assertEquals(0x10, buf.readByte());
            assertEquals(0, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfByteMustBoundsCheckWhenReadOffsetAndSizeIsBeyondWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            byte value = 0x01;
            buf.writeByte(value);
            buf.readerOffset(1);
            assertEquals(0, buf.readableBytes());
            assertEquals(7, buf.writableBytes());
            assertThrows(IndexOutOfBoundsException.class, buf::readByte);
            assertEquals(0, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfUnsignedByteMustNotBoundsCheckWhenReadOffsetAndSizeIsEqualToWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            int value = 0x01;
            buf.writeUnsignedByte(value);
            assertEquals(1, buf.readableBytes());
            assertEquals(7, buf.writableBytes());
            assertEquals(value, buf.readUnsignedByte());
            assertEquals(0, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfUnsignedByteMustReadWithDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            int value = 0x01;
            buf.writeUnsignedByte(value);
            buf.setByte(0, (byte) 0x10);
            assertEquals(1, buf.readableBytes());
            assertEquals(7, buf.writableBytes());
            assertEquals(0x10, buf.readUnsignedByte());
            assertEquals(0, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfUnsignedByteMustBoundsCheckWhenReadOffsetAndSizeIsBeyondWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            int value = 0x01;
            buf.writeUnsignedByte(value);
            buf.readerOffset(1);
            assertEquals(0, buf.readableBytes());
            assertEquals(7, buf.writableBytes());
            assertThrows(IndexOutOfBoundsException.class, () -> buf.readUnsignedByte());
            assertEquals(0, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfUnsignedByteReadOnlyMustBoundsCheckWhenReadOffsetAndSizeIsBeyondWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            int value = 0x01;
            buf.writeUnsignedByte(value);
            buf.readerOffset(1);
            assertEquals(0, buf.readableBytes());
            assertEquals(7, buf.writableBytes());
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().readUnsignedByte());
            assertEquals(0, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeWriteOfByteMustBoundsCheckWhenWriteOffsetAndSizeIsBeyondCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(Long.BYTES, buf.capacity());
            buf.writerOffset(8);
            byte value = 0x01;
            assertThrows(IndexOutOfBoundsException.class, () -> buf.writeByte(value));
            buf.writerOffset(Long.BYTES);
            // Verify contents are unchanged.
            assertEquals(0, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeWriteOfByteMustHaveDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            byte value = 0x01;
            buf.writeByte(value);
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
    void relativeWriteOfUnsignedByteMustBoundsCheckWhenWriteOffsetAndSizeIsBeyondCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(Long.BYTES, buf.capacity());
            buf.writerOffset(8);
            int value = 0x01;
            assertThrows(IndexOutOfBoundsException.class, () -> buf.writeUnsignedByte(value));
            buf.writerOffset(Long.BYTES);
            // Verify contents are unchanged.
            assertEquals(0, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeWriteOfUnsignedByteMustHaveDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            int value = 0x01;
            buf.writeUnsignedByte(value);
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
    void relativeReadOfCharMustNotBoundsCheckWhenReadOffsetAndSizeIsEqualToWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            char value = 0x0102;
            buf.writeChar(value);
            assertEquals(2, buf.readableBytes());
            assertEquals(6, buf.writableBytes());
            assertEquals(value, buf.readChar());
            assertEquals(0, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfCharMustReadWithDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            char value = 0x0102;
            buf.writeChar(value);
            buf.setByte(0, (byte) 0x10);
            assertEquals(2, buf.readableBytes());
            assertEquals(6, buf.writableBytes());
            assertEquals(0x1002, buf.readChar());
            assertEquals(0, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfCharMustBoundsCheckWhenReadOffsetAndSizeIsBeyondWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            char value = 0x0102;
            buf.writeChar(value);
            buf.readerOffset(1);
            assertEquals(1, buf.readableBytes());
            assertEquals(6, buf.writableBytes());
            assertThrows(IndexOutOfBoundsException.class, buf::readChar);
            assertEquals(1, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfCharReadOnlyMustBoundsCheckWhenReadOffsetAndSizeIsBeyondWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            char value = 0x0102;
            buf.writeChar(value);
            buf.readerOffset(1);
            assertEquals(1, buf.readableBytes());
            assertEquals(6, buf.writableBytes());
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().readChar());
            assertEquals(1, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeWriteOfCharMustBoundsCheckWhenWriteOffsetAndSizeIsBeyondCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(Long.BYTES, buf.capacity());
            buf.writerOffset(7);
            char value = 0x0102;
            assertThrows(IndexOutOfBoundsException.class, () -> buf.writeChar(value));
            buf.writerOffset(Long.BYTES);
            // Verify contents are unchanged.
            assertEquals(0, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeWriteOfCharMustHaveDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            char value = 0x0102;
            buf.writeChar(value);
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
    void relativeReadOfShortMustNotBoundsCheckWhenReadOffsetAndSizeIsEqualToWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            short value = 0x0102;
            buf.writeShort(value);
            assertEquals(2, buf.readableBytes());
            assertEquals(6, buf.writableBytes());
            assertEquals(value, buf.readShort());
            assertEquals(0, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfShortMustReadWithDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            short value = 0x0102;
            buf.writeShort(value);
            buf.setByte(0, (byte) 0x10);
            assertEquals(2, buf.readableBytes());
            assertEquals(6, buf.writableBytes());
            assertEquals(0x1002, buf.readShort());
            assertEquals(0, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfShortMustBoundsCheckWhenReadOffsetAndSizeIsBeyondWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            short value = 0x0102;
            buf.writeShort(value);
            buf.readerOffset(1);
            assertEquals(1, buf.readableBytes());
            assertEquals(6, buf.writableBytes());
            assertThrows(IndexOutOfBoundsException.class, buf::readShort);
            assertEquals(1, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfShortReadOnlyMustBoundsCheckWhenReadOffsetAndSizeIsBeyondWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            short value = 0x0102;
            buf.writeShort(value);
            buf.readerOffset(1);
            assertEquals(1, buf.readableBytes());
            assertEquals(6, buf.writableBytes());
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().readShort());
            assertEquals(1, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfUnsignedShortMustNotBoundsCheckWhenReadOffsetAndSizeIsEqualToWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            int value = 0x0102;
            buf.writeUnsignedShort(value);
            assertEquals(2, buf.readableBytes());
            assertEquals(6, buf.writableBytes());
            assertEquals(value, buf.readUnsignedShort());
            assertEquals(0, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfUnsignedShortMustReadWithDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            int value = 0x0102;
            buf.writeUnsignedShort(value);
            buf.setByte(0, (byte) 0x10);
            assertEquals(2, buf.readableBytes());
            assertEquals(6, buf.writableBytes());
            assertEquals(0x1002, buf.readUnsignedShort());
            assertEquals(0, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfUnsignedShortMustBoundsCheckWhenReadOffsetAndSizeIsBeyondWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            int value = 0x0102;
            buf.writeUnsignedShort(value);
            buf.readerOffset(1);
            assertEquals(1, buf.readableBytes());
            assertEquals(6, buf.writableBytes());
            assertThrows(IndexOutOfBoundsException.class, buf::readUnsignedShort);
            assertEquals(1, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfUnsignedShortReadOnlyMustBoundsCheckWhenReadOffsetAndSizeIsBeyondWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            int value = 0x0102;
            buf.writeUnsignedShort(value);
            buf.readerOffset(1);
            assertEquals(1, buf.readableBytes());
            assertEquals(6, buf.writableBytes());
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().readUnsignedShort());
            assertEquals(1, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeWriteOfShortMustBoundsCheckWhenWriteOffsetAndSizeIsBeyondCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(Long.BYTES, buf.capacity());
            buf.writerOffset(7);
            short value = 0x0102;
            assertThrows(IndexOutOfBoundsException.class, () -> buf.writeShort(value));
            buf.writerOffset(Long.BYTES);
            // Verify contents are unchanged.
            assertEquals(0, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeWriteOfShortMustHaveDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            short value = 0x0102;
            buf.writeShort(value);
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
    void relativeWriteOfUnsignedShortMustBoundsCheckWhenWriteOffsetAndSizeIsBeyondCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(Long.BYTES, buf.capacity());
            buf.writerOffset(7);
            int value = 0x0102;
            assertThrows(IndexOutOfBoundsException.class, () -> buf.writeUnsignedShort(value));
            buf.writerOffset(Long.BYTES);
            // Verify contents are unchanged.
            assertEquals(0, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeWriteOfUnsignedShortMustHaveDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            int value = 0x0102;
            buf.writeUnsignedShort(value);
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
    void relativeReadOfMediumMustNotBoundsCheckWhenReadOffsetAndSizeIsEqualToWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            int value = 0x010203;
            buf.writeMedium(value);
            assertEquals(3, buf.readableBytes());
            assertEquals(5, buf.writableBytes());
            assertEquals(value, buf.readMedium());
            assertEquals(0, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfMediumMustReadWithDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            int value = 0x010203;
            buf.writeMedium(value);
            buf.setByte(0, (byte) 0x10);
            assertEquals(3, buf.readableBytes());
            assertEquals(5, buf.writableBytes());
            assertEquals(0x100203, buf.readMedium());
            assertEquals(0, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfMediumMustBoundsCheckWhenReadOffsetAndSizeIsBeyondWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            int value = 0x010203;
            buf.writeMedium(value);
            buf.readerOffset(1);
            assertEquals(2, buf.readableBytes());
            assertEquals(5, buf.writableBytes());
            assertThrows(IndexOutOfBoundsException.class, buf::readMedium);
            assertEquals(2, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfMediumReadOnlyMustBoundsCheckWhenReadOffsetAndSizeIsBeyondWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            int value = 0x010203;
            buf.writeMedium(value);
            buf.readerOffset(1);
            assertEquals(2, buf.readableBytes());
            assertEquals(5, buf.writableBytes());
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().readMedium());
            assertEquals(2, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfUnsignedMediumMustNotBoundsCheckWhenReadOffsetAndSizeIsEqualToWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            int value = 0x010203;
            buf.writeUnsignedMedium(value);
            assertEquals(3, buf.readableBytes());
            assertEquals(5, buf.writableBytes());
            assertEquals(value, buf.readUnsignedMedium());
            assertEquals(0, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfUnsignedMediumMustReadWithDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            int value = 0x010203;
            buf.writeUnsignedMedium(value);
            buf.setByte(0, (byte) 0x10);
            assertEquals(3, buf.readableBytes());
            assertEquals(5, buf.writableBytes());
            assertEquals(0x100203, buf.readUnsignedMedium());
            assertEquals(0, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfUnsignedMediumMustBoundsCheckWhenReadOffsetAndSizeIsBeyondWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            int value = 0x010203;
            buf.writeUnsignedMedium(value);
            buf.readerOffset(1);
            assertEquals(2, buf.readableBytes());
            assertEquals(5, buf.writableBytes());
            assertThrows(IndexOutOfBoundsException.class, buf::readUnsignedMedium);
            assertEquals(2, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfUnsignedMediumReadOnlyMustBoundsCheckWhenReadOffsetAndSizeIsBeyondWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            int value = 0x010203;
            buf.writeUnsignedMedium(value);
            buf.readerOffset(1);
            assertEquals(2, buf.readableBytes());
            assertEquals(5, buf.writableBytes());
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().readUnsignedMedium());
            assertEquals(2, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeWriteOfMediumMustBoundsCheckWhenWriteOffsetAndSizeIsBeyondCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(Long.BYTES, buf.capacity());
            buf.writerOffset(6);
            int value = 0x010203;
            assertThrows(IndexOutOfBoundsException.class, () -> buf.writeMedium(value));
            buf.writerOffset(Long.BYTES);
            // Verify contents are unchanged.
            assertEquals(0, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeWriteOfMediumMustHaveDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            int value = 0x010203;
            buf.writeMedium(value);
            buf.writerOffset(Long.BYTES);
            assertEquals((byte) 0x01, buf.readByte());
            assertEquals((byte) 0x02, buf.readByte());
            assertEquals((byte) 0x03, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeWriteOfUnsignedMediumMustBoundsCheckWhenWriteOffsetAndSizeIsBeyondCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(Long.BYTES, buf.capacity());
            buf.writerOffset(6);
            int value = 0x010203;
            assertThrows(IndexOutOfBoundsException.class, () -> buf.writeUnsignedMedium(value));
            buf.writerOffset(Long.BYTES);
            // Verify contents are unchanged.
            assertEquals(0, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeWriteOfUnsignedMediumMustHaveDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            int value = 0x010203;
            buf.writeUnsignedMedium(value);
            buf.writerOffset(Long.BYTES);
            assertEquals((byte) 0x01, buf.readByte());
            assertEquals((byte) 0x02, buf.readByte());
            assertEquals((byte) 0x03, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfIntMustNotBoundsCheckWhenReadOffsetAndSizeIsEqualToWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            int value = 0x01020304;
            buf.writeInt(value);
            assertEquals(4, buf.readableBytes());
            assertEquals(4, buf.writableBytes());
            assertEquals(value, buf.readInt());
            assertEquals(0, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfIntMustReadWithDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            int value = 0x01020304;
            buf.writeInt(value);
            buf.setByte(0, (byte) 0x10);
            assertEquals(4, buf.readableBytes());
            assertEquals(4, buf.writableBytes());
            assertEquals(0x10020304, buf.readInt());
            assertEquals(0, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfIntMustBoundsCheckWhenReadOffsetAndSizeIsBeyondWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            int value = 0x01020304;
            buf.writeInt(value);
            buf.readerOffset(1);
            assertEquals(3, buf.readableBytes());
            assertEquals(4, buf.writableBytes());
            assertThrows(IndexOutOfBoundsException.class, buf::readInt);
            assertEquals(3, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfIntReadOnlyMustBoundsCheckWhenReadOffsetAndSizeIsBeyondWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            int value = 0x01020304;
            buf.writeInt(value);
            buf.readerOffset(1);
            assertEquals(3, buf.readableBytes());
            assertEquals(4, buf.writableBytes());
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().readInt());
            assertEquals(3, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfUnsignedIntMustNotBoundsCheckWhenReadOffsetAndSizeIsEqualToWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            long value = 0x01020304;
            buf.writeUnsignedInt(value);
            assertEquals(4, buf.readableBytes());
            assertEquals(4, buf.writableBytes());
            assertEquals(value, buf.readUnsignedInt());
            assertEquals(0, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfUnsignedIntMustReadWithDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            long value = 0x01020304;
            buf.writeUnsignedInt(value);
            buf.setByte(0, (byte) 0x10);
            assertEquals(4, buf.readableBytes());
            assertEquals(4, buf.writableBytes());
            assertEquals(0x10020304, buf.readUnsignedInt());
            assertEquals(0, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfUnsignedIntMustBoundsCheckWhenReadOffsetAndSizeIsBeyondWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            long value = 0x01020304;
            buf.writeUnsignedInt(value);
            buf.readerOffset(1);
            assertEquals(3, buf.readableBytes());
            assertEquals(4, buf.writableBytes());
            assertThrows(IndexOutOfBoundsException.class, buf::readUnsignedInt);
            assertEquals(3, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfUnsignedIntReadOnlyMustBoundsCheckWhenReadOffsetAndSizeIsBeyondWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            long value = 0x01020304;
            buf.writeUnsignedInt(value);
            buf.readerOffset(1);
            assertEquals(3, buf.readableBytes());
            assertEquals(4, buf.writableBytes());
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().readUnsignedInt());
            assertEquals(3, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeWriteOfIntMustBoundsCheckWhenWriteOffsetAndSizeIsBeyondCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(Long.BYTES, buf.capacity());
            buf.writerOffset(5);
            int value = 0x01020304;
            assertThrows(IndexOutOfBoundsException.class, () -> buf.writeInt(value));
            buf.writerOffset(Long.BYTES);
            // Verify contents are unchanged.
            assertEquals(0, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeWriteOfIntMustHaveDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            int value = 0x01020304;
            buf.writeInt(value);
            buf.writerOffset(Long.BYTES);
            assertEquals((byte) 0x01, buf.readByte());
            assertEquals((byte) 0x02, buf.readByte());
            assertEquals((byte) 0x03, buf.readByte());
            assertEquals((byte) 0x04, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeWriteOfUnsignedIntMustBoundsCheckWhenWriteOffsetAndSizeIsBeyondCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(Long.BYTES, buf.capacity());
            buf.writerOffset(5);
            long value = 0x01020304;
            assertThrows(IndexOutOfBoundsException.class, () -> buf.writeUnsignedInt(value));
            buf.writerOffset(Long.BYTES);
            // Verify contents are unchanged.
            assertEquals(0, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeWriteOfUnsignedIntMustHaveDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            long value = 0x01020304;
            buf.writeUnsignedInt(value);
            buf.writerOffset(Long.BYTES);
            assertEquals((byte) 0x01, buf.readByte());
            assertEquals((byte) 0x02, buf.readByte());
            assertEquals((byte) 0x03, buf.readByte());
            assertEquals((byte) 0x04, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfFloatMustNotBoundsCheckWhenReadOffsetAndSizeIsEqualToWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            float value = Float.intBitsToFloat(0x01020304);
            buf.writeFloat(value);
            assertEquals(4, buf.readableBytes());
            assertEquals(4, buf.writableBytes());
            assertEquals(value, buf.readFloat());
            assertEquals(0, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfFloatMustReadWithDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            float value = Float.intBitsToFloat(0x01020304);
            buf.writeFloat(value);
            buf.setByte(0, (byte) 0x10);
            assertEquals(4, buf.readableBytes());
            assertEquals(4, buf.writableBytes());
            assertEquals(Float.intBitsToFloat(0x10020304), buf.readFloat());
            assertEquals(0, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfFloatMustBoundsCheckWhenReadOffsetAndSizeIsBeyondWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            float value = Float.intBitsToFloat(0x01020304);
            buf.writeFloat(value);
            buf.readerOffset(1);
            assertEquals(3, buf.readableBytes());
            assertEquals(4, buf.writableBytes());
            assertThrows(IndexOutOfBoundsException.class, buf::readFloat);
            assertEquals(3, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfFloatReadOnlyMustBoundsCheckWhenReadOffsetAndSizeIsBeyondWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            float value = Float.intBitsToFloat(0x01020304);
            buf.writeFloat(value);
            buf.readerOffset(1);
            assertEquals(3, buf.readableBytes());
            assertEquals(4, buf.writableBytes());
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().readFloat());
            assertEquals(3, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeWriteOfFloatMustBoundsCheckWhenWriteOffsetAndSizeIsBeyondCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(Long.BYTES, buf.capacity());
            buf.writerOffset(5);
            float value = Float.intBitsToFloat(0x01020304);
            assertThrows(IndexOutOfBoundsException.class, () -> buf.writeFloat(value));
            buf.writerOffset(Long.BYTES);
            // Verify contents are unchanged.
            assertEquals(0, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeWriteOfFloatMustHaveDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            float value = Float.intBitsToFloat(0x01020304);
            buf.writeFloat(value);
            buf.writerOffset(Long.BYTES);
            assertEquals((byte) 0x01, buf.readByte());
            assertEquals((byte) 0x02, buf.readByte());
            assertEquals((byte) 0x03, buf.readByte());
            assertEquals((byte) 0x04, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
            assertEquals((byte) 0x00, buf.readByte());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfLongMustNotBoundsCheckWhenReadOffsetAndSizeIsEqualToWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            long value = 0x0102030405060708L;
            buf.writeLong(value);
            assertEquals(8, buf.readableBytes());
            assertEquals(0, buf.writableBytes());
            assertEquals(value, buf.readLong());
            assertEquals(0, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfLongMustReadWithDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            long value = 0x0102030405060708L;
            buf.writeLong(value);
            buf.setByte(0, (byte) 0x10);
            assertEquals(8, buf.readableBytes());
            assertEquals(0, buf.writableBytes());
            assertEquals(0x1002030405060708L, buf.readLong());
            assertEquals(0, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfLongMustBoundsCheckWhenReadOffsetAndSizeIsBeyondWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            long value = 0x0102030405060708L;
            buf.writeLong(value);
            buf.readerOffset(1);
            assertEquals(7, buf.readableBytes());
            assertEquals(0, buf.writableBytes());
            assertThrows(IndexOutOfBoundsException.class, buf::readLong);
            assertEquals(7, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfLongReadOnlyMustBoundsCheckWhenReadOffsetAndSizeIsBeyondWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            long value = 0x0102030405060708L;
            buf.writeLong(value);
            buf.readerOffset(1);
            assertEquals(7, buf.readableBytes());
            assertEquals(0, buf.writableBytes());
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().readLong());
            assertEquals(7, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeWriteOfLongMustBoundsCheckWhenWriteOffsetAndSizeIsBeyondCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(Long.BYTES, buf.capacity());
            buf.writerOffset(1);
            long value = 0x0102030405060708L;
            assertThrows(IndexOutOfBoundsException.class, () -> buf.writeLong(value));
            buf.writerOffset(Long.BYTES);
            // Verify contents are unchanged.
            assertEquals(0, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeWriteOfLongMustHaveDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            long value = 0x0102030405060708L;
            buf.writeLong(value);
            buf.writerOffset(Long.BYTES);
            assertEquals((byte) 0x01, buf.readByte());
            assertEquals((byte) 0x02, buf.readByte());
            assertEquals((byte) 0x03, buf.readByte());
            assertEquals((byte) 0x04, buf.readByte());
            assertEquals((byte) 0x05, buf.readByte());
            assertEquals((byte) 0x06, buf.readByte());
            assertEquals((byte) 0x07, buf.readByte());
            assertEquals((byte) 0x08, buf.readByte());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfDoubleMustNotBoundsCheckWhenReadOffsetAndSizeIsEqualToWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            double value = Double.longBitsToDouble(0x0102030405060708L);
            buf.writeDouble(value);
            assertEquals(8, buf.readableBytes());
            assertEquals(0, buf.writableBytes());
            assertEquals(value, buf.readDouble());
            assertEquals(0, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfDoubleMustReadWithDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            double value = Double.longBitsToDouble(0x0102030405060708L);
            buf.writeDouble(value);
            buf.setByte(0, (byte) 0x10);
            assertEquals(8, buf.readableBytes());
            assertEquals(0, buf.writableBytes());
            assertEquals(Double.longBitsToDouble(0x1002030405060708L), buf.readDouble());
            assertEquals(0, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfDoubleMustBoundsCheckWhenReadOffsetAndSizeIsBeyondWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            double value = Double.longBitsToDouble(0x0102030405060708L);
            buf.writeDouble(value);
            buf.readerOffset(1);
            assertEquals(7, buf.readableBytes());
            assertEquals(0, buf.writableBytes());
            assertThrows(IndexOutOfBoundsException.class, buf::readDouble);
            assertEquals(7, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeReadOfDoubleReadOnlyMustBoundsCheckWhenReadOffsetAndSizeIsBeyondWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(0, buf.readableBytes());
            assertEquals(Long.BYTES, buf.writableBytes());
            double value = Double.longBitsToDouble(0x0102030405060708L);
            buf.writeDouble(value);
            buf.readerOffset(1);
            assertEquals(7, buf.readableBytes());
            assertEquals(0, buf.writableBytes());
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().readDouble());
            assertEquals(7, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeWriteOfDoubleMustBoundsCheckWhenWriteOffsetAndSizeIsBeyondCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(Long.BYTES, buf.capacity());
            buf.writerOffset(1);
            double value = Double.longBitsToDouble(0x0102030405060708L);
            assertThrows(IndexOutOfBoundsException.class, () -> buf.writeDouble(value));
            buf.writerOffset(Long.BYTES);
            // Verify contents are unchanged.
            assertEquals(0, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void relativeWriteOfDoubleMustHaveDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            double value = Double.longBitsToDouble(0x0102030405060708L);
            buf.writeDouble(value);
            buf.writerOffset(Long.BYTES);
            assertEquals((byte) 0x01, buf.readByte());
            assertEquals((byte) 0x02, buf.readByte());
            assertEquals((byte) 0x03, buf.readByte());
            assertEquals((byte) 0x04, buf.readByte());
            assertEquals((byte) 0x05, buf.readByte());
            assertEquals((byte) 0x06, buf.readByte());
            assertEquals((byte) 0x07, buf.readByte());
            assertEquals((byte) 0x08, buf.readByte());
        }
    }
}
