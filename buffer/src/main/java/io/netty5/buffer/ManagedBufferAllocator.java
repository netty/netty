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
package io.netty5.buffer;

import io.netty5.buffer.internal.CleanerDrop;
import io.netty5.buffer.internal.InternalBufferUtils;
import io.netty5.buffer.internal.WrappingAllocation;

import java.nio.charset.Charset;
import java.util.function.Supplier;

import static io.netty5.buffer.internal.InternalBufferUtils.allocatorClosedException;
import static io.netty5.buffer.internal.InternalBufferUtils.standardDrop;

class ManagedBufferAllocator implements BufferAllocator, AllocatorControl {
    private static volatile UnpooledAllocator unpooledAllocator;

    private final MemoryManager manager;
    private final AllocationType allocationType;
    private volatile boolean closed;

    ManagedBufferAllocator(MemoryManager manager, boolean direct) {
        this.manager = manager;
        allocationType = direct? StandardAllocationTypes.OFF_HEAP : StandardAllocationTypes.ON_HEAP;
    }

    static ManagedBufferAllocator getUnpooledBufferAllocator(MemoryManager manager) {
        UnpooledAllocator unpooled = unpooledAllocator;
        if (unpooled == null || unpooled.manager != manager) {
            // No locking necessary. A few duplicates at runtime is fine.
            unpooled = new UnpooledAllocator(manager, new ManagedBufferAllocator(manager, false));
            unpooledAllocator = unpooled;
        }
        return unpooled.allocator;
    }

    @Override
    public boolean isPooling() {
        return false;
    }

    @Override
    public AllocationType getAllocationType() {
        return allocationType;
    }

    @Override
    public Buffer allocate(int size) {
        if (closed) {
            throw allocatorClosedException();
        }
        InternalBufferUtils.assertValidBufferSize(size);
        return manager.allocateShared(this, size, standardDrop(manager), allocationType);
    }

    @Override
    public Supplier<Buffer> constBufferSupplier(byte[] bytes) {
        if (closed) {
            throw allocatorClosedException();
        }
        Buffer constantBuffer = manager.allocateShared(
                this, bytes.length, drop -> CleanerDrop.wrapWithoutLeakDetection(drop, manager), allocationType);
        constantBuffer.writeBytes(bytes).makeReadOnly();
        return () -> manager.allocateConstChild(constantBuffer);
    }

    @Override
    public Buffer copyOf(String str, Charset charset) {
        if (!allocationType.isDirect()) {
            // For on-heap buffers we can optimise a bit, and allocate with just one copy operation.
            byte[] bytes = str.getBytes(charset);
            // We use a wrapping allocation type, because the byte array is guaranteed by String to be un-aliased.
            WrappingAllocation allocation = new WrappingAllocation(bytes);
            Buffer buffer = manager.allocateShared(this, bytes.length, standardDrop(manager), allocation);
            return buffer.writerOffset(bytes.length);
        }
        return BufferAllocator.super.copyOf(str, charset);
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public BufferAllocator getAllocator() {
        return this;
    }

    @Override
    public String toString() {
        return "BufferAllocator(" + allocationType + (closed ? ", closed)" : ")");
    }

    private static final class UnpooledAllocator {
        final MemoryManager manager;
        final ManagedBufferAllocator allocator;

        UnpooledAllocator(MemoryManager manager, ManagedBufferAllocator allocator) {
            this.manager = manager;
            this.allocator = allocator;
        }
    }
}
