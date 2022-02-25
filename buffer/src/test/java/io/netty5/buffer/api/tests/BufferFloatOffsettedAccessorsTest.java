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

public class BufferFloatOffsettedAccessorsTest extends BufferTestSupport {

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfFloatMustBoundsCheckOnNegativeOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.getFloat(-1));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfFloatReadOnlyMustBoundsCheckOnNegativeOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().getFloat(-1));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfFloatMustNotBoundsCheckWhenReadOffsetAndSizeIsEqualToWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            float value = Float.intBitsToFloat(0x01020304);
            buf.writeFloat(value);
            assertEquals(value, buf.getFloat(0));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfFloatMustReadWithDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            float value = Float.intBitsToFloat(0x01020304);
            buf.writeFloat(value);
            buf.setByte(0, (byte) 0x10);
            assertEquals(Float.intBitsToFloat(0x10020304), buf.getFloat(0));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfFloatMustNotBoundsCheckWhenReadOffsetAndSizeIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            float value = Float.intBitsToFloat(0x01020304);
            buf.writeFloat(value);
            buf.getFloat(1);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfFloatMustBoundsCheckWhenReadOffsetAndSizeIsGreaterThanCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.getFloat(7));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfFloatReadOnlyMustNotBoundsCheckWhenReadOffsetAndSizeIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            float value = Float.intBitsToFloat(0x01020304);
            buf.writeFloat(value);
            buf.makeReadOnly().getFloat(1);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfFloatReadOnlyMustBoundsCheckWhenReadOffsetAndSizeIsGreaterThanCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().getFloat(5));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfFloatMustNotBoundsCheckWhenReadOffsetIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.getFloat(0);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfFloatMustBoundsCheckWhenReadOffsetIsGreaterThanCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.getFloat(8));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfFloatReadOnlyMustNotBoundsCheckWhenReadOffsetIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.makeReadOnly().getFloat(0);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfFloatReadOnlyMustBoundsCheckWhenReadOffsetIsGreaterThan(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().getFloat(8));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedSetOfFloatMustBoundsCheckWhenWriteOffsetIsNegative(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(Long.BYTES, buf.capacity());
            float value = Float.intBitsToFloat(0x01020304);
            assertThrows(IndexOutOfBoundsException.class, () -> buf.setFloat(-1, value));
            buf.writerOffset(Long.BYTES);
            // Verify contents are unchanged.
            assertEquals(0, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedSetOfFloatMustBoundsCheckWhenWriteOffsetAndSizeIsBeyondCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(Long.BYTES, buf.capacity());
            float value = Float.intBitsToFloat(0x01020304);
            assertThrows(IndexOutOfBoundsException.class, () -> buf.setFloat(5, value));
            buf.writerOffset(Long.BYTES);
            // Verify contents are unchanged.
            assertEquals(0, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedSetOfFloatMustHaveDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            float value = Float.intBitsToFloat(0x01020304);
            buf.setFloat(0, value);
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
}
