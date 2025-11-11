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
package io.netty5.buffer.tests;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.buffer.internal.ResourceSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static io.netty5.buffer.internal.InternalBufferUtils.acquire;
import static io.netty5.buffer.internal.InternalBufferUtils.isOwned;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BufferMoveAndCloseTest extends BufferTestSupport {
    @ParameterizedTest
    @MethodSource("allocators")
    void originalBufferMustBecomeClosed(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            Buffer buf1 = allocator.allocate(8);
            Buffer buf2 = buf1.move();
            assertFalse(buf1.isAccessible());
            assertTrue(buf2.isAccessible());
            buf2.close();
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void originalReadOnlyBufferMustBecomeClosed(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            Buffer buf1 = allocator.allocate(8).makeReadOnly();
            Buffer buf2 = buf1.move();
            assertFalse(buf1.isAccessible());
            assertTrue(buf2.isAccessible());
            assertTrue(buf2.readOnly());
            buf2.close();
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void moveMustWorkOnZeroSizedBuffers(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            Buffer buf1 = allocator.allocate(0);
            Buffer buf2 = buf1.move();
            assertFalse(buf1.isAccessible());
            assertTrue(buf2.isAccessible());
            buf2.close();
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void returnedBufferMustRetainContentsAndOffsets(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            Buffer buf1 = allocator.allocate(8);
            buf1.writeInt(0x12131415);
            assertEquals((byte) 0x12, buf1.readByte());
            int readerOffset = buf1.readerOffset();
            int writerOffset = buf1.writerOffset();
            int capacity = buf1.capacity();
            Buffer buf2 = buf1.move();
            assertFalse(buf1.isAccessible());
            assertTrue(buf2.isAccessible());
            assertEquals(readerOffset, buf2.readerOffset());
            assertEquals(writerOffset, buf2.writerOffset());
            assertEquals(capacity, buf2.capacity());
            buf2.close();
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void moveMustThrowIfBufferIsAcquired(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            Buffer buf1 = allocator.allocate(8);
            try (Buffer ignored = acquire((ResourceSupport<?, ?>) buf1)) {
                assertFalse(isOwned((ResourceSupport<?, ?>) buf1));
                assertThrows(IllegalStateException.class, buf1::move);
            }
            Buffer buf2 = buf1.move();
            assertFalse(buf1.isAccessible());
            assertTrue(buf2.isAccessible());
            buf2.close();
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void moveMustFailIfBufferIsAlreadyClosed(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            Buffer buf1 = allocator.allocate(8);
            buf1.close();
            assertThrows(IllegalStateException.class, buf1::move);

            buf1 = allocator.allocate(8);
            Buffer buf2 = buf1.move();
            assertThrows(IllegalStateException.class, buf1::move);
            buf2.close();
        }
    }
}
