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
import io.netty5.util.internal.PlatformDependent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;

import static io.netty5.buffer.api.CompositeBuffer.compose;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.writeLong(0x0102030405060708L);
            byte[] array = new byte[8];
            buf.copyInto(0, array, 0, array.length);
            assertThat(array).containsExactly(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08);

            array = new byte[6];
            buf.copyInto(1, array, 1, 3);
            assertThat(array).containsExactly(0x00, 0x02, 0x03, 0x04, 0x00, 0x00);
        }
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
        testCopyIntoBuf(fixture, BufferAllocator.onHeapUnpooled()::allocate);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyIntoOffHeapBuf(Fixture fixture) {
        testCopyIntoBuf(fixture, BufferAllocator.offHeapUnpooled()::allocate);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyIntoCompositeOnHeapOnHeapBuf(Fixture fixture) {
        try (var a = BufferAllocator.onHeapUnpooled();
             var b = BufferAllocator.onHeapUnpooled()) {
            testCopyIntoBuf(fixture, size -> {
                int first = size / 2;
                int second = size - first;
                try (var bufFirst = a.allocate(first);
                     var bufSecond = b.allocate(second)) {
                    return compose(a, bufFirst.send(), bufSecond.send());
                }
            });
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyIntoCompositeOnHeapOffHeapBuf(Fixture fixture) {
        try (var a = BufferAllocator.onHeapUnpooled();
             var b = BufferAllocator.offHeapUnpooled()) {
            testCopyIntoBuf(fixture, size -> {
                int first = size / 2;
                int second = size - first;
                try (var bufFirst = a.allocate(first);
                     var bufSecond = b.allocate(second)) {
                    return compose(a, bufFirst.send(), bufSecond.send());
                }
            });
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyIntoCompositeOffHeapOnHeapBuf(Fixture fixture) {
        try (var a = BufferAllocator.offHeapUnpooled();
             var b = BufferAllocator.onHeapUnpooled()) {
            testCopyIntoBuf(fixture, size -> {
                int first = size / 2;
                int second = size - first;
                try (var bufFirst = a.allocate(first);
                     var bufSecond = b.allocate(second)) {
                    return compose(a, bufFirst.send(), bufSecond.send());
                }
            });
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyIntoCompositeOffHeapOffHeapBuf(Fixture fixture) {
        try (var a = BufferAllocator.offHeapUnpooled();
             var b = BufferAllocator.offHeapUnpooled()) {
            testCopyIntoBuf(fixture, size -> {
                int first = size / 2;
                int second = size - first;
                try (var bufFirst = a.allocate(first);
                     var bufSecond = b.allocate(second)) {
                    return compose(a, bufFirst.send(), bufSecond.send());
                }
            });
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyIntoCompositeOnHeapOnHeapBufCopy(Fixture fixture) {
        try (var a = BufferAllocator.onHeapUnpooled();
             var b = BufferAllocator.onHeapUnpooled()) {
            testCopyIntoBuf(fixture, size -> {
                int first = size / 2;
                int second = size - first;
                try (var bufFirst = a.allocate(first);
                     var bufSecond = b.allocate(second)) {
                    return compose(a, bufFirst.send(), bufSecond.send()).writerOffset(size).copy();
                }
            });
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyIntoCompositeOnHeapOffHeapBufCopy(Fixture fixture) {
        try (var a = BufferAllocator.onHeapUnpooled();
             var b = BufferAllocator.offHeapUnpooled()) {
            testCopyIntoBuf(fixture, size -> {
                int first = size / 2;
                int second = size - first;
                try (var bufFirst = a.allocate(first);
                     var bufSecond = b.allocate(second)) {
                    return compose(a, bufFirst.send(), bufSecond.send()).writerOffset(size).copy();
                }
            });
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyIntoCompositeOffHeapOnHeapBufCopy(Fixture fixture) {
        try (var a = BufferAllocator.offHeapUnpooled();
             var b = BufferAllocator.onHeapUnpooled()) {
            testCopyIntoBuf(fixture, size -> {
                int first = size / 2;
                int second = size - first;
                try (var bufFirst = a.allocate(first);
                     var bufSecond = b.allocate(second)) {
                    return compose(a, bufFirst.send(), bufSecond.send()).writerOffset(size).copy();
                }
            });
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyIntoCompositeOffHeapOffHeapBufCopy(Fixture fixture) {
        try (var a = BufferAllocator.offHeapUnpooled();
             var b = BufferAllocator.offHeapUnpooled()) {
            testCopyIntoBuf(fixture, size -> {
                int first = size / 2;
                int second = size - first;
                try (var bufFirst = a.allocate(first);
                     var bufSecond = b.allocate(second)) {
                    return compose(a, bufFirst.send(), bufSecond.send()).writerOffset(size).copy();
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
                component.skipWritable(4);
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
                component.skipReadable(1);
                assertThat(component.readableBytes()).isEqualTo(3);
                assertThat(component.readableNativeAddress()).isEqualTo(addr + 1);
                assertThat(component.readableBuffer().get()).isEqualTo((byte) 0x02);
                component.skipReadable(1);
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
