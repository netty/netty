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
package io.netty5.buffer.pool;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.Drop;

class PooledDrop implements Drop<Buffer> {
    private final Drop<Buffer> baseDrop;
    private final PoolChunk chunk;
    private final PoolThreadCache threadCache;
    private final long handle;
    private final int normSize;

    PooledDrop(PoolChunk chunk, PoolThreadCache threadCache, long handle, int normSize) {
        baseDrop = chunk.baseDrop.fork();
        this.chunk = chunk;
        this.threadCache = threadCache;
        this.handle = handle;
        this.normSize = normSize;
    }

    @Override
    public void drop(Buffer obj) {
        chunk.arena.free(chunk, handle, normSize, threadCache);
        baseDrop.drop(chunk.base);
    }

    @Override
    public Drop<Buffer> fork() {
        throw new IllegalStateException(this + " cannot fork. Must be guarded by an ArcDrop.");
    }

    @Override
    public void attach(Buffer obj) {
        baseDrop.attach(chunk.base);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder()
                .append("PooledDrop@")
                .append(Integer.toHexString(System.identityHashCode(this)))
                .append('(')
                .append(chunk)
                .append(", ")
                .append(baseDrop)
                .append(')');
        return sb.toString();
    }
}
