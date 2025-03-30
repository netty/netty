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
package io.netty.microbench.util;

import io.netty.util.concurrent.AbstractEventExecutor;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.ThreadExecutorMap;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;

/**
 * Default implementation of the JMH microbenchmark adapter.  There may be context switches introduced by this harness.
 */
@Fork(AbstractMicrobenchmark.DEFAULT_FORKS)
public class AbstractMicrobenchmark extends AbstractMicrobenchmarkBase {

    protected static final int DEFAULT_FORKS = 2;

    public static final class HarnessExecutor extends ThreadPoolExecutor {
        private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractMicrobenchmark.class);

        public HarnessExecutor(int maxThreads, String prefix) {
            super(maxThreads, maxThreads, 0, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<Runnable>(),
                    new DefaultThreadFactory(prefix));
            EventExecutor eventExecutor = new AbstractEventExecutor() {
                @Override
                public void shutdown() {
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
                public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public Future<?> terminationFuture() {
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

    /**
     * Default settings:
     * <br>
     * Disable assertion in package: {@code io.netty.*}, except {@code io.netty.microbench.*}.
     * <br>
     * Use custom {@code HarnessExecutor}.
     */
    public AbstractMicrobenchmark() {
        this(true, false);
    }

    /**
     * @param disableAssertions If true, it will disable assertion in package: {@code io.netty.*},
     *                          except {@code io.netty.microbench.*},
     *                          which means package {@code io.netty.microbench.*} always gets assertion enabled.
     */
    public AbstractMicrobenchmark(boolean disableAssertions) {
        this(disableAssertions, false);
    }

    /**
     * @param disableAssertions If true, it will disable assertion in package: {@code io.netty.*},
     *                          except {@code io.netty.microbench.*},
     *                          which means package {@code io.netty.microbench.*} always gets assertion enabled.
     */
    public AbstractMicrobenchmark(boolean disableAssertions, boolean disableHarnessExecutor) {

        final List<String> jvmArgs = new ArrayList<>(Arrays.asList(BASE_JVM_ARGS));
        jvmArgs.add("-Xms768m");
        jvmArgs.add("-Xmx768m");
        jvmArgs.add("-XX:MaxDirectMemorySize=768m");
        if (PlatformDependent.javaVersion() < 15) { // not entirely sure when this option was removed, but
            jvmArgs.add("-XX:BiasedLockingStartupDelay=0");
        }
        if (!disableHarnessExecutor) {
            jvmArgs.add("-Djmh.executor=CUSTOM");
            jvmArgs.add("-Djmh.executor.class=" + HarnessExecutor.class.getName());
        }
        if (disableAssertions) {
            removeAssertions(jvmArgs);
            // Enable assertion in 'io.netty.microbench.*' package.
            jvmArgs.add("-ea:io.netty.microbench...");
        }
        this.jvmArgs = jvmArgs.toArray(EmptyArrays.EMPTY_STRINGS);
    }

    @Override
    protected String[] jvmArgs() {
        return jvmArgs;
    }

    @Override
    protected ChainedOptionsBuilder newOptionsBuilder() throws Exception {
        ChainedOptionsBuilder runnerOptions = super.newOptionsBuilder();
        if (getForks() > 0) {
            runnerOptions.forks(getForks());
        }

        return runnerOptions;
    }

    protected int getForks() {
        return SystemPropertyUtil.getInt("forks", -1);
    }
}
