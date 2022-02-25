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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BufferOffsetsTest extends BufferTestSupport {

    @ParameterizedTest
    @MethodSource("initialCombinations")
    void mustThrowWhenAllocatingNegativeSizedBuffer(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            assertThrows(IllegalArgumentException.class, () -> allocator.allocate(-1));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void setReaderOffsetMustThrowOnNegativeIndex(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.readerOffset(-1));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void setReaderOffsetMustThrowOnOversizedIndex(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.readerOffset(1));
            buf.writeLong(0);
            assertThrows(IndexOutOfBoundsException.class, () -> buf.readerOffset(9));

            buf.readerOffset(8);
            assertThrows(IndexOutOfBoundsException.class, buf::readByte);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void setWriterOffsetMustThrowOutsideOfWritableRegion(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            // Writer offset cannot be negative.
            assertThrows(IndexOutOfBoundsException.class, () -> buf.writerOffset(-1));

            buf.writerOffset(4);
            buf.readerOffset(4);

            // Cannot set writer offset before reader offset.
            assertThrows(IndexOutOfBoundsException.class, () -> buf.writerOffset(3));
            assertThrows(IndexOutOfBoundsException.class, () -> buf.writerOffset(0));

            buf.writerOffset(buf.capacity());

            // Cannot set writer offset beyond capacity.
            assertThrows(IndexOutOfBoundsException.class, () -> buf.writerOffset(buf.capacity() + 1));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void setReaderOffsetMustNotThrowWithinBounds(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThat(buf.readerOffset(0)).isSameAs(buf);
            buf.writeLong(0);
            assertThat(buf.readerOffset(7)).isSameAs(buf);
            assertThat(buf.readerOffset(8)).isSameAs(buf);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void capacityMustBeAllocatedSize(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertEquals(8, buf.capacity());
            try (Buffer b = allocator.allocate(13)) {
                assertEquals(13, b.capacity());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void readerWriterOffsetUpdates(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(22)) {
            assertEquals(0, buf.writerOffset());
            assertThat(buf.writerOffset(1)).isSameAs(buf);
            assertEquals(1, buf.writerOffset());
            assertThat(buf.writeByte((byte) 7)).isSameAs(buf);
            assertEquals(2, buf.writerOffset());
            assertThat(buf.writeShort((short) 3003)).isSameAs(buf);
            assertEquals(4, buf.writerOffset());
            assertThat(buf.writeInt(0x5A55_BA55)).isSameAs(buf);
            assertEquals(8, buf.writerOffset());
            assertThat(buf.writeLong(0x123456789ABCDEF0L)).isSameAs(buf);
            assertEquals(16, buf.writerOffset());
            assertEquals(6, buf.writableBytes());
            assertEquals(16, buf.readableBytes());

            assertEquals(0, buf.readerOffset());
            assertThat(buf.readerOffset(1)).isSameAs(buf);
            assertEquals(1, buf.readerOffset());
            assertEquals((byte) 7, buf.readByte());
            assertEquals(2, buf.readerOffset());
            assertEquals((short) 3003, buf.readShort());
            assertEquals(4, buf.readerOffset());
            assertEquals(0x5A55_BA55, buf.readInt());
            assertEquals(8, buf.readerOffset());
            assertEquals(0x123456789ABCDEF0L, buf.readLong());
            assertEquals(16, buf.readerOffset());
            assertEquals(0, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void readAndWriteBoundsChecksWithIndexUpdates(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.writeLong(0);

            buf.readLong(); // Fine.
            buf.readerOffset(1);
            assertThrows(IndexOutOfBoundsException.class, buf::readLong);

            buf.readerOffset(4);
            buf.readInt(); // Fine.
            buf.readerOffset(5);

            assertThrows(IndexOutOfBoundsException.class, buf::readInt);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void resetMustSetReaderAndWriterOffsetsToTheirInitialPositions(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.writeInt(0).readShort();
            buf.resetOffsets();
            assertEquals(0, buf.readerOffset());
            assertEquals(0, buf.writerOffset());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void readableBytesMustMatchWhatWasWritten(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(16)) {
            buf.writeLong(0);
            assertEquals(Long.BYTES, buf.readableBytes());
            buf.readShort();
            assertEquals(Long.BYTES - Short.BYTES, buf.readableBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void skipReadable(Fixture fixture) {
        skipReadable(fixture, 32, 16, 8, 8);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void skipReadableNegative(Fixture fixture) {
        skipReadable(fixture, 16, 8, 4, -2);
    }

    private void skipReadable(Fixture fixture, int capacity, int writeBytes, int readBytes, int offset) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(capacity)) {
            writeRandomBytes(buf, writeBytes);

            for (int i = 0; i < readBytes; i++) {
                buf.readByte();
            }

            buf.skipReadable(offset);
            assertEquals(readBytes + offset, buf.readerOffset());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void skipWritable(Fixture fixture) {
        skipWritable(fixture, 32, 16, 8);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void skipWritableNegative(Fixture fixture) {
        skipWritable(fixture, 16, 8, -2);
    }

    private void skipWritable(Fixture fixture, int capacity, int writeBytes, int offset) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(capacity)) {
            writeRandomBytes(buf, writeBytes);

            buf.skipWritable(offset);
            assertEquals(writeBytes + offset, buf.writerOffset());
        }
    }
}
