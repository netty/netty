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
package io.netty.buffer;

import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * The {@link ByteBufAllocator} implementation that use fallback policy.
 */
public final class FallbackByteBufAllocator extends AbstractByteBufAllocator {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(FallbackByteBufAllocator.class);

    private final ByteBufAllocator allocator;
    private final boolean preferDirect;
    private final boolean canFallback;

    /**
     * Create a new instance which uses {@link ByteBufAllocator}.
     *
     * @param allocator the {@link ByteBufAllocator}
     */
    public FallbackByteBufAllocator(ByteBufAllocator allocator) {
        this(allocator, false, true);
    }

    /**
     * Create a new instance which uses {@link ByteBufAllocator} and preferDirect.
     *
     * @param allocator    the {@link ByteBufAllocator}
     * @param preferDirect {@code true} if {@link ByteBufAllocator} should try to allocate a direct buffer rather than
     *                     a heap buffer
     */
    public FallbackByteBufAllocator(ByteBufAllocator allocator, boolean preferDirect) {
        this(allocator, preferDirect, true);
    }

    /**
     * Create a new instance which uses {@link ByteBufAllocator}, preferDirect and canFallback.
     *
     * @param allocator    the {@link ByteBufAllocator}
     * @param preferDirect {@code true} if {@link ByteBufAllocator} should try to allocate a direct buffer rather than
     *                     a heap buffer
     * @param canFallback  {@code true} if {@link ByteBufAllocator} should try to allocate a heap buffer when fail to
     *                     allocate a direct buffer
     */
    public FallbackByteBufAllocator(ByteBufAllocator allocator, boolean preferDirect, boolean canFallback) {
        super(preferDirect);
        this.allocator = ObjectUtil.checkNotNull(allocator, "allocator");
        this.preferDirect = preferDirect;
        this.canFallback = canFallback;
    }

    @Override
    public ByteBuf buffer(int initialCapacity, int maxCapacity) {
        if (preferDirect) {
            return newDirectBuffer(initialCapacity, maxCapacity, canFallback);
        } else {
            return newHeapBuffer(initialCapacity, maxCapacity);
        }
    }

    @Override
    public ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
        return allocator.heapBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
        return newDirectBuffer(initialCapacity, maxCapacity, false);
    }

    private ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity, boolean canFallback) {

        try {
            return allocator.directBuffer(initialCapacity, maxCapacity);
        } catch (OutOfMemoryError e) {
            if (canFallback) {
                logger.error("allocate direct buffer error,fall back to heap", e);
                return allocator.heapBuffer(initialCapacity, maxCapacity);
            } else {
                throw e;
            }
        }
    }

    @Override
    public boolean isDirectBufferPooled() {
        return allocator.isDirectBufferPooled();
    }
}
