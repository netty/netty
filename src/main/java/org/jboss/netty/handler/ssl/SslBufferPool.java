/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.handler.ssl;

import java.nio.ByteBuffer;

import javax.net.ssl.SSLEngine;

/**
 * A {@link ByteBuffer} pool dedicated for {@link SslHandler} performance
 * improvement.
 * <p>
 * In most cases, you won't need to create a new pool instance because
 * {@link SslHandler} has a default pool instance internally.
 * <p>
 * The reason why {@link SslHandler} requires a buffer pool is because the
 * current {@link SSLEngine} implementation always requires a 17KiB buffer for
 * the 'wrap' and 'unwrap' operation.  In most cases, the size of the required
 * buffer is much smaller than that, and therefore allocating a 17KiB buffer
 * for every 'wrap' and 'unwrap' operation wastes a lot of memory bandwidth,
 * resulting in the application performance degradation.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 */
public class SslBufferPool {

    // Add 1024 as a room for compressed data.
    private static final int MAX_PACKET_SIZE = 16665 + 1024;
    private static final int DEFAULT_POOL_SIZE = MAX_PACKET_SIZE * 1024;

    private final ByteBuffer[] pool;
    private final int maxBufferCount;
    private int index;

    /**
     * Creates a new buffer pool whose size is {@code 18113536}, which can
     * hold {@code 1024} buffers.
     */
    public SslBufferPool() {
        this(DEFAULT_POOL_SIZE);
    }

    /**
     * Creates a new buffer pool.
     *
     * @param maxPoolSize the maximum number of bytes that this pool can hold
     */
    public SslBufferPool(int maxPoolSize) {
        if (maxPoolSize <= 0) {
            throw new IllegalArgumentException("maxPoolSize: " + maxPoolSize);
        }

        int maxBufferCount = maxPoolSize / MAX_PACKET_SIZE;
        if (maxPoolSize % MAX_PACKET_SIZE != 0) {
            maxBufferCount ++;
        }
        maxPoolSize = maxBufferCount * MAX_PACKET_SIZE;

        pool = new ByteBuffer[maxBufferCount];
        this.maxBufferCount = maxBufferCount;
    }

    /**
     * Returns the maximum size of this pool in byte unit.  The returned value
     * can be somewhat different from what was specified in the constructor.
     */
    public int getMaxPoolSize() {
        return maxBufferCount * MAX_PACKET_SIZE;
    }

    /**
     * Returns the number of bytes which were allocated but not acquired yet.
     * You can estimate how optimal the specified maximum pool size is from
     * this value.  If it keeps returning {@code 0}, it means the pool is
     * getting exhausted.  If it keeps returns a unnecessarily big value, it
     * means the pool is wasting the heap space.
     */
    public synchronized int getUnacquiredPoolSize() {
        return index * MAX_PACKET_SIZE;
    }

    synchronized ByteBuffer acquire() {
        if (index == 0) {
            return ByteBuffer.allocate(MAX_PACKET_SIZE);
        } else {
            return (ByteBuffer) pool[-- index].clear();
        }
    }

    synchronized void release(ByteBuffer buffer) {
        if (index < maxBufferCount) {
            pool[index ++] = buffer;
        }
    }
}
