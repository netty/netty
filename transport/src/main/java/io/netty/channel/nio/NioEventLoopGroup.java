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
package io.netty.channel.nio;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.util.concurrent.DefaultExecutorFactory;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.ExecutorFactory;

import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.Executor;

/**
 * A {@link MultithreadEventLoopGroup} implementation which is used for NIO {@link Selector} based {@link Channel}s.
 */
public class NioEventLoopGroup extends MultithreadEventLoopGroup {

    /**
     * Create a new instance that uses twice as many {@link EventLoop}s as there processors/cores
     * available, as well as the default {@link Executor} and the {@link SelectorProvider} which
     * is returned by {@link SelectorProvider#provider()}.
     *
     * @see DefaultExecutorFactory
     */
    public NioEventLoopGroup() {
        this(0);
    }

    /**
     * Create a new instance that uses the default {@link Executor} and the {@link SelectorProvider} which
     * is returned by {@link SelectorProvider#provider()}.
     *
     * @see DefaultExecutorFactory
     *
     * @param nEventLoops   the number of {@link EventLoop}s that will be used by this instance.
     *                      If {@code executor} is {@code null} this number will also be the parallelism
     *                      requested from the default executor. It is generally advised for the number
     *                      of {@link EventLoop}s and the number of {@link Thread}s used by the
     */
    public NioEventLoopGroup(int nEventLoops) {
        this(nEventLoops, (Executor) null);
    }

    /**
     * Create a new instance that uses the the {@link SelectorProvider} which is returned by
     * {@link SelectorProvider#provider()}.
     *
     * @param nEventLoops   the number of {@link EventLoop}s that will be used by this instance.
     *                      If {@code executor} is {@code null} this number will also be the parallelism
     *                      requested from the default executor. It is generally advised for the number
     *                      of {@link EventLoop}s and the number of {@link Thread}s used by the
     *                      {@code executor} to lie very close together.
     * @param executor   the {@link Executor} to use, or {@code null} if the default should be used.
     */
    public NioEventLoopGroup(int nEventLoops, Executor executor) {
        this(nEventLoops, executor, SelectorProvider.provider());
    }

    /**
     * Create a new instance that uses the the {@link SelectorProvider} which is returned by
     * {@link SelectorProvider#provider()}.
     *
     * @param nEventLoops   the number of {@link EventLoop}s that will be used by this instance.
     *                      If {@code executor} is {@code null} this number will also be the parallelism
     *                      requested from the default executor. It is generally advised for the number
     *                      of {@link EventLoop}s and the number of {@link Thread}s used by the
     *                      {@code executor} to lie very close together.
     * @param executorFactory   the {@link ExecutorFactory} to use, or {@code null} if the default should be used.
     */
    public NioEventLoopGroup(int nEventLoops, ExecutorFactory executorFactory) {
        this(nEventLoops, executorFactory, SelectorProvider.provider());
    }

    /**
     * @param nEventLoops   the number of {@link EventLoop}s that will be used by this instance.
     *                      If {@code executor} is {@code null} this number will also be the parallelism
     *                      requested from the default executor. It is generally advised for the number
     *                      of {@link EventLoop}s and the number of {@link Thread}s used by the
     *                      {@code executor} to lie very close together.
     * @param executor  the {@link Executor} to use, or {@code null} if the default should be used.
     * @param selectorProvider  the {@link SelectorProvider} to use. This value must not be {@code null}.
     */
    public NioEventLoopGroup(int nEventLoops, Executor executor, final SelectorProvider selectorProvider) {
        super(nEventLoops, executor, selectorProvider);
    }

    /**
     * @param nEventLoops   the number of {@link EventLoop}s that will be used by this instance.
     *                      If {@code executor} is {@code null} this number will also be the parallelism
     *                      requested from the default executor. It is generally advised for the number
     *                      of {@link EventLoop}s and the number of {@link Thread}s used by the
     *                      {@code executor} to lie very close together.
     * @param executorFactory   the {@link ExecutorFactory} to use, or {@code null} if the default should be used.
     * @param selectorProvider  the {@link SelectorProvider} to use. This value must not be {@code null}.
     */
    public NioEventLoopGroup(
            int nEventLoops, ExecutorFactory executorFactory, final SelectorProvider selectorProvider) {
        super(nEventLoops, executorFactory, selectorProvider);
    }

    /**
     * Sets the percentage of the desired amount of time spent for I/O in the child event loops.  The default value is
     * {@code 50}, which means the event loop will try to spend the same amount of time for I/O as for non-I/O tasks.
     */
    public void setIoRatio(int ioRatio) {
        for (EventExecutor e: children()) {
            ((NioEventLoop) e).setIoRatio(ioRatio);
        }
    }

    /**
     * Replaces the current {@link Selector}s of the child event loops with newly created {@link Selector}s to work
     * around the  infamous epoll 100% CPU bug.
     */
    public void rebuildSelectors() {
        for (EventExecutor e: children()) {
            ((NioEventLoop) e).rebuildSelector();
        }
    }

    @Override
    protected EventLoop newChild(Executor executor, Object... args) throws Exception {
        return new NioEventLoop(this, executor, (SelectorProvider) args[0]);
    }
}
