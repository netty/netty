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
package io.netty5.buffer.api;

import io.netty5.buffer.api.internal.Statics;
import io.netty5.buffer.api.internal.WrappingAllocation;

import java.nio.charset.Charset;
import java.util.function.Supplier;

import static io.netty5.buffer.api.internal.Statics.allocatorClosedException;
import static io.netty5.buffer.api.internal.Statics.standardDrop;

class ManagedBufferAllocator implements BufferAllocator, AllocatorControl {
    private final MemoryManager manager;
    private final AllocationType allocationType;
    private volatile boolean closed;

    ManagedBufferAllocator(MemoryManager manager, boolean direct) {
        this.manager = manager;
        allocationType = direct? StandardAllocationTypes.OFF_HEAP : StandardAllocationTypes.ON_HEAP;
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
        Statics.assertValidBufferSize(size);
        return manager.allocateShared(this, size, standardDrop(manager), allocationType);
    }

    @Override
    public Supplier<Buffer> constBufferSupplier(byte[] bytes) {
        if (closed) {
            throw allocatorClosedException();
        }
        Buffer constantBuffer = manager.allocateShared(
                this, bytes.length, standardDrop(manager), allocationType);
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
    public BufferAllocator getAllocator() {
        return this;
    }
}
