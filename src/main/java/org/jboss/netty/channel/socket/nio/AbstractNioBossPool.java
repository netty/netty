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
package org.jboss.netty.channel.socket.nio;

import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.ExternalResourceReleasable;
import org.jboss.netty.util.internal.ExecutorUtil;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractNioBossPool<E extends Boss>
        implements BossPool<E>, ExternalResourceReleasable {

    /**
     * The boss pool raises an exception unless all boss threads start and run within this timeout (in seconds.)
     */
    private static final int INITIALIZATION_TIMEOUT = 10;

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractNioBossPool.class);

    private final Boss[] bosses;
    private final AtomicInteger bossIndex = new AtomicInteger();
    private final Executor bossExecutor;
    private volatile boolean initialized;

    /**
     * Create a new instance
     *
     * @param bossExecutor the {@link Executor} to use for the {@link Boss}'s
     * @param bossCount the count of {@link Boss}'s to create
     */
    AbstractNioBossPool(Executor bossExecutor, int bossCount) {
        this(bossExecutor, bossCount, true);
    }

    AbstractNioBossPool(Executor bossExecutor, int bossCount, boolean autoInit) {
        if (bossExecutor == null) {
            throw new NullPointerException("bossExecutor");
        }
        if (bossCount <= 0) {
            throw new IllegalArgumentException(
                    "bossCount (" + bossCount + ") " +
                            "must be a positive integer.");
        }
        bosses = new Boss[bossCount];
        this.bossExecutor = bossExecutor;
        if (autoInit) {
            init();
        }
    }

    protected void init() {
        if (initialized) {
            throw new IllegalStateException("initialized already");
        }
        initialized = true;

        for (int i = 0; i < bosses.length; i++) {
            bosses[i] = newBoss(bossExecutor);
        }

        waitForBossThreads();
    }

    private void waitForBossThreads() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(INITIALIZATION_TIMEOUT);
        boolean warn = false;
        for (Boss boss: bosses) {
            if (!(boss instanceof AbstractNioSelector)) {
                continue;
            }

            AbstractNioSelector selector = (AbstractNioSelector) boss;
            long waitTime = deadline - System.nanoTime();
            try {
                if (waitTime <= 0) {
                    if (selector.thread == null) {
                        warn = true;
                        break;
                    }
                } else if (!selector.startupLatch.await(waitTime, TimeUnit.NANOSECONDS)) {
                    warn = true;
                    break;
                }
            } catch (InterruptedException ignore) {
                // Stop waiting for the boss threads and let someone else take care of the interruption.
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (warn) {
            logger.warn(
                    "Failed to get all boss threads ready within " + INITIALIZATION_TIMEOUT + " second(s). " +
                    "Make sure to specify the executor which has more threads than the requested bossCount. " +
                    "If unsure, use Executors.newCachedThreadPool().");
        }
    }

    /**
     * Create a new {@link Boss} which uses the given {@link Executor} to service IO
     *
     *
     * @param executor the {@link Executor} to use
     * @return worker the new {@link Boss}
     */
    protected abstract E newBoss(Executor executor);

    @SuppressWarnings("unchecked")
    public E nextBoss() {
        return (E) bosses[Math.abs(bossIndex.getAndIncrement() % bosses.length)];
    }

    public void rebuildSelectors() {
        for (Boss boss: bosses) {
            boss.rebuildSelector();
        }
    }

    public void releaseExternalResources() {
        shutdown();
        ExecutorUtil.shutdownNow(bossExecutor);
    }

    public void shutdown() {
        for (Boss boss: bosses) {
            boss.shutdown();
        }
    }
}
