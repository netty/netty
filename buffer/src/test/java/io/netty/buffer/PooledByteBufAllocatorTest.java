/*
 * Copyright 2015 The Netty Project
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

import io.netty.util.internal.SystemPropertyUtil;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class PooledByteBufAllocatorTest {

    private static final int[] ALLOCATION_SIZES = new int[16 * 1024];
    static {
        for (int i = 0; i < ALLOCATION_SIZES.length; i++) {
            ALLOCATION_SIZES[i] = i;
        }
    }

    @Test
    public void testConcurrentUsage() throws Throwable {
        long runningTime = TimeUnit.MILLISECONDS.toNanos(SystemPropertyUtil.getLong(
                "io.netty.buffer.PooledByteBufAllocatorTest.testConcurrentUsageTime", 15000));

        // We use no caches and only one arena to maximize the chance of hitting the race-condition we
        // had before.
        ByteBufAllocator allocator = new PooledByteBufAllocator(true, 1, 1, 8192, 11, 0, 0, 0);
        List<AllocationThread> threads = new ArrayList<AllocationThread>();
        try {
            for (int i = 0; i < 512; i++) {
                AllocationThread thread = new AllocationThread(allocator);
                thread.start();
                threads.add(thread);
            }

            long start = System.nanoTime();
            while (!isExpired(start, runningTime)) {
                checkForErrors(threads);
                Thread.sleep(100);
            }
        } finally {
            for (AllocationThread t : threads) {
                t.finish();
            }
        }
    }

    private static boolean isExpired(long start, long expireTime) {
        return System.nanoTime() - start > expireTime;
    }

    private static void checkForErrors(List<AllocationThread> threads) throws Throwable {
        for (AllocationThread t : threads) {
            if (t.isFinished()) {
                t.checkForError();
            }
        }
    }

    private static final class AllocationThread extends Thread {
        private final CountDownLatch latch = new CountDownLatch(1);
        private final Queue<ByteBuf> buffers = new ArrayDeque<ByteBuf>(10);
        private final ByteBufAllocator allocator;
        private volatile boolean finished;
        private volatile Throwable error;

        public AllocationThread(ByteBufAllocator allocator) {
            this.allocator = allocator;
        }

        @Override
        public void run() {
            try {
                int idx = 0;
                while (!finished) {
                    for (int i = 0; i < 10; i++) {
                        buffers.add(allocator.directBuffer(
                                ALLOCATION_SIZES[Math.abs(idx++ % ALLOCATION_SIZES.length)],
                                Integer.MAX_VALUE));
                    }
                    releaseBuffers();
                }
            } catch (Throwable cause) {
                error = cause;
                finished = true;
            } finally {
                releaseBuffers();
            }
            latch.countDown();
        }

        private void releaseBuffers() {
            for (;;) {
                ByteBuf buf = buffers.poll();
                if (buf == null) {
                    break;
                }
                buf.release();
            }
        }

        public boolean isFinished() {
            return finished;
        }

        public void finish() throws Throwable {
            try {
                finished = true;
                latch.await();
                checkForError();
            } finally {
                releaseBuffers();
            }
        }

        public void checkForError() throws Throwable {
            if (error != null) {
                throw error;
            }
        }
    }
}
