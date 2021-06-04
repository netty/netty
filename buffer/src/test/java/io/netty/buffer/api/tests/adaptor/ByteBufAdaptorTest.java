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
package io.netty.buffer.api.tests.adaptor;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.api.BufferAllocator;
import io.netty.buffer.api.MemoryManager;
import io.netty.buffer.api.adaptor.ByteBufAllocatorAdaptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;

import java.util.Optional;

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

    @Disabled("No longer allowed to allocate 0 sized buffers, except for composite buffers with no components.")
    @Override
    public void testLittleEndianWithExpand() {
    }
}
