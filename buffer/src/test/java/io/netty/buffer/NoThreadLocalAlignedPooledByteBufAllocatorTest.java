/*
 * Copyright 2025 The Netty Project
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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class NoThreadLocalAlignedPooledByteBufAllocatorTest extends AlignedPooledByteBufAllocatorTest {

    @Override
    protected PooledByteBufAllocator newAllocator(boolean preferDirect) {
        assumeTrue(PooledByteBufAllocator.isDirectMemoryCacheAlignmentSupported());
        int directMemoryCacheAlignment = 1;
        return new PooledByteBufAllocator(
                preferDirect,
                PooledByteBufAllocator.defaultNumHeapArena(),
                PooledByteBufAllocator.defaultNumDirectArena(),
                PooledByteBufAllocator.defaultPageSize(),
                11,
                PooledByteBufAllocator.defaultSmallCacheSize(),
                64,
                PooledByteBufAllocator.defaultUseCacheForAllThreads(),
                directMemoryCacheAlignment,
                false);
    }

    @Override
    @Test
    public void testTrim() {
        PooledByteBufAllocator allocator = newAllocator(true);

        // Should return false as we never allocated from this thread yet.
        assertFalse(allocator.trimCurrentThreadCache());

        ByteBuf directBuffer = allocator.directBuffer();

        assertTrue(directBuffer.release());

        // Should return false as there is no thread-local cache used.
        assertFalse(allocator.trimCurrentThreadCache());
    }
}
