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

import io.netty.buffer.api.pool.PooledBufferAllocator;

import java.util.function.Supplier;

/**
 * Interface for allocating {@link Buffer}s.
 */
public interface BufferAllocator extends AutoCloseable {
    /**
     * Produces a {@link BufferAllocator} that allocates unpooled, on-heap buffers.
     * On-heap buffers have a {@code byte[]} internally, and their {@linkplain Buffer#nativeAddress() native address}
     * is zero.
     * <p>
     * The concrete {@link Buffer} implementation is chosen by {@link MemoryManager#instance()}.
     *
     * @return A non-pooling allocator of on-heap buffers
     */
    static BufferAllocator heap() {
        return new ManagedBufferAllocator(MemoryManager.instance(), false);
    }

    /**
     * Produces a {@link BufferAllocator} that allocates unpooled, off-heap buffers.
     * Off-heap buffers a native memory pointer internally, which can be obtained from their
     * {@linkplain Buffer#nativeAddress() native address method.
     * <p>
     * The concrete {@link Buffer} implementation is chosen by {@link MemoryManager#instance()}.
     *
     * @return A non-pooling allocator of on-heap buffers
     */
    static BufferAllocator direct() {
        return new ManagedBufferAllocator(MemoryManager.instance(), true);
    }

    /**
     * Produces a pooling {@link BufferAllocator} that allocates and recycles on-heap buffers.
     * On-heap buffers have a {@code byte[]} internally, and their {@linkplain Buffer#nativeAddress() native address}
     * is zero.
     * <p>
     * The concrete {@link Buffer} implementation is chosen by {@link MemoryManager#instance()}.
     *
     * @return A pooling allocator of on-heap buffers
     */
    static BufferAllocator pooledHeap() {
        return new PooledBufferAllocator(MemoryManager.instance(), false);
    }

    /**
     * Produces a pooling {@link BufferAllocator} that allocates and recycles off-heap buffers.
     * Off-heap buffers a native memory pointer internally, which can be obtained from their
     * {@linkplain Buffer#nativeAddress() native address method.
     * <p>
     * The concrete {@link Buffer} implementation is chosen by {@link MemoryManager#instance()}.
     *
     * @return A pooling allocator of on-heap buffers
     */
    static BufferAllocator pooledDirect() {
        return new PooledBufferAllocator(MemoryManager.instance(), true);
    }

    /**
     * Allocate a {@link Buffer} of the given size in bytes. This method may throw an {@link OutOfMemoryError} if there
     * is not enough free memory available to allocate a {@link Buffer} of the requested size.
     * <p>
     * The buffer will use big endian byte order.
     *
     * @param size The size of {@link Buffer} to allocate.
     * @return The newly allocated {@link Buffer}.
     * @throws IllegalStateException if this allocator has been {@linkplain #close() closed}.
     */
    Buffer allocate(int size);

    /**
     * Create a supplier of "constant" {@linkplain Buffer Buffers} from this allocator, that all have the given
     * byte contents. The buffer has the same capacity as the byte array length, and its write offset is placed at the
     * end, and its read offset is at the beginning, such that the entire buffer contents are readable.
     * <p>
     * The buffers produced by the supplier will each have their own independent life-cycle, and closing them will
     * make them {@linkplain Buffer#isAccessible() inaccessible}, just like normally allocated buffers.
     * <p>
     * The buffers produced are "constants", in the sense that they are {@linkplain Buffer#readOnly() read-only}.
     * <p>
     * It can generally be expected, but is not guaranteed, that the returned supplier is more resource efficient than
     * allocating and copying memory with other available APIs. In such optimised implementations, the underlying memory
     * baking the buffers will be shared among all the buffers produced by the supplier.
     * <p>
     * The primary use case for this API, is when you need to repeatedly produce buffers with the same contents, and
     * you perhaps wish to keep a {@code static final} field with these contents. The supplier-based API enforces
     * that each usage get their own distinct buffer instance. Each of these instances cannot interfere with each other,
     * so bugs like closing, or modifying the contents, of a shared buffer cannot occur.
     *
     * @param bytes The byte contents of the buffers produced by the returned supplier.
     * @return A supplier of read-only buffers with the given contents.
     * @throws IllegalStateException if this allocator has been {@linkplain #close() closed}, but any supplier obtained
     * prior to closing the allocator will continue to work.
     */
    Supplier<Buffer> constBufferSupplier(byte[] bytes);

    /**
     * Close this allocator, freeing all of its internal resources.
     * <p>
     * Existing (currently in-use) allocated buffers will not be impacted by calling this method.
     * If this is a pooling or caching allocator, then existing buffers will be immediately freed when they are closed,
     * instead of being pooled or cached.
     * <p>
     * The allocator can no longer be used to allocate more buffers after calling this method.
     * Attempting to allocate from a closed allocator will cause {@link IllegalStateException}s to be thrown.
     */
    @Override
    void close();
}
