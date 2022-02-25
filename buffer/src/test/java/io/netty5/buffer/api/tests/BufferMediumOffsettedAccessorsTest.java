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

public class BufferMediumOffsettedAccessorsTest extends BufferTestSupport {
    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfMediumMustBoundsCheckOnNegativeOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.getMedium(-1));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfMediumReadOnlyMustBoundsCheckOnNegativeOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().getMedium(-1));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfMediumMustNotBoundsCheckWhenReadOffsetAndSizeIsEqualToWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            int value = 0x010203;
            buf.writeMedium(value);
            assertEquals(value, buf.getMedium(0));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfMediumMustReadWithDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            int value = 0x010203;
            buf.writeMedium(value);
            buf.setByte(0, (byte) 0x10);
            assertEquals(0x100203, buf.getMedium(0));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfMediumMustNotBoundsCheckWhenReadOffsetAndSizeIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            int value = 0x010203;
            buf.writeMedium(value);
            buf.getMedium(1);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfMediumMustBoundsCheckWhenReadOffsetAndSizeIsGreaterThanCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            int value = 0x010203;
            buf.writeMedium(value);
            assertThrows(IndexOutOfBoundsException.class, () -> buf.getMedium(6));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfMediumReadOnlyMustNotBoundsCheckWhenReadOffsetAndSizeIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            int value = 0x010203;
            buf.writeMedium(value);
            buf.makeReadOnly().getMedium(1);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfMediumReadOnlyMustBoundsCheckWhenReadOffsetAndSizeIsGreaterThanCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().getMedium(6));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfMediumMustNotBoundsCheckWhenReadOffsetIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.getMedium(0);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfMediumMustBoundsCheckWhenReadOffsetIsGreaterThanCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.getMedium(8));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfMediumReadOnlyMustNotBoundsCheckWhenReadOffsetIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.makeReadOnly().getMedium(0);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfMediumReadOnlyMustBoundsCheckWhenReadOffsetIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().getMedium(8));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedMediumMustBoundsCheckOnNegativeOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.getUnsignedMedium(-1));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedMediumReadOnlyMustBoundsCheckOnNegativeOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().getUnsignedMedium(-1));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedMediumMustNotBoundsCheckWhenReadOffsetAndSizeIsEqualToWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            int value = 0x010203;
            buf.writeUnsignedMedium(value);
            assertEquals(value, buf.getUnsignedMedium(0));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedMediumMustReadWithDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            int value = 0x010203;
            buf.writeUnsignedMedium(value);
            buf.setByte(0, (byte) 0x10);
            assertEquals(0x100203, buf.getUnsignedMedium(0));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedMediumMustNotBoundsCheckWhenReadOffsetAndSizeIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            int value = 0x010203;
            buf.writeUnsignedMedium(value);
            buf.getUnsignedMedium(1);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedMediumMustBoundsCheckWhenReadOffsetAndSizeIsGreaterThanCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.getUnsignedMedium(6));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedMediumReadOnlyMustNotBoundsCheckWhenReadOffsetAndSizeIsGreaterThanWriteOffset(
            Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            int value = 0x010203;
            buf.writeUnsignedMedium(value);
            buf.makeReadOnly().getUnsignedMedium(1);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedMediumReadOnlyMustBoundsCheckWhenReadOffsetAndSizeIsGreaterThanCapacity(
            Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().getUnsignedMedium(6));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedMediumMustNotBoundsCheckWhenReadOffsetIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.getUnsignedMedium(0);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedMediumMustBoundsCheckWhenReadOffsetIsGreaterThanCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.getUnsignedMedium(6));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedMediumReadOnlyMustNotBoundsCheckWhenReadOffsetIsGreaterThanWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.makeReadOnly().getUnsignedMedium(0);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedGetOfUnsignedMediumReadOnlyMustBoundsCheckWhenReadOffsetIsGreaterThanCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.makeReadOnly().getUnsignedMedium(8));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedSetOfMediumMustBoundsCheckWhenWriteOffsetIsNegative(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(Long.BYTES, buf.capacity());
            int value = 0x010203;
            assertThrows(IndexOutOfBoundsException.class, () -> buf.setMedium(-1, value));
            buf.writerOffset(Long.BYTES);
            // Verify contents are unchanged.
            assertEquals(0, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedSetOfMediumMustBoundsCheckWhenWriteOffsetAndSizeIsBeyondCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(Long.BYTES, buf.capacity());
            int value = 0x010203;
            assertThrows(IndexOutOfBoundsException.class, () -> buf.setMedium(6, value));
            buf.writerOffset(Long.BYTES);
            // Verify contents are unchanged.
            assertEquals(0, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedSetOfMediumMustHaveDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            int value = 0x010203;
            buf.setMedium(0, value);
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
    void offsettedSetOfUnsignedMediumMustBoundsCheckWhenWriteOffsetIsNegative(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(Long.BYTES, buf.capacity());
            int value = 0x010203;
            assertThrows(IndexOutOfBoundsException.class, () -> buf.setUnsignedMedium(-1, value));
            buf.writerOffset(Long.BYTES);
            // Verify contents are unchanged.
            assertEquals(0, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedSetOfUnsignedMediumMustBoundsCheckWhenWriteOffsetAndSizeIsBeyondCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(Long.BYTES, buf.capacity());
            int value = 0x010203;
            assertThrows(IndexOutOfBoundsException.class, () -> buf.setUnsignedMedium(6, value));
            buf.writerOffset(Long.BYTES);
            // Verify contents are unchanged.
            assertEquals(0, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void offsettedSetOfUnsignedMediumMustHaveDefaultEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            int value = 0x010203;
            buf.setUnsignedMedium(0, value);
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
}
