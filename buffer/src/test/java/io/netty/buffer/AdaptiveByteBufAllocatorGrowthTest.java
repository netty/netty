/*
 * Copyright 2026 The Netty Project
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

import io.netty.util.NettyRuntime;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.test.DisabledForSlowLeakDetection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Isolated("Uses a large amount of heap memory, so we don't want it to run concurrently with other allocator tests")
public class AdaptiveByteBufAllocatorGrowthTest {
    private static final int THREAD_COUNT = Math.max(4, NettyRuntime.availableProcessors() * 2);
    private static final ExecutorService THREAD_POOL = Executors.newFixedThreadPool(THREAD_COUNT,
            new DefaultThreadFactory("AdaptiveAllocatorGrowthTest", true));
    private static AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(false);

    @AfterAll
    static void cleanUp() {
        allocator = null;
        THREAD_POOL.shutdown();
    }

    @DisabledForSlowLeakDetection
    @RepeatedTest(400)
    void concurrentBufferAllocateAndGrowth(RepetitionInfo info) throws Exception {
        // This test targets data races where Chunk.remainingCapacity() is called concurrently
        // with other operations on the chunk. It is important that calling this method does not
        // modify or corrupt the state of the chunks.

        final int bufSizeBase;
        final int bufSizeAdditional;
        final int bufSizeGrowth;
        if ((info.getCurrentRepetition() & 1) == 0) {
            // Target large buffers.
            bufSizeBase = 17000;
            bufSizeAdditional = 50000;
            bufSizeGrowth = 80000;
        } else {
            // Target small buffers.
            bufSizeBase = 64;
            bufSizeAdditional = 512;
            bufSizeGrowth = 1024;
        }

        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>(THREAD_COUNT);

        for (int t = 0; t < threadCount; t++) {
            futures.add(THREAD_POOL.submit(() -> {
                startLatch.await();
                SplittableRandom rng = new SplittableRandom();
                for (int i = 0; i < 2000; i++) {
                    // Allocate buffers in various sizes.
                    // In the BuddyChunk, we'll exercise different buddy tree levels.
                    int initialSize = bufSizeBase + rng.nextInt(bufSizeAdditional);
                    ByteBuf buf = allocator.heapBuffer(initialSize);
                    try {
                        // Grow the buffer, which will allocate more space, and deallocate the old space.
                        int growth = rng.nextInt(bufSizeGrowth);
                        buf.ensureWritable(initialSize + growth);
                    } finally {
                        buf.release();
                    }
                }
                return null;
            }));
        }

        startLatch.countDown();
        for (Future<Void> future : futures) {
            // We're asserting that the tasks did not fail,
            // and thus that the futures do not propagate any exception.
            future.get(30, TimeUnit.SECONDS);
        }
    }
}
