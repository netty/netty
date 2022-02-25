/*
 * Copyright 2015 The Netty Project
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
package io.netty5.microbench.util;

import io.netty5.channel.EventLoop;
import io.netty5.util.concurrent.AbstractEventExecutor;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;
import org.openjdk.jmh.annotations.Fork;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * This harness facilitates the sharing of an executor between JMH and Netty and
 * thus avoid measuring context switching in microbenchmarks.
 */
@Fork(AbstractSharedExecutorMicrobenchmark.DEFAULT_FORKS)
public class AbstractSharedExecutorMicrobenchmark extends AbstractMicrobenchmarkBase {

    protected static final int DEFAULT_FORKS = 1;
    protected static final String[] JVM_ARGS;

    static {
        final String[] customArgs = {
        "-Xms2g", "-Xmx2g", "-XX:MaxDirectMemorySize=2g", "-Djmh.executor=CUSTOM",
        "-Djmh.executor.class=io.netty5.microbench.util.AbstractSharedExecutorMicrobenchmark$DelegateHarnessExecutor" };

        JVM_ARGS = new String[BASE_JVM_ARGS.length + customArgs.length];
        System.arraycopy(BASE_JVM_ARGS, 0, JVM_ARGS, 0, BASE_JVM_ARGS.length);
        System.arraycopy(customArgs, 0, JVM_ARGS, BASE_JVM_ARGS.length, customArgs.length);
    }

    /**
     * Set the executor (in the form of an {@link EventLoop}) which JMH will use.
     * <p>
     * This must be called before JMH requires an executor to execute objects.
     * @param eventLoop Used as an executor by JMH to run benchmarks.
     */
    public static void executor(EventLoop eventLoop) {
        DelegateHarnessExecutor.executor(eventLoop);
    }

    /**
     * This executor allows Netty and JMH to share a common executor.
     * This is achieved by using {@link DelegateHarnessExecutor#executor(EventLoop)}
     * with the {@link EventLoop} used by Netty.
     */
    public static final class DelegateHarnessExecutor extends AbstractEventExecutor {
        private static EventLoop executor;
        private final InternalLogger logger = InternalLoggerFactory.getInstance(DelegateHarnessExecutor.class);

        public DelegateHarnessExecutor(int maxThreads, String prefix) {
            logger.debug("Using DelegateHarnessExecutor executor {}", this);
        }

        /**
         * Set the executor (in the form of an {@link EventLoop}) which JMH will use.
         * <p>
         * This must be called before JMH requires an executor to execute objects.
         * @param service Used as an executor by JMH to run benchmarks.
         */
        public static void executor(EventLoop service) {
            executor = service;
        }

        @Override
        public boolean inEventLoop(Thread thread) {
            return executor.inEventLoop(thread);
        }

        @Override
        public Future<Void> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
            return executor.shutdownGracefully(quietPeriod, timeout, unit);
        }

        @Override
        public Future<Void> terminationFuture() {
            return executor.terminationFuture();
        }

        @Override
        public boolean isShuttingDown() {
            return executor.isShuttingDown();
        }

        @Override
        public boolean isShutdown() {
            return executor.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return executor.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            try {
                return executor.awaitTermination(timeout, unit);
            } catch (InterruptedException e) {
                handleUnexpectedException(e);
            }
            return false;
        }

        @Override
        public void execute(Runnable task) {
            executor.execute(task);
        }

        @Override
        public <V> Promise<V> newPromise() {
            return executor.newPromise();
        }

        @Override
        public Future<Void> schedule(Runnable task, long delay, TimeUnit unit) {
            return executor.schedule(task, delay, unit);
        }

        @Override
        public <V> Future<V> schedule(Callable<V> task, long delay, TimeUnit unit) {
            return executor.schedule(task, delay, unit);
        }

        @Override
        public Future<Void> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
            return executor.scheduleAtFixedRate(task, initialDelay, period, unit);
        }

        @Override
        public Future<Void> scheduleWithFixedDelay(
                Runnable task, long initialDelay, long delay, TimeUnit unit) {
            return executor.scheduleWithFixedDelay(task, initialDelay, delay, unit);
        }
    }

    @Override
    protected String[] jvmArgs() {
        return JVM_ARGS;
    }

    public static void handleUnexpectedException(Throwable t) {
        if (t != null) {
            throw new IllegalStateException(t);
        }
    }
}
