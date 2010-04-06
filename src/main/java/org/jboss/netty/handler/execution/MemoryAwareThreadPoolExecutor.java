/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.execution;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.WriteCompletionEvent;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.DefaultObjectSizeEstimator;
import org.jboss.netty.util.ObjectSizeEstimator;
import org.jboss.netty.util.internal.ConcurrentIdentityHashMap;
import org.jboss.netty.util.internal.LinkedTransferQueue;
import org.jboss.netty.util.internal.SharedResourceMisuseDetector;

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
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.has org.jboss.netty.util.ObjectSizeEstimator oneway - -
 * @apiviz.has org.jboss.netty.handler.execution.ChannelEventRunnable oneway - - executes
 */
public class MemoryAwareThreadPoolExecutor extends ThreadPoolExecutor {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(MemoryAwareThreadPoolExecutor.class);

    private static final SharedResourceMisuseDetector misuseDetector =
        new SharedResourceMisuseDetector(MemoryAwareThreadPoolExecutor.class);

    private volatile Settings settings;

    private final ConcurrentMap<Channel, AtomicLong> channelCounters =
        new ConcurrentIdentityHashMap<Channel, AtomicLong>();
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

        super(corePoolSize, corePoolSize, keepAliveTime, unit,
              new LinkedTransferQueue<Runnable>(), threadFactory, new NewThreadRunsPolicy());

        if (objectSizeEstimator == null) {
            throw new NullPointerException("objectSizeEstimator");
        }
        if (maxChannelMemorySize < 0) {
            throw new IllegalArgumentException(
                    "maxChannelMemorySize: " + maxChannelMemorySize);
        }
        if (maxTotalMemorySize < 0) {
            throw new IllegalArgumentException(
                    "maxTotalMemorySize: " + maxTotalMemorySize);
        }

        // Call allowCoreThreadTimeOut(true) using reflection
        // because it is not supported in Java 5.
        try {
            Method m = getClass().getMethod("allowCoreThreadTimeOut", new Class[] { boolean.class });
            m.invoke(this, Boolean.TRUE);
        } catch (Throwable t) {
            // Java 5
            logger.debug(
                    "ThreadPoolExecutor.allowCoreThreadTimeOut() is not " +
                    "supported in this platform.");
        }

        settings = new Settings(
                objectSizeEstimator, maxChannelMemorySize, maxTotalMemorySize);

        // Misuse check
        misuseDetector.increase();
    }

    @Override
    protected void terminated() {
        super.terminated();
        misuseDetector.decrease();
    }

    /**
     * Returns the {@link ObjectSizeEstimator} of this pool.
     */
    public ObjectSizeEstimator getObjectSizeEstimator() {
        return settings.objectSizeEstimator;
    }

    /**
     * Sets the {@link ObjectSizeEstimator} of this pool.
     */
    public void setObjectSizeEstimator(ObjectSizeEstimator objectSizeEstimator) {
        if (objectSizeEstimator == null) {
            throw new NullPointerException("objectSizeEstimator");
        }

        settings = new Settings(
                objectSizeEstimator,
                settings.maxChannelMemorySize, settings.maxTotalMemorySize);
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

        settings = new Settings(
                settings.objectSizeEstimator,
                maxChannelMemorySize, settings.maxTotalMemorySize);
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

        settings = new Settings(
                settings.objectSizeEstimator,
                settings.maxChannelMemorySize, maxTotalMemorySize);
    }

    @Override
    public void execute(Runnable command) {
        if (!(command instanceof ChannelEventRunnable)) {
            command = new MemoryAwareRunnable(command);
        }

        boolean pause = increaseCounter(command);
        doExecute(command);
        if (pause) {
            //System.out.println("ACQUIRE: " + command);
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

    protected boolean increaseCounter(Runnable task) {
        if (!shouldCount(task)) {
            return false;
        }

        Settings settings = this.settings;
        long maxTotalMemorySize = settings.maxTotalMemorySize;
        long maxChannelMemorySize = settings.maxChannelMemorySize;

        int increment = settings.objectSizeEstimator.estimateSize(task);
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
                    ChannelHandlerContext ctx = eventTask.getContext();
                    if (ctx.getHandler() instanceof ExecutionHandler) {
                        // readSuspended = true;
                        ctx.setAttachment(Boolean.TRUE);
                    }
                    channel.setReadable(false);
                }
            }
        } else {
            ((MemoryAwareRunnable) task).estimatedSize = increment;
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
            increment = ((MemoryAwareRunnable) task).estimatedSize;
        }

        long totalCounter = this.totalCounter.addAndGet(-increment);

        //System.out.println("D: " + totalCounter + ", " + increment);
        if (maxTotalMemorySize != 0 && totalCounter + increment >= maxTotalMemorySize) {
            //System.out.println("RELEASE: " + task);
            semaphore.release();
        }

        if (task instanceof ChannelEventRunnable) {
            ChannelEventRunnable eventTask = (ChannelEventRunnable) task;
            Channel channel = eventTask.getEvent().getChannel();
            long channelCounter = getChannelCounter(channel).addAndGet(-increment);
            //System.out.println("DC: " + channelCounter + ", " + increment);
            if (maxChannelMemorySize != 0 && channelCounter < maxChannelMemorySize && channel.isOpen()) {
                if (!channel.isReadable()) {
                    //System.out.println("READABLE");
                    ChannelHandlerContext ctx = eventTask.getContext();
                    if (ctx.getHandler() instanceof ExecutionHandler) {
                        // readSuspended = false;
                        ctx.setAttachment(null);
                    }
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
            ChannelEvent e = r.getEvent();
            if (e instanceof WriteCompletionEvent) {
                return false;
            } else if (e instanceof ChannelStateEvent) {
                if (((ChannelStateEvent) e).getState() == ChannelState.INTEREST_OPS) {
                    return false;
                }
            }
        }
        return true;
    }

    private static final class Settings {
        final ObjectSizeEstimator objectSizeEstimator;
        final long maxChannelMemorySize;
        final long maxTotalMemorySize;

        Settings(ObjectSizeEstimator objectSizeEstimator,
                 long maxChannelMemorySize, long maxTotalMemorySize) {
            this.objectSizeEstimator = objectSizeEstimator;
            this.maxChannelMemorySize = maxChannelMemorySize;
            this.maxTotalMemorySize = maxTotalMemorySize;
        }
    }

    private static final class NewThreadRunsPolicy implements RejectedExecutionHandler {
        NewThreadRunsPolicy() {
            super();
        }

        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            try {
                final Thread t = new Thread(r, "Temporary task executor");
                t.start();
            } catch (Throwable e) {
                throw new RejectedExecutionException(
                        "Failed to start a new thread", e);
            }
        }
    }

    private static final class MemoryAwareRunnable implements Runnable {
        final Runnable task;
        int estimatedSize;

        MemoryAwareRunnable(Runnable task) {
            this.task = task;
        }

        public void run() {
            task.run();
        }
    }
}
