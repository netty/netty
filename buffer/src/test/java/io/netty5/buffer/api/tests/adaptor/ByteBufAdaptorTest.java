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
package io.netty5.buffer.api.tests.adaptor;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.MemoryManager;
import io.netty5.buffer.api.adaptor.ByteBufAdaptor;
import io.netty5.buffer.api.adaptor.ByteBufAllocatorAdaptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public abstract class ByteBufAdaptorTest extends AbstractByteBufTest {
    static ByteBufAllocatorAdaptor alloc;

    static void setUpAllocator(String name) {
        Optional<MemoryManager> managers = MemoryManager.lookupImplementation(name);
        assumeTrue(managers.isPresent(), () -> "Memory implementation '" + name + "' not found.");
        BufferAllocator onheap = MemoryManager.using(managers.get(), BufferAllocator::onHeapPooled);
        BufferAllocator offheap = MemoryManager.using(managers.get(), BufferAllocator::onHeapPooled);
        alloc = new ByteBufAllocatorAdaptor(onheap, offheap);
    }

    @AfterAll
    public static void tearDownAllocator() throws Exception {
        if (alloc != null) {
            alloc.close();
        }
    }

    @Override
    protected ByteBuf newBuffer(int capacity, int maxCapacity) {
        return alloc.buffer(capacity, maxCapacity);
    }

    @Test
    public void byteBufOfFromOnHeapBufferMustMirrorContentsOfBuffer() {
        byteBufOfFromBufferMustMirrorContentsOfBuffer(alloc.getOnHeap());
    }

    @Test
    public void byteBufOfFromOffHeapBufferMustMirrorContentsOfBuffer() {
        byteBufOfFromBufferMustMirrorContentsOfBuffer(alloc.getOffHeap());
    }

    private static void byteBufOfFromBufferMustMirrorContentsOfBuffer(BufferAllocator allocator) {
        try (Buffer buffer = allocator.allocate(8)) {
            buffer.writeInt(0x01020304);
            ByteBuf byteBuf = ByteBufAdaptor.intoByteBuf(buffer);
            assertEquals(buffer.capacity(), byteBuf.capacity());
            assertEquals(buffer.readableBytes(), byteBuf.readableBytes());
            assertEquals(buffer.writableBytes(), byteBuf.writableBytes());
            assertEquals(buffer.getInt(0), byteBuf.getInt(0));
            assertEquals(buffer.getLong(0), byteBuf.getLong(0));
        }
    }

    @Disabled("This test codifies that asking to reading 0 bytes from an empty but unclosed stream should return -1, " +
            "which is just weird.")
    @Override
    public void testStreamTransfer1() throws Exception {
    }

    @Disabled("Relies on capacity and max capacity being separate things.")
    @Override
    public void testCapacityIncrease() {
    }

    @Disabled("Decreasing capacity not supported in new API.")
    @Override
    public void testCapacityDecrease() {
    }

    @Disabled("Decreasing capacity not supported in new API.")
    @Override
    public void testCapacityNegative() {
        throw new IllegalArgumentException(); // Can't ignore tests annotated with throws expectation?
    }

    @Disabled("Decreasing capacity not supported in new API.")
    @Override
    public void testCapacityEnforceMaxCapacity() {
        throw new IllegalArgumentException(); // Can't ignore tests annotated with throws expectation?
    }

    @Disabled("Decreasing capacity not supported in new API.")
    @Override
    public void testMaxFastWritableBytes() {
    }

    @Disabled("Impossible to expose entire memory as a ByteBuffer using new API.")
    @Override
    public void testNioBufferExposeOnlyRegion() {
    }

    @Disabled("Impossible to expose entire memory as a ByteBuffer using new API.")
    @Override
    public void testToByteBuffer2() {
    }
}
