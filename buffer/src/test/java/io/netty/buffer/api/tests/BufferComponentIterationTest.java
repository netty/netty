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
package io.netty.buffer.api.tests;

import io.netty.buffer.api.Buffer;
import io.netty.buffer.api.BufferAllocator;
import io.netty.buffer.api.BufferClosedException;
import io.netty.buffer.api.BufferReadOnlyException;
import io.netty.buffer.api.ByteCursor;
import io.netty.buffer.api.CompositeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BufferComponentIterationTest extends BufferTestSupport {
    @ParameterizedTest
    @MethodSource("nonCompositeAllocators")
    public void componentCountOfNonCompositeBufferMustBeOne(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThat(buf.countComponents()).isOne();
        }
    }

    @ParameterizedTest
    @MethodSource("nonCompositeAllocators")
    public void readableComponentCountMustBeOneIfThereAreReadableBytes(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThat(buf.countReadableComponents()).isZero();
            buf.writeByte((byte) 1);
            assertThat(buf.countReadableComponents()).isOne();
        }
    }

    @ParameterizedTest
    @MethodSource("nonCompositeAllocators")
    public void writableComponentCountMustBeOneIfThereAreWritableBytes(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThat(buf.countWritableComponents()).isOne();
            buf.writeLong(1);
            assertThat(buf.countWritableComponents()).isZero();
        }
    }

    @Test
    public void compositeBufferComponentCountMustBeTransitiveSum() {
        try (BufferAllocator allocator = BufferAllocator.heap()) {
            Buffer buf;
            try (Buffer a = allocator.allocate(8);
                 Buffer b = allocator.allocate(8);
                 Buffer c = allocator.allocate(8);
                 Buffer x = CompositeBuffer.compose(allocator, b.send(), c.send())) {
                buf = CompositeBuffer.compose(allocator, a.send(), x.send());
            }
            assertThat(buf.countComponents()).isEqualTo(3);
            assertThat(buf.countReadableComponents()).isZero();
            assertThat(buf.countWritableComponents()).isEqualTo(3);
            buf.writeInt(1);
            assertThat(buf.countReadableComponents()).isOne();
            assertThat(buf.countWritableComponents()).isEqualTo(3);
            buf.writeInt(1);
            assertThat(buf.countReadableComponents()).isOne();
            assertThat(buf.countWritableComponents()).isEqualTo(2);
            buf.writeInt(1);
            assertThat(buf.countReadableComponents()).isEqualTo(2);
            assertThat(buf.countWritableComponents()).isEqualTo(2);
            buf.writeInt(1);
            assertThat(buf.countReadableComponents()).isEqualTo(2);
            assertThat(buf.countWritableComponents()).isOne();
            buf.writeInt(1);
            assertThat(buf.countReadableComponents()).isEqualTo(3);
            assertThat(buf.countWritableComponents()).isOne();
            buf.writeInt(1);
            assertThat(buf.countReadableComponents()).isEqualTo(3);
            assertThat(buf.countWritableComponents()).isZero();
        }
    }

    @ParameterizedTest
    @MethodSource("nonCompositeAllocators")
    public void forEachReadableMustVisitBuffer(Fixture fixture) {
        long value = 0x0102030405060708L;
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer bufBERW = allocator.allocate(8).order(BIG_ENDIAN).writeLong(value);
             Buffer bufLERW = allocator.allocate(8).order(LITTLE_ENDIAN).writeLong(value);
             Buffer bufBERO = allocator.allocate(8).order(BIG_ENDIAN).writeLong(value).makeReadOnly();
             Buffer bufLERO = allocator.allocate(8).order(LITTLE_ENDIAN).writeLong(value).makeReadOnly()) {
            verifyForEachReadableSingleComponent(fixture, bufBERW);
            verifyForEachReadableSingleComponent(fixture, bufLERW);
            verifyForEachReadableSingleComponent(fixture, bufBERO);
            verifyForEachReadableSingleComponent(fixture, bufLERO);
        }
    }

    @Test
    public void forEachReadableMustVisitAllReadableConstituentBuffersInOrder() {
        try (BufferAllocator allocator = BufferAllocator.heap()) {
            Buffer composite;
            try (Buffer a = allocator.allocate(4);
                 Buffer b = allocator.allocate(4);
                 Buffer c = allocator.allocate(4)) {
                a.writeInt(1);
                b.writeInt(2);
                c.writeInt(3);
                composite = CompositeBuffer.compose(allocator, a.send(), b.send(), c.send());
            }
            var list = new LinkedList<Integer>(List.of(1, 2, 3));
            int count = composite.forEachReadable(0, (index, component) -> {
                var buffer = component.readableBuffer();
                int bufferValue = buffer.getInt();
                assertEquals(list.pollFirst().intValue(), bufferValue);
                assertEquals(bufferValue, index + 1);
                assertThrows(ReadOnlyBufferException.class, () -> buffer.put(0, (byte) 0xFF));
                return true;
            });
            assertEquals(3, count);
            assertThat(list).isEmpty();
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void forEachReadableMustReturnNegativeCountWhenProcessorReturnsFalse(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.writeLong(0x0102030405060708L);
            int count = buf.forEachReadable(0, (index, component) -> false);
            assertEquals(-1, count);
        }
    }

    @Test
    public void forEachReadableMustStopIterationWhenProcessorReturnsFalse() {
        try (BufferAllocator allocator = BufferAllocator.heap()) {
            Buffer composite;
            try (Buffer a = allocator.allocate(4);
                 Buffer b = allocator.allocate(4);
                 Buffer c = allocator.allocate(4)) {
                a.writeInt(1);
                b.writeInt(2);
                c.writeInt(3);
                composite = CompositeBuffer.compose(allocator, a.send(), b.send(), c.send());
            }
            int readPos = composite.readerOffset();
            int writePos = composite.writerOffset();
            var list = new LinkedList<Integer>(List.of(1, 2, 3));
            int count = composite.forEachReadable(0, (index, component) -> {
                var buffer = component.readableBuffer();
                int bufferValue = buffer.getInt();
                assertEquals(list.pollFirst().intValue(), bufferValue);
                assertEquals(bufferValue, index + 1);
                return false;
            });
            assertEquals(-1, count);
            assertThat(list).containsExactly(2, 3);
            assertEquals(readPos, composite.readerOffset());
            assertEquals(writePos, composite.writerOffset());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void forEachReadableOnClosedBufferMustThrow(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            var buf = allocator.allocate(8);
            buf.writeLong(0);
            buf.close();
            assertThrows(BufferClosedException.class, () -> buf.forEachReadable(0, (component, index) -> true));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void forEachReadableMustAllowCollectingBuffersInArray(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            Buffer buf;
            try (Buffer a = allocator.allocate(4);
                 Buffer b = allocator.allocate(4);
                 Buffer c = allocator.allocate(4)) {
                buf = CompositeBuffer.compose(allocator, a.send(), b.send(), c.send());
            }
            int i = 1;
            while (buf.writableBytes() > 0) {
                buf.writeByte((byte) i++);
            }
            ByteBuffer[] buffers = new ByteBuffer[buf.countReadableComponents()];
            buf.forEachReadable(0, (index, component) -> {
                buffers[index] = component.readableBuffer();
                return true;
            });
            i = 1;
            assertThat(buffers.length).isGreaterThanOrEqualTo(1);
            for (ByteBuffer buffer : buffers) {
                while (buffer.hasRemaining()) {
                    assertEquals((byte) i++, buffer.get());
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void forEachReadableMustExposeByteCursors(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(32).order(BIG_ENDIAN)) {
            buf.writeLong(0x0102030405060708L);
            buf.writeLong(0x1112131415161718L);
            assertEquals(0x01020304, buf.readInt());
            try (Buffer actualData = allocator.allocate(buf.readableBytes()).order(BIG_ENDIAN);
                 Buffer expectedData = allocator.allocate(12).order(BIG_ENDIAN)) {
                expectedData.writeInt(0x05060708);
                expectedData.writeInt(0x11121314);
                expectedData.writeInt(0x15161718);

                buf.forEachReadable(0, (i, component) -> {
                    ByteCursor forward = component.openCursor();
                    while (forward.readByte()) {
                        actualData.writeByte(forward.getByte());
                    }
                    return true;
                });

                assertEquals(expectedData.readableBytes(), actualData.readableBytes());
                while (expectedData.readableBytes() > 0) {
                    assertEquals(expectedData.readByte(), actualData.readByte());
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("nonCompositeAllocators")
    public void forEachWritableMustVisitBuffer(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer bufBERW = allocator.allocate(8).order(BIG_ENDIAN);
             Buffer bufLERW = allocator.allocate(8).order(LITTLE_ENDIAN)) {
            verifyForEachWritableSingleComponent(fixture, bufBERW);
            verifyForEachWritableSingleComponent(fixture, bufLERW);
        }
    }

    @Test
    public void forEachWritableMustVisitAllWritableConstituentBuffersInOrder() {
        try (BufferAllocator allocator = BufferAllocator.heap()) {
            Buffer buf;
            try (Buffer a = allocator.allocate(8);
                 Buffer b = allocator.allocate(8);
                 Buffer c = allocator.allocate(8)) {
                buf = CompositeBuffer.compose(allocator, a.send(), b.send(), c.send());
            }
            buf.order(BIG_ENDIAN);
            buf.forEachWritable(0, (index, component) -> {
                component.writableBuffer().putLong(0x0102030405060708L + 0x1010101010101010L * index);
                return true;
            });
            buf.writerOffset(3 * 8);
            assertEquals(0x0102030405060708L, buf.readLong());
            assertEquals(0x1112131415161718L, buf.readLong());
            assertEquals(0x2122232425262728L, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void forEachWritableMustReturnNegativeCountWhenProcessorReturnsFalse(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            int count = buf.forEachWritable(0, (index, component) -> false);
            assertEquals(-1, count);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void forEachWritableMustStopIterationWhenProcessorRetursFalse(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            AtomicInteger counter = new AtomicInteger();
            buf.forEachWritable(0, (index, component) -> {
                counter.incrementAndGet();
                return false;
            });
            assertEquals(1, counter.get());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void forEachWritableChangesMadeToByteBufferComponentMustBeReflectedInBuffer(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(9).order(BIG_ENDIAN)) {
            buf.writeByte((byte) 0xFF);
            AtomicInteger writtenCounter = new AtomicInteger();
            buf.forEachWritable(0, (index, component) -> {
                var buffer = component.writableBuffer();
                while (buffer.hasRemaining()) {
                    buffer.put((byte) writtenCounter.incrementAndGet());
                }
                return true;
            });
            buf.writerOffset(9);
            assertEquals((byte) 0xFF, buf.readByte());
            assertEquals(0x0102030405060708L, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void changesMadeToByteBufferComponentsShouldBeReflectedInBuffer(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            AtomicInteger counter = new AtomicInteger();
            buf.forEachWritable(0, (index, component) -> {
                var buffer = component.writableBuffer();
                while (buffer.hasRemaining()) {
                    buffer.put((byte) counter.incrementAndGet());
                }
                return true;
            });
            buf.writerOffset(buf.capacity());
            for (int i = 0; i < 8; i++) {
                assertEquals((byte) i + 1, buf.getByte(i));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void forEachWritableOnClosedBufferMustThrow(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            Buffer buf = allocator.allocate(8);
            buf.close();
            assertThrows(BufferClosedException.class, () -> buf.forEachWritable(0, (index, component) -> true));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void forEachWritableOnReadOnlyBufferMustThrow(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8).makeReadOnly()) {
            assertThrows(BufferReadOnlyException.class, () -> buf.forEachWritable(0, (index, component) -> true));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void forEachWritableMustAllowCollectingBuffersInArray(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            ByteBuffer[] buffers = new ByteBuffer[buf.countWritableComponents()];
            buf.forEachWritable(0, (index, component) -> {
                buffers[index] = component.writableBuffer();
                return true;
            });
            assertThat(buffers.length).isGreaterThanOrEqualTo(1);
            int i = 1;
            for (ByteBuffer buffer : buffers) {
                while (buffer.hasRemaining()) {
                    buffer.put((byte) i++);
                }
            }
            buf.writerOffset(buf.capacity());
            i = 1;
            while (buf.readableBytes() > 0) {
                assertEquals((byte) i++, buf.readByte());
            }
        }
    }
}
