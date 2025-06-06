/*
 * Copyright 2015 The Netty Project
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractPooledByteBufTest extends AbstractByteBufTest {

    protected abstract ByteBuf alloc(int length, int maxCapacity);

    @Override
    protected ByteBuf newBuffer(int length, int maxCapacity) {
        ByteBuf buffer = alloc(length, maxCapacity);

        // Testing if the writerIndex and readerIndex are correct when allocate and also after we reset the mark.
        assertEquals(0, buffer.writerIndex());
        assertEquals(0, buffer.readerIndex());
        buffer.resetReaderIndex();
        buffer.resetWriterIndex();
        assertEquals(0, buffer.writerIndex());
        assertEquals(0, buffer.readerIndex());
        return buffer;
    }

    @Test
    public void ensureWritableWithEnoughSpaceShouldNotThrow() {
        ByteBuf buf = newBuffer(1, 10);
        buf.ensureWritable(3);
        assertThat(buf.writableBytes()).isGreaterThanOrEqualTo(3);
        buf.release();
    }

    @Test
    public void ensureWritableWithNotEnoughSpaceShouldThrow() {
        final ByteBuf buf = newBuffer(1, 10);
        try {
            assertThrows(IndexOutOfBoundsException.class, new Executable() {
                @Override
                public void execute() {
                    buf.ensureWritable(11);
                }
            });
        } finally {
            buf.release();
        }
    }

    @Override
    @Test
    public void testMaxFastWritableBytes() {
        ByteBuf buffer = newBuffer(150, 500).writerIndex(100);
        assertEquals(50, buffer.writableBytes());
        assertEquals(150, buffer.capacity());
        assertEquals(500, buffer.maxCapacity());
        assertEquals(400, buffer.maxWritableBytes());

        int chunkSize = pooledByteBuf(buffer).maxLength;
        assertTrue(chunkSize >= 150);
        int remainingInAlloc = Math.min(chunkSize - 100, 400);
        assertEquals(remainingInAlloc, buffer.maxFastWritableBytes());

        // write up to max, chunk alloc should not change (same handle)
        long handleBefore = pooledByteBuf(buffer).handle;
        buffer.writeBytes(new byte[remainingInAlloc]);
        assertEquals(handleBefore, pooledByteBuf(buffer).handle);

        assertEquals(0, buffer.maxFastWritableBytes());
        // writing one more should trigger a reallocation (new handle)
        buffer.writeByte(7);
        assertNotEquals(handleBefore, pooledByteBuf(buffer).handle);

        // should not exceed maxCapacity even if chunk alloc does
        buffer.capacity(500);
        assertEquals(500 - buffer.writerIndex(), buffer.maxFastWritableBytes());
        buffer.release();
    }

    private static PooledByteBuf<?> pooledByteBuf(ByteBuf buffer) {
        // might need to unwrap if swapped (LE) and/or leak-aware-wrapped
        while (!(buffer instanceof PooledByteBuf)) {
            buffer = buffer.unwrap();
        }
        return (PooledByteBuf<?>) buffer;
    }

    @Test
    public void testEnsureWritableDoesntGrowTooMuch() {
        ByteBuf buffer = newBuffer(150, 500).writerIndex(100);

        assertEquals(50, buffer.writableBytes());
        int fastWritable = buffer.maxFastWritableBytes();
        assertTrue(fastWritable > 50);

        long handleBefore = pooledByteBuf(buffer).handle;

        // capacity expansion should not cause reallocation
        // (should grow precisely the specified amount)
        buffer.ensureWritable(fastWritable);
        assertEquals(handleBefore, pooledByteBuf(buffer).handle);
        assertEquals(100 + fastWritable, buffer.capacity());
        assertEquals(buffer.writableBytes(), buffer.maxFastWritableBytes());
        buffer.release();
    }

    @Test
    public void testIsContiguous() {
        ByteBuf buf = newBuffer(4);
        assertTrue(buf.isContiguous());
        buf.release();
    }

    @Test
    public void distinctBuffersMustNotOverlap() {
        ByteBuf a = newBuffer(16384);
        ByteBuf b = newBuffer(65536);
        a.setByte(a.capacity() - 1, 1);
        b.setByte(0, 2);
        try {
            assertEquals(1, a.getByte(a.capacity() - 1));
        } finally {
            a.release();
            b.release();
        }
    }
}
