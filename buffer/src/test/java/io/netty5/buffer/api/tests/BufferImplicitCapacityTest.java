/*
 * Copyright 2022 The Netty Project
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

import static io.netty5.buffer.api.internal.Statics.MAX_BUFFER_SIZE;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BufferImplicitCapacityTest extends BufferTestSupport {
    @ParameterizedTest
    @MethodSource("allocators")
    public void implicitLimitMustBeWithinBounds(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.implicitCapacityLimit(0));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.implicitCapacityLimit(buffer.capacity() - 1));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.implicitCapacityLimit(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.implicitCapacityLimit(MAX_BUFFER_SIZE + 1));
            buffer.implicitCapacityLimit(MAX_BUFFER_SIZE);
            buffer.implicitCapacityLimit(buffer.capacity());
            buffer.writeLong(0x0102030405060708L);
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeByte((byte) 1));
            assertEquals(8, buffer.capacity());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void bufferFromCopyMustHaveResetImplicitCapacityLimit(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8)) {
            buffer.writeLong(0x0102030405060708L);
            buffer.implicitCapacityLimit(buffer.capacity());
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeByte((byte) 1));
            try (Buffer copy = buffer.copy()) {
                assertEquals(8, copy.readableBytes());
                copy.writeByte((byte) 2); // No more implicit limit
            }
            try (Buffer copy = buffer.copy(0, 8)) {
                assertEquals(8, copy.readableBytes());
                copy.writeByte((byte) 2); // No more implicit limit
            }
            try (Buffer copy = buffer.copy(1, 6)) {
                assertEquals(6, copy.readableBytes());
                copy.writeByte((byte) 3); // No more implicit limit
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void bufferFromSplitMustHaveResetImplicitCapacityLimit(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(32)) {
            buffer.implicitCapacityLimit(32);
            try (Buffer split = buffer.split()) {
                split.writeLong(0).writeLong(0).writeLong(0).writeLong(0); // 32 bytes written.
                split.writeByte((byte) 0); // 33rd byte, mustn't fail.
            }
            assertEquals(32, buffer.capacity());
            try (Buffer split = buffer.split(2)) {
                split.writeLong(0).writeLong(0).writeLong(0).writeLong(0); // 32 bytes written.
                split.writeByte((byte) 0); // 33rd byte, mustn't fail.
            }
            assertEquals(30, buffer.capacity());
            buffer.writeInt(42).readInt();
            try (Buffer split = buffer.readSplit(4)) {
                split.writeLong(0).writeLong(0).writeLong(0).writeLong(0); // 32 bytes written.
                split.writeByte((byte) 0); // 33rd byte, mustn't fail.
            }
            assertEquals(22, buffer.capacity());
            buffer.writeLong(0);
            try (Buffer split = buffer.writeSplit(8)) {
                split.writeLong(0).writeLong(0).writeLong(0).writeLong(0); // 32 bytes written.
                split.writeByte((byte) 0); // 33rd byte, mustn't fail.
            }
            assertEquals(6, buffer.capacity());
            buffer.writeLong(0).writeLong(0).writeLong(0).writeLong(0); // 32 bytes written.
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeByte((byte) 0)); // 33rd byte, at limit.
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void splitDoesNotReduceImplicitCapacityLimit(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8)) {
            buffer.implicitCapacityLimit(8);
            buffer.writeLong(0x0102030405060708L);
            buffer.split().close();
            assertEquals(0, buffer.capacity());
            buffer.writeLong(0x0102030405060708L);
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeByte((byte) 1));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void sendMustPreserveImplicitCapacityLimit(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8)) {
            buffer.writeLong(0x0102030405060708L);
            try (Buffer sent = buffer.implicitCapacityLimit(8).send().receive()) {
                assertThrows(IndexOutOfBoundsException.class, () -> sent.writeByte((byte) 0));
                assertEquals(0x0102030405060708L, sent.readLong());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void ensureWritableCanGrowBeyondImplicitCapacityLimit(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8).implicitCapacityLimit(8)) {
            buffer.writeLong(0x0102030405060708L);
            buffer.ensureWritable(8);
            buffer.writeLong(0x0102030405060708L);
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeByte((byte) 1));
            buffer.readByte();
            buffer.compact(); // Now we have room and don't need to expand capacity implicitly.
            buffer.writeByte((byte) 1);
            // And now there's no more room.
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeByte((byte) 1));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void writesMustThrowIfSizeWouldGoBeyondImplicitCapacityLimit(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            // Short
            try (Buffer buffer = allocator.allocate(0).implicitCapacityLimit(1)) {
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeShort((short) 0));
                buffer.writeByte((byte) 0);
            }
            try (Buffer buffer = allocator.allocate(1).implicitCapacityLimit(2)) {
                buffer.writeByte((byte) 0);
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeShort((short) 0));
                buffer.writeByte((byte) 0);
            }
            try (Buffer buffer = allocator.allocate(0).implicitCapacityLimit(1)) {
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeUnsignedShort(0));
                buffer.writeByte((byte) 0);
            }
            try (Buffer buffer = allocator.allocate(1).implicitCapacityLimit(2)) {
                buffer.writeByte((byte) 0);
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeUnsignedShort(0));
                buffer.writeByte((byte) 0);
            }

            // Char
            try (Buffer buffer = allocator.allocate(0).implicitCapacityLimit(1)) {
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeChar('0'));
                buffer.writeByte((byte) 0);
            }
            try (Buffer buffer = allocator.allocate(1).implicitCapacityLimit(2)) {
                buffer.writeByte((byte) 0);
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeChar('0'));
                buffer.writeByte((byte) 0);
            }

            // Medium
            try (Buffer buffer = allocator.allocate(0).implicitCapacityLimit(2)) {
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeMedium(0));
                buffer.writeByte((byte) 0);
            }
            try (Buffer buffer = allocator.allocate(1).implicitCapacityLimit(3)) {
                buffer.writeByte((byte) 0);
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeMedium(0));
                buffer.writeByte((byte) 0);
            }
            try (Buffer buffer = allocator.allocate(0).implicitCapacityLimit(2)) {
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeUnsignedMedium(0));
                buffer.writeByte((byte) 0);
            }
            try (Buffer buffer = allocator.allocate(1).implicitCapacityLimit(3)) {
                buffer.writeByte((byte) 0);
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeUnsignedMedium(0));
                buffer.writeByte((byte) 0);
            }

            // Int
            try (Buffer buffer = allocator.allocate(0).implicitCapacityLimit(3)) {
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeInt(0));
                buffer.writeByte((byte) 0);
            }
            try (Buffer buffer = allocator.allocate(1).implicitCapacityLimit(4)) {
                buffer.writeByte((byte) 0);
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeInt(0));
                buffer.writeByte((byte) 0);
            }
            try (Buffer buffer = allocator.allocate(0).implicitCapacityLimit(3)) {
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeUnsignedInt(0));
                buffer.writeByte((byte) 0);
            }
            try (Buffer buffer = allocator.allocate(1).implicitCapacityLimit(4)) {
                buffer.writeByte((byte) 0);
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeUnsignedInt(0));
                buffer.writeByte((byte) 0);
            }

            // Float
            try (Buffer buffer = allocator.allocate(0).implicitCapacityLimit(3)) {
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeFloat(0));
                buffer.writeByte((byte) 0);
            }
            try (Buffer buffer = allocator.allocate(1).implicitCapacityLimit(4)) {
                buffer.writeByte((byte) 0);
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeFloat(0));
                buffer.writeByte((byte) 0);
            }

            // Long
            try (Buffer buffer = allocator.allocate(0).implicitCapacityLimit(7)) {
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeLong(0));
                buffer.writeByte((byte) 0);
            }
            try (Buffer buffer = allocator.allocate(1).implicitCapacityLimit(8)) {
                buffer.writeByte((byte) 0);
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeLong(0));
                buffer.writeByte((byte) 0);
            }

            // Double
            try (Buffer buffer = allocator.allocate(0).implicitCapacityLimit(7)) {
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeDouble(0));
                buffer.writeByte((byte) 0);
            }
            try (Buffer buffer = allocator.allocate(1).implicitCapacityLimit(8)) {
                buffer.writeByte((byte) 0);
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeDouble(0));
                buffer.writeByte((byte) 0);
            }
        }
    }
}
