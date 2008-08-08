/*
 * Copyright (C) 2008  Trustin Heuiseung Lee
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.handler.execution;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.gleamynode.netty.channel.Channel;
import net.gleamynode.netty.channel.ChannelState;
import net.gleamynode.netty.channel.ChannelStateEvent;

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 * @apiviz.uses net.gleamynode.netty.handler.execution.ObjectSizeEstimator
 * @apiviz.uses net.gleamynode.netty.handler.execution.ChannelEventRunnable
 */
public class MemoryAwareThreadPoolExecutor extends ThreadPoolExecutor {

    private volatile int maxChannelMemorySize;
    private volatile int maxTotalMemorySize;
    private final ObjectSizeEstimator objectSizeEstimator;

    private final ConcurrentMap<Channel, AtomicInteger> channelCounters =
        new ConcurrentHashMap<Channel, AtomicInteger>();
    private final AtomicInteger totalCounter = new AtomicInteger();

    private final Semaphore semaphore = new Semaphore(0);

    public MemoryAwareThreadPoolExecutor(
            int corePoolSize, int maxChannelMemorySize, int maxTotalMemorySize) {
        this(corePoolSize, maxChannelMemorySize, maxTotalMemorySize, 30, TimeUnit.SECONDS);
    }

    public MemoryAwareThreadPoolExecutor(
            int corePoolSize, int maxChannelMemorySize, int maxTotalMemorySize, long keepAliveTime, TimeUnit unit) {
        this(corePoolSize, maxChannelMemorySize, maxTotalMemorySize, keepAliveTime, unit, Executors.defaultThreadFactory());
    }

    public MemoryAwareThreadPoolExecutor(
            int corePoolSize, int maxChannelMemorySize, int maxTotalMemorySize, long keepAliveTime, TimeUnit unit, ThreadFactory threadFactory) {
        this(corePoolSize, maxChannelMemorySize, maxTotalMemorySize, keepAliveTime, unit, new DefaultObjectSizeEstimator(), threadFactory);
    }
    public MemoryAwareThreadPoolExecutor(
            int corePoolSize, int maxChannelMemorySize, int maxTotalMemorySize, long keepAliveTime, TimeUnit unit, ObjectSizeEstimator objectSizeEstimator, ThreadFactory threadFactory) {
        super(corePoolSize, corePoolSize, keepAliveTime, unit, new LinkedBlockingQueue<Runnable>(), threadFactory);

        if (objectSizeEstimator == null) {
            throw new NullPointerException("objectSizeEstimator");
        }
        this.objectSizeEstimator = objectSizeEstimator;

        // Call allowCoreThreadTimeOut(true) using reflection
        // because it's not supported in Java 5.
        try {
            Method m = getClass().getMethod("allowCoreThreadTimeOut", new Class[] { boolean.class });
            m.invoke(this, Boolean.TRUE);
        } catch (Exception e) {
            // Java 5
        }

        setMaxChannelMemorySize(maxChannelMemorySize);
        setMaxTotalMemorySize(maxTotalMemorySize);
    }

    public ObjectSizeEstimator getObjectSizeEstimator() {
        return objectSizeEstimator;
    }

    public int getMaxChannelMemorySize() {
        return maxChannelMemorySize;
    }

    public void setMaxChannelMemorySize(int maxChannelMemorySize) {
        if (maxChannelMemorySize < 0) {
            throw new IllegalArgumentException(
                    "maxChannelMemorySize: " + maxChannelMemorySize);
        }
        this.maxChannelMemorySize = maxChannelMemorySize;
    }

    public int getMaxTotalMemorySize() {
        return maxTotalMemorySize;
    }

    public void setMaxTotalMemorySize(int maxTotalMemorySize) {
        if (maxTotalMemorySize < 0) {
            throw new IllegalArgumentException(
                    "maxTotalMemorySize: " + maxTotalMemorySize);
        }
        this.maxTotalMemorySize = maxTotalMemorySize;
    }

    @Override
    public void execute(Runnable command) {
        boolean pause = increaseCounter(command);
        doExecute(command);
        if (pause) {
            for (;;) {
                try {
                    semaphore.acquire();
                    break;
                } catch (InterruptedException e) {
                    // Ignore.
                }
            }
        }
    }

    protected void doExecute(Runnable task) {
        doUnorderedExecute(task);
    }

    protected final void doUnorderedExecute(Runnable task) {
        super.execute(task);
    }

    @Override
    public boolean remove(Runnable task) {
        boolean removed = super.remove(task);
        if (removed) {
            decreaseCounter(task);
        }
        return removed;
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);
        decreaseCounter(r);
    }

    private boolean increaseCounter(Runnable task) {
        if (isInterestOpsEvent(task)) {
            return false;
        }

        int increment = getObjectSizeEstimator().estimateSize(task);
        int maxTotalMemorySize = getMaxTotalMemorySize();
        int totalCounter = this.totalCounter.addAndGet(increment);

        if (task instanceof ChannelEventRunnable) {
            Channel channel = ((ChannelEventRunnable) task).getEvent().getChannel();
            int maxChannelMemorySize = getMaxChannelMemorySize();
            int channelCounter = getChannelCounter(channel).addAndGet(increment);
            if (maxChannelMemorySize != 0 && channelCounter >= maxChannelMemorySize && channel.isOpen()) {
                if (channel.isReadable()) {
                    channel.setReadable(false);
                }
            }
        }

        return maxTotalMemorySize != 0 && totalCounter >= maxTotalMemorySize;
    }

    private void decreaseCounter(Runnable task) {
        if (isInterestOpsEvent(task)) {
            return;
        }

        int increment = getObjectSizeEstimator().estimateSize(task);
        int maxTotalMemorySize = getMaxTotalMemorySize();
        int totalCounter = this.totalCounter.addAndGet(-increment);

        if (maxTotalMemorySize == 0 || totalCounter < maxTotalMemorySize) {
            semaphore.release();
        }

        if (task instanceof ChannelEventRunnable) {
            Channel channel = ((ChannelEventRunnable) task).getEvent().getChannel();
            int maxChannelMemorySize = getMaxChannelMemorySize();
            int channelCounter = getChannelCounter(channel).addAndGet(-increment);
            if ((maxChannelMemorySize == 0 || channelCounter < maxChannelMemorySize) && channel.isOpen()) {
                if (!channel.isReadable()) {
                    channel.setReadable(true);
                }
            }
        }
    }

    private AtomicInteger getChannelCounter(Channel channel) {
        AtomicInteger counter = channelCounters.get(channel);
        if (counter == null) {
            counter = new AtomicInteger();
            AtomicInteger oldCounter = channelCounters.putIfAbsent(channel, counter);
            if (oldCounter != null) {
                counter = oldCounter;
            }
        }

        // Remove the entry when the channel closes.
        if (!channel.isOpen()) {
            channelCounters.remove(channel);
        }
        return counter;
    }

    private static boolean isInterestOpsEvent(Runnable task) {
        if (task instanceof ChannelEventRunnable) {
            ChannelEventRunnable r = (ChannelEventRunnable) task;
            if (r.getEvent() instanceof ChannelStateEvent) {
                ChannelStateEvent e = (ChannelStateEvent) r.getEvent();
                if (e.getState() == ChannelState.INTEREST_OPS) {
                    return true;
                }
            }
        }
        return false;
    }
}
