/*
 * Copyright 2022 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty5.buffer.api;

import io.netty5.util.internal.PlatformDependent;
import io.netty5.util.internal.SystemPropertyUtil;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.util.Locale;
import java.util.function.Supplier;

import static io.netty5.util.internal.PlatformDependent.directBufferPreferred;
import static java.lang.Runtime.getRuntime;

/**
 * Accessor for the default, shared {@link BufferAllocator} instances.
 * Two allocators are provided; one on-heap allocator, and one off-heap allocator.
 * <p>
 * These default allocators are configured via system properties.
 * <p>
 * These allocators cannot be {@linkplain Buffer#close() closed} directly.
 * They will instead be disposed of when the {@link Runtime} is shutdown.
 */
public final class DefaultBufferAllocators {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultBufferAllocators.class);
    private static final BufferAllocator DEFAULT_PREFERRED_ALLOCATOR;
    private static final BufferAllocator DEFAULT_ON_HEAP_ALLOCATOR;
    private static final BufferAllocator DEFAULT_OFF_HEAP_ALLOCATOR;

    static {
        String allocType = SystemPropertyUtil.get(
                "io.netty5.allocator.type", PlatformDependent.isAndroid() ? "unpooled" : "pooled");
        allocType = allocType.toLowerCase(Locale.US).trim();
        boolean directBufferPreferred = directBufferPreferred();

        final BufferAllocator onHeap;
        final BufferAllocator offHeap;
        if ("unpooled".equals(allocType)) {
            onHeap = BufferAllocator.onHeapUnpooled();
            offHeap = BufferAllocator.offHeapUnpooled();
            logger.debug("-Dio.netty5.allocator.type: {}", allocType);
        } else if ("pooled".equals(allocType)) {
            onHeap = BufferAllocator.onHeapPooled();
            offHeap = BufferAllocator.offHeapPooled();
            logger.debug("-Dio.netty5.allocator.type: {}", allocType);
        } else {
            onHeap = BufferAllocator.onHeapPooled();
            offHeap = BufferAllocator.offHeapPooled();
            logger.debug("-Dio.netty5.allocator.type: pooled (unknown: {})", allocType);
        }
        getRuntime().addShutdownHook(new Thread(() -> {
            //noinspection EmptyTryBlock
            try (onHeap; offHeap) {
                // Left blank.
            }
        }));
        UncloseableBufferAllocator onHeapUnclosable = new UncloseableBufferAllocator(onHeap);
        UncloseableBufferAllocator offHeapUnclosable = new UncloseableBufferAllocator(offHeap);
        DEFAULT_PREFERRED_ALLOCATOR = directBufferPreferred? offHeapUnclosable : onHeapUnclosable;
        DEFAULT_ON_HEAP_ALLOCATOR = onHeapUnclosable;
        DEFAULT_OFF_HEAP_ALLOCATOR = offHeapUnclosable;
    }

    private DefaultBufferAllocators() {
    }

    /**
     * Get the preferred, shared allocator.
     * This allocator is either on- or off-heap, and either pooling or unpooled, depending on the global configuration.
     *
     * @return The shared, generally preferred allocator.
     */
    public static BufferAllocator preferredAllocator() {
        return DEFAULT_PREFERRED_ALLOCATOR;
    }

    /**
     * Get the shared on-heap allocator.
     * This allocator always allocates on-heap buffers, and is either pooled, or unpooled.
     *
     * @return The shared on-heap allocator.
     */
    public static BufferAllocator onHeapAllocator() {
        return DEFAULT_ON_HEAP_ALLOCATOR;
    }

    /**
     * Get the shared off-heap allocator.
     * This allocator always allocates off-heap buffers, and is either pooled, or unpooled.
     *
     * @return The shared off-heap allocator.
     */
    public static BufferAllocator offHeapAllocator() {
        return DEFAULT_OFF_HEAP_ALLOCATOR;
    }

    private static final class UncloseableBufferAllocator implements BufferAllocator {
        private final BufferAllocator delegate;

        UncloseableBufferAllocator(BufferAllocator delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isPooling() {
            return delegate.isPooling();
        }

        @Override
        public AllocationType getAllocationType() {
            return delegate.getAllocationType();
        }

        @Override
        public Buffer allocate(int size) {
            return delegate.allocate(size);
        }

        @Override
        public Supplier<Buffer> constBufferSupplier(byte[] bytes) {
            return delegate.constBufferSupplier(bytes);
        }

        /**
         * @throws UnsupportedOperationException Close is not supported on this allocator.
         */
        @Override
        public void close() {
            throw new UnsupportedOperationException("Global default buffer allocator can not be closed explicitly.");
        }
    }
}
