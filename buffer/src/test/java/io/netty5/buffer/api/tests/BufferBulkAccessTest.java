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
import io.netty5.buffer.api.internal.ResourceSupport;
import io.netty5.buffer.api.internal.Statics;
import io.netty5.util.Resource;
import io.netty5.util.internal.PlatformDependent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class BufferBulkAccessTest extends BufferTestSupport {
    @ParameterizedTest
    @MethodSource("allocators")
    void fill(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(16)) {
            assertThat(buf.fill((byte) 0xA5)).isSameAs(buf);
            buf.writerOffset(16);
            assertEquals(0xA5A5A5A5_A5A5A5A5L, buf.readLong());
            assertEquals(0xA5A5A5A5_A5A5A5A5L, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyIntoByteArray(Fixture fixture) {
        testCopyIntoByteArray(fixture);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyIntoHeapByteBuffer(Fixture fixture) {
        testCopyIntoByteBuffer(fixture, ByteBuffer::allocate);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyIntoDirectByteBuffer(Fixture fixture) {
        testCopyIntoByteBuffer(fixture, ByteBuffer::allocateDirect);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyIntoOnHeapBuf(Fixture fixture) {
        testCopyIntoBuffer(fixture, BufferAllocator.onHeapUnpooled()::allocate);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyIntoOffHeapBuf(Fixture fixture) {
        testCopyIntoBuffer(fixture, BufferAllocator.offHeapUnpooled()::allocate);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyIntoCompositeOnHeapOnHeapBuf(Fixture fixture) {
        try (var a = BufferAllocator.onHeapUnpooled();
             var b = BufferAllocator.onHeapUnpooled()) {
            testCopyIntoBuffer(fixture, size -> {
                int first = size / 2;
                int second = size - first;
                try (var bufFirst = a.allocate(first);
                     var bufSecond = b.allocate(second)) {
                    return a.compose(asList(bufFirst.send(), bufSecond.send()));
                }
            });
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyIntoCompositeOnHeapOffHeapBuf(Fixture fixture) {
        try (var a = BufferAllocator.onHeapUnpooled();
             var b = BufferAllocator.offHeapUnpooled()) {
            testCopyIntoBuffer(fixture, size -> {
                int first = size / 2;
                int second = size - first;
                try (var bufFirst = a.allocate(first);
                     var bufSecond = b.allocate(second)) {
                    return a.compose(asList(bufFirst.send(), bufSecond.send()));
                }
            });
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyIntoCompositeOffHeapOnHeapBuf(Fixture fixture) {
        try (var a = BufferAllocator.offHeapUnpooled();
             var b = BufferAllocator.onHeapUnpooled()) {
            testCopyIntoBuffer(fixture, size -> {
                int first = size / 2;
                int second = size - first;
                try (var bufFirst = a.allocate(first);
                     var bufSecond = b.allocate(second)) {
                    return a.compose(asList(bufFirst.send(), bufSecond.send()));
                }
            });
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyIntoCompositeOffHeapOffHeapBuf(Fixture fixture) {
        try (var a = BufferAllocator.offHeapUnpooled();
             var b = BufferAllocator.offHeapUnpooled()) {
            testCopyIntoBuffer(fixture, size -> {
                int first = size / 2;
                int second = size - first;
                try (var bufFirst = a.allocate(first);
                     var bufSecond = b.allocate(second)) {
                    return a.compose(asList(bufFirst.send(), bufSecond.send()));
                }
            });
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyIntoCompositeOnHeapOnHeapBufCopy(Fixture fixture) {
        try (var a = BufferAllocator.onHeapUnpooled();
             var b = BufferAllocator.onHeapUnpooled()) {
            testCopyIntoBuffer(fixture, size -> {
                int first = size / 2;
                int second = size - first;
                try (var bufFirst = a.allocate(first);
                     var bufSecond = b.allocate(second)) {
                    return a.compose(asList(bufFirst.send(), bufSecond.send())).writerOffset(size).copy();
                }
            });
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyIntoCompositeOnHeapOffHeapBufCopy(Fixture fixture) {
        try (var a = BufferAllocator.onHeapUnpooled();
             var b = BufferAllocator.offHeapUnpooled()) {
            testCopyIntoBuffer(fixture, size -> {
                int first = size / 2;
                int second = size - first;
                try (var bufFirst = a.allocate(first);
                     var bufSecond = b.allocate(second)) {
                    return a.compose(asList(bufFirst.send(), bufSecond.send())).writerOffset(size).copy();
                }
            });
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyIntoCompositeOffHeapOnHeapBufCopy(Fixture fixture) {
        try (var a = BufferAllocator.offHeapUnpooled();
             var b = BufferAllocator.onHeapUnpooled()) {
            testCopyIntoBuffer(fixture, size -> {
                int first = size / 2;
                int second = size - first;
                try (var bufFirst = a.allocate(first);
                     var bufSecond = b.allocate(second)) {
                    return a.compose(asList(bufFirst.send(), bufSecond.send())).writerOffset(size).copy();
                }
            });
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyIntoCompositeOffHeapOffHeapBufCopy(Fixture fixture) {
        try (var a = BufferAllocator.offHeapUnpooled();
             var b = BufferAllocator.offHeapUnpooled()) {
            testCopyIntoBuffer(fixture, size -> {
                int first = size / 2;
                int second = size - first;
                try (var bufFirst = a.allocate(first);
                     var bufSecond = b.allocate(second)) {
                    return a.compose(asList(bufFirst.send(), bufSecond.send())).writerOffset(size).copy();
                }
            });
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void byteIterationOfBuffers(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            checkByteIteration(buf);
            buf.resetOffsets();
            checkByteIterationOfRegion(buf);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void reverseByteIterationOfBuffers(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(0x28)) {
            checkReverseByteIteration(buf);
            buf.resetOffsets();
            checkReverseByteIterationOfRegion(buf);
        }
    }

    @ParameterizedTest
    @MethodSource("heapAllocators")
    public void heapBufferMustHaveZeroAddress(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThat(buf.countReadableComponents()).isZero();
            assertThat(buf.countWritableComponents()).isOne();
            buf.forEachWritable(0, (index, component) -> {
                assertThat(component.writableNativeAddress()).isZero();
                return true;
            });
            buf.writeInt(42);
            assertThat(buf.countReadableComponents()).isOne();
            assertThat(buf.countWritableComponents()).isOne();
            buf.forEachReadable(0, (index, component) -> {
                assertThat(component.readableNativeAddress()).isZero();
                return true;
            });
            buf.forEachWritable(0, (index, component) -> {
                assertThat(component.writableNativeAddress()).isZero();
                return true;
            });
            buf.writeInt(42);
            assertThat(buf.countReadableComponents()).isOne();
            assertThat(buf.countWritableComponents()).isZero();
            buf.forEachReadable(0, (index, component) -> {
                assertThat(component.readableNativeAddress()).isZero();
                return true;
            });
        }
    }

    @ParameterizedTest
    @MethodSource("directAllocators")
    public void directBufferMustHaveNonZeroAddress(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThat(buf.countReadableComponents()).isZero();
            assertThat(buf.countWritableComponents()).isOne();
            buf.forEachWritable(0, (index, component) -> {
                assertThat(component.writableNativeAddress()).isNotZero();
                return true;
            });
            buf.writeInt(42);
            assertThat(buf.countReadableComponents()).isOne();
            assertThat(buf.countWritableComponents()).isOne();
            buf.forEachReadable(0, (index, component) -> {
                assertThat(component.readableNativeAddress()).isNotZero();
                return true;
            });
            buf.forEachWritable(0, (index, component) -> {
                assertThat(component.writableNativeAddress()).isNotZero();
                return true;
            });
            buf.writeInt(42);
            assertThat(buf.countReadableComponents()).isOne();
            assertThat(buf.countWritableComponents()).isZero();
            buf.forEachReadable(0, (index, component) -> {
                assertThat(component.readableNativeAddress()).isNotZero();
                return true;
            });
        }
    }

    @ParameterizedTest
    @MethodSource("directAllocators")
    public void directBuffersMustAdjustReadableWritableNativeAddress(Fixture fixture) {
        assumeTrue(PlatformDependent.hasUnsafe());

        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThat(buf.writableBytes()).isEqualTo(8);
            assertThat(buf.readableBytes()).isZero();

            buf.forEachWritable(0, (index, component) -> {
                assertThat(component.writableBytes()).isEqualTo(8);
                long addr = component.writableNativeAddress();
                assertThat(addr).isNotZero();
                component.writableBuffer().putInt(0x01020304);
                component.skipWritableBytes(4);
                assertThat(component.writableBytes()).isEqualTo(4);
                assertThat(component.writableNativeAddress()).isEqualTo(addr + 4);
                return true;
            });

            assertThat(buf.writableBytes()).isEqualTo(4);
            assertThat(buf.readableBytes()).isEqualTo(4);

            buf.forEachReadable(0, (index, component) -> {
                assertThat(component.readableBytes()).isEqualTo(4);
                long addr = component.readableNativeAddress();
                assertThat(addr).isNotZero();
                assertThat(component.readableBuffer().get()).isEqualTo((byte) 0x01);
                component.skipReadableBytes(1);
                assertThat(component.readableBytes()).isEqualTo(3);
                assertThat(component.readableNativeAddress()).isEqualTo(addr + 1);
                assertThat(component.readableBuffer().get()).isEqualTo((byte) 0x02);
                component.skipReadableBytes(1);
                return true;
            });

            assertThat(buf.readableBytes()).isEqualTo(2);
            assertThat(buf.readShort()).isEqualTo((short) 0x0304);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void writeBytesMustWriteAllBytesFromByteArray(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8)) {
            buffer.writeByte((byte) 1);
            buffer.writeBytes(new byte[] {2, 3, 4, 5, 6, 7});
            assertThat(buffer.writerOffset()).isEqualTo(7);
            assertThat(buffer.readerOffset()).isZero();
            assertThat(toByteArray(buffer)).containsExactly(1, 2, 3, 4, 5, 6, 7, 0);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void writeBytesWithOffsetMustWriteAllBytesFromByteArray(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(3)) {
            buffer.writeByte((byte) 1);
            buffer.writeBytes(new byte[] {2, 3, 4, 5, 6, 7}, 1, 2);
            assertThat(buffer.writerOffset()).isEqualTo(3);
            assertThat(buffer.readerOffset()).isZero();
            assertThat(toByteArray(buffer)).containsExactly(1, 3, 4);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void readBytesWithOffsetMustWriteAllBytesIntoByteArray(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8)) {
            buffer.writeLong(0x0102030405060708L);
            byte[] array = new byte[4];
            assertThat(buffer.readByte()).isEqualTo((byte) 1);
            assertThat(buffer.readByte()).isEqualTo((byte) 2);
            buffer.readBytes(array, 1, 2);
            assertThat(buffer.writerOffset()).isEqualTo(8);
            assertThat(buffer.readerOffset()).isEqualTo(4);
            assertThat(array).containsExactly(0, 0x03, 0x04, 0);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void writeBytesMustWriteAllBytesFromHeapByteBuffer(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8)) {
            buffer.writeByte((byte) 1);
            ByteBuffer source = ByteBuffer.allocate(8).put(new byte[] {2, 3, 4, 5, 6, 7}).flip();
            buffer.writeBytes(source);
            assertThat(source.position()).isEqualTo(source.limit());
            assertThat(buffer.writerOffset()).isEqualTo(7);
            assertThat(buffer.readerOffset()).isZero();
            assertThat(readByteArray(buffer)).containsExactly(1, 2, 3, 4, 5, 6, 7);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void writeBytesMustWriteAllBytesFromDirectByteBuffer(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8)) {
            buffer.writeByte((byte) 1);
            ByteBuffer source = ByteBuffer.allocateDirect(8).put(new byte[] {2, 3, 4, 5, 6, 7}).flip();
            buffer.writeBytes(source);
            assertThat(source.position()).isEqualTo(source.limit());

            assertThat(buffer.writerOffset()).isEqualTo(7);
            assertThat(buffer.readerOffset()).isZero();
            assertThat(readByteArray(buffer)).containsExactly(1, 2, 3, 4, 5, 6, 7);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void writeBytesHeapByteBufferMustExpandCapacityIfBufferIsTooSmall(Fixture fixture) {
        // With zero offsets
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8)) {
            ByteBuffer source = ByteBuffer.wrap(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7});
            buffer.writeBytes(source);
            assertThat(buffer.capacity()).isGreaterThanOrEqualTo(source.capacity());
            assertThat(source.position()).isEqualTo(source.limit());
            assertThat(buffer.writerOffset()).isEqualTo(16);
            assertThat(buffer.readerOffset()).isZero();
            assertThat(readByteArray(buffer)).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7);
        }
        // With non-zero offsets
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8)) {
            ByteBuffer source = ByteBuffer.wrap(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7});
            buffer.writeByte((byte) -1).readByte();
            buffer.writeBytes(source);
            assertThat(buffer.capacity()).isGreaterThanOrEqualTo(source.capacity() + 1);
            assertThat(source.position()).isEqualTo(source.limit());
            assertThat(buffer.writerOffset()).isEqualTo(17);
            assertThat(buffer.readerOffset()).isOne();
            assertThat(readByteArray(buffer)).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void writeBytesHeapByteBufferMustThrowIfCannotBeExpanded(Fixture fixture) {
        // With zero offsets
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8).implicitCapacityLimit(15)) {
            ByteBuffer source = ByteBuffer.wrap(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7});
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeBytes(source));
            assertThat(buffer.capacity()).isEqualTo(8);
            assertThat(source.position()).isEqualTo(0);
            assertThat(buffer.writerOffset()).isEqualTo(0);
            assertThat(buffer.readerOffset()).isZero();
        }
        // With non-zero offsets
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8).implicitCapacityLimit(15)) {
            ByteBuffer source = ByteBuffer.wrap(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7});
            buffer.writeByte((byte) -1).readByte();
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeBytes(source));
            assertThat(buffer.capacity()).isEqualTo(8);
            assertThat(source.position()).isEqualTo(0);
            assertThat(buffer.writerOffset()).isOne();
            assertThat(buffer.readerOffset()).isOne();
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void writeBytesHeapByteBufferWithinCapacityToSharedBufferMustNotThrow(Fixture fixture) {
        // With zero offsets
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8)) {
            ByteBuffer source = ByteBuffer.wrap(new byte[] {0, 1, 2, 3, 4, 5, 6, 7});
            try (Resource<?> ignore = Statics.acquire((ResourceSupport<?, ?>) buffer)) {
                buffer.writeBytes(source);
            }
            assertThat(buffer.capacity()).isEqualTo(source.capacity());
            assertThat(source.position()).isEqualTo(source.limit());
            assertThat(buffer.writerOffset()).isEqualTo(8);
            assertThat(buffer.readerOffset()).isZero();
            assertThat(readByteArray(buffer)).containsExactly(0, 1, 2, 3, 4, 5, 6, 7);
        }
        // With non-zero offsets
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8)) {
            ByteBuffer source = ByteBuffer.wrap(new byte[] {0, 1, 2, 3, 4, 5, 6});
            buffer.writeByte((byte) -1).readByte();
            try (Resource<?> ignore = Statics.acquire((ResourceSupport<?, ?>) buffer)) {
                buffer.writeBytes(source);
            }
            assertThat(buffer.capacity()).isEqualTo(8);
            assertThat(source.position()).isEqualTo(source.limit());
            assertThat(buffer.writerOffset()).isEqualTo(8);
            assertThat(buffer.readerOffset()).isOne();
            assertThat(readByteArray(buffer)).containsExactly(0, 1, 2, 3, 4, 5, 6);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void writeBytesDirectByteBufferMustExpandCapacityIfBufferIsTooSmall(Fixture fixture) {
        // With zero offsets
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8)) {
            ByteBuffer source = ByteBuffer.allocateDirect(16).put(
                    new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7}).flip();
            buffer.writeBytes(source);
            assertThat(buffer.capacity()).isGreaterThanOrEqualTo(source.capacity());
            assertThat(source.position()).isEqualTo(source.limit());
            assertThat(buffer.writerOffset()).isEqualTo(16);
            assertThat(buffer.readerOffset()).isZero();
            assertThat(readByteArray(buffer)).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7);
        }
        // With non-zero offsets
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8)) {
            ByteBuffer source = ByteBuffer.allocateDirect(16).put(
                    new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7}).flip();
            buffer.writeByte((byte) -1).readByte();
            buffer.writeBytes(source);
            assertThat(buffer.capacity()).isGreaterThanOrEqualTo(source.capacity() + 1);
            assertThat(source.position()).isEqualTo(source.limit());
            assertThat(buffer.writerOffset()).isEqualTo(17);
            assertThat(buffer.readerOffset()).isOne();
            assertThat(readByteArray(buffer)).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void writeBytesDirectByteBufferMustThrowIfCannotBeExpanded(Fixture fixture) {
        // With zero offsets
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8).implicitCapacityLimit(15)) {
            ByteBuffer source = ByteBuffer.allocateDirect(16).put(
                    new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7}).flip();
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeBytes(source));
            assertThat(buffer.capacity()).isEqualTo(8);
            assertThat(source.position()).isEqualTo(0);
            assertThat(buffer.writerOffset()).isEqualTo(0);
            assertThat(buffer.readerOffset()).isZero();
        }
        // With non-zero offsets
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8).implicitCapacityLimit(15)) {
            ByteBuffer source = ByteBuffer.allocateDirect(16).put(
                    new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7}).flip();
            buffer.writeByte((byte) -1).readByte();
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeBytes(source));
            assertThat(buffer.capacity()).isEqualTo(8);
            assertThat(source.position()).isEqualTo(0);
            assertThat(buffer.writerOffset()).isOne();
            assertThat(buffer.readerOffset()).isOne();
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void writeBytesByteBufferWithinCapacityToSharedBufferMustNotThrow(Fixture fixture) {
        // With zero offsets
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8)) {
            ByteBuffer source = ByteBuffer.allocateDirect(8).put(new byte[] {0, 1, 2, 3, 4, 5, 6, 7}).flip();
            try (Resource<?> ignore = Statics.acquire((ResourceSupport<?, ?>) buffer)) {
                buffer.writeBytes(source);
            }
            assertThat(buffer.capacity()).isEqualTo(8);
            assertThat(source.position()).isEqualTo(source.limit());
            assertThat(buffer.writerOffset()).isEqualTo(8);
            assertThat(buffer.readerOffset()).isZero();
            assertThat(readByteArray(buffer)).containsExactly(0, 1, 2, 3, 4, 5, 6, 7);
        }
        // With non-zero offsets
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8)) {
            ByteBuffer source = ByteBuffer.allocateDirect(7).put(new byte[] {0, 1, 2, 3, 4, 5, 6}).flip();
            buffer.writeByte((byte) -1).readByte();
            try (Resource<?> ignore = Statics.acquire((ResourceSupport<?, ?>) buffer)) {
                buffer.writeBytes(source);
            }
            assertThat(buffer.capacity()).isEqualTo(8);
            assertThat(source.position()).isEqualTo(source.limit());
            assertThat(buffer.writerOffset()).isEqualTo(8);
            assertThat(buffer.readerOffset()).isOne();
            assertThat(readByteArray(buffer)).containsExactly(0, 1, 2, 3, 4, 5, 6);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void writeBytesByteArrayMustExpandCapacityIfTooSmall(Fixture fixture) {
        // Starting at offsets zero.
        byte[] expected = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8)) {
            assertThat(expected.length).isEqualTo(16);
            buffer.writeBytes(expected);
            assertThat(buffer.capacity()).isGreaterThanOrEqualTo(expected.length);
            byte[] actual = new byte[expected.length];
            buffer.readBytes(actual, 0, actual.length);
            assertThat(actual).containsExactly(expected);
        }

        // With non-zero start offsets.
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8)) {
            buffer.writeByte((byte) 1).readByte();
            assertThat(expected.length).isEqualTo(16);
            buffer.writeBytes(expected);
            assertThat(buffer.capacity()).isGreaterThanOrEqualTo(expected.length + 1);
            byte[] actual = new byte[expected.length];
            buffer.readBytes(actual, 0, actual.length);
            assertThat(actual).containsExactly(expected);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void writeBytesByteArrayMustThrowIfCannotBeExpanded(Fixture fixture) {
        // Starting at offsets zero.
        byte[] expected = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8).implicitCapacityLimit(15)) {
            assertThat(expected.length).isEqualTo(16);
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeBytes(expected));
            assertThat(buffer.capacity()).isEqualTo(8);
            assertThat(buffer.readableBytes()).isZero();
        }

        // With non-zero start offsets.
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8).implicitCapacityLimit(15)) {
            buffer.writeByte((byte) 1).readByte();
            assertThat(expected.length).isEqualTo(16);
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeBytes(expected));
            assertThat(buffer.capacity()).isEqualTo(8);
            assertThat(buffer.readableBytes()).isZero();
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void writeBytesByteArrayWithinCapacityToSharedBufferMustNotThrow(Fixture fixture) {
        // With zero offsets
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8)) {
            byte[] expected = {0, 1, 2, 3, 4, 5, 6, 7};
            try (Resource<?> ignore = Statics.acquire((ResourceSupport<?, ?>) buffer)) {
                buffer.writeBytes(expected);
            }
            assertThat(buffer.capacity()).isEqualTo(8);
            assertThat(buffer.writerOffset()).isEqualTo(8);
            assertThat(buffer.readerOffset()).isZero();
            assertThat(readByteArray(buffer)).containsExactly(0, 1, 2, 3, 4, 5, 6, 7);
        }
        // With non-zero offsets
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8)) {
            buffer.writeByte((byte) -1).readByte();
            byte[] expected = {0, 1, 2, 3, 4, 5, 6};
            try (Resource<?> ignore = Statics.acquire((ResourceSupport<?, ?>) buffer)) {
                buffer.writeBytes(expected);
            }
            assertThat(buffer.capacity()).isEqualTo(8);
            assertThat(buffer.writerOffset()).isEqualTo(8);
            assertThat(buffer.readerOffset()).isOne();
            assertThat(readByteArray(buffer)).containsExactly(0, 1, 2, 3, 4, 5, 6);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void writeBytesByteArrayWithOffsetMustExpandCapacityIfTooSmall(Fixture fixture) {
        // Starting at offsets zero.
        byte[] expected = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8)) {
            assertThat(expected.length).isEqualTo(16);
            buffer.writeBytes(expected, 1, expected.length - 1);
            assertThat(buffer.capacity()).isGreaterThanOrEqualTo(expected.length - 1);
            byte[] actual = new byte[expected.length - 1];
            buffer.readBytes(actual, 0, actual.length);
            assertThat(actual).containsExactly("123456789ABCDEF".getBytes(StandardCharsets.US_ASCII));
        }

        // With non-zero start offsets.
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8)) {
            buffer.writeByte((byte) 1).readByte();
            assertThat(expected.length).isEqualTo(16);
            buffer.writeBytes(expected, 1, expected.length - 1);
            assertThat(buffer.capacity()).isGreaterThanOrEqualTo(expected.length);
            byte[] actual = new byte[expected.length - 1];
            buffer.readBytes(actual, 0, actual.length);
            assertThat(actual).containsExactly("123456789ABCDEF".getBytes(StandardCharsets.US_ASCII));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void writeBytesByteArrayWithOffsetMustThrowIfCannotBeExpanded(Fixture fixture) {
        // Starting at offsets zero.
        byte[] expected = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8).implicitCapacityLimit(14)) {
            assertThat(expected.length).isEqualTo(16);
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeBytes(expected, 1, expected.length - 1));
            assertThat(buffer.capacity()).isEqualTo(8);
            assertThat(buffer.readableBytes()).isZero();
        }

        // With non-zero start offsets.
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8).implicitCapacityLimit(14)) {
            buffer.writeByte((byte) 1).readByte();
            assertThat(expected.length).isEqualTo(16);
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeBytes(expected, 1, expected.length - 1));
            assertThat(buffer.capacity()).isEqualTo(8);
            assertThat(buffer.readableBytes()).isZero();
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void writeBytesByteArrayWithOffsetsWithinCapacityToSharedBufferMustNotThrow(Fixture fixture) {
        // With zero offsets
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8)) {
            byte[] expected = {0, 1, 2, 3, 4, 5, 6, 7};
            try (Resource<?> ignore = Statics.acquire((ResourceSupport<?, ?>) buffer)) {
                buffer.writeBytes(expected, 1, expected.length - 1);
            }
            assertThat(buffer.capacity()).isEqualTo(8);
            assertThat(buffer.writerOffset()).isEqualTo(7);
            assertThat(buffer.readerOffset()).isZero();
            assertThat(readByteArray(buffer)).containsExactly(1, 2, 3, 4, 5, 6, 7);
        }
        // With non-zero offsets
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8)) {
            buffer.writeByte((byte) -1).readByte();
            byte[] expected = {0, 1, 2, 3, 4, 5, 6};
            try (Resource<?> ignore = Statics.acquire((ResourceSupport<?, ?>) buffer)) {
                buffer.writeBytes(expected, 1, expected.length - 1);
            }
            assertThat(buffer.capacity()).isEqualTo(8);
            assertThat(buffer.writerOffset()).isEqualTo(7);
            assertThat(buffer.readerOffset()).isOne();
            assertThat(readByteArray(buffer)).containsExactly(1, 2, 3, 4, 5, 6);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void writeBytesBufferMustExpandCapacityIfTooSmall(Fixture fixture) {
        // Starting at offsets zero.
        byte[] expectedByteArray = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8);
             Buffer expected = allocator.copyOf(expectedByteArray)) {
            assertThat(expected.readableBytes()).isEqualTo(16);
            buffer.writeBytes(expected);
            assertThat(buffer.capacity()).isGreaterThanOrEqualTo(expectedByteArray.length);
            byte[] actual = new byte[expectedByteArray.length];
            buffer.readBytes(actual, 0, actual.length);
            assertThat(actual).containsExactly(expectedByteArray);
        }

        // With non-zero start offsets.
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8);
             Buffer expected = allocator.copyOf(expectedByteArray)) {
            buffer.writeByte((byte) 1).readByte();
            assertThat(expected.readableBytes()).isEqualTo(16);
            buffer.writeBytes(expected);
            assertThat(buffer.capacity()).isGreaterThanOrEqualTo(expectedByteArray.length + 1);
            byte[] actual = new byte[expectedByteArray.length];
            buffer.readBytes(actual, 0, actual.length);
            assertThat(actual).containsExactly(expectedByteArray);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void writeBytesBufferMustThrowIfCannotBeExpanded(Fixture fixture) {
        // Starting at offsets zero.
        byte[] expectedByteArray = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8).implicitCapacityLimit(15);
             Buffer expected = allocator.copyOf(expectedByteArray)) {
            assertThat(expected.readableBytes()).isEqualTo(16);
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeBytes(expected));
            assertThat(buffer.capacity()).isEqualTo(8);
            assertThat(buffer.readableBytes()).isZero();
        }

        // With non-zero start offsets.
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8).implicitCapacityLimit(15);
             Buffer expected = allocator.copyOf(expectedByteArray)) {
            buffer.writeByte((byte) 1).readByte();
            assertThat(expected.readableBytes()).isEqualTo(16);
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.writeBytes(expected));
            assertThat(buffer.capacity()).isEqualTo(8);
            assertThat(buffer.readableBytes()).isZero();
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void writeBytesBufferWithinCapacityToSharedBufferMustNotThrow(Fixture fixture) {
        // With zero offsets
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8)) {
            byte[] expectedByteArray = {0, 1, 2, 3, 4, 5, 6, 7};
            try (Resource<?> ignore = Statics.acquire((ResourceSupport<?, ?>) buffer);
                 Buffer expected = allocator.copyOf(expectedByteArray)) {
                buffer.writeBytes(expected);
            }
            assertThat(buffer.capacity()).isEqualTo(8);
            assertThat(buffer.writerOffset()).isEqualTo(8);
            assertThat(buffer.readerOffset()).isZero();
            assertThat(readByteArray(buffer)).containsExactly(0, 1, 2, 3, 4, 5, 6, 7);
        }
        // With non-zero offsets
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8)) {
            buffer.writeByte((byte) -1).readByte();
            byte[] expectedByteArray = {0, 1, 2, 3, 4, 5, 6};
            try (Resource<?> ignore = Statics.acquire((ResourceSupport<?, ?>) buffer);
                 Buffer expected = allocator.copyOf(expectedByteArray)) {
                buffer.writeBytes(expected);
            }
            assertThat(buffer.capacity()).isEqualTo(8);
            assertThat(buffer.writerOffset()).isEqualTo(8);
            assertThat(buffer.readerOffset()).isOne();
            assertThat(readByteArray(buffer)).containsExactly(0, 1, 2, 3, 4, 5, 6);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void readBytesIntoHeapByteBuffer(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8)) {
            buffer.writeLong(0x0102030405060708L);
            ByteBuffer dest = ByteBuffer.allocate(4);
            assertThat(buffer.readByte()).isEqualTo((byte) 1);
            assertThat(buffer.readByte()).isEqualTo((byte) 2);
            buffer.readBytes(dest);
            assertThat(dest.position()).isEqualTo(dest.limit());
            assertThat(buffer.writerOffset()).isEqualTo(8);
            assertThat(buffer.readerOffset()).isEqualTo(6);
            assertThat(toByteArray(dest.flip())).containsExactly(0x03, 0x04, 0x05, 0x06);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void readBytesIntoDirectByteBuffer(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8)) {
            buffer.writeLong(0x0102030405060708L);
            ByteBuffer dest = ByteBuffer.allocateDirect(4);
            assertThat(buffer.readByte()).isEqualTo((byte) 1);
            assertThat(buffer.readByte()).isEqualTo((byte) 2);
            buffer.readBytes(dest);
            assertThat(dest.position()).isEqualTo(dest.limit());
            assertThat(buffer.writerOffset()).isEqualTo(8);
            assertThat(buffer.readerOffset()).isEqualTo(6);
            assertThat(toByteArray(dest.flip())).containsExactly(0x03, 0x04, 0x05, 0x06);
        }
    }

    @ParameterizedTest
    @MethodSource("directAllocators")
    public void offHeapBuffersMustBeDirect(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertTrue(buf.isDirect());
        }
    }

    @ParameterizedTest
    @MethodSource("heapAllocators")
    public void onHeapBuffersMustNotBeDirect(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertFalse(buf.isDirect());
        }
    }
}
