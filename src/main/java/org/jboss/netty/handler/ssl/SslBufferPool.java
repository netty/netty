/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.ssl;

import javax.net.ssl.SSLEngine;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link ByteBuffer} pool dedicated for {@link SslHandler} performance
 * improvement.
 * <p>
 * In most cases, you won't need to create a new pool instance because
 * {@link SslHandler} has a default pool instance internally.
 * <p>
 * The reason why {@link SslHandler} requires a buffer pool is because the
 * current {@link SSLEngine} implementation always requires a 17KiB buffer for
 * every 'wrap' and 'unwrap' operation.  In most cases, the actual size of the
 * required buffer is much smaller than that, and therefore allocating a 17KiB
 * buffer for every 'wrap' and 'unwrap' operation wastes a lot of memory
 * bandwidth, resulting in the application performance degradation.
 */
public class SslBufferPool {

    // Add 1024 as a room for compressed data and another 1024 for Apache Harmony compatibility.
    private static final int MAX_PACKET_SIZE_ALIGNED = (OpenSslEngine.MAX_ENCRYPTED_PACKET_LENGTH / 128 + 1) * 128;

    private static final int DEFAULT_POOL_SIZE = MAX_PACKET_SIZE_ALIGNED * 1024;

    private final ByteBuffer preallocated;
    private final BlockingQueue<ByteBuffer> pool;
    private final int maxBufferCount;
    private final boolean allocateDirect;

    /**
     * The number of buffers allocated so far. Used only when {@link #preallocated} is null.
     */
    private final AtomicInteger numAllocations;

    /**
     * Creates a new buffer pool whose size is {@code 19267584}, which can
     * hold {@code 1024} buffers.
     */
    public SslBufferPool() {
        this(DEFAULT_POOL_SIZE);
    }

    /**
     * Creates a new buffer pool whose size is {@code 19267584}, which can
     * hold {@code 1024} buffers.
     */
    public SslBufferPool(boolean preallocate, boolean allocateDirect) {
        this(DEFAULT_POOL_SIZE, preallocate, allocateDirect);
    }

    /**
     * Creates a new buffer pool.
     *
     * @param maxPoolSize the maximum number of bytes that this pool can hold
     */
    public SslBufferPool(int maxPoolSize) {
        this(maxPoolSize, false, false);
    }

    /**
     * Creates a new buffer pool.
     *
     * @param maxPoolSize the maximum number of bytes that this pool can hold
     */
    public SslBufferPool(int maxPoolSize, boolean preallocate, boolean allocateDirect) {
        if (maxPoolSize <= 0) {
            throw new IllegalArgumentException("maxPoolSize: " + maxPoolSize);
        }

        int maxBufferCount = maxPoolSize / MAX_PACKET_SIZE_ALIGNED;
        if (maxPoolSize % MAX_PACKET_SIZE_ALIGNED != 0) {
            maxBufferCount ++;
        }

        this.maxBufferCount = maxBufferCount;
        this.allocateDirect = allocateDirect;

        pool = new ArrayBlockingQueue<ByteBuffer>(maxBufferCount);

        if (preallocate) {
            preallocated = allocate(maxBufferCount * MAX_PACKET_SIZE_ALIGNED);
            numAllocations = null;
            for (int i = 0; i < maxBufferCount; i ++) {
                int pos = i * MAX_PACKET_SIZE_ALIGNED;
                preallocated.clear().position(pos).limit(pos + MAX_PACKET_SIZE_ALIGNED);
                pool.add(preallocated.slice());
            }
        } else {
            preallocated = null;
            numAllocations = new AtomicInteger();
        }
    }

    /**
     * Returns the maximum size of this pool in byte unit.  The returned value
     * can be somewhat different from what was specified in the constructor.
     */
    public int getMaxPoolSize() {
        return maxBufferCount * MAX_PACKET_SIZE_ALIGNED;
    }

    /**
     * Returns the number of bytes which were allocated but have not been
     * acquired yet.  You can estimate how optimal the specified maximum pool
     * size is from this value.  If it keeps returning {@code 0}, it means the
     * pool is getting exhausted.  If it keeps returns a unnecessarily big
     * value, it means the pool is wasting the heap space.
     */
    public int getUnacquiredPoolSize() {
        return pool.size() * MAX_PACKET_SIZE_ALIGNED;
    }

    /**
     * Acquire a new {@link ByteBuffer} out of the {@link SslBufferPool}
     *
     */
    public ByteBuffer acquireBuffer() {
        ByteBuffer buf;
        if (preallocated != null || numAllocations.get() >= maxBufferCount) {
            boolean interrupted = false;
            for (;;) {
                try {
                    buf = pool.take();
                    break;
                } catch (InterruptedException ignore) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        } else {
            buf = pool.poll();
            if (buf == null) {
                // Note that we can allocate more buffers than maxBufferCount.
                // We will discard the buffers allocated after numAllocations reached maxBufferCount in releaseBuffer().
                numAllocations.incrementAndGet();
                buf = allocate(OpenSslEngine.MAX_ENCRYPTED_PACKET_LENGTH);
            }
        }

        buf.clear();
        return buf;
    }

    /**
     * Release a previous acquired {@link ByteBuffer}
     */
    public void releaseBuffer(ByteBuffer buffer) {
        pool.offer(buffer);
    }

    private ByteBuffer allocate(int capacity) {
        if (allocateDirect) {
            return ByteBuffer.allocateDirect(capacity);
        } else {
            return ByteBuffer.allocate(capacity);
        }
    }
}
