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
import io.netty5.util.internal.SystemPropertyUtil;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.Cleaner;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * Helper class used to maintain a set of configurable Cleaner instances that can then be mapped to Event Loop threads.
 */
final class CleanerPool {
    /**
     * Our logger.
     */
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(CleanerPool.class);

    /**
     * System property name used to configure Cleaner pool size (default=0 for all available processors)
     */
    private static final String CNF_POOL_SIZE = "io.netty5.cleanerpool.size";

    /**
     * Number of cleaners used to track phantom resources.
     * 0 means size will match number of available processors (default value=0)
     */
    private static final int POOL_SIZE = getPoolSize();

    /**
     * System property used to configure whether virtual threads should be used as cleaner daemon threads
     * (default=false)
     * if this property is set to true, and if the platform supports loom, then the Cleaners
     * will be created using virtual ThreadFactory obtained from Thread.ofVirtual().factory() method.
     * Threadfactory will be created using MethodHandles (if the platform version is >= 19).
     */
    private static final String CNF_USE_VTHREAD = "io.netty5.cleanerpool.vthread";

    /**
     * If platform supports loom, then use virtual threads for cleaner daemon threads.
     * false by default.
     */
    private static final boolean USE_VTHREAD = SystemPropertyUtil.getBoolean(CNF_USE_VTHREAD, false);

    /**
     * Method handle used to optionally load Virtual ThreadFactory if the current platform is supporting Loom.
     */
    private static final MethodHandles.Lookup publicLookup = MethodHandles.publicLookup();

    /**
     * Method handle for Thread.ofVirtual() method. Null in case jvm version is < 19
     */
    private static final MethodHandle mhOfVirtual;

    /**
     * Method handle for java.lang.Thread.Builder.OfVirtual.factory() method. Null in case jvm version is < 19
     */
    private static final MethodHandle mhFactory;

    /**
     * Cleaner instances shared by all event loops.
     */
    private final Cleaner[] cleaners = IntStream.range(0, POOL_SIZE).mapToObj(i -> createCleaner())
            .toArray(Cleaner[]::new);

    /**
     * A FastThreadLocal which allows to map a random cleaner to each thread calling the nextCleaner method.
     * Cleaners are distributed in round robin, and the same EventLoop will get the same Cleaner for every
     * nextCleaner method invocations.
     */
    private final CleanerThreadLocal threadLocalCleaner = new CleanerThreadLocal(cleaners);

    /**
     * Static initializer used to setup Method Handles that are used to obtain loom virtual Threadfactory
     * (if the platform supports it).
     */
    static {
        MethodHandle mhOfVirtualTmp = null;
        MethodHandle mhFactoryTmp = null;

        if (USE_VTHREAD) {
            try {
                Class<?> clzOfVirtual = Class.forName("java.lang.Thread$Builder$OfVirtual");
                mhOfVirtualTmp = publicLookup.findStatic(Thread.class, "ofVirtual",
                        MethodType.methodType(clzOfVirtual));
                mhFactoryTmp = publicLookup.findVirtual(clzOfVirtual, "factory",
                        MethodType.methodType(ThreadFactory.class));
            } catch (ClassNotFoundException e) {
                logger.debug("Loom not supported, Cleaner will use default cleaner daemon threads.");
            } catch (Throwable t) {
                logger.warn("Could not create virtual thread factory, will use default cleaner daemon threads.", t);
            }
        }

        mhOfVirtual = mhOfVirtualTmp;
        mhFactory = mhFactoryTmp;
    }

    /**
     * The singleton for the CleanerPool.
     */
    public static final CleanerPool INSTANCE = new CleanerPool();

    /**
     * The FastThreadLocal used to map a Cleaner instance to Event Loop threads.
     */
    private static class CleanerThreadLocal extends FastThreadLocal<Cleaner> {
        private final Cleaner[] cleaners;
        private static final AtomicInteger counter = new AtomicInteger();

        CleanerThreadLocal(Cleaner[] cleaners) {
            this.cleaners = cleaners;
        }

        @Override
        protected Cleaner initialValue() {
            return cleaners[counter.getAndIncrement() % POOL_SIZE];
        }
    }

    private CleanerPool() {
        logger.info("Instantiating CleanerPool: {}={}, {}={}, using vthreads={}",
                        CNF_POOL_SIZE, POOL_SIZE,
                        CNF_USE_VTHREAD, USE_VTHREAD,
                        mhFactory != null);
    }

    /**
     * Returns the next available cleaner to the calling thread.
     * The same thread will get the same cleaner for every nextCleaner method calls.
     */
    Cleaner nextCleaner() {
        return threadLocalCleaner.get();
    }

    private static int getPoolSize() {
        int poolSize = SystemPropertyUtil.getInt(CNF_POOL_SIZE, 0);
        if (poolSize < 0) {
            throw new IllegalArgumentException(CNF_POOL_SIZE + " is negative: " + poolSize);
        }
        return poolSize == 0 ? Runtime.getRuntime().availableProcessors() : poolSize;
    }

    /**
     * Creates a cleaner initialized with a virtual ThreadFactory if the platform supports loom,
     * else creates an usual Cleaner with default cleaner daemon thread.
     */
    private Cleaner createCleaner() {
        ThreadFactory virtualThreadFactory = getVirtualThreadFactory();
        if (logger.isDebugEnabled()) {
            if (virtualThreadFactory != null) {
                logger.debug("Creating cleaner with virtual thread factory");
            } else {
                logger.debug("Creating cleaner with default cleaner daemon thread");
            }
        }
        return virtualThreadFactory == null ? Cleaner.create() : Cleaner.create(virtualThreadFactory);
    }

    /**
     * Returns a Virtual Thread Factory in case Loom is supported, else null
     * (meaning that default Cleaner threads will be used).
     *
     * @return a Virtual Thread Factory in case Loom is supported, else null
     */
    private ThreadFactory getVirtualThreadFactory() {
        if (!USE_VTHREAD || mhOfVirtual == null) {
            return null;
        }

        try {
            ThreadFactory threadFactory = (ThreadFactory) mhFactory.invoke(mhOfVirtual.invoke());
            return threadFactory;
        } catch (Throwable t) {
            logger.warn("Could not create virtual thread factory, will use default cleaner daemon threads.", t);
            return null;
        }
    }
}
