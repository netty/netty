/*
 * Copyright 2024 The Netty Project
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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public abstract class AbstractAdaptiveByteBufTest extends AbstractPooledByteBufTest {
    private final AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator();

    @Override
    protected final ByteBuf alloc(int length, int maxCapacity) {
        return alloc(allocator, length, maxCapacity);
    }

    protected abstract ByteBuf alloc(AdaptiveByteBufAllocator allocator, int length, int maxCapacity);

    @Disabled("Assumes the ByteBuf can be cast to PooledByteBuf")
    @Test
    @Override
    public void testMaxFastWritableBytes() {
    }

    @Disabled("Assumes the ByteBuf can be cast to PooledByteBuf")
    @Test
    @Override
    public void testEnsureWritableDoesntGrowTooMuch() {
    }

    @Test
    public void testSetBytesFromBuffersOfOtherKinds() {
        final byte[] bytes = new byte[64];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i + 1);
        }
        ByteBuf pooledDirect = PooledByteBufAllocator.DEFAULT.directBuffer(72).writeZero(8).writeBytes(bytes);
        ByteBuf unpooledHeap = Unpooled.buffer(72).writeZero(8).writeBytes(bytes);
        ByteBuf adaptive = alloc(72, 72).writeZero(8).writeBytes(bytes);
        CompositeByteBuf composite = Unpooled.compositeBuffer().addComponents(true,
                Unpooled.directBuffer(32).writeBytes(bytes, 0, 32),
                Unpooled.buffer(32).writeBytes(bytes, 32, 32));
        ByteBuf wrappedHeap = Unpooled.wrappedBuffer(bytes);
        final ByteBuf[] sources = {
                pooledDirect.slice(8, 64),
                unpooledHeap.slice(8, 64),
                adaptive.slice(8, 64),
                composite,
                wrappedHeap.asReadOnly(),
        };
        final ByteBuf target = alloc(128, 128);
        try {
            for (ByteBuf src : sources) {
                target.clear().writeZero(128);
                target.setBytes(16, src, 4, 40);
                for (int i = 0; i < 128; i++) {
                    byte expected = i >= 16 && i < 56 ? bytes[i - 16 + 4] : 0;
                    assertEquals(expected, target.getByte(i), "index " + i + " from " + src);
                }
                assertEquals(0, src.readerIndex());
                assertEquals(64, src.writerIndex());
            }
            assertThrows(IndexOutOfBoundsException.class, () -> target.setBytes(0, sources[0], 40, 40));
            assertThrows(IndexOutOfBoundsException.class, () -> target.setBytes(100, sources[0], 0, 40));
        } finally {
            target.release();
            pooledDirect.release();
            unpooledHeap.release();
            adaptive.release();
            composite.release();
            wrappedHeap.release();
        }
    }
}
