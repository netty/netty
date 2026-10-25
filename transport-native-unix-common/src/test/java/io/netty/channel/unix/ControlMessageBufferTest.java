/*
 * Copyright 2026 The Netty Project
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
package io.netty.channel.unix;

import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class ControlMessageBufferTest {

    @BeforeAll
    public static void assumeDirectBufferAddress() {
        // Otherwise we would need the native library to get the memory address.
        assumeTrue(PlatformDependent.hasDirectByteBufferAddress(ByteBuffer.allocateDirect(1)));
    }

    @Test
    public void testReserve() {
        ControlMessageBuffer buffer = new ControlMessageBuffer();
        try {
            assertNull(buffer.buffer());
            assertEquals(0, buffer.reserve(16));
            assertTrue(buffer.buffer().isDirect());
            assertEquals(ByteOrder.nativeOrder(), buffer.buffer().order());
            int capacity = buffer.buffer().capacity();
            assertTrue(capacity >= 16);
            assertEquals(16, buffer.reserve(8));
            assertEquals(buffer.memoryAddress(0) + 16, buffer.memoryAddress(16));

            // Fill up the rest.
            assertEquals(24, buffer.reserve(capacity - 24));
            // We can't grow while something is reserved.
            assertEquals(-1, buffer.reserve(1));
            assertEquals(capacity, buffer.buffer().capacity());

            // As we did run out of space before we should have grown.
            buffer.clear();
            assertTrue(buffer.buffer().capacity() > capacity);
            assertEquals(0, buffer.reserve(capacity + 1));
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testReserveMoreThanCapacityWhenEmpty() {
        ControlMessageBuffer buffer = new ControlMessageBuffer();
        try {
            assertEquals(0, buffer.reserve(1));
            buffer.clear();
            int capacity = buffer.buffer().capacity();
            // Empty, so we can just grow.
            assertEquals(0, buffer.reserve(capacity * 4));
            assertTrue(buffer.buffer().capacity() >= capacity * 4);
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testEnsureCapacity() {
        ControlMessageBuffer buffer = new ControlMessageBuffer();
        try {
            buffer.ensureCapacity(8192);
            assertTrue(buffer.buffer().capacity() >= 8192);
            long address = buffer.memoryAddress(0);
            // Should not re-allocate if there is enough capacity.
            buffer.ensureCapacity(16);
            assertEquals(address, buffer.memoryAddress(0));

            assertEquals(0, buffer.reserve(1));
            assertThrows(IllegalStateException.class, () -> buffer.ensureCapacity(16 * 1024));
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testRelease() {
        ControlMessageBuffer buffer = new ControlMessageBuffer();
        assertEquals(0, buffer.reserve(1));
        long address = buffer.memoryAddress(0);
        assertNotEquals(0, address);
        buffer.release();
        assertNull(buffer.buffer());
        // Can be used again after release, which will allocate new memory.
        assertEquals(0, buffer.reserve(1));
        buffer.release();
        // Calling release multiple times is fine.
        buffer.release();
    }
}
