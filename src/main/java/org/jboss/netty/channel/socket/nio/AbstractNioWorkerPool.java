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

import org.jboss.netty.channel.socket.Worker;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.ExternalResourceReleasable;
import org.jboss.netty.util.internal.ExecutorUtil;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract base class for {@link WorkerPool} implementations that create the {@link Worker}'s
 * up-front and return them in a "fair" fashion when calling {@link #nextWorker()}
 */
public abstract class AbstractNioWorkerPool<E extends AbstractNioWorker>
        implements WorkerPool<E>, ExternalResourceReleasable {

    /**
     * The worker pool raises an exception unless all worker threads start and run within this timeout (in seconds.)
     */
    private static final int INITIALIZATION_TIMEOUT = 10;

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractNioWorkerPool.class);

    private final AbstractNioWorker[] workers;
    private final AtomicInteger workerIndex = new AtomicInteger();
    private final Executor workerExecutor;
    private volatile boolean initialized;

    /**
     * Create a new instance
     *
     * @param workerExecutor the {@link Executor} to use for the {@link Worker}'s
     * @param workerCount the count of {@link Worker}'s to create
     */
    AbstractNioWorkerPool(Executor workerExecutor, int workerCount) {
        this(workerExecutor, workerCount, true);
    }

    AbstractNioWorkerPool(Executor workerExecutor, int workerCount, boolean autoInit) {
        if (workerExecutor == null) {
            throw new NullPointerException("workerExecutor");
        }
        if (workerCount <= 0) {
            throw new IllegalArgumentException(
                    "workerCount (" + workerCount + ") " + "must be a positive integer.");
        }
        workers = new AbstractNioWorker[workerCount];
        this.workerExecutor = workerExecutor;
        if (autoInit) {
            init();
        }
    }

    protected void init() {
        if (initialized) {
            throw new IllegalStateException("initialized already");
        }

        initialized = true;

        for (int i = 0; i < workers.length; i++) {
            workers[i] = newWorker(workerExecutor);
        }

        waitForWorkerThreads();
    }

    private void waitForWorkerThreads() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(INITIALIZATION_TIMEOUT);
        boolean warn = false;
        for (AbstractNioSelector worker: workers) {
            long waitTime = deadline - System.nanoTime();
            try {
                if (waitTime <= 0) {
                    if (worker.thread == null) {
                        warn = true;
                        break;
                    }
                } else if (!worker.startupLatch.await(waitTime, TimeUnit.NANOSECONDS)) {
                    warn = true;
                    break;
                }
            } catch (InterruptedException ignore) {
                // Stop waiting for the worker threads and let someone else take care of the interruption.
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (warn) {
            logger.warn(
                    "Failed to get all worker threads ready within " + INITIALIZATION_TIMEOUT + " second(s). " +
                    "Make sure to specify the executor which has more threads than the requested workerCount. " +
                    "If unsure, use Executors.newCachedThreadPool().");
        }
    }

    /**
     * Only here for backward compability and will be removed in later releases. Please use
     * {@link #newWorker(Executor)}
     *
     *
     * @param executor the {@link Executor} to use
     * @return worker the new {@link Worker}
     * @deprecated use {@link #newWorker(Executor)}
     */
    @Deprecated
    protected E createWorker(Executor executor) {
        throw new IllegalStateException("This will be removed. Override this and the newWorker(..) method!");
    }

    /**
     * Create a new {@link Worker} which uses the given {@link Executor} to service IO.
     *
     * This method will be made abstract in further releases (once {@link #createWorker(Executor)}
     * was removed).
     *
     *
     * @param executor the {@link Executor} to use
     * @return worker the new {@link Worker}
     */
    @SuppressWarnings("deprecation")
    protected E newWorker(Executor executor) {
        return createWorker(executor);
    }

    @SuppressWarnings("unchecked")
    public E nextWorker() {
        return (E) workers[Math.abs(workerIndex.getAndIncrement() % workers.length)];
    }

    public void rebuildSelectors() {
        for (AbstractNioWorker worker: workers) {
            worker.rebuildSelector();
        }
    }

    public void releaseExternalResources() {
        shutdown();
        ExecutorUtil.shutdownNow(workerExecutor);
    }

    public void shutdown() {
        for (AbstractNioWorker worker: workers) {
            worker.shutdown();
        }
    }

}
