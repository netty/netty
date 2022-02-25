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
package io.netty5.buffer.api.pool;

import io.netty5.util.internal.StringUtil;

import java.util.List;

/**
 * Exposed metric for {@link PooledBufferAllocator}.
 */
final class PooledBufferAllocatorMetric implements BufferAllocatorMetric {

    private final PooledBufferAllocator allocator;

    PooledBufferAllocatorMetric(PooledBufferAllocator allocator) {
        this.allocator = allocator;
    }

    @Override
    public int numArenas() {
        return allocator.numArenas();
    }

    @Override
    public List<PoolArenaMetric> arenaMetrics() {
        return allocator.arenaMetrics();
    }

    @Override
    public int numThreadLocalCaches() {
        return allocator.numThreadLocalCaches();
    }

    @Override
    public int smallCacheSize() {
        return allocator.smallCacheSize();
    }

    @Override
    public int normalCacheSize() {
        return allocator.normalCacheSize();
    }

    @Override
    public int chunkSize() {
        return allocator.chunkSize();
    }

    @Override
    public long usedMemory() {
        return allocator.usedMemory();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(256);
        sb.append(StringUtil.simpleClassName(this))
                .append("(usedMemory: ").append(usedMemory())
                .append("; numArenas: ").append(numArenas())
                .append("; smallCacheSize: ").append(smallCacheSize())
                .append("; normalCacheSize: ").append(normalCacheSize())
                .append("; numThreadLocalCaches: ").append(numThreadLocalCaches())
                .append("; chunkSize: ").append(chunkSize()).append(')');
        return sb.toString();
    }
}
