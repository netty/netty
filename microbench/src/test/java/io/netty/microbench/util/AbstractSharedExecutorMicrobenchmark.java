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
package io.netty.microbench.util;

import static org.junit.Assert.assertNull;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.AbstractEventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ProgressivePromise;
import io.netty.util.concurrent.Promise;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Fork;

/**
 * This harness facilitates the sharing of an executor between JMH and Netty and
 * thus avoid measuring context switching in microbenchmarks.
 */
@Fork(AbstractSharedExecutorMicrobenchmark.DEFAULT_FORKS)
public class AbstractSharedExecutorMicrobenchmark extends AbstractMicrobenchmarkBase {

    protected static final int DEFAULT_FORKS = 0; // Forks has to be 0 so tasks are run immediately by JMH
    protected static final String[] JVM_ARGS;

    static {
        final String[] customArgs = {
        "-Xms2g", "-Xmx2g", "-XX:MaxDirectMemorySize=2g", "-Dharness.executor=CUSTOM",
        "-Dharness.executor.class=io.netty.microbench.util.AbstractExecutorMicrobenchmark$DelegateHarnessExecutor" };

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
        public DelegateHarnessExecutor(int maxThreads, String prefix) {
            System.out.println("Using DelegateHarnessExecutor executor " + this);
        }

        /**
         * Set the executor (in the form of an {@link EventLoop}) which JMH will use.
         * <p>
         * This must be called before JMH requires an executor to execute objects.
         * @param eventLoop Used as an executor by JMH to run benchmarks.
         */
        public static void executor(EventLoop service) {
            executor = service;
        }

        @Override
        public boolean inEventLoop() {
            return executor.inEventLoop();
        }

        @Override
        public boolean inEventLoop(Thread thread) {
            return executor.inEventLoop(thread);
        }

        @Override
        public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
            return executor.shutdownGracefully(quietPeriod, timeout, unit);
        }

        @Override
        public Future<?> terminationFuture() {
            return executor.terminationFuture();
        }

        @Override
        @Deprecated
        public void shutdown() {
            executor.shutdown();
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
        public void execute(Runnable command) {
            executor.execute(command);
        }

        @Override
        public <V> Promise<V> newPromise() {
            return executor.newPromise();
        }

        @Override
        public <V> ProgressivePromise<V> newProgressivePromise() {
            return executor.newProgressivePromise();
        }
    }

    @Override
    protected String[] jvmArgs() {
        return JVM_ARGS;
    }

    public static void handleUnexpectedException(Throwable t) {
        assertNull(t);
    }
}
