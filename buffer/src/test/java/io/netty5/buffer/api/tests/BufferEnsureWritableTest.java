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
import io.netty5.buffer.api.CompositeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BufferEnsureWritableTest extends BufferTestSupport {
    @ParameterizedTest
    @MethodSource("allocators")
    public void ensureWritableMustThrowForNegativeSize(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IllegalArgumentException.class, () -> buf.ensureWritable(-1));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void ensureWritableMustThrowIfRequestedSizeWouldGrowBeyondMaxAllowed(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IllegalArgumentException.class, () -> buf.ensureWritable(Integer.MAX_VALUE - 7));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void ensureWritableMustNotThrowWhenSpaceIsAlreadyAvailable(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.ensureWritable(8);
            buf.writeLong(1);
            assertThrows(IndexOutOfBoundsException.class, () -> buf.writeByte((byte) 1));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void ensureWritableMustExpandBufferCapacity(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThat(buf.writableBytes()).isEqualTo(8);
            buf.writeLong(0x0102030405060708L);
            assertThat(buf.writableBytes()).isEqualTo(0);
            buf.ensureWritable(8);
            assertThat(buf.writableBytes()).isGreaterThanOrEqualTo(8);
            assertThat(buf.capacity()).isGreaterThanOrEqualTo(16);
            buf.writeLong(0xA1A2A3A4A5A6A7A8L);
            assertThat(buf.readableBytes()).isEqualTo(16);
            assertThat(buf.readLong()).isEqualTo(0x0102030405060708L);
            assertThat(buf.readLong()).isEqualTo(0xA1A2A3A4A5A6A7A8L);
            assertThrows(IndexOutOfBoundsException.class, buf::readByte);
            // Is it implementation dependent if the capacity increased by *exactly* the requested size, or more.
        }
    }

    @Test
    public void ensureWritableMustExpandCapacityOfEmptyCompositeBuffer() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled();
             Buffer buf = CompositeBuffer.compose(allocator)) {
            assertThat(buf.writableBytes()).isEqualTo(0);
            buf.ensureWritable(8);
            assertThat(buf.writableBytes()).isGreaterThanOrEqualTo(8);
            buf.writeLong(0xA1A2A3A4A5A6A7A8L);
            assertThat(buf.readableBytes()).isEqualTo(8);
            assertThat(buf.readLong()).isEqualTo(0xA1A2A3A4A5A6A7A8L);
            assertThrows(IndexOutOfBoundsException.class, buf::readByte);
            // Is it implementation dependent if the capacity increased by *exactly* the requested size, or more.
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void mustBeAbleToCopyAfterEnsureWritable(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(4)) {
            buf.ensureWritable(8);
            assertThat(buf.writableBytes()).isGreaterThanOrEqualTo(8);
            assertThat(buf.capacity()).isGreaterThanOrEqualTo(8);
            buf.writeLong(0x0102030405060708L);
            try (Buffer copy = buf.copy()) {
                long actual = copy.readLong();
                assertEquals(0x0102030405060708L, actual);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void ensureWritableWithCompactionMustNotAllocateIfCompactionIsEnough(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(64)) {
            while (buf.writableBytes() > 0) {
                buf.writeByte((byte) 42);
            }
            while (buf.readableBytes() > 0) {
                buf.readByte();
            }
            buf.ensureWritable(4, 4, true);
            buf.writeInt(42);
            assertThat(buf.capacity()).isEqualTo(64);

            buf.writerOffset(60).readerOffset(60);
            buf.ensureWritable(8, 8, true);
            buf.writeLong(42);
            // Don't assert the capacity on this one, because single-component
            // composite buffers may choose to allocate rather than compact.
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void ensureWritableWithLargeMinimumGrowthMustGrowByAtLeastThatMuch(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(16)) {
            buf.writeLong(0).writeInt(0);
            buf.readLong();
            buf.readInt(); // Compaction is now possible as well.
            buf.ensureWritable(8, 32, true); // We don't need to allocate.
            assertThat(buf.capacity()).isEqualTo(16);
            buf.writeByte((byte) 1);
            buf.ensureWritable(16, 32, true); // Now we DO need to allocate, because we can't compact.
            assertThat(buf.capacity()).isEqualTo(16 /* existing capacity */ + 32 /* minimum growth */);
        }
    }
}
