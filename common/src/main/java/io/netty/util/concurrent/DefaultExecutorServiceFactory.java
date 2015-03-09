/*
 * Copyright 2014 The Netty Project
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

package io.netty.util.concurrent;

import io.netty.util.internal.InternalThreadLocalMap;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.chmv8.ForkJoinPool;
import io.netty.util.internal.chmv8.ForkJoinPool.ForkJoinWorkerThreadFactory;
import io.netty.util.internal.chmv8.ForkJoinWorkerThread;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An implementation of an {@link ExecutorServiceFactory} that creates a new {@link ForkJoinPool} on each
 * call to {@link #newExecutorService(int)}.
 * <p>
 * This {@link ExecutorServiceFactory} powers Netty's nio and epoll eventloops by default. Netty moved from managing its
 * own threads and pinning a thread to each eventloop to an {@link Executor}-based approach. That way advanced
 * users of Netty can plug in their own threadpools and gain more control of scheduling the eventloops.
 * <p>
 * The main reason behind choosing a {@link ForkJoinPool} as the default {@link Executor} is that it uses
 * thread-local task queues, providing a high level of thread affinity to Netty's eventloops.
 * <p>
 * The whole discussion can be found on GitHub
 * <a href="https://github.com/netty/netty/issues/2250">https://github.com/netty/netty/issues/2250</a>.
 */
public final class DefaultExecutorServiceFactory implements ExecutorServiceFactory {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(DefaultExecutorServiceFactory.class);

    private static final AtomicInteger executorId = new AtomicInteger();
    private final String namePrefix;

    /**
     * @param clazzNamePrefix   the name of the class will be used to prefix the name of each
     *                          {@link ForkJoinWorkerThread} with.
     */
    public DefaultExecutorServiceFactory(Class<?> clazzNamePrefix) {
        this(toName(clazzNamePrefix));
    }

    /**
     * @param namePrefix    the string to prefix the name of each {@link ForkJoinWorkerThread} with.
     */
    public DefaultExecutorServiceFactory(String namePrefix) {
        this.namePrefix = namePrefix;
    }

    @Override
    public ExecutorService newExecutorService(int parallelism) {
        ForkJoinWorkerThreadFactory threadFactory =
                new DefaultForkJoinWorkerThreadFactory(namePrefix + '-' + executorId.getAndIncrement());

        return new ForkJoinPool(parallelism, threadFactory, DefaultUncaughtExceptionHandler.INSTANCE, true);
    }

    private static String toName(Class<?> clazz) {
        if (clazz == null) {
            throw new NullPointerException("clazz");
        }

        String clazzName = StringUtil.simpleClassName(clazz);
        switch (clazzName.length()) {
            case 0:
                return "unknown";
            case 1:
                return clazzName.toLowerCase(Locale.US);
            default:
                if (Character.isUpperCase(clazzName.charAt(0)) && Character.isLowerCase(clazzName.charAt(1))) {
                    return Character.toLowerCase(clazzName.charAt(0)) + clazzName.substring(1);
                } else {
                    return clazzName;
                }
        }
    }

    private static final class DefaultUncaughtExceptionHandler implements UncaughtExceptionHandler {

        private static final DefaultUncaughtExceptionHandler INSTANCE = new DefaultUncaughtExceptionHandler();

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            if (logger.isErrorEnabled()) {
                logger.error("Uncaught exception in thread: {}", t.getName(), e);
            }
        }
    }

    private static final class DefaultForkJoinWorkerThreadFactory implements ForkJoinWorkerThreadFactory {

        private final AtomicInteger idx = new AtomicInteger();
        private final String namePrefix;

        DefaultForkJoinWorkerThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            // Note: The ForkJoinPool will create these threads as daemon threads.
            ForkJoinWorkerThread thread = new DefaultForkJoinWorkerThread(pool);
            thread.setName(namePrefix + '-' + idx.getAndIncrement());
            thread.setPriority(Thread.MAX_PRIORITY);
            return thread;
        }
    }

    private static final class DefaultForkJoinWorkerThread
            extends ForkJoinWorkerThread implements FastThreadLocalAccess {

        private InternalThreadLocalMap threadLocalMap;

        DefaultForkJoinWorkerThread(ForkJoinPool pool) {
            super(pool);
        }

        @Override
        public InternalThreadLocalMap threadLocalMap() {
            return threadLocalMap;
        }

        @Override
        public void setThreadLocalMap(InternalThreadLocalMap threadLocalMap) {
            this.threadLocalMap = threadLocalMap;
        }
    }
}
