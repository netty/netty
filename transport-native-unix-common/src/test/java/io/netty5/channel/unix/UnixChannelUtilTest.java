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

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.CompositeBuffer;
import io.netty5.buffer.api.Send;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.netty5.channel.unix.UnixChannelUtil.isBufferCopyNeededForWrite;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UnixChannelUtilTest {

    private static final int IOV_MAX = 1024;

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

    private static void testIsBufferCopyNeededForWrite(BufferAllocator onHeap, BufferAllocator offHeap) {
        try (Buffer buf = offHeap.allocate(256)) {
            assertFalse(isBufferCopyNeededForWrite(buf, IOV_MAX));
            assertFalse(isBufferCopyNeededForWrite(buf.makeReadOnly(), IOV_MAX));
        }

        try (Buffer buf = onHeap.allocate(256)) {
            assertTrue(isBufferCopyNeededForWrite(buf, IOV_MAX));
            assertTrue(isBufferCopyNeededForWrite(buf.makeReadOnly(), IOV_MAX));
        }

        assertCompositeBufferIsBufferCopyNeededForWrite(offHeap, 2, onHeap, 0, false);
        assertCompositeBufferIsBufferCopyNeededForWrite(offHeap, IOV_MAX + 1, onHeap, 0, true);
        assertCompositeBufferIsBufferCopyNeededForWrite(offHeap, 0, onHeap, 2, true);
        assertCompositeBufferIsBufferCopyNeededForWrite(offHeap, 1, onHeap, 1, true);
    }

    @SuppressWarnings("unchecked")
    private static void assertCompositeBufferIsBufferCopyNeededForWrite(
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
        try (CompositeBuffer comp = offHeap.compose(buffers)) {
            assertEquals(expected, isBufferCopyNeededForWrite(comp, IOV_MAX), buffers.toString());
        }
    }
}
