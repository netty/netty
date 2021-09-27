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
package io.netty.buffer.api;

import io.netty.buffer.api.internal.CleanerDrop;
import io.netty.buffer.api.internal.Statics;

import java.util.function.Supplier;

import static io.netty.buffer.api.internal.Statics.NO_OP_DROP;
import static io.netty.buffer.api.internal.Statics.allocatorClosedException;

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
        return manager.allocateShared(this, size, createDrop(), Statics.CLEANER, allocationType);
    }

    Drop<Buffer> createDrop() {
        return CleanerDrop.wrap(manager.drop(), manager);
    }

    @Override
    public Supplier<Buffer> constBufferSupplier(byte[] bytes) {
        if (closed) {
            throw allocatorClosedException();
        }
        Buffer constantBuffer = manager.allocateShared(
                this, bytes.length, manager.drop(), Statics.CLEANER, allocationType);
        constantBuffer.writeBytes(bytes).makeReadOnly();
        return () -> manager.allocateConstChild(constantBuffer);
    }

    @Override
    public void close() {
        closed = true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public UntetheredMemory allocateUntethered(Buffer originator, int size) {
        Statics.assertValidBufferSize(size);
        var buf = manager.allocateShared(this, size, NO_OP_DROP, Statics.CLEANER, allocationType);
        return new UntetheredMemory() {
            @Override
            public <Memory> Memory memory() {
                return (Memory) manager.unwrapRecoverableMemory(buf);
            }

            @Override
            public <BufferType extends Buffer> Drop<BufferType> drop() {
                return (Drop<BufferType>) createDrop();
            }
        };
    }

    @Override
    public BufferAllocator getAllocator() {
        return this;
    }
}
