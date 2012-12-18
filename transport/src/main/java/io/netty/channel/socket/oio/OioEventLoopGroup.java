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
package io.netty.channel.socket.oio;


import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelTaskScheduler;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;

import java.util.Collections;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * {@link EventLoopGroup} which is used to handle OIO {@link Channel}'s. Each {@link Channel} will be handled by its
 * own {@link EventLoop} to not block others.
 */
public class OioEventLoopGroup implements EventLoopGroup {

    private static final StackTraceElement[] STACK_ELEMENTS = new StackTraceElement[0];
    private final int maxChannels;
    final ChannelTaskScheduler scheduler;
    final ThreadFactory threadFactory;
    final Set<OioEventLoop> activeChildren = Collections.newSetFromMap(
            new ConcurrentHashMap<OioEventLoop, Boolean>());
    final Queue<OioEventLoop> idleChildren = new ConcurrentLinkedQueue<OioEventLoop>();
    private final ChannelException tooManyChannels;

    /**
     * Create a new {@link OioEventLoopGroup} with no limit in place.
     */
    public OioEventLoopGroup() {
        this(0);
    }

    /**
     * Create a new {@link OioEventLoopGroup}.
     *
     * @param maxChannels       the maximum number of channels to handle with this instance. Once you try to register
     *                          a new {@link Channel} and the maximum is exceed it will throw an
     *                          {@link ChannelException} on the {@link #register(Channel)} and
     *                          {@link #register(Channel, ChannelFuture)} method.
     *                          Use {@code 0} to use no limit
     */
    public OioEventLoopGroup(int maxChannels) {
        this(maxChannels, Executors.defaultThreadFactory());
    }

    /**
     * Create a new {@link OioEventLoopGroup}.
     *
     * @param maxChannels       the maximum number of channels to handle with this instance. Once you try to register
     *                          a new {@link Channel} and the maximum is exceed it will throw an
     *                          {@link ChannelException} on the {@link #register(Channel)} and
     *                          {@link #register(Channel, ChannelFuture)} method.
     *                          Use {@code 0} to use no limit
     * @param threadFactory     the {@link ThreadFactory} used to create new {@link Thread} instances that handle the
     *                          registered {@link Channel}s
     */
    public OioEventLoopGroup(int maxChannels, ThreadFactory threadFactory) {
        if (maxChannels < 0) {
            throw new IllegalArgumentException(String.format(
                    "maxChannels: %d (expected: >= 0)", maxChannels));
        }
        if (threadFactory == null) {
            throw new NullPointerException("threadFactory");
        }

        this.maxChannels = maxChannels;
        this.threadFactory = threadFactory;

        scheduler = new ChannelTaskScheduler(threadFactory);

        tooManyChannels = new ChannelException("too many channels (max: " + maxChannels + ')');
        tooManyChannels.setStackTrace(STACK_ELEMENTS);
    }

    @Override
    public EventLoop next() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void shutdown() {
        scheduler.shutdown();
        for (EventLoop l: activeChildren) {
            l.shutdown();
        }
        for (EventLoop l: idleChildren) {
            l.shutdown();
        }
    }

    @Override
    public boolean isShutdown() {
        if (!scheduler.isShutdown()) {
            return false;
        }
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
        if (!scheduler.isTerminated()) {
            return false;
        }
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
        for (;;) {
            long timeLeft = deadline - System.nanoTime();
            if (timeLeft <= 0) {
                return isTerminated();
            }
            if (scheduler.awaitTermination(timeLeft, TimeUnit.NANOSECONDS)) {
                break;
            }
        }
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
    public ChannelFuture register(Channel channel, ChannelFuture future) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        try {
            return nextChild().register(channel, future);
        } catch (Throwable t) {
            return channel.newFailedFuture(t);
        }
    }

    private EventLoop nextChild() {
        OioEventLoop loop = idleChildren.poll();
        if (loop == null) {
            if (maxChannels > 0 && activeChildren.size() >= maxChannels) {
                throw tooManyChannels;
            }
            loop = new OioEventLoop(this);
        }
        activeChildren.add(loop);
        return loop;
    }
}
