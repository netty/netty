/*
 * Copyright 2022 The Netty Project
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
package io.netty5.buffer.internal;

import io.netty5.util.concurrent.FastThreadLocal;
import io.netty5.util.concurrent.FastThreadLocalThread;
import io.netty5.util.internal.SystemPropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * Cleaner allocation strategy used to assign cleaners to event-loops and external threads.
 * The default policy is used:
 * <ul>
 *     <li>for event-loop threads, by default a fast-thread-local shared-nothing Cleaner instance is allocated and used
 *         for each event-loop thread.</li>
 *     <li>for external threads, a shared Cleaner pool is used to distribute and map Cleaners to each external threads
 *     in a round-robin way using fast-thread-local. This pool is allocated lazily, just if some external threads need
 *     it</li>
 * </ul>
 *
 * <p>System property configuration:</p>
 * <ul>
 *     <li>-Dio.netty5.cleanerpool.size: integer value (default=1). Size of the shared Cleaner pool. This pool is
 *     used for external (non-event-loop) threads. "0" means all available processors are used as the pool size.</li>
 *     <li>-Dio.netty5.cleanerpool.eventloop.usepool: boolean (default=false). If set to true, all event-loop threads
 *     will use the shared cleaner pool, like external threads. This can be useful if we need to revert to a singleton
 *     Cleaner to be used by all event-loop/external threads (using io.netty5.cleanerpool.eventloop.usepool=true and
 *     io.netty5.cleanerpool.size=1). if set to false, it means all event-loop threads will be assigned to a dedicated
 *     Cleaner instance, so the pool won't be used in this case.</li>
 * </ul>
 */
final class CleanerPool {
    /**
     * Our logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(CleanerPool.class);

    /**
     * System property name used to configure the shared Cleaner pool size. External (non-event-loop) threads
     * will always use the shared cleaner pool. The shared cleaner pool is instantiated lazily (only if it is
     * needed).
     * default=1.
     * 0 means size will be set with the number of available processors.
     */
    private static final String CNF_POOL_SIZE = "io.netty5.cleanerpool.size";

    /**
     * System property name used to configure whether event-loop threads must use the shared cleaner pool.
     * default=false, meaning that each event-loop thread will use its own cleaner instance
     */
    private static final String CNF_EVENT_LOOP_USE_POOL = "io.netty5.cleanerpool.eventloop.usepool";

    /**
     * Size of the shared Cleaner pool used by external threads, and optionally by event-loop threads.
     * @see #CNF_POOL_SIZE
     */
    private static final int POOL_SIZE = getPoolSize(1);

    /**
     * Flag to configure whether event-loop threads must use the shared cleaner pool (default=false).
     * @see #CNF_EVENT_LOOP_USE_POOL
     */
    private static final boolean EVENT_LOOP_USE_POOL = SystemPropertyUtil.getBoolean(CNF_EVENT_LOOP_USE_POOL, false);

    /**
     * Cleaner instances shared by all external threads, and optionally by event-loop threads
     * These cleaners are wrapped in a static inner class for lazy initialization purpose.
     */
    private static final class CleanersPool {
        static final Cleaner[] cleaners = IntStream.range(0, POOL_SIZE)
                .mapToObj(i -> Cleaner.create()).toArray(Cleaner[]::new);
    }

    /**
     * A FastThreadLocal that returns a Cleaner to the caller thread.
     * For event-loop threads, by default a dedicated cleaner instance is allocated and mapped to the
     * caller thread, but by configuration, event-loop threads can use the shared cleaner pool if this is
     * necessary.
     * For external threads, a cleaner is always returned from the shared fixed cleaner pool in a round-robin way.
     */
    private final CleanerThreadLocal threadLocalCleaner = new CleanerThreadLocal();

    /**
     * The singleton for the CleanerPool.
     */
    public static final CleanerPool INSTANCE = new CleanerPool();

    /**
     * The FastThreadLocal used to map a Cleaner instance to Event Loop threads.
     */
    private static final class CleanerThreadLocal extends FastThreadLocal<Cleaner> {
        private static final AtomicInteger counter = new AtomicInteger();

        @Override
        protected Cleaner initialValue() {
            if (!EVENT_LOOP_USE_POOL && FastThreadLocalThread.currentThreadHasFastThreadLocal()) {
                // Allocate one dedicated cleaner for the caller event-loop thread
                return Cleaner.create();
            }

            // Return one of the shared cleaners from the shared cleaner pool.
            return CleanersPool.cleaners[(counter.getAndIncrement() & 0x7F_FF_FF_FF) % POOL_SIZE];
        }
    }

    private CleanerPool() {
        logger.debug("Instantiating CleanerPool: {}={}, {}={}",
                CNF_EVENT_LOOP_USE_POOL, EVENT_LOOP_USE_POOL,
                CNF_POOL_SIZE, POOL_SIZE);
    }

    /**
     * Returns a Cleaner to the calling thread.
     * The same thread will get the same cleaner for every getCleaner method calls.
     */
    Cleaner getCleaner() {
        return threadLocalCleaner.get();
    }

    private static int getPoolSize(int defSize) {
        int poolSize = SystemPropertyUtil.getInt(CNF_POOL_SIZE, defSize);
        if (poolSize < 0) {
            throw new IllegalArgumentException(CNF_POOL_SIZE + " is negative: " + poolSize);
        }
        return poolSize == 0 ? Runtime.getRuntime().availableProcessors() : poolSize;
    }
}
