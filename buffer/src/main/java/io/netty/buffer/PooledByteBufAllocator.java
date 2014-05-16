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

package io.netty.buffer;

import java.util.concurrent.atomic.AtomicInteger;

public class PooledByteBufAllocator extends AbstractPooledByteBufAllocator {

    public static final DefaultPooledByteBufAllocator DEFAULT = new DefaultPooledByteBufAllocator();

    final ThreadLocal<PoolThreadCache> threadCache = new ThreadLocal<PoolThreadCache>() {
        private final AtomicInteger index = new AtomicInteger();
        @Override
        protected PoolThreadCache initialValue() {
            final int idx = index.getAndIncrement();
            return newCache(idx);
        }
    };

    protected PoolThreadCache cache() {
        return threadCache.get();
    }

    public PooledByteBufAllocator() {
        super(false);
    }

    public PooledByteBufAllocator(boolean preferDirect) {
        super(preferDirect);
    }

    public PooledByteBufAllocator(int nHeapArena, int nDirectArena, int pageSize, int maxOrder) {
        super(nHeapArena, nDirectArena, pageSize, maxOrder);
    }

    public PooledByteBufAllocator(boolean preferDirect, int nHeapArena, int nDirectArena, int pageSize, int maxOrder) {
        super(preferDirect);
    }
}
