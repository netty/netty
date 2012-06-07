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
import io.netty.channel.EventExecutor;
import io.netty.channel.EventLoop;
import io.netty.channel.SingleThreadEventExecutor;
import io.netty.util.internal.QueueFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class OioEventLoop implements EventLoop {

    private final int maxChannels;
    final ThreadFactory threadFactory;
    final Set<OioChildEventLoop> activeChildren = Collections.newSetFromMap(
            new ConcurrentHashMap<OioChildEventLoop, Boolean>());
    final Queue<OioChildEventLoop> idleChildren = QueueFactory.createQueue();
    private final ChannelException tooManyChannels;
    private final Unsafe unsafe = new Unsafe() {
        @Override
        public EventExecutor nextChild() {
            throw new UnsupportedOperationException();
        }
    };

    public OioEventLoop() {
        this(0);
    }

    public OioEventLoop(int maxChannels) {
        this(maxChannels, Executors.defaultThreadFactory());
    }

    public OioEventLoop(int maxChannels, ThreadFactory threadFactory) {
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
    public Unsafe unsafe() {
        return unsafe;
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
    public List<Runnable> shutdownNow() {
        for (EventLoop l: activeChildren) {
            l.shutdownNow();
        }
        for (EventLoop l: idleChildren) {
            l.shutdownNow();
        }
        return Collections.emptyList();
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
    public <T> Future<T> submit(Callable<T> task) {
        return currentEventLoop().submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return currentEventLoop().submit(task, result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return currentEventLoop().submit(task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        return currentEventLoop().invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(
            Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        return currentEventLoop().invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        return currentEventLoop().invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks,
            long timeout, TimeUnit unit) throws InterruptedException,
            ExecutionException, TimeoutException {
        return currentEventLoop().invokeAny(tasks, timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        currentEventLoop().execute(command);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay,
            TimeUnit unit) {
        return currentEventLoop().schedule(command, delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return currentEventLoop().schedule(callable, delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return currentEventLoop().scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return currentEventLoop().scheduleWithFixedDelay(command, initialDelay, delay, unit);
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

    @Override
    public boolean inEventLoop() {
        return SingleThreadEventExecutor.currentEventLoop() != null;
    }

    @Override
    public boolean inEventLoop(Thread thread) {
        throw new UnsupportedOperationException();
    }

    private EventLoop nextChild() {
        OioChildEventLoop loop = idleChildren.poll();
        if (loop == null) {
            if (maxChannels > 0 && activeChildren.size() >= maxChannels) {
                throw tooManyChannels;
            }
            loop = new OioChildEventLoop(OioEventLoop.this);
        }
        activeChildren.add(loop);
        return loop;
    }

    private static OioChildEventLoop currentEventLoop() {
        OioChildEventLoop loop =
                (OioChildEventLoop) SingleThreadEventExecutor.currentEventLoop();
        if (loop == null) {
            throw new IllegalStateException("not called from an event loop thread");
        }
        return loop;
    }
}
