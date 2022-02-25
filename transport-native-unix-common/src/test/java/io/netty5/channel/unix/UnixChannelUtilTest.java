/*
 * Copyright 2017 The Netty Project
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
package io.netty5.channel.unix;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.CompositeByteBuf;
import io.netty5.buffer.PooledByteBufAllocator;
import io.netty5.buffer.UnpooledByteBufAllocator;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.CompositeBuffer;
import io.netty5.buffer.api.Send;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static io.netty5.channel.unix.UnixChannelUtil.isBufferCopyNeededForWrite;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UnixChannelUtilTest {

    private static final int IOV_MAX = 1024;

    @Test
    public void testPooledByteBufAllocatorIsBufferCopyNeededForWrite() {
        testIsBufferCopyNeededForWrite(PooledByteBufAllocator.DEFAULT);
    }

    @Test
    public void testUnPooledByteBufAllocatorIsBufferCopyNeededForWrite() {
        testIsBufferCopyNeededForWrite(UnpooledByteBufAllocator.DEFAULT);
    }

    @Test
    public void testPooledBufferAllocatorIsBufferCopyNeededForWrite() {
        try (BufferAllocator onHeap = BufferAllocator.onHeapPooled();
             BufferAllocator offHeap = BufferAllocator.offHeapPooled()) {
            testIsBufferCopyNeededForWrite(onHeap, offHeap);
        }
    }

    @Test
    public void testUnPooledBufferAllocatorIsBufferCopyNeededForWrite() {
        try (BufferAllocator onHeap = BufferAllocator.onHeapUnpooled();
             BufferAllocator offHeap = BufferAllocator.offHeapUnpooled()) {
            testIsBufferCopyNeededForWrite(onHeap, offHeap);
        }
    }

    private static void testIsBufferCopyNeededForWrite(ByteBufAllocator alloc) {
        ByteBuf byteBuf = alloc.directBuffer();
        assertFalse(isBufferCopyNeededForWrite(byteBuf, IOV_MAX));
        assertFalse(isBufferCopyNeededForWrite(byteBuf.asReadOnly(), IOV_MAX));
        assertTrue(byteBuf.release());

        byteBuf = alloc.heapBuffer();
        assertTrue(isBufferCopyNeededForWrite(byteBuf, IOV_MAX));
        assertTrue(isBufferCopyNeededForWrite(byteBuf.asReadOnly(), IOV_MAX));
        assertTrue(byteBuf.release());

        assertCompositeByteBufIsBufferCopyNeededForWrite(alloc, 2, 0, false);
        assertCompositeByteBufIsBufferCopyNeededForWrite(alloc, IOV_MAX + 1, 0, true);
        assertCompositeByteBufIsBufferCopyNeededForWrite(alloc, 0, 2, true);
        assertCompositeByteBufIsBufferCopyNeededForWrite(alloc, 1, 1, true);
    }

    private static void assertCompositeByteBufIsBufferCopyNeededForWrite(ByteBufAllocator alloc, int numDirect,
                                                                         int numHeap, boolean expected) {
        CompositeByteBuf comp = alloc.compositeBuffer(numDirect + numHeap);
        List<ByteBuf> byteBufs = new LinkedList<>();

        while (numDirect > 0) {
            byteBufs.add(alloc.directBuffer(1));
            numDirect--;
        }
        while (numHeap > 0) {
            byteBufs.add(alloc.heapBuffer(1));
            numHeap--;
        }

        Collections.shuffle(byteBufs);
        for (ByteBuf byteBuf : byteBufs) {
            comp.addComponent(byteBuf);
        }

        assertEquals(expected, isBufferCopyNeededForWrite(comp, IOV_MAX), byteBufs.toString());
        assertTrue(comp.release());
    }

    private static void testIsBufferCopyNeededForWrite(BufferAllocator onHeap, BufferAllocator offHeap) {
        try (Buffer buf = offHeap.allocate(256)) {
            assertFalse(isBufferCopyNeededForWrite(buf, IOV_MAX));
            assertFalse(isBufferCopyNeededForWrite(buf.makeReadOnly(), IOV_MAX));
        }

        try (Buffer buf = onHeap.allocate(256)) {
            assertTrue(isBufferCopyNeededForWrite(buf, IOV_MAX));
            assertTrue(isBufferCopyNeededForWrite(buf.makeReadOnly(), IOV_MAX));
        }

        assertCompositeByteBufIsBufferCopyNeededForWrite(offHeap, 2, onHeap, 0, false);
        assertCompositeByteBufIsBufferCopyNeededForWrite(offHeap, IOV_MAX + 1, onHeap, 0, true);
        assertCompositeByteBufIsBufferCopyNeededForWrite(offHeap, 0, onHeap, 2, true);
        assertCompositeByteBufIsBufferCopyNeededForWrite(offHeap, 1, onHeap, 1, true);
    }

    @SuppressWarnings("unchecked")
    private static void assertCompositeByteBufIsBufferCopyNeededForWrite(
            BufferAllocator offHeap, int numDirect, BufferAllocator onHeap, int numHeap, boolean expected) {
        List<Send<Buffer>> buffers = new ArrayList<>(numHeap + numDirect);

        while (numDirect > 0) {
            buffers.add(offHeap.allocate(1).writeByte((byte) 1).send());
            numDirect--;
        }
        while (numHeap > 0) {
            buffers.add(onHeap.allocate(1).writeByte((byte) 1).send());
            numHeap--;
        }

        Collections.shuffle(buffers);
        try (CompositeBuffer comp = CompositeBuffer.compose(offHeap, buffers.toArray(Send[]::new))) {
            assertEquals(expected, isBufferCopyNeededForWrite(comp, IOV_MAX), buffers.toString());
        }
    }
}
