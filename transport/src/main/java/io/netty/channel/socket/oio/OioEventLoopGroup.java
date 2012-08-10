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

public class OioEventLoopGroup implements EventLoopGroup {

    private final int maxChannels;
    final ThreadFactory threadFactory;
    final Set<OioEventLoop> activeChildren = Collections.newSetFromMap(
            new ConcurrentHashMap<OioEventLoop, Boolean>());
    final Queue<OioEventLoop> idleChildren = new ConcurrentLinkedQueue<OioEventLoop>();
    private final ChannelException tooManyChannels;

    public OioEventLoopGroup() {
        this(0);
    }

    public OioEventLoopGroup(int maxChannels) {
        this(maxChannels, Executors.defaultThreadFactory());
    }

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

        tooManyChannels = new ChannelException("too many channels (max: " + maxChannels + ')');
        tooManyChannels.setStackTrace(new StackTraceElement[0]);
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
