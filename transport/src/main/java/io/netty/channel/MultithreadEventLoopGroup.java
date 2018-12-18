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
package io.netty.channel;

import io.netty.util.NettyRuntime;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.MultithreadEventExecutorGroup;
import io.netty.util.concurrent.RejectedExecutionHandler;
import io.netty.util.concurrent.RejectedExecutionHandlers;
import io.netty.util.concurrent.ThreadPerTaskExecutor;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

/**
 * {@link EventLoopGroup} implementation that will handle its tasks with multiple threads.
 */
public class MultithreadEventLoopGroup extends MultithreadEventExecutorGroup implements EventLoopGroup {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(MultithreadEventLoopGroup.class);

    public static final int DEFAULT_EVENT_LOOP_THREADS;

    static {
        DEFAULT_EVENT_LOOP_THREADS = Math.max(1, SystemPropertyUtil.getInt(
                "io.netty.eventLoopThreads", NettyRuntime.availableProcessors() * 2));

        if (logger.isDebugEnabled()) {
            logger.debug("-Dio.netty.eventLoopThreads: {}", DEFAULT_EVENT_LOOP_THREADS);
        }
    }

    /**
     * Create a new instance.
     *
     * @param ioHandlerFactory  the {@link IoHandlerFactory} to use for creating new
     *                          {@link IoHandler} instances that will handle the IO for the
     *                          {@link EventLoop}.
     */
    public MultithreadEventLoopGroup(IoHandlerFactory ioHandlerFactory) {
        this(0, (Executor) null, ioHandlerFactory);
    }

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param ioHandlerFactory  the {@link IoHandlerFactory} to use for creating new
     *                          {@link IoHandler} instances that will handle the IO for the
     *                          {@link EventLoop}.
     */
    public MultithreadEventLoopGroup(int nThreads, IoHandlerFactory ioHandlerFactory) {
        this(nThreads, (Executor) null, ioHandlerFactory);
    }

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param executor          the {@link Executor} to use, or {@code null} if the default should be used.
     * @param ioHandlerFactory  the {@link IoHandlerFactory} to use for creating new
     *                          {@link IoHandler} instances that will handle the IO for the
     *                          {@link EventLoop}.
     */
    public MultithreadEventLoopGroup(int nThreads, Executor executor,
                                     IoHandlerFactory ioHandlerFactory) {
        this(nThreads, executor, ioHandlerFactory,
                SingleThreadEventLoop.DEFAULT_MAX_PENDING_TASKS, RejectedExecutionHandlers.reject(),
                SingleThreadEventLoop.DEFAULT_MAX_TASKS_PER_RUN);
    }

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param threadFactory     the {@link ThreadFactory} to use, or {@code null} if the default should be used.
     * @param ioHandlerFactory  the {@link IoHandlerFactory} to use for creating new
     *                          {@link IoHandler} instances that will handle the IO for the
     *                          {@link EventLoop}.
     */
    public MultithreadEventLoopGroup(int nThreads, ThreadFactory threadFactory,
                                     IoHandlerFactory ioHandlerFactory) {
        this(nThreads, threadFactory, ioHandlerFactory,
                SingleThreadEventLoop.DEFAULT_MAX_PENDING_TASKS, RejectedExecutionHandlers.reject());
    }

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param executor          the {@link Executor} to use, or {@code null} if the default should be used.
     * @param ioHandlerFactory  the {@link IoHandlerFactory} to use for creating new
     *                          {@link IoHandler} instances that will handle the IO for the
     *                          {@link EventLoop}.
     * @param maxPendingTasks   the maximum number of pending tasks before new tasks will be rejected.
     * @param rejectedHandler   the {@link RejectedExecutionHandler} to use.
     */
    public MultithreadEventLoopGroup(int nThreads, Executor executor,
                                     IoHandlerFactory ioHandlerFactory,
                                     int maxPendingTasks, RejectedExecutionHandler rejectedHandler) {
        this(nThreads, executor, ioHandlerFactory, maxPendingTasks, rejectedHandler,
                SingleThreadEventLoop.DEFAULT_MAX_TASKS_PER_RUN);
    }

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param threadFactory     the {@link ThreadFactory} to use, or {@code null} if the default should be used.
     * @param ioHandlerFactory  the {@link IoHandlerFactory} to use for creating new
     *                          {@link IoHandler} instances that will handle the IO for the
     *                          {@link EventLoop}.
     * @param maxPendingTasks   the maximum number of pending tasks before new tasks will be rejected.
     * @param rejectedHandler   the {@link RejectedExecutionHandler} to use.
     */
    public MultithreadEventLoopGroup(int nThreads, ThreadFactory threadFactory,
                                     IoHandlerFactory ioHandlerFactory,
                                     int maxPendingTasks, RejectedExecutionHandler rejectedHandler) {
        this(nThreads, threadFactory, ioHandlerFactory, maxPendingTasks, rejectedHandler,
                SingleThreadEventLoop.DEFAULT_MAX_TASKS_PER_RUN);
    }

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param executor          the {@link Executor} to use, or {@code null} if the default should be used.
     * @param ioHandlerFactory  the {@link IoHandlerFactory} to use for creating new
     *                          {@link IoHandler} instances that will handle the IO for the
     *                          {@link EventLoop}.
     * @param maxPendingTasks   the maximum number of pending tasks before new tasks will be rejected.
     * @param rejectedHandler   the {@link RejectedExecutionHandler} to use.
     * @param maxTasksPerRun    the maximum number of tasks per {@link EventLoop} run that will be processed
     *                          before trying to handle IO again.
     */
    public MultithreadEventLoopGroup(int nThreads, Executor executor,
                                     IoHandlerFactory ioHandlerFactory,
                                     int maxPendingTasks, RejectedExecutionHandler rejectedHandler,
                                     int maxTasksPerRun) {
        this(nThreads, executor, ioHandlerFactory,
                maxPendingTasks, rejectedHandler, maxTasksPerRun, EmptyArrays.EMPTY_OBJECTS);
    }

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param threadFactory     the {@link ThreadFactory} to use, or {@code null} if the default should be used.
     * @param ioHandlerFactory  the {@link IoHandlerFactory} to use for creating new
     *                          {@link IoHandler} instances that will handle the IO for the
     *                          {@link EventLoop}.
     * @param maxPendingTasks   the maximum number of pending tasks before new tasks will be rejected.
     * @param rejectedHandler   the {@link RejectedExecutionHandler} to use.
     * @param maxTasksPerRun    the maximum number of tasks per {@link EventLoop} run that will be processed
     *                          before trying to handle IO again.
     */
    public MultithreadEventLoopGroup(int nThreads, ThreadFactory threadFactory,
                                     IoHandlerFactory ioHandlerFactory,
                                     int maxPendingTasks, RejectedExecutionHandler rejectedHandler,
                                     int maxTasksPerRun) {
        this(nThreads, threadFactory, ioHandlerFactory,
                maxPendingTasks, rejectedHandler, maxTasksPerRun, EmptyArrays.EMPTY_OBJECTS);
    }

    // Constructors provided for sub-classes that want to pass more args to newChild(...).

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param executor          the {@link Executor} to use, or {@code null} if the default should be used.
     * @param ioHandlerFactory  the {@link IoHandlerFactory} to use for creating new
     *                          {@link IoHandler} instances that will handle the IO for the
     *                          {@link EventLoop}.
     * @param maxPendingTasks   the maximum number of pending tasks before new tasks will be rejected.
     * @param rejectedHandler   the {@link RejectedExecutionHandler} to use.
     * @param maxTasksPerRun    the maximum number of tasks per {@link EventLoop} run that will be processed
     *                          before trying to handle IO again.
     * @param args              extra arguments passed to {@link #newChild(Executor, int, RejectedExecutionHandler,
     *                          IoHandler, int, Object...)}
     */
    protected MultithreadEventLoopGroup(int nThreads, Executor executor,
                                     IoHandlerFactory ioHandlerFactory,
                                     int maxPendingTasks, RejectedExecutionHandler rejectedHandler,
                                     int maxTasksPerRun, Object... args) {
        super(pickThreadCount(nThreads),
                executor == null ? new ThreadPerTaskExecutor(newDefaultThreadFactory()) : executor,
                maxPendingTasks, rejectedHandler, merge(ioHandlerFactory, maxTasksPerRun, args));
    }

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param threadFactory     the {@link ThreadFactory} to use, or {@code null} if the default should be used.
     * @param ioHandlerFactory  the {@link IoHandlerFactory} to use for creating new
     *                          {@link IoHandler} instances that will handle the IO for the
     *                          {@link EventLoop}.
     * @param maxPendingTasks   the maximum number of pending tasks before new tasks will be rejected.
     * @param rejectedHandler   the {@link RejectedExecutionHandler} to use.
     * @param maxTasksPerRun    the maximum number of tasks per {@link EventLoop} run that will be processed
     *                          before trying to handle IO again.
     * @param args              extra arguments passed to {@link #newChild(Executor, int, RejectedExecutionHandler,
     *                          IoHandler, int, Object...)}
     */
    protected MultithreadEventLoopGroup(int nThreads, ThreadFactory threadFactory,
                                     IoHandlerFactory ioHandlerFactory,
                                     int maxPendingTasks, RejectedExecutionHandler rejectedHandler,
                                     int maxTasksPerRun, Object... args) {
        super(pickThreadCount(nThreads), threadFactory == null ? newDefaultThreadFactory() : threadFactory,
                maxPendingTasks, rejectedHandler, merge(ioHandlerFactory, maxTasksPerRun, args));
    }

    private static ThreadFactory newDefaultThreadFactory() {
        return new DefaultThreadFactory(MultithreadEventLoopGroup.class, Thread.MAX_PRIORITY);
    }

    /**
     * Return the number of threads to use based on the given {@code nThreads}.
     */
    protected static int pickThreadCount(int nThreads) {
        return nThreads == 0 ? DEFAULT_EVENT_LOOP_THREADS : nThreads;
    }

    private static Object[] merge(IoHandlerFactory ioHandlerFactory,
                                  int maxTasksPerRun, Object... args) {
        List<Object> argList = new ArrayList<Object>(2 + args.length);
        argList.add(ioHandlerFactory);
        argList.add(maxTasksPerRun);
        Collections.addAll(argList, args);
        return argList.toArray();
    }

    @Override
    public final EventLoop next() {
        return (EventLoop) super.next();
    }

    @Override
    protected final EventLoop newChild(Executor executor, int maxPendingTasks,
                                       RejectedExecutionHandler rejectedExecutionHandler, Object... args) {
        return newChild(executor, maxPendingTasks, rejectedExecutionHandler,
                ((IoHandlerFactory) args[0]).newHandler(), (Integer) args[1],
                Arrays.copyOfRange(args, 2, args.length));
    }

    /**
     * Creates a new {@link EventLoop} to use.
     *
     * As this method is called from within the constructor you can only use the parameters passed into the method when
     * overriding this method.
     *
     * @param executor                  the {@link Executor} to use for execution.
     * @param maxPendingTasks           the maximum number of pending tasks.
     * @param rejectedExecutionHandler  the {@link RejectedExecutionHandler} to use when the number of outstanding tasks
     *                                  reach {@code maxPendingTasks}.
     * @param ioHandler                 the {@link IoHandler} to use.
     * @param maxTasksPerRun            the maximum number of tasks per {@link EventLoop} run that will be processed
     *                                  before trying to handle IO again.
     * @param args                      any extra args needed to construct the {@link EventLoop}. This will be an empty
     *                                  array if not sub-classes and extra arguments are given.
     * @return                          the {@link EventLoop} to use.
     */
    protected EventLoop newChild(Executor executor, int maxPendingTasks,
                                 RejectedExecutionHandler rejectedExecutionHandler,
                                 IoHandler ioHandler, int maxTasksPerRun,
                                 Object... args) {
        assert args.length == 0;
        return new SingleThreadEventLoop(executor, ioHandler, maxPendingTasks,
                rejectedExecutionHandler, maxTasksPerRun);
    }
}
