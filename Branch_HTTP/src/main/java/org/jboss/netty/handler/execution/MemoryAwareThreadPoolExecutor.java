/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.handler.execution;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.util.LinkedTransferQueue;

/**
 * A {@link ThreadPoolExecutor} which blocks the task submission when there's
 * too many tasks in the queue.
 * <p>
 * Both per-{@link Channel} and per-{@link Executor} limitation can be applied.
 * If the total size of the unprocessed tasks (i.e. {@link Runnable}s) exceeds
 * either per-{@link Channel} or per-{@link Executor} threshold, any further
 * {@link #execute(Runnable)} call will block until the tasks in the queue
 * are processed so that the total size goes under the threshold.
 * <p>
 * {@link ObjectSizeEstimator} is used to calculate the size of each task.
 * <p>
 * Please note that this executor does not maintain the order of the
 * {@link ChannelEvent}s for the same {@link Channel}.  For example,
 * you can even receive a {@code "channelClosed"} event before a
 * {@code "messageReceived"} event, as depicted by the following diagram.
 *
 * For example, the events can be processed as depicted below:
 *
 * <pre>
 *           --------------------------------&gt; Timeline --------------------------------&gt;
 *
 * Thread X: --- Channel A (Event 2) --- Channel A (Event 1) ---------------------------&gt;
 *
 * Thread Y: --- Channel A (Event 3) --- Channel B (Event 2) --- Channel B (Event 3) ---&gt;
 *
 * Thread Z: --- Channel B (Event 1) --- Channel B (Event 4) --- Channel A (Event 4) ---&gt;
 * </pre>
 *
 * To maintain the event order, you must use {@link OrderedMemoryAwareThreadPoolExecutor}.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 * @apiviz.uses org.jboss.netty.handler.execution.ObjectSizeEstimator
 * @apiviz.uses org.jboss.netty.handler.execution.ChannelEventRunnable
 */
public class MemoryAwareThreadPoolExecutor extends ThreadPoolExecutor {

    private volatile Settings settings = new Settings(0, 0);

    // XXX Can be changed in runtime now.  Make it mutable in 3.1.
    private final ObjectSizeEstimator objectSizeEstimator;

    private final ConcurrentMap<Channel, AtomicLong> channelCounters =
        new ConcurrentHashMap<Channel, AtomicLong>();
    private final AtomicLong totalCounter = new AtomicLong();

    private final Semaphore semaphore = new Semaphore(0);

    /**
     * Creates a new instance.
     *
     * @param corePoolSize          the maximum number of active threads
     * @param maxChannelMemorySize  the maximum total size of the queued events per channel.
     *                              Specify {@code 0} to disable.
     * @param maxTotalMemorySize    the maximum total size of the queued events for this pool
     *                              Specify {@code 0} to disable.
     */
    public MemoryAwareThreadPoolExecutor(
            int corePoolSize, long maxChannelMemorySize, long maxTotalMemorySize) {

        this(corePoolSize, maxChannelMemorySize, maxTotalMemorySize, 30, TimeUnit.SECONDS);
    }

    /**
     * Creates a new instance.
     *
     * @param corePoolSize          the maximum number of active threads
     * @param maxChannelMemorySize  the maximum total size of the queued events per channel.
     *                              Specify {@code 0} to disable.
     * @param maxTotalMemorySize    the maximum total size of the queued events for this pool
     *                              Specify {@code 0} to disable.
     * @param keepAliveTime         the amount of time for an inactive thread to shut itself down
     * @param unit                  the {@link TimeUnit} of {@code keepAliveTime}
     */
    public MemoryAwareThreadPoolExecutor(
            int corePoolSize, long maxChannelMemorySize, long maxTotalMemorySize,
            long keepAliveTime, TimeUnit unit) {

        this(corePoolSize, maxChannelMemorySize, maxTotalMemorySize, keepAliveTime, unit, Executors.defaultThreadFactory());
    }

    /**
     * Creates a new instance.
     *
     * @param corePoolSize          the maximum number of active threads
     * @param maxChannelMemorySize  the maximum total size of the queued events per channel.
     *                              Specify {@code 0} to disable.
     * @param maxTotalMemorySize    the maximum total size of the queued events for this pool
     *                              Specify {@code 0} to disable.
     * @param keepAliveTime         the amount of time for an inactive thread to shut itself down
     * @param unit                  the {@link TimeUnit} of {@code keepAliveTime}
     * @param threadFactory         the {@link ThreadFactory} of this pool
     */
    public MemoryAwareThreadPoolExecutor(
            int corePoolSize, long maxChannelMemorySize, long maxTotalMemorySize,
            long keepAliveTime, TimeUnit unit, ThreadFactory threadFactory) {

        this(corePoolSize, maxChannelMemorySize, maxTotalMemorySize, keepAliveTime, unit, new DefaultObjectSizeEstimator(), threadFactory);
    }

    /**
     * Creates a new instance.
     *
     * @param corePoolSize          the maximum number of active threads
     * @param maxChannelMemorySize  the maximum total size of the queued events per channel.
     *                              Specify {@code 0} to disable.
     * @param maxTotalMemorySize    the maximum total size of the queued events for this pool
     *                              Specify {@code 0} to disable.
     * @param keepAliveTime         the amount of time for an inactive thread to shut itself down
     * @param unit                  the {@link TimeUnit} of {@code keepAliveTime}
     * @param threadFactory         the {@link ThreadFactory} of this pool
     * @param objectSizeEstimator   the {@link ObjectSizeEstimator} of this pool
     */
    public MemoryAwareThreadPoolExecutor(
            int corePoolSize, long maxChannelMemorySize, long maxTotalMemorySize,
            long keepAliveTime, TimeUnit unit, ObjectSizeEstimator objectSizeEstimator,
            ThreadFactory threadFactory) {

        super(corePoolSize, corePoolSize, keepAliveTime, unit, new LinkedTransferQueue<Runnable>(), threadFactory);

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

    /**
     * Returns the {@link ObjectSizeEstimator} of this pool.
     */
    public ObjectSizeEstimator getObjectSizeEstimator() {
        return objectSizeEstimator;
    }

    /**
     * Returns the maximum total size of the queued events per channel.
     */
    public long getMaxChannelMemorySize() {
        return settings.maxChannelMemorySize;
    }

    /**
     * Sets the maximum total size of the queued events per channel.
     * Specify {@code 0} to disable.
     */
    public void setMaxChannelMemorySize(long maxChannelMemorySize) {
        if (maxChannelMemorySize < 0) {
            throw new IllegalArgumentException(
                    "maxChannelMemorySize: " + maxChannelMemorySize);
        }

        if (getTaskCount() > 0) {
            throw new IllegalStateException(
                    "can't be changed after a task is executed");
        }

        settings = new Settings(maxChannelMemorySize, settings.maxTotalMemorySize);
    }

    /**
     * Returns the maximum total size of the queued events for this pool.
     */
    public long getMaxTotalMemorySize() {
        return settings.maxTotalMemorySize;
    }

    /**
     * Sets the maximum total size of the queued events for this pool.
     * Specify {@code 0} to disable.
     */
    public void setMaxTotalMemorySize(long maxTotalMemorySize) {
        if (maxTotalMemorySize < 0) {
            throw new IllegalArgumentException(
                    "maxTotalMemorySize: " + maxTotalMemorySize);
        }

        if (getTaskCount() > 0) {
            throw new IllegalStateException(
                    "can't be changed after a task is executed");
        }

        settings = new Settings(settings.maxChannelMemorySize, maxTotalMemorySize);
    }

    @Override
    public void execute(Runnable command) {
        boolean pause = increaseCounter(command);
        doExecute(command);
        if (pause) {
            //System.out.println("ACQUIRE");
            semaphore.acquireUninterruptibly();
        }
    }

    /**
     * Put the actual execution logic here.  The default implementation simply
     * calls {@link #doUnorderedExecute(Runnable)}.
     */
    protected void doExecute(Runnable task) {
        doUnorderedExecute(task);
    }

    /**
     * Executes the specified task without maintaining the event order.
     */
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

    @Override
    protected void afterExecute(Runnable r, Throwable e) {
        super.afterExecute(r, e);
    }
    protected boolean increaseCounter(Runnable task) {
        if (!shouldCount(task)) {
            return false;
        }

        Settings settings = this.settings;
        long maxTotalMemorySize = settings.maxTotalMemorySize;
        long maxChannelMemorySize = settings.maxChannelMemorySize;

        int increment = getObjectSizeEstimator().estimateSize(task);
        long totalCounter = this.totalCounter.addAndGet(increment);

        if (task instanceof ChannelEventRunnable) {
            ChannelEventRunnable eventTask = (ChannelEventRunnable) task;
            eventTask.estimatedSize = increment;
            Channel channel = eventTask.getEvent().getChannel();
            long channelCounter = getChannelCounter(channel).addAndGet(increment);
            //System.out.println("IC: " + channelCounter + ", " + increment);
            if (maxChannelMemorySize != 0 && channelCounter >= maxChannelMemorySize && channel.isOpen()) {
                if (channel.isReadable()) {
                    //System.out.println("UNREADABLE");
                    channel.setReadable(false);
                }
            }
        }

        //System.out.println("I: " + totalCounter + ", " + increment);
        return maxTotalMemorySize != 0 && totalCounter >= maxTotalMemorySize;
    }

    protected void decreaseCounter(Runnable task) {
        if (!shouldCount(task)) {
            return;
        }

        Settings settings = this.settings;
        long maxTotalMemorySize = settings.maxTotalMemorySize;
        long maxChannelMemorySize = settings.maxChannelMemorySize;

        int increment;
        if (task instanceof ChannelEventRunnable) {
            increment = ((ChannelEventRunnable) task).estimatedSize;
        } else {
            increment = getObjectSizeEstimator().estimateSize(task);
        }

        long totalCounter = this.totalCounter.addAndGet(-increment);

        //System.out.println("D: " + totalCounter + ", " + increment);
        if (maxTotalMemorySize != 0 && totalCounter + increment >= maxTotalMemorySize) {
            //System.out.println("RELEASE");
            semaphore.release();
        }

        if (task instanceof ChannelEventRunnable) {
            Channel channel = ((ChannelEventRunnable) task).getEvent().getChannel();
            long channelCounter = getChannelCounter(channel).addAndGet(-increment);
            //System.out.println("DC: " + channelCounter + ", " + increment);
            if (maxChannelMemorySize != 0 && channelCounter < maxChannelMemorySize && channel.isOpen()) {
                if (!channel.isReadable()) {
                    //System.out.println("READABLE");
                    channel.setReadable(true);
                }
            }
        }
    }

    private AtomicLong getChannelCounter(Channel channel) {
        AtomicLong counter = channelCounters.get(channel);
        if (counter == null) {
            counter = new AtomicLong();
            AtomicLong oldCounter = channelCounters.putIfAbsent(channel, counter);
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

    /**
     * Returns {@code true} if and only if the specified {@code task} should
     * be counted to limit the global and per-channel memory consumption.
     * To override this method, you must call {@code super.shouldCount()} to
     * make sure important tasks are not counted.
     */
    protected boolean shouldCount(Runnable task) {
        if (task instanceof ChannelEventRunnable) {
            ChannelEventRunnable r = (ChannelEventRunnable) task;
            if (r.getEvent() instanceof ChannelStateEvent) {
                ChannelStateEvent e = (ChannelStateEvent) r.getEvent();
                if (e.getState() == ChannelState.INTEREST_OPS) {
                    return false;
                }
            }
        }
        return true;
    }

    private static class Settings {
        final long maxChannelMemorySize;
        final long maxTotalMemorySize;

        Settings(long maxChannelMemorySize, long maxTotalMemorySize) {
            this.maxChannelMemorySize = maxChannelMemorySize;
            this.maxTotalMemorySize = maxTotalMemorySize;
        }
    }
}
