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


import io.netty.util.concurrent.AbstractEventExecutorGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.ReadOnlyIterator;

import java.util.Collections;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * An {@link EventLoopGroup} that creates one {@link EventLoop} per {@link Channel}.
 */
public class ThreadPerChannelEventLoopGroup extends AbstractEventExecutorGroup implements EventLoopGroup {

    private static final Object[] NO_ARGS = new Object[0];
    private static final StackTraceElement[] STACK_ELEMENTS = new StackTraceElement[0];

    private final Object[] childArgs;
    private final int maxChannels;
    final ThreadFactory threadFactory;
    final Set<ThreadPerChannelEventLoop> activeChildren =
            Collections.newSetFromMap(PlatformDependent.<ThreadPerChannelEventLoop, Boolean>newConcurrentHashMap());
    final Queue<ThreadPerChannelEventLoop> idleChildren = new ConcurrentLinkedQueue<ThreadPerChannelEventLoop>();
    private final ChannelException tooManyChannels;

    /**
     * Create a new {@link ThreadPerChannelEventLoopGroup} with no limit in place.
     */
    protected ThreadPerChannelEventLoopGroup() {
        this(0);
    }

    /**
     * Create a new {@link ThreadPerChannelEventLoopGroup}.
     *
     * @param maxChannels       the maximum number of channels to handle with this instance. Once you try to register
     *                          a new {@link Channel} and the maximum is exceed it will throw an
     *                          {@link ChannelException} on the {@link #register(Channel)} and
     *                          {@link #register(Channel, ChannelPromise)} method.
     *                          Use {@code 0} to use no limit
     */
    protected ThreadPerChannelEventLoopGroup(int maxChannels) {
        this(maxChannels, Executors.defaultThreadFactory());
    }

    /**
     * Create a new {@link ThreadPerChannelEventLoopGroup}.
     *
     * @param maxChannels       the maximum number of channels to handle with this instance. Once you try to register
     *                          a new {@link Channel} and the maximum is exceed it will throw an
     *                          {@link ChannelException} on the {@link #register(Channel)} and
     *                          {@link #register(Channel, ChannelPromise)} method.
     *                          Use {@code 0} to use no limit
     * @param threadFactory     the {@link ThreadFactory} used to create new {@link Thread} instances that handle the
     *                          registered {@link Channel}s
     * @param args              arguments which will passed to each {@link #newChild(Object...)} call.
     */
    protected ThreadPerChannelEventLoopGroup(int maxChannels, ThreadFactory threadFactory, Object... args) {
        if (maxChannels < 0) {
            throw new IllegalArgumentException(String.format(
                    "maxChannels: %d (expected: >= 0)", maxChannels));
        }
        if (threadFactory == null) {
            throw new NullPointerException("threadFactory");
        }

        if (args == null) {
            childArgs = NO_ARGS;
        } else {
            childArgs = args.clone();
        }

        this.maxChannels = maxChannels;
        this.threadFactory = threadFactory;

        tooManyChannels = new ChannelException("too many channels (max: " + maxChannels + ')');
        tooManyChannels.setStackTrace(STACK_ELEMENTS);
    }

    /**
     * Creates a new {@link EventLoop}.  The default implementation creates a new {@link ThreadPerChannelEventLoop}.
     */
    protected ThreadPerChannelEventLoop newChild(
            @SuppressWarnings("UnusedParameters") Object... args) throws Exception {
        return new ThreadPerChannelEventLoop(this);
    }

    @Override
    public Iterator<EventExecutor> iterator() {
        return new ReadOnlyIterator<EventExecutor>(activeChildren.iterator());
    }

    @Override
    public EventLoop next() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void shutdown() {
        for (EventLoop l: activeChildren) {
            l.shutdown();
        }
        for (EventLoop l: idleChildren) {
            l.shutdown();
        }
    }

    @Override
    public boolean isShutdown() {
        for (EventLoop l: activeChildren) {
            if (!l.isShutdown()) {
                return false;
            }
        }
        for (EventLoop l: idleChildren) {
            if (!l.isShutdown()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isTerminated() {
        for (EventLoop l: activeChildren) {
            if (!l.isTerminated()) {
                return false;
            }
        }
        for (EventLoop l: idleChildren) {
            if (!l.isTerminated()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        for (EventLoop l: activeChildren) {
            for (;;) {
                long timeLeft = deadline - System.nanoTime();
                if (timeLeft <= 0) {
                    return isTerminated();
                }
                if (l.awaitTermination(timeLeft, TimeUnit.NANOSECONDS)) {
                    break;
                }
            }
        }
        for (EventLoop l: idleChildren) {
            for (;;) {
                long timeLeft = deadline - System.nanoTime();
                if (timeLeft <= 0) {
                    return isTerminated();
                }
                if (l.awaitTermination(timeLeft, TimeUnit.NANOSECONDS)) {
                    break;
                }
            }
        }
        return isTerminated();
    }

    @Override
    public ChannelFuture register(Channel channel) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        try {
            return nextChild().register(channel);
        } catch (Throwable t) {
            return channel.newFailedFuture(t);
        }
    }

    @Override
    public ChannelFuture register(Channel channel, ChannelPromise promise) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        try {
            return nextChild().register(channel, promise);
        } catch (Throwable t) {
            promise.setFailure(t);
            return promise;
        }
    }

    private EventLoop nextChild() throws Exception {
        ThreadPerChannelEventLoop loop = idleChildren.poll();
        if (loop == null) {
            if (maxChannels > 0 && activeChildren.size() >= maxChannels) {
                throw tooManyChannels;
            }
            loop = newChild(childArgs);
        }
        activeChildren.add(loop);
        return loop;
    }
}
