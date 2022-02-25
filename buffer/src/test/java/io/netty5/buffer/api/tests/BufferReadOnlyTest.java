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
import io.netty5.buffer.api.BufferReadOnlyException;
import io.netty5.buffer.api.CompositeBuffer;
import io.netty5.buffer.api.Send;
import io.netty5.buffer.api.internal.ResourceSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Supplier;

import static io.netty5.buffer.api.internal.Statics.isOwned;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BufferReadOnlyTest extends BufferTestSupport {

    @ParameterizedTest
    @MethodSource("allocators")
    public void readOnlyBufferMustPreventWriteAccess(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            var b = buf.makeReadOnly();
            assertThat(b).isSameAs(buf);
            verifyWriteInaccessible(buf, BufferReadOnlyException.class);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void closedBuffersAreNotReadOnly(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            Buffer buf = allocator.allocate(8);
            buf.makeReadOnly();
            buf.close();
            assertFalse(buf.readOnly());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void readOnlyBufferMustMustStayReadOnlyAfterRepeatedToggles(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertFalse(buf.readOnly());
            buf.makeReadOnly();
            assertTrue(buf.readOnly());
            verifyWriteInaccessible(buf, BufferReadOnlyException.class);

            buf.makeReadOnly();
            assertTrue(buf.readOnly());

            verifyWriteInaccessible(buf, BufferReadOnlyException.class);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void readOnlyBufferMustRemainReadOnlyAfterSend(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.makeReadOnly();
            var send = buf.send();
            try (Buffer receive = send.receive()) {
                assertTrue(receive.readOnly());
                verifyWriteInaccessible(receive, BufferReadOnlyException.class);
            }
        }
    }

    @Test
    public void readOnlyBufferMustRemainReadOnlyAfterSendForEmptyCompositeBuffer() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled();
             Buffer buf = CompositeBuffer.compose(allocator)) {
            buf.makeReadOnly();
            var send = buf.send();
            try (Buffer receive = send.receive()) {
                assertTrue(receive.readOnly());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("pooledAllocators")
    public void readOnlyBufferMustNotBeReadOnlyAfterBeingReusedFromPool(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            for (int i = 0; i < 1000; i++) {
                try (Buffer buf = allocator.allocate(8)) {
                    assertFalse(buf.readOnly());
                    buf.makeReadOnly();
                    assertTrue(buf.readOnly());
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void compactOnReadOnlyBufferMustThrow(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.makeReadOnly();
            assertThrows(BufferReadOnlyException.class, () -> buf.compact());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void ensureWritableOnReadOnlyBufferMustThrow(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.makeReadOnly();
            assertThrows(BufferReadOnlyException.class, () -> buf.ensureWritable(1));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void copyIntoOnReadOnlyBufferMustThrow(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer dest = allocator.allocate(8)) {
            dest.makeReadOnly();
            try (Buffer src = allocator.allocate(8)) {
                assertThrows(BufferReadOnlyException.class, () -> src.copyInto(0, dest, 0, 1));
                assertThrows(BufferReadOnlyException.class, () -> src.copyInto(0, dest, 0, 0));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void readOnlyBuffersCannotChangeWriteOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8).makeReadOnly()) {
            assertThrows(BufferReadOnlyException.class, () -> buf.writerOffset(0));
            assertThrows(BufferReadOnlyException.class, () -> buf.writerOffset(4));
        }
    }

    @ParameterizedTest
    @MethodSource("initialCombinations")
    public void constBufferInitialState(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.constBufferSupplier(new byte[] {1, 2, 3, 4}).get()) {
            assertTrue(buf.readOnly());
            assertThat(buf.readerOffset()).isZero();
            assertThat(buf.capacity()).isEqualTo(4);
            assertThat(buf.writerOffset()).isEqualTo(4);
            assertTrue(isOwned((ResourceSupport<?, ?>) buf));
            assertTrue(buf.isAccessible());
            assertThat(buf.countComponents()).isOne();
            assertEquals((byte) 1, buf.readByte());
            assertEquals((byte) 2, buf.readByte());
            assertEquals((byte) 3, buf.readByte());
            assertEquals((byte) 4, buf.readByte());
        }
    }

    @ParameterizedTest
    @MethodSource("initialCombinations")
    public void constBuffersCanBeSplit(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            Supplier<Buffer> supplier = allocator.constBufferSupplier(new byte[16]);
            verifyConstBufferSplit(supplier);
            // These shenanigans must not interfere with the parent const buffer.
            verifyConstBufferSplit(supplier);
        }
    }

    private static void verifyConstBufferSplit(Supplier<Buffer> supplier) {
        try (Buffer a = supplier.get();
             Buffer b = a.split(8)) {
            assertTrue(a.readOnly());
            assertTrue(b.readOnly());
            assertTrue(isOwned((ResourceSupport<?, ?>) a));
            assertTrue(isOwned((ResourceSupport<?, ?>) b));
            assertThat(a.capacity()).isEqualTo(8);
            assertThat(b.capacity()).isEqualTo(8);
            try (Buffer c = b.copy()) {
                assertFalse(c.readOnly()); // Buffer copies are never read-only.
                assertTrue(isOwned((ResourceSupport<?, ?>) c));
                assertTrue(isOwned((ResourceSupport<?, ?>) b));
                assertThat(c.capacity()).isEqualTo(8);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("initialCombinations")
    public void compactOnConstBufferMustNotImpactSiblings(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            Supplier<Buffer> supplier = allocator.constBufferSupplier(new byte[] {1, 2, 3, 4});
            try (Buffer a = supplier.get();
                 Buffer b = supplier.get();
                 Buffer c = a.copy()) {
                assertEquals(1, a.readByte());
                assertEquals(2, a.readByte());
                assertThrows(BufferReadOnlyException.class, () -> a.compact()); // Can't compact read-only buffer.
                assertEquals(3, a.readByte());
                assertEquals(4, a.readByte());

                assertEquals(1, b.readByte());
                assertEquals(2, b.readByte());
                assertThrows(BufferReadOnlyException.class, () -> b.compact()); // Can't compact read-only buffer.
                assertEquals(3, b.readByte());
                assertEquals(4, b.readByte());

                assertEquals(1, c.readByte());
                assertEquals(2, c.readByte());
                c.compact(); // Copies are not read-only, so we can compact this one.
                assertEquals(3, c.readByte());
                assertEquals(4, c.readByte());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("initialCombinations")
    public void constBuffersMustBeSendable(Fixture fixture) throws Exception {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            Supplier<Buffer> supplier = allocator.constBufferSupplier(new byte[] {1, 2, 3, 4});
            try (Buffer buffer = supplier.get()) {
                Send<Buffer> send = buffer.send();
                var future = executor.submit(() -> {
                    try (Buffer receive = send.receive()) {
                        return receive.readInt();
                    }
                });
                assertEquals(0x01020304, future.get());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void copyOfReadOnlyBufferIsNotReadOnly(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8).writeLong(0x0102030405060708L).makeReadOnly();
             Buffer copy = buf.copy()) {
            assertFalse(copy.readOnly());
            Assertions.assertEquals(buf, copy);
            assertEquals(0, copy.readerOffset());
            copy.setLong(0, 0xA1A2A3A4A5A6A7A8L);
            assertEquals(0xA1A2A3A4A5A6A7A8L, copy.getLong(0));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void resetOffsetsOfReadOnlyBufferOnlyChangesReadOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(4).writeInt(0x01020304).makeReadOnly()) {
            assertEquals(4, buf.readableBytes());
            assertEquals(0x01020304, buf.readInt());
            assertEquals(0, buf.readableBytes());
            buf.resetOffsets();
            assertEquals(4, buf.readableBytes());
            assertEquals(0x01020304, buf.readInt());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void resetOffsetsOfConstBufferOnlyChangesReadOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            Supplier<Buffer> supplier = allocator.constBufferSupplier(new byte[] {1, 2, 3, 4});
            try (Buffer buf = supplier.get()) {
                assertEquals(4, buf.readableBytes());
                assertEquals(0x01020304, buf.readInt());
                assertEquals(0, buf.readableBytes());
                buf.resetOffsets();
                assertEquals(4, buf.readableBytes());
                assertEquals(0x01020304, buf.readInt());
            }
        }
    }
}
