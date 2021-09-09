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
package io.netty.buffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class FallbackByteBufAllocatorTest {

    @Test
    public void testHeapBuffer() {
        ByteBufAllocator allocator = new FallbackByteBufAllocator(new PooledByteBufAllocator(), false);
        ByteBuf buffer = allocator.heapBuffer(1, 8);
        try {
            assertBuffer(buffer, false, 1, 8);
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testBuffer() {
        testBuffer(true, 8);
        testBuffer(false, 8);
    }

    private static void testBuffer(boolean preferDirect, int maxCapacity) {
        ByteBufAllocator allocator = new FallbackByteBufAllocator(new PooledByteBufAllocator(), preferDirect);
        ByteBuf buffer = allocator.buffer(1, maxCapacity);
        try {
            assertBuffer(buffer, preferDirect, 1, maxCapacity);
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testDirectBuffer() {
        ByteBufAllocator allocator = new FallbackByteBufAllocator(new PooledByteBufAllocator(true), true);
        ByteBuf buffer = allocator.directBuffer(1, 8);
        try {
            assertBuffer(buffer, true, 1, 8);
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testDirectFallbackBuffer() {
        ByteBufAllocator allocator = new FallbackByteBufAllocator(new PooledByteBufAllocator(true) {
            @Override
            protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
                throw new OutOfMemoryError();
            }
        }, true);
        ByteBuf buffer = allocator.buffer(1, 8);
        try {
            assertBuffer(buffer, false, 1, 8);
        } finally {
            buffer.release();
        }
    }

    private static void assertBuffer(ByteBuf buffer, boolean expectedDirect,
                                     int expectedCapacity, int expectedMaxCapacity) {
        assertEquals(expectedDirect, buffer.isDirect());
        assertEquals(expectedCapacity, buffer.capacity());
        assertEquals(expectedMaxCapacity, buffer.maxCapacity());
    }
}
