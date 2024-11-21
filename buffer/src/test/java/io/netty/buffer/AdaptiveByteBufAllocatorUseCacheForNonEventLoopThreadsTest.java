/*
 * Copyright 2024 The Netty Project
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
import io.netty.util.internal.ObjectUtil;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;

public class AdaptiveByteBufAllocatorUseCacheForNonEventLoopThreadsTest extends AdaptiveByteBufAllocatorTest {

    @Override
    protected AdaptiveByteBufAllocator newAllocator(final boolean preferDirect) {
        return new AdaptiveByteBufAllocator(preferDirect, true);
    }

    @Override
    protected AdaptiveByteBufAllocator newUnpooledAllocator() {
        return newAllocator(false);
    }

    @Test
    void testFastThreadLocalThreadWithoutCleanupFastThreadLocals() throws InterruptedException {
        final AtomicReference<Throwable> throwable = new AtomicReference<Throwable>();
        Runnable task = new Runnable() {
            @Override
            public void run() {
                try {
                    AdaptiveByteBufAllocatorUseCacheForNonEventLoopThreadsTest.super.testUsedHeapMemory();
                    AdaptiveByteBufAllocatorUseCacheForNonEventLoopThreadsTest.super.testUsedDirectMemory();
                } catch (Throwable e) {
                    throwable.set(e);
                }
            }
        };
        Thread customizefastThreadLocalThread = new CustomizeFastThreadLocalThreadWithoutCleanupFastThreadLocals(task);
        customizefastThreadLocalThread.start();
        customizefastThreadLocalThread.join();
        assertNull(throwable.get());
    }

    private static final class CustomizeFastThreadLocalThreadWithoutCleanupFastThreadLocals
            extends FastThreadLocalThread implements Runnable {

        private final Runnable runnable;

        private CustomizeFastThreadLocalThreadWithoutCleanupFastThreadLocals(Runnable runnable) {
            this.runnable = ObjectUtil.checkNotNull(runnable, "runnable");
        }

        @Override
        public boolean willCleanupFastThreadLocals() {
            return false;
        }

        @Override
        public void run() {
            runnable.run(); // Without calling `FastThreadLocal.removeAll()`.
        }
    }
}
