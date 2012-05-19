/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.ssl;

import java.nio.ByteBuffer;

import javax.net.ssl.SSLEngine;

public class DefaultSslBufferPool implements SslBufferPool {

    private static final int DEFAULT_POOL_SIZE = MAX_PACKET_SIZE * 1024;

    private final ByteBuffer[] pool;
    private final int maxBufferCount;
    private int index;

    /**
     * Creates a new buffer pool whose size is {@code 18113536}, which can
     * hold {@code 1024} buffers.
     */
    public DefaultSslBufferPool() {
        this(DEFAULT_POOL_SIZE);
    }

    /**
     * Creates a new buffer pool.
     *
     * @param maxPoolSize the maximum number of bytes that this pool can hold
     */
    public DefaultSslBufferPool(int maxPoolSize) {
        if (maxPoolSize <= 0) {
            throw new IllegalArgumentException("maxPoolSize: " + maxPoolSize);
        }

        int maxBufferCount = maxPoolSize / MAX_PACKET_SIZE;
        if (maxPoolSize % MAX_PACKET_SIZE != 0) {
            maxBufferCount ++;
        }

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
     * Returns the number of bytes which were allocated but have not been
     * acquired yet.  You can estimate how optimal the specified maximum pool
     * size is from this value.  If it keeps returning {@code 0}, it means the
     * pool is getting exhausted.  If it keeps returns a unnecessarily big
     * value, it means the pool is wasting the heap space.
     */
    public synchronized int getUnacquiredPoolSize() {
        return index * MAX_PACKET_SIZE;
    }

    public synchronized ByteBuffer acquireBuffer() {
        if (index == 0) {
            return ByteBuffer.allocate(MAX_PACKET_SIZE);
        } else {
            return (ByteBuffer) pool[-- index].clear();
        }
    }

    public synchronized void releaseBuffer(ByteBuffer buffer) {
        if (index < maxBufferCount) {
            pool[index ++] = buffer;
        }
    }
}
