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

import io.netty5.buffer.pool.PooledBufferAllocator;
import io.netty5.util.SafeCloseable;
import io.netty5.util.Send;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.function.Supplier;

/**
 * Interface for allocating {@link Buffer}s.
 */
public interface BufferAllocator extends SafeCloseable {
    /**
     * Produces a {@link BufferAllocator} that allocates unpooled, on-heap buffers.
     * On-heap buffers have a {@code byte[]} internally, and their
     * {@linkplain BufferComponent#readableNativeAddress() buffer component} native addresses are zero.
     * <p>
     * The concrete {@link Buffer} implementation is chosen by {@link MemoryManager#instance()}.
     * <p>
     * <strong>Note:</strong> This method always creates a new allocator instance.
     * To get a shared and cached allocator instance, use the {@link DefaultBufferAllocators#onHeapAllocator()}
     * method instead.
     *
     * @return A non-pooling allocator of on-heap buffers
     */
    static BufferAllocator onHeapUnpooled() {
        return new ManagedBufferAllocator(MemoryManager.instance(), false);
    }

    /**
     * Produces a {@link BufferAllocator} that allocates unpooled, off-heap buffers.
     * Off-heap buffers a native memory pointer internally, which can be obtained from their
     * {@linkplain BufferComponent#readableNativeAddress() buffer component} native address methods.
     * <p>
     * The concrete {@link Buffer} implementation is chosen by {@link MemoryManager#instance()}.
     * <p>
     * <strong>Note:</strong> This method always creates a new allocator instance.
     * To get a shared and cached allocator instance, use the {@link DefaultBufferAllocators#offHeapAllocator()}
     * method instead.
     *
     * @return A non-pooling allocator of on-heap buffers
     */
    static BufferAllocator offHeapUnpooled() {
        return new ManagedBufferAllocator(MemoryManager.instance(), true);
    }

    /**
     * Produces a pooling {@link BufferAllocator} that allocates and recycles on-heap buffers.
     * On-heap buffers have a {@code byte[]} internally, and their
     * {@linkplain BufferComponent#readableNativeAddress() buffer component} native addresses are zero.
     * <p>
     * The concrete {@link Buffer} implementation is chosen by {@link MemoryManager#instance()}.
     * <p>
     * <strong>Note:</strong> This method always creates a new allocator instance.
     * To get a shared and cached allocator instance, use the {@link DefaultBufferAllocators#onHeapAllocator()}
     * method instead.
     *
     * @return A pooling allocator of on-heap buffers
     */
    static BufferAllocator onHeapPooled() {
        return new PooledBufferAllocator(MemoryManager.instance(), false);
    }

    /**
     * Produces a pooling {@link BufferAllocator} that allocates and recycles off-heap buffers.
     * Off-heap buffers a native memory pointer internally, which can be obtained from their
     * {@linkplain BufferComponent#readableNativeAddress() buffer component} native address methods.
     * <p>
     * The concrete {@link Buffer} implementation is chosen by {@link MemoryManager#instance()}.
     * <p>
     * <strong>Note:</strong> This method always creates a new allocator instance.
     * To get a shared and cached allocator instance, use the {@link DefaultBufferAllocators#offHeapAllocator()}
     * method instead.
     *
     * @return A pooling allocator of on-heap buffers
     */
    static BufferAllocator offHeapPooled() {
        return new PooledBufferAllocator(MemoryManager.instance(), true);
    }

    /**
     * Determine if this allocator is pooling and reusing its allocated memory.
     *
     * @return {@code true} if this allocator is pooling and reusing its memory, {@code false} otherwise.
     */
    boolean isPooling();

    /**
     * Get the {@link AllocationType} from this allocator.
     * This would typically be one of the {@link StandardAllocationTypes}.
     *
     * @return The type of allocations performed by this allocator.
     */
    AllocationType getAllocationType();

    /**
     * Allocate a {@link Buffer} of the given size in bytes. This method may throw an {@link OutOfMemoryError} if there
     * is not enough free memory available to allocate a {@link Buffer} of the requested size.
     * <p>
     * The buffer will use big endian byte order.
     * <p>
     * <strong>Note:</strong> unlike the JDK {@link ByteBuffer}s, Netty {@code Buffers} are not guaranteed to be zeroed
     * when allocated. In other words, the memory of a newly allocated buffer may contain garbage data from prior
     * allocations, and the memory is likewise not guaranteed to be erased when the buffer is closed.
     * If the data is sensitive and needs to be overwritten when the buffer is closed,
     * then the buffer should be allocated with the {@link SensitiveBufferAllocator}.
     *
     * @param size The size of {@link Buffer} to allocate.
     * @return The newly allocated {@link Buffer}.
     * @throws IllegalStateException if this allocator has been {@linkplain #close() closed}.
     */
    Buffer allocate(int size);

    /**
     * Compose the Send of a buffer and present them as a single buffer.
     * <p>
     * When a composite buffer is closed, all of its constituent component buffers are closed as well.
     * <p>
     * See the class documentation for more information on how buffers compose, and what is required of the given
     * buffers for composition to be allowed.
     *
     * @param send The sent buffer to compose into a single buffer view.
     * @return A buffer composed of, and backed by, the given buffers.
     * @throws IllegalStateException if one of the sends have already been received. The remaining buffers and sends
     * will be closed and discarded, respectively.
     */
    default CompositeBuffer compose(Send<Buffer> send) {
        return DefaultCompositeBuffer.compose(this, Collections.singleton(send));
    }

    /**
     * Compose the given sequence of Sends of buffers and present them as a single buffer.
     * <p>
     * When a composite buffer is closed, all of its constituent component buffers are closed as well.
     * <p>
     * See the class documentation for more information on how buffers compose, and what is required of the given
     * buffers for composition to be allowed.
     *
     * @param sends The sent buffers to compose into a single buffer view.
     * @return A buffer composed of, and backed by, the given buffers.
     * @throws IllegalStateException if one of the sends have already been received. The remaining buffers and sends
     * will be closed and discarded, respectively.
     */
    default CompositeBuffer compose(Iterable<Send<Buffer>> sends) {
        return DefaultCompositeBuffer.compose(this, sends);
    }

    /**
     * Create an empty composite buffer, that has no components. The buffer can be extended with components using either
     * {@link CompositeBuffer#ensureWritable(int)} or {@link CompositeBuffer#extendWith(Send)}.
     * @return A composite buffer that has no components, and has a capacity of zero.
     */
    default CompositeBuffer compose() {
        return DefaultCompositeBuffer.compose(this);
    }

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
     * Allocate a {@link Buffer} with the same size and contents of the given byte array.
     * The allocated buffer is <em>NOT</em> backed by the given byte array, and changes to the contents of either
     * will not be reflected in the other.
     * This may throw an {@link OutOfMemoryError} if there is not enough free memory available to allocate a
     * {@link Buffer} of the requested size.
     * <p>
     * The allocated buffer will use big endian byte order.
     *
     * @param bytes The byte array that determines the size and contents of the new buffer.
     * @return The newly allocated {@link Buffer}.
     * @throws IllegalStateException if this allocator has been {@linkplain #close() closed}.
     */
    default Buffer copyOf(byte[] bytes) {
        return allocate(bytes.length).writeBytes(bytes);
    }

    /**
     * Allocate a {@link Buffer} with the same size and contents of the given {@link String},
     * when interpreted as a sequence of bytes with the given {@link Charset}.
     * This may throw an {@link OutOfMemoryError} if there is not enough free memory available to allocate a
     * {@link Buffer} of the requested size.
     * <p>
     * The allocated buffer will use big endian byte order.
     *
     * @param str The {@link String} that determines the size and contents of the new buffer.
     * @param charset The {@link Charset} that determines how to turn the string into a sequence of bytes.
     * @return The newly allocated {@link Buffer}.
     * @throws IllegalStateException if this allocator has been {@linkplain #close() closed}.
     */
    default Buffer copyOf(String str, Charset charset) {
        byte[] bytes = str.getBytes(charset);
        return allocate(bytes.length).writeBytes(bytes);
    }

    /**
     * Allocate a {@link Buffer} with the same size and contents, as the contents of the given {@link ByteBuffer}.
     * The allocated buffer is <em>NOT</em> backed by the give byte buffer, and changes to the contents of either
     * will not be reflected in the other.
     * The position and limit of the given byte buffer defines the region of contents that will be copied.
     * The position and limit are not modified by this method.
     * This may throw an {@link OutOfMemoryError} if there is not enough free memory available to allocate a
     * {@link Buffer} of the requested size.
     * <p>
     * The allocated buffer will use big endian byte order, regardless of the byte order of the given buffer.
     * The contents of the byte buffer will be copied without regard to the byte order, as if the bytes are copied
     * one at a time.
     *
     * @param buffer The byte buffer, whose contents determine the capacity and contents of the allocated buffer.
     * @return The newly allocated {@link Buffer}.
     * @throws IllegalStateException if this allocator has been {@linkplain #close() closed}.
     */
    default Buffer copyOf(ByteBuffer buffer) {
        if (!buffer.hasRemaining()) {
            return allocate(0);
        }
        int bytesToCopy = buffer.remaining();
        final Buffer copy = allocate(bytesToCopy);
        final ByteBuffer duplicate = buffer.duplicate();
        try (var iteration = copy.forEachComponent()) {
            for (var c = iteration.firstWritable(); c != null; c = c.nextWritable()) {
                ByteBuffer dest = c.writableBuffer();
                int length = Math.min(dest.capacity(), duplicate.remaining());
                dest.put(0, duplicate, duplicate.position(), length);
                duplicate.position(length + duplicate.position());
            }
        }
        copy.skipWritableBytes(bytesToCopy);
        return copy;
    }

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
