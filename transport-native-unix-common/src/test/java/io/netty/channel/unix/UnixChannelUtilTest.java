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

package io.netty.channel.unix;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static io.netty.channel.unix.UnixChannelUtil.isBufferCopyNeededForWrite;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UnixChannelUtilTest {

    private static final int IOV_MAX = 1024;

    @Test
    public void testPooledAllocatorIsBufferCopyNeededForWrite() {
        testIsBufferCopyNeededForWrite(PooledByteBufAllocator.DEFAULT);
    }

    @Test
    public void testUnPooledAllocatorIsBufferCopyNeededForWrite() {
        testIsBufferCopyNeededForWrite(UnpooledByteBufAllocator.DEFAULT);
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
        List<ByteBuf> byteBufs = new LinkedList<ByteBuf>();

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

        assertEquals(byteBufs.toString(), expected, isBufferCopyNeededForWrite(comp, IOV_MAX));
        assertTrue(comp.release());
    }
}
