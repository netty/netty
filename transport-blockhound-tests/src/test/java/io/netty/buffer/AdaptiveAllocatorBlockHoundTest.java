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

import io.netty.util.concurrent.FastThreadLocalThread;
import io.netty.util.internal.SystemPropertyUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.blockhound.BlockHound;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class AdaptiveAllocatorBlockHoundTest {
    @BeforeAll
    static void installBlockHound() {
        assumeFalse(SystemPropertyUtil.getBoolean("io.netty.allocator.lowMemory",
                Runtime.getRuntime().maxMemory() <= 512 * 1024 * 1024),
                "Low-memory mode disables magazine expansion");
        BlockHound.install();
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void magazineRetirementAllowsLockContention() throws Exception {
        CountDownLatch allocatingChunk = new CountDownLatch(1);
        CountDownLatch releaseAllocation = new CountDownLatch(1);
        AtomicBoolean firstAllocation = new AtomicBoolean(true);
        AdaptivePoolingAllocator allocator = new AdaptivePoolingAllocator((initialCapacity, maxCapacity) -> {
            if (firstAllocation.compareAndSet(true, false)) {
                allocatingChunk.countDown();
                try {
                    assertTrue(releaseAllocation.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            }
            return new UnpooledHeapByteBuf(UnpooledByteBufAllocator.DEFAULT, initialCapacity, maxCapacity);
        }, false);
        FutureTask<Void> allocation = new FutureTask<>(() -> {
            allocator.allocate(32, 32).release();
            return null;
        });
        FutureTask<Void> retirement = new FutureTask<>(() -> {
            allocator.allocate(32, 32).release();
            return null;
        });
        Thread allocatingThread = new Thread(allocation);
        Thread retiringThread = new FastThreadLocalThread(retirement);
        boolean contended = false;
        try {
            allocatingThread.start();
            assertTrue(allocatingChunk.await(5, TimeUnit.SECONDS));
            retiringThread.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!retirement.isDone() && System.nanoTime() < deadline) {
                if (retiringThread.getState() == Thread.State.WAITING) {
                    for (StackTraceElement frame : retiringThread.getStackTrace()) {
                        if (frame.getClassName().equals("io.netty.buffer.AdaptivePoolingAllocator$Magazine")
                                && frame.getMethodName().equals("free")) {
                            contended = true;
                            break;
                        }
                    }
                    if (contended) {
                        break;
                    }
                }
                Thread.sleep(1);
            }
        } finally {
            releaseAllocation.countDown();
            allocatingThread.join(5000);
            retiringThread.join(5000);
        }
        allocation.get(5, TimeUnit.SECONDS);
        retirement.get(5, TimeUnit.SECONDS);
        assertTrue(contended, "The retiring thread must block in Magazine.free()");
    }
}
