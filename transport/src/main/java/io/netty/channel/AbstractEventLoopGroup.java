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

import io.netty.util.concurrent.ExecutorFactory;
import io.netty.util.concurrent.AbstractEventExecutorGroup;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.concurrent.Executor;

/**
 * Abstract base class for {@link EventLoopGroup} implementations that handle their tasks with multiple threads at
 * the same time.
 */
public abstract class AbstractEventLoopGroup extends AbstractEventExecutorGroup implements EventLoopGroup {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractEventLoopGroup.class);

    private static final int DEFAULT_EVENT_LOOP_PARALLELISM;

    static {
        DEFAULT_EVENT_LOOP_PARALLELISM = Math.max(1, SystemPropertyUtil.getInt(
                "io.netty.eventLoopParallelism", Runtime.getRuntime().availableProcessors() * 2));

        if (logger.isDebugEnabled()) {
            logger.debug("-Dio.netty.eventLoopParallelism: {}", DEFAULT_EVENT_LOOP_PARALLELISM);
        }
    }

    /**
     * @see {@link AbstractEventExecutorGroup#AbstractEventExecutorGroup(int, Executor, Object...)}
     */
    protected AbstractEventLoopGroup(int nEventLoops, Executor executor, Object... args) {
        super(nEventLoops == 0 ? DEFAULT_EVENT_LOOP_PARALLELISM : nEventLoops, executor, args);
    }

    /**
     * @see {@link AbstractEventExecutorGroup#AbstractEventExecutorGroup(int, ExecutorFactory, Object...)}
     */
    protected AbstractEventLoopGroup(int nEventLoops, ExecutorFactory executorFactory, Object... args) {
        super(nEventLoops == 0 ? DEFAULT_EVENT_LOOP_PARALLELISM : nEventLoops, executorFactory, args);
    }

    @Override
    public EventLoop next() {
        return (EventLoop) super.next();
    }

    @Override
    protected abstract EventLoop newChild(Executor executor, Object... args) throws Exception;

    @Override
    public ChannelFuture register(Channel channel) {
        return next().register(channel);
    }

    @Override
    public ChannelFuture register(Channel channel, ChannelPromise promise) {
        return next().register(channel, promise);
    }
}
