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
package io.netty.channel.epoll;

import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.ExecutorFactory;

import java.util.concurrent.Executor;
import io.netty.util.concurrent.DefaultExecutorFactory;


/**
 * A {@link MultithreadEventLoopGroup} which uses <a href="http://en.wikipedia.org/wiki/Epoll">epoll</a> under the
 * covers. This {@link EventLoopGroup} works only on Linux systems!
 */
public final class EpollEventLoopGroup extends MultithreadEventLoopGroup {

    /**
     * Create a new instance that uses twice as many {@link EventLoop}s as there processors/cores
     * available, as well as the default {@link Executor}.
     *
     * @see DefaultExecutorFactory
     */
    public EpollEventLoopGroup() {
        this(0);
    }

    /**
     * Create a new instance that uses the default {@link Executor}.
     *
     * @see DefaultExecutorFactory
     *
     * @param nEventLoops       the number of {@link EventLoop}s that will be used by this instance.
     *                             If {@code executor} is {@code null} this number will also be the parallelism
     *                             requested from the default executor. It is generally advised for the number
     *                             of {@link EventLoop}s and the number of {@link Thread}s used by the
     */
    public EpollEventLoopGroup(int nEventLoops) {
        this(nEventLoops, (Executor) null);
    }

    /**
     * @param nEventLoops       the number of {@link EventLoop}s that will be used by this instance.
     *                             If {@code executor} is {@code null} this number will also be the parallelism
     *                             requested from the default executor. It is generally advised for the number
     *                             of {@link EventLoop}s and the number of {@link Thread}s used by the
     *                             {@code executor} to lie very close together.
     * @param executor           the {@link Executor} to use, or {@code null} if the default should be used.
     */
    public EpollEventLoopGroup(int nEventLoops, Executor executor) {
        this(nEventLoops, executor, 128);
    }

    /**
     * @param nEventLoops       the number of {@link EventLoop}s that will be used by this instance.
     *                             If {@code executor} is {@code null} this number will also be the parallelism
     *                             requested from the default executor. It is generally advised for the number
     *                             of {@link EventLoop}s and the number of {@link Thread}s used by the
     *                             {@code executor} to lie very close together.
     * @param executorFactory   the {@link ExecutorFactory} to use, or {@code null} if the default should be used.
     */
    public EpollEventLoopGroup(int nEventLoops, ExecutorFactory executorFactory) {
        this(nEventLoops, executorFactory, 128);
    }

    /**
     * @param nEventLoops       the number of {@link EventLoop}s that will be used by this instance.
     *                             If {@code executor} is {@code null} this number will also be the parallelism
     *                             requested from the default executor. It is generally advised for the number
     *                             of {@link EventLoop}s and the number of {@link Thread}s used by the
     *                             {@code executor} to lie very close together.
     * @param executor           the {@link Executor} to use, or {@code null} if the default should be used.
     * @param maxEventsAtOnce   the maximum number of epoll events to handle per epollWait(...).
     */
    public EpollEventLoopGroup(int nEventLoops, Executor executor, int maxEventsAtOnce) {
        super(nEventLoops, executor, maxEventsAtOnce);
    }

    /**
     * @param nEventLoops       the number of {@link EventLoop}s that will be used by this instance.
     *                             If {@code executor} is {@code null} this number will also be the parallelism
     *                             requested from the default executor. It is generally advised for the number
     *                             of {@link EventLoop}s and the number of {@link Thread}s used by the
     *                             {@code executor} to lie very close together.
     * @param executorFactory   the {@link ExecutorFactory} to use, or {@code null} if the default should be used.
     * @param maxEventsAtOnce   the maximum number of epoll events to handle per epollWait(...).
     */
    public EpollEventLoopGroup(int nEventLoops, ExecutorFactory executorFactory, int maxEventsAtOnce) {
        super(nEventLoops, executorFactory, maxEventsAtOnce);
    }

    /**
     * Sets the percentage of the desired amount of time spent for I/O in the child event loops.  The default value is
     * {@code 50}, which means the event loop will try to spend the same amount of time for I/O as for non-I/O tasks.
     */
    public void setIoRatio(int ioRatio) {
        for (EventExecutor e: children()) {
            ((EpollEventLoop) e).setIoRatio(ioRatio);
        }
    }

    @Override
    protected EventLoop newChild(Executor executor, Object... args) throws Exception {
        return new EpollEventLoop(this, executor, (Integer) args[0]);
    }
}
