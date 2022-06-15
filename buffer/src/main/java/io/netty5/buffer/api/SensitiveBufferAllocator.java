/*
 * Copyright 2022 The Netty Project
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

import io.netty5.buffer.api.internal.ArcDrop;
import io.netty5.buffer.api.internal.CleanerDrop;
import io.netty5.buffer.api.internal.MemoryManagerOverride;

import java.nio.ByteBuffer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * This {@link BufferAllocator} is for allocating {@linkplain StandardAllocationTypes#OFF_HEAP off-heap}
 * {@link Buffer}s that may contain sensitive information, which should be erased from memory (overwritten) when the
 * buffer is closed.
 * <p>
 * The features and behaviours of this allocator are otherwise exactly the same as
 * {@link BufferAllocator#offHeapUnpooled()}.
 * <p>
 * Of particular note, the byte arrays passed to {@link BufferAllocator#copyOf(byte[])}
 * and {@link BufferAllocator#constBufferSupplier(byte[])}, and the {@linkplain ByteBuffer buffer} passed to
 * {@link BufferAllocator#copyOf(ByteBuffer)} are <strong>not</strong> cleared by this allocator.
 * These copies of the sensitive data must be cleared separately!
 * Ideally these methods should not be used for sensitive data in the first place, and the sensitive data should
 * instead be directly written to, or created in, a sensitive buffer.
 * <p>
 * This allocator is stateless, and the {@link #close()} method has no effect.
 */
public final class SensitiveBufferAllocator implements BufferAllocator {
    private static final BufferAllocator INSTANCE = new SensitiveBufferAllocator(null);
    private final AllocatorControl control = () -> this;
    private final Function<Drop<Buffer>, Drop<Buffer>> decorator = this::decorate;
    private final MemoryManager manager;

    /**
     * Get the sensitive off-heap buffer allocator instance.
     *
     * @return The allocator.
     */
    public static BufferAllocator sensitiveOffHeapAllocator() {
        MemoryManager memoryManagerOverride = MemoryManagerOverride.configuredOrDefaultManager(null);
        if (memoryManagerOverride != null) {
            return new SensitiveBufferAllocator(memoryManagerOverride);
        }
        return INSTANCE;
    }

    private SensitiveBufferAllocator(MemoryManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean isPooling() {
        return false;
    }

    @Override
    public AllocationType getAllocationType() {
        return StandardAllocationTypes.OFF_HEAP;
    }

    @Override
    public Buffer allocate(int size) {
        MemoryManager manager = getManager();
        return manager.allocateShared(control, size, decorator, getAllocationType());
    }

    private Drop<Buffer> decorate(Drop<Buffer> base) {
        MemoryManager manager = getManager();
        return CleanerDrop.wrap(ArcDrop.wrap(new ZeroingDrop(manager, control, base)), manager);
    }

    private MemoryManager getManager() {
        if (manager != null) {
            return MemoryManagerOverride.configuredOrDefaultManager(manager);
        }
        return MemoryManager.instance();
    }

    @Override
    public Supplier<Buffer> constBufferSupplier(byte[] bytes) {
        Buffer origin = copyOf(bytes).makeReadOnly();
        return () -> origin.copy(true);
    }

    @Override
    public void close() {
    }

    private static final class ZeroingDrop implements Drop<Buffer> {
        private final MemoryManager manager;
        private final Drop<Buffer> base;
        private final AllocatorControl control;

        ZeroingDrop(MemoryManager manager, AllocatorControl control, Drop<Buffer> base) {
            this.manager = manager;
            this.control = control;
            this.base = base;
        }

        @Override
        public void drop(Buffer obj) {
            // The given buffer object might only be a small piece of the original buffer, due to split() calls.
            // We go through the memory recovery process in order to get back the full memory allocation.
            Object memory = manager.unwrapRecoverableMemory(obj);
            manager.clearMemory(memory);
            base.drop(obj);
        }

        @Override
        public Drop<Buffer> fork() {
            // ZeroingDrop should be guarded by an ArcDrop, because we can only zero after we're sure
            // there is no more structural sharing of the memory!
            throw new UnsupportedOperationException();
        }

        @Override
        public void attach(Buffer obj) {
            base.attach(obj);
        }

        @Override
        public String toString() {
            return "ZeroingDrop(" + base + ')';
        }
    }
}
