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
import io.netty5.buffer.api.BufferClosedException;
import io.netty5.buffer.api.CompositeBuffer;
import io.netty5.buffer.api.internal.ResourceSupport;
import io.netty5.util.internal.EmptyArrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import static io.netty5.buffer.api.internal.Statics.acquire;
import static io.netty5.buffer.api.internal.Statics.isOwned;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BufferLifeCycleTest extends BufferTestSupport {
    @ParameterizedTest
    @MethodSource("allocators")
    void allocateAndAccessingBuffer(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.writeByte((byte) 1);
            buf.writeByte((byte) 2);
            try (Buffer inner = acquire((ResourceSupport<?, ?>) buf)) {
                inner.writeByte((byte) 3);
                inner.writeByte((byte) 4);
                inner.writeByte((byte) 5);
                inner.writeByte((byte) 6);
                inner.writeByte((byte) 7);
                inner.writeByte((byte) 8);
                assertThrows(IndexOutOfBoundsException.class, () -> inner.writeByte((byte) 9));
                assertThrows(IndexOutOfBoundsException.class, () -> inner.writeByte((byte) 9));
                assertThrows(IndexOutOfBoundsException.class, () -> buf.writeByte((byte) 9));
            }
            assertEquals((byte) 1, buf.readByte());
            assertEquals((byte) 2, buf.readByte());
            assertEquals((byte) 3, buf.readByte());
            assertEquals((byte) 4, buf.readByte());
            assertEquals((byte) 5, buf.readByte());
            assertEquals((byte) 6, buf.readByte());
            assertEquals((byte) 7, buf.readByte());
            assertEquals((byte) 8, buf.readByte());
            assertThrows(IndexOutOfBoundsException.class, buf::readByte);
            assertThat(toByteArray(buf)).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        }
    }

    @ParameterizedTest
    @MethodSource("initialCombinations")
    public void allocatingZeroSizedBuffer(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            Supplier<Buffer> supplier = allocator.constBufferSupplier(EmptyArrays.EMPTY_BYTES);

            try (Buffer empty = supplier.get()) {
                assertThat(empty.capacity()).isZero();
                assertTrue(empty.readOnly());
            }

            try (Buffer empty = allocator.allocate(0)) {
                assertThat(empty.capacity()).isZero();
                empty.ensureWritable(8);
                assertThat(empty.capacity()).isGreaterThanOrEqualTo(8);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void acquireOnClosedBufferMustThrow(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            var buf = allocator.allocate(8);
            buf.close();
            assertThrows(BufferClosedException.class, () -> acquire((ResourceSupport<?, ?>) buf));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void bufferShouldNotBeAccessibleAfterClose(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            Buffer buf = allocator.allocate(24);
            buf.writeLong(42);
            buf.close();
            verifyInaccessible(buf);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void bufferMustNotBeThreadConfined(Fixture fixture) throws Exception {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.writeInt(42);
            Future<Integer> fut = executor.submit(() -> buf.readInt());
            assertEquals(42, fut.get());
            fut = executor.submit(() -> {
                buf.writeInt(32);
                return buf.readInt();
            });
            assertEquals(32, fut.get());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyWithoutOffsetAndSizeMustReturnReadableRegion(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            for (byte b : new byte[] { 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08 }) {
                buf.writeByte(b);
            }
            assertEquals(0x01, buf.readByte());
            buf.writerOffset(buf.writerOffset() - 1);
            try (Buffer copy = buf.copy()) {
                assertThat(toByteArray(copy)).containsExactly(0x02, 0x03, 0x04, 0x05, 0x06, 0x07);
                assertEquals(0, copy.readerOffset());
                assertEquals(6, copy.readableBytes());
                assertEquals(6, copy.writerOffset());
                assertEquals(6, copy.capacity());
                assertEquals(0x02, copy.readByte());
                assertEquals(0x03, copy.readByte());
                assertEquals(0x04, copy.readByte());
                assertEquals(0x05, copy.readByte());
                assertEquals(0x06, copy.readByte());
                assertEquals(0x07, copy.readByte());
                assertThrows(IndexOutOfBoundsException.class, copy::readByte);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyWithOffsetAndSizeMustReturnGivenRegion(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            for (byte b : new byte[] { 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08 }) {
                buf.writeByte(b);
            }
            buf.readerOffset(3); // Reader and writer offsets must be ignored.
            buf.writerOffset(6);
            try (Buffer copy = buf.copy(1, 6)) {
                assertThat(toByteArray(copy)).containsExactly(0x02, 0x03, 0x04, 0x05, 0x06, 0x07);
                assertEquals(0, copy.readerOffset());
                assertEquals(6, copy.readableBytes());
                assertEquals(6, copy.writerOffset());
                assertEquals(6, copy.capacity());
                assertEquals(0x02, copy.readByte());
                assertEquals(0x03, copy.readByte());
                assertEquals(0x04, copy.readByte());
                assertEquals(0x05, copy.readByte());
                assertEquals(0x06, copy.readByte());
                assertEquals(0x07, copy.readByte());
                assertThrows(IndexOutOfBoundsException.class, copy::readByte);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyWithoutOffsetAndSizeMustNotInfluenceOwnership(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            try (Buffer copy = buf.copy()) {
                assertTrue(isOwned((ResourceSupport<?, ?>) buf));
                assertTrue(isOwned((ResourceSupport<?, ?>) copy));
                copy.send().close();
            }
            assertTrue(isOwned((ResourceSupport<?, ?>) buf));
            buf.send().close();
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyWithOffsetAndSizeMustNotInfluenceOwnership(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            try (Buffer copy = buf.copy(0, 8)) {
                assertTrue(isOwned((ResourceSupport<?, ?>) buf));
                assertTrue(isOwned((ResourceSupport<?, ?>) copy));
                copy.send().close();
            }
            assertTrue(isOwned((ResourceSupport<?, ?>) buf));
            buf.send().close();
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyWithoutOffsetAndSizeHasSameEndianAsParent(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.writeLong(0x0102030405060708L);
            try (Buffer copy = buf.copy()) {
                assertEquals(0x0102030405060708L, copy.readLong());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyWithOffsetAndSizeHasSameEndianAsParent(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.writeLong(0x0102030405060708L);
            try (Buffer copy = buf.copy(0, 8)) {
                assertEquals(0x0102030405060708L, copy.readLong());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void sendOnCopyWithoutOffsetAndSizeMustNotThrow(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            try (Buffer copy = buf.copy()) {
                assertTrue(isOwned((ResourceSupport<?, ?>) buf));
                copy.send().close();
            }
            // Verify that the copy is closed properly afterwards.
            assertTrue(isOwned((ResourceSupport<?, ?>) buf));
            buf.send().receive().close();
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void sendOnCopyWithOffsetAndSizeMustThrow(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            try (Buffer copy = buf.copy(0, 8)) {
                assertTrue(isOwned((ResourceSupport<?, ?>) buf));
                copy.send().close();
            }
            // Verify that the copy is closed properly afterwards.
            assertTrue(isOwned((ResourceSupport<?, ?>) buf));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyWithNegativeOffsetMustThrow(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.copy(-1, 1));
            // Verify that the copy is closed properly afterwards.
            assertTrue(isOwned((ResourceSupport<?, ?>) buf));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyWithNegativeSizeMustThrow(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IllegalArgumentException.class, () -> buf.copy(0, -1));
            assertThrows(IllegalArgumentException.class, () -> buf.copy(2, -1));
            // Verify that the copy is closed properly afterwards.
            assertTrue(isOwned((ResourceSupport<?, ?>) buf));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyWithSizeGreaterThanCapacityMustThrow(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.copy(0, 9));
            buf.copy(0, 8).close(); // This is still fine.
            assertThrows(IndexOutOfBoundsException.class, () -> buf.copy(1, 8));
            // Verify that the copy is closed properly afterwards.
            assertTrue(isOwned((ResourceSupport<?, ?>) buf));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyWithZeroSizeMustBeAllowed(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.copy(0, 0).close(); // This is fine.
            // Verify that the copy is closed properly afterwards.
            assertTrue(isOwned((ResourceSupport<?, ?>) buf));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void copyMustBeOwned(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            Buffer buf = allocator.allocate(8);
            buf.writeInt(42);
            try (Buffer copy = buf.copy()) {
                assertTrue(isOwned((ResourceSupport<?, ?>) copy));
                assertTrue(isOwned((ResourceSupport<?, ?>) buf));
                buf.close();
                assertFalse(buf.isAccessible());
                assertTrue(isOwned((ResourceSupport<?, ?>) copy));
                try (Buffer receive = copy.send().receive()) {
                    assertTrue(isOwned((ResourceSupport<?, ?>) receive));
                    assertFalse(copy.isAccessible());
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void copyOfLastByte(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8).writeLong(0x0102030405060708L);
             Buffer copy = buf.copy(7, 1)) {
            assertThat(copy.capacity()).isOne();
            assertEquals((byte) 0x08, copy.readByte());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void pooledBuffersMustResetStateBeforeReuse(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer expected = allocator.allocate(8)) {
            for (int i = 0; i < 10; i++) {
                try (Buffer buf = allocator.allocate(8)) {
                    assertEquals(expected.capacity(), buf.capacity());
                    assertEquals(expected.readableBytes(), buf.readableBytes());
                    assertEquals(expected.readerOffset(), buf.readerOffset());
                    assertEquals(expected.writableBytes(), buf.writableBytes());
                    assertEquals(expected.writerOffset(), buf.writerOffset());
                    byte[] bytes = new byte[8];
                    buf.copyInto(0, bytes, 0, 8);
                    assertThat(bytes).containsExactly(0, 0, 0, 0, 0, 0, 0, 0);

                    var tlr = ThreadLocalRandom.current();
                    for (int j = 0; j < tlr.nextInt(0, 8); j++) {
                        buf.writeByte((byte) 1);
                    }
                    if (buf.readableBytes() > 0) {
                        for (int j = 0; j < tlr.nextInt(0, buf.readableBytes()); j++) {
                            buf.readByte();
                        }
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void splitWithNegativeOffsetMustThrow(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.split(0).close();
            assertThrows(IllegalArgumentException.class, () -> buf.split(-1));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void splitWithOversizedOffsetMustThrow(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IllegalArgumentException.class, () -> buf.split(9));
            buf.split(8).close();
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void splitOfNonOwnedBufferMustThrow(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.writeInt(1);
            try (Buffer acquired = acquire((ResourceSupport<?, ?>) buf)) {
                var exc = assertThrows(IllegalStateException.class, () -> acquired.split());
                assertThat(exc).hasMessageContaining("owned");
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void splitOnOffsetOfNonOwnedBufferMustThrow(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            try (Buffer acquired = acquire((ResourceSupport<?, ?>) buf)) {
                var exc = assertThrows(IllegalStateException.class, () -> acquired.split(4));
                assertThat(exc).hasMessageContaining("owned");
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void splitOnOffsetMustTruncateGreaterOffsets(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.writeInt(0x01020304);
            buf.writeByte((byte) 0x05);
            buf.readInt();
            try (Buffer split = buf.split(2)) {
                assertThat(buf.readerOffset()).isEqualTo(2);
                assertThat(buf.writerOffset()).isEqualTo(3);

                assertThat(split.readerOffset()).isEqualTo(2);
                assertThat(split.writerOffset()).isEqualTo(2);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void splitOnOffsetMustExtendLesserOffsets(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.writeInt(0x01020304);
            buf.readInt();
            try (Buffer split = buf.split(6)) {
                assertThat(buf.readerOffset()).isEqualTo(0);
                assertThat(buf.writerOffset()).isEqualTo(0);

                assertThat(split.readerOffset()).isEqualTo(4);
                assertThat(split.writerOffset()).isEqualTo(4);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void splitPartMustContainFirstHalfOfBuffer(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(16)) {
            buf.writeLong(0x0102030405060708L);
            assertThat(buf.readByte()).isEqualTo((byte) 0x01);
            try (Buffer split = buf.split()) {
                // Original buffer:
                assertThat(buf.capacity()).isEqualTo(8);
                assertThat(buf.readerOffset()).isZero();
                assertThat(buf.writerOffset()).isZero();
                assertThat(buf.readableBytes()).isZero();
                assertThrows(IndexOutOfBoundsException.class, () -> buf.readByte());

                // Split part:
                assertThat(split.capacity()).isEqualTo(8);
                assertThat(split.readerOffset()).isOne();
                assertThat(split.writerOffset()).isEqualTo(8);
                assertThat(split.readableBytes()).isEqualTo(7);
                assertThat(split.readByte()).isEqualTo((byte) 0x02);
                assertThat(split.readInt()).isEqualTo(0x03040506);
                assertThat(split.readByte()).isEqualTo((byte) 0x07);
                assertThat(split.readByte()).isEqualTo((byte) 0x08);
                assertThrows(IndexOutOfBoundsException.class, () -> split.readByte());
            }

            // Split part does NOT return when closed:
            assertThat(buf.capacity()).isEqualTo(8);
            assertThat(buf.readerOffset()).isZero();
            assertThat(buf.writerOffset()).isZero();
            assertThat(buf.readableBytes()).isZero();
            assertThrows(IndexOutOfBoundsException.class, () -> buf.readByte());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void splitPartsMustBeIndividuallySendable(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(16)) {
            buf.writeLong(0x0102030405060708L);
            assertThat(buf.readByte()).isEqualTo((byte) 0x01);
            try (Buffer sentSplit = buf.split().send().receive()) {
                try (Buffer sentBuf = buf.send().receive()) {
                    assertThat(sentBuf.capacity()).isEqualTo(8);
                    assertThat(sentBuf.readerOffset()).isZero();
                    assertThat(sentBuf.writerOffset()).isZero();
                    assertThat(sentBuf.readableBytes()).isZero();
                    assertThrows(IndexOutOfBoundsException.class, () -> sentBuf.readByte());
                }

                assertThat(sentSplit.capacity()).isEqualTo(8);
                assertThat(sentSplit.readerOffset()).isOne();
                assertThat(sentSplit.writerOffset()).isEqualTo(8);
                assertThat(sentSplit.readableBytes()).isEqualTo(7);
                assertThat(sentSplit.readByte()).isEqualTo((byte) 0x02);
                assertThat(sentSplit.readInt()).isEqualTo(0x03040506);
                assertThat(sentSplit.readByte()).isEqualTo((byte) 0x07);
                assertThat(sentSplit.readByte()).isEqualTo((byte) 0x08);
                assertThrows(IndexOutOfBoundsException.class, () -> sentSplit.readByte());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void mustBePossibleToSplitMoreThanOnce(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(16)) {
            buf.writeLong(0x0102030405060708L);
            try (Buffer a = buf.split()) {
                a.writerOffset(4);
                try (Buffer b = a.split()) {
                    assertEquals(0x01020304, b.readInt());
                    a.writerOffset(4);
                    assertEquals(0x05060708, a.readInt());
                    assertThrows(IndexOutOfBoundsException.class, () -> b.readByte());
                    assertThrows(IndexOutOfBoundsException.class, () -> a.readByte());
                    buf.writeLong(0xA1A2A3A4A5A6A7A8L);
                    buf.writerOffset(4);
                    try (Buffer c = buf.split()) {
                        assertEquals(0xA1A2A3A4, c.readInt());
                        buf.writerOffset(4);
                        assertEquals(0xA5A6A7A8, buf.readInt());
                        assertThrows(IndexOutOfBoundsException.class, () -> c.readByte());
                        assertThrows(IndexOutOfBoundsException.class, () -> buf.readByte());
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void mustBePossibleToSplitCopies(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            Buffer buf = allocator.allocate(16);
            buf.writeLong(0x0102030405060708L);
            try (Buffer copy = buf.copy()) {
                buf.close();
                assertEquals(0x0102030405060708L, copy.getLong(0));
                assertTrue(isOwned((ResourceSupport<?, ?>) copy));
                try (Buffer split = copy.split(4)) {
                    split.resetOffsets().ensureWritable(Long.BYTES);
                    copy.resetOffsets().ensureWritable(Long.BYTES);
                    assertThat(split.capacity()).isEqualTo(Long.BYTES);
                    assertThat(copy.capacity()).isEqualTo(Long.BYTES);
                    assertEquals(0x01020304, split.getInt(0));
                    assertEquals(0x05060708, copy.getInt(0));
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void ensureWritableOnSplitBuffers(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.writeLong(0x0102030405060708L);
            try (Buffer a = buf.split()) {
                assertEquals(0x0102030405060708L, a.readLong());
                a.ensureWritable(8);
                a.writeLong(0xA1A2A3A4A5A6A7A8L);
                assertEquals(0xA1A2A3A4A5A6A7A8L, a.readLong());

                buf.ensureWritable(8);
                buf.writeLong(0xA1A2A3A4A5A6A7A8L);
                assertEquals(0xA1A2A3A4A5A6A7A8L, buf.readLong());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void ensureWritableOnSplitBuffersWithOddOffsets(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(10)) {
            buf.writeLong(0x0102030405060708L);
            buf.writeByte((byte) 0x09);
            buf.readByte();
            try (Buffer a = buf.split()) {
                assertEquals(0x0203040506070809L, a.readLong());
                a.ensureWritable(8);
                a.writeLong(0xA1A2A3A4A5A6A7A8L);
                assertEquals(0xA1A2A3A4A5A6A7A8L, a.readLong());

                buf.ensureWritable(8);
                buf.writeLong(0xA1A2A3A4A5A6A7A8L);
                assertEquals(0xA1A2A3A4A5A6A7A8L, buf.readLong());
            }
        }
    }

    @Test
    public void splitOnEmptyCompositeBuffer() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled();
             Buffer buf = CompositeBuffer.compose(allocator)) {
            verifySplitEmptyCompositeBuffer(buf);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void splitBuffersMustBeAccessibleInOtherThreads(Fixture fixture) throws Exception {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.writeInt(42);
            var send = buf.split().send();
            var fut = executor.submit(() -> {
                try (Buffer receive = send.receive()) {
                    assertEquals(42, receive.readInt());
                    receive.readerOffset(0).writerOffset(0).writeInt(24);
                    assertEquals(24, receive.readInt());
                }
            });
            fut.get();
            buf.writeInt(32);
            assertEquals(32, buf.readInt());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void acquireOfReadOnlyBufferMustBeReadOnly(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.makeReadOnly();
            try (Buffer acquire = acquire((ResourceSupport<?, ?>) buf)) {
                assertTrue(acquire.readOnly());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void splitOfReadOnlyBufferMustBeReadOnly(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(16)) {
            buf.writeLong(0x0102030405060708L);
            buf.makeReadOnly();
            try (Buffer split = buf.split()) {
                assertTrue(split.readOnly());
                assertTrue(buf.readOnly());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void allocatingOnClosedAllocatorMustThrow(Fixture fixture) {
        BufferAllocator allocator = fixture.createAllocator();
        Supplier<Buffer> supplier = allocator.constBufferSupplier(new byte[8]);
        allocator.close();
        assertThrows(IllegalStateException.class, () -> allocator.allocate(8));
        assertThrows(IllegalStateException.class, () -> allocator.constBufferSupplier(EmptyArrays.EMPTY_BYTES));
        assertThrows(IllegalStateException.class, () -> allocator.constBufferSupplier(new byte[8]));
        // Existing const suppliers continue to work because they hold on to static memory allocation.
        supplier.get().close();
    }
}
