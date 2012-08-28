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
package io.netty.channel.socket.aio;

import io.netty.channel.ChannelTaskScheduler;
import io.netty.channel.EventExecutor;
import io.netty.channel.EventLoopException;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;
import io.netty.util.internal.DetectionUtil;

import java.io.IOException;
import java.nio.channels.AsynchronousChannelGroup;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class AioEventLoopGroup extends MultithreadEventLoopGroup {
    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(AioEventLoopGroup.class);
    private static final AioChannelFinder CHANNEL_FINDER;

    static {
        AioChannelFinder finder;
        try {
            if (DetectionUtil.hasUnsafe()) {
                finder = new UnsafeAioChannelFinder();
            } else {
                finder = new DefaultAioChannelFinder();
            }
        } catch (Throwable t) {
            LOGGER.debug(String.format(
                    "Unable to instance the optimal %s implementation - falling back to %s.",
                    AioChannelFinder.class.getSimpleName(), DefaultAioChannelFinder.class.getSimpleName()), t);
            finder = new DefaultAioChannelFinder();
        }
        CHANNEL_FINDER = finder;
    }

    final AsynchronousChannelGroup group;

    public AioEventLoopGroup() {
        this(0);
    }

    public AioEventLoopGroup(int nThreads) {
        this(nThreads, null);
    }

    public AioEventLoopGroup(int nThreads, ThreadFactory threadFactory) {
        super(nThreads, threadFactory);
        try {
            group = AsynchronousChannelGroup.withThreadPool(new AioExecutorService());
        } catch (IOException e) {
            throw new EventLoopException("Failed to create an AsynchronousChannelGroup", e);
        }
    }

    @Override
    protected EventExecutor newChild(
            ThreadFactory threadFactory, ChannelTaskScheduler scheduler, Object... args) throws Exception {
        return new AioEventLoop(this, threadFactory, scheduler);
    }

    private void executeAioTask(Runnable command) {
        AbstractAioChannel ch = null;
        try {
            ch = CHANNEL_FINDER.findChannel(command);
        } catch (Throwable t) {
            // Ignore
        }

        EventExecutor l;
        if (ch != null) {
            l = ch.eventLoop();
        } else {
            l = next();
        }

        if (l.isShutdown()) {
            command.run();
        } else {
            l.execute(command);
        }
    }

    private final class AioExecutorService extends AbstractExecutorService {

        @Override
        public void shutdown() {
            AioEventLoopGroup.this.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            AioEventLoopGroup.this.shutdown();
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return AioEventLoopGroup.this.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return AioEventLoopGroup.this.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return AioEventLoopGroup.this.awaitTermination(timeout, unit);
        }

        @Override
        public void execute(Runnable command) {
            Class<? extends Runnable> commandType = command.getClass();
            if (commandType.getName().startsWith("sun.nio.ch.")) {
                executeAioTask(command);
            } else {
                next().execute(command);
            }
        }
    }
}
