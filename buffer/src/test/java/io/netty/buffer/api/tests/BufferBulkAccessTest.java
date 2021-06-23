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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;

import static io.netty.buffer.api.CompositeBuffer.compose;
import static org.assertj.core.api.Assertions.assertThat;

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
        testCopyIntoBuf(fixture, BufferAllocator.heap()::allocate);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyIntoOffHeapBuf(Fixture fixture) {
        testCopyIntoBuf(fixture, BufferAllocator.direct()::allocate);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyIntoCompositeOnHeapOnHeapBuf(Fixture fixture) {
        try (var a = BufferAllocator.heap();
             var b = BufferAllocator.heap()) {
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
        try (var a = BufferAllocator.heap();
             var b = BufferAllocator.direct()) {
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
        try (var a = BufferAllocator.direct();
             var b = BufferAllocator.heap()) {
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
        try (var a = BufferAllocator.direct();
             var b = BufferAllocator.direct()) {
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
        try (var a = BufferAllocator.heap();
             var b = BufferAllocator.heap()) {
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
        try (var a = BufferAllocator.heap();
             var b = BufferAllocator.direct()) {
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
        try (var a = BufferAllocator.direct();
             var b = BufferAllocator.heap()) {
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
        try (var a = BufferAllocator.direct();
             var b = BufferAllocator.direct()) {
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
            assertThat(buf.nativeAddress()).isZero();
        }
    }

    @ParameterizedTest
    @MethodSource("directAllocators")
    public void directBufferMustHaveNonZeroAddress(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThat(buf.nativeAddress()).isNotZero();
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
}
