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

public class BufferDoubleOffsettedAccessorsTest extends BufferTestSupport {
    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfDoubleMustBoundsCheckOnNegativeOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.getDouble(-1));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfDoubleReadOnlyMustBoundsCheckOnNegativeOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().getDouble(-1));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfDoubleMustNotBoundsCheckWhenReadOffsetAndSizeIsEqualToWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            double value = Double.longBitsToDouble(0x0102030405060708L);
            buf.writeDouble(value);
            assertEquals(value, buf.getDouble(0));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfDoubleMustReadWithDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            double value = Double.longBitsToDouble(0x0102030405060708L);
            buf.writeDouble(value);
            buf.setByte(0, (byte) 0x10);
            assertEquals(Double.longBitsToDouble(0x1002030405060708L), buf.getDouble(0));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfDoubleMustBoundsCheckWhenReadOffsetAndSizeIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            double value = Double.longBitsToDouble(0x0102030405060708L);
            buf.writeDouble(value);
            assertThrows(IndexOutOfBoundsException.class, () -> buf.getDouble(1));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfDoubleReadOnlyMustBoundsCheckWhenReadOffsetAndSizeIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            double value = Double.longBitsToDouble(0x0102030405060708L);
            buf.writeDouble(value);
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().getDouble(1));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfDoubleMustNotBoundsCheckWhenReadOffsetIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.getDouble(0);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfDoubleMustBoundsCheckWhenReadOffsetIsGreaterThanCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.getDouble(8));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfDoubleReadOnlyMustNotBoundsCheckWhenReadOffsetIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.makeReadOnly().getDouble(0);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfDoubleReadOnlyMustBoundsCheckWhenReadOffsetIsGreaterThanCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().getDouble(8));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedSetOfDoubleMustBoundsCheckWhenWriteOffsetIsNegative(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(Long.BYTES, buf.capacity());
            double value = Double.longBitsToDouble(0x0102030405060708L);
            assertThrows(IndexOutOfBoundsException.class, () -> buf.setDouble(-1, value));
            buf.writerOffset(Long.BYTES);
            // Verify contents are unchanged.
            assertEquals(0, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedSetOfDoubleMustBoundsCheckWhenWriteOffsetAndSizeIsBeyondCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(Long.BYTES, buf.capacity());
            double value = Double.longBitsToDouble(0x0102030405060708L);
            assertThrows(IndexOutOfBoundsException.class, () -> buf.setDouble(1, value));
            buf.writerOffset(Long.BYTES);
            // Verify contents are unchanged.
            assertEquals(0, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedSetOfDoubleMustHaveDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            double value = Double.longBitsToDouble(0x0102030405060708L);
            buf.setDouble(0, value);
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
