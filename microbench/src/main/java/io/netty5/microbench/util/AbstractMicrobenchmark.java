/*
 * Copyright 2012 The Netty Project
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

import io.netty5.util.concurrent.AbstractEventExecutor;
import io.netty5.util.concurrent.DefaultThreadFactory;
import io.netty5.util.concurrent.EventExecutor;
import io.netty5.util.concurrent.FastThreadLocalThread;
import io.netty5.util.concurrent.Future;
import io.netty5.util.internal.SystemPropertyUtil;
import io.netty5.util.internal.ThreadExecutorMap;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Default implementation of the JMH microbenchmark adapter.  There may be context switches introduced by this harness.
 */
@Fork(AbstractMicrobenchmark.DEFAULT_FORKS)
public class AbstractMicrobenchmark extends AbstractMicrobenchmarkBase {

    protected static final int DEFAULT_FORKS = 2;

    public static final class HarnessExecutor extends ThreadPoolExecutor {

        private static final Logger logger = LoggerFactory.getLogger(AbstractMicrobenchmark.class);

        public HarnessExecutor(int maxThreads, String prefix) {
            super(maxThreads, maxThreads, 0, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<Runnable>(),
                    new DefaultThreadFactory(prefix));
            EventExecutor eventExecutor = new AbstractEventExecutor() {

                @Override
                public Future<Void> schedule(Runnable task, long delay, TimeUnit unit) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public <V> Future<V> schedule(Callable<V> task, long delay, TimeUnit unit) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public Future<Void> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public Future<Void> scheduleWithFixedDelay(
                        Runnable task, long initialDelay, long delay, TimeUnit unit) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public boolean inEventLoop(Thread thread) {
                    return thread instanceof FastThreadLocalThread;
                }

                @Override
                public boolean isShuttingDown() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public Future<Void> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public Future<Void> terminationFuture() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public boolean isShutdown() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public boolean isTerminated() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void execute(Runnable command) {
                    throw new UnsupportedOperationException();
                }
            };
            setThreadFactory(ThreadExecutorMap.apply(getThreadFactory(), eventExecutor));

            logger.debug("Using harness executor");
        }
    }

    private final String[] jvmArgs;

    public AbstractMicrobenchmark() {
        this(false, false);
    }

    public AbstractMicrobenchmark(boolean disableAssertions) {
        this(disableAssertions, false);
    }

    public AbstractMicrobenchmark(boolean disableAssertions, boolean disableHarnessExecutor) {
        final String[] customArgs;
        if (disableHarnessExecutor) {
            customArgs = new String[]{"-Xms768m", "-Xmx768m", "-XX:MaxDirectMemorySize=768m"};
        } else {
            customArgs = new String[]{"-Xms768m", "-Xmx768m", "-XX:MaxDirectMemorySize=768m",
                    "-Djmh.executor=CUSTOM",
                    "-Djmh.executor.class=io.netty5.microbench.util.AbstractMicrobenchmark$HarnessExecutor"};
        }
        String[] jvmArgs = new String[BASE_JVM_ARGS.length + customArgs.length];
        System.arraycopy(BASE_JVM_ARGS, 0, jvmArgs, 0, BASE_JVM_ARGS.length);
        System.arraycopy(customArgs, 0, jvmArgs, BASE_JVM_ARGS.length, customArgs.length);
        if (disableAssertions) {
            jvmArgs = removeAssertions(jvmArgs);
        }
        this.jvmArgs = jvmArgs;
    }

    @Override
    protected String[] jvmArgs() {
        return jvmArgs;
    }

    @Override
    protected ChainedOptionsBuilder newOptionsBuilder() throws Exception {
        ChainedOptionsBuilder runnerOptions = super.newOptionsBuilder();
        if (getForks() >= 0) {
            runnerOptions.forks(getForks());
        }
        // Async Profiler.
//        runnerOptions.addProfiler(org.openjdk.jmh.profile.AsyncProfiler.class,
//                                  "output=flamegraph;libPath=/<path>/async-profiler/build/libasyncProfiler.dylib");

        // Assembly profiler on Mac OS.
//        runnerOptions.addProfiler("dtraceasm");

        return runnerOptions;
    }

    protected int getForks() {
        return SystemPropertyUtil.getInt("forks", -1);
    }
}
