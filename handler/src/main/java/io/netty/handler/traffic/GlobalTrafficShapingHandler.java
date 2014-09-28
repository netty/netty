/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.traffic;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.util.concurrent.EventExecutor;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 * This implementation of the {@link AbstractTrafficShapingHandler} is for global
 * traffic shaping, that is to say a global limitation of the bandwidth, whatever
 * the number of opened channels.<br><br>
 *
 * The general use should be as follow:<br>
 * <ul>
 * <li>Create your unique GlobalTrafficShapingHandler like:<br><br>
 * <tt>GlobalTrafficShapingHandler myHandler = new GlobalTrafficShapingHandler(executor);</tt><br><br>
 * The executor could be the underlying IO worker pool<br>
 * <tt>pipeline.addLast(myHandler);</tt><br><br>
 *
 * <b>Note that this handler has a Pipeline Coverage of "all" which means only one such handler must be created
 * and shared among all channels as the counter must be shared among all channels.</b><br><br>
 *
 * Other arguments can be passed like write or read limitation (in bytes/s where 0 means no limitation)
 * or the check interval (in millisecond) that represents the delay between two computations of the
 * bandwidth and so the call back of the doAccounting method (0 means no accounting at all).<br><br>
 *
 * A value of 0 means no accounting for checkInterval. If you need traffic shaping but no such accounting,
 * it is recommended to set a positive value, even if it is high since the precision of the
 * Traffic Shaping depends on the period where the traffic is computed. The highest the interval,
 * the less precise the traffic shaping will be. It is suggested as higher value something close
 * to 5 or 10 minutes.<br><br>
 *
 * maxTimeToWait, by default set to 15s, allows to specify an upper bound of time shaping.<br>
 * </li>
 * </ul><br>
 *
 * Be sure to call {@link #release()} once this handler is not needed anymore to release all internal resources.
 * This will not shutdown the {@link EventExecutor} as it may be shared, so you need to do this by your own.
 */
@Sharable
public class GlobalTrafficShapingHandler extends AbstractTrafficShapingHandler {
    /**
     * All queues per channel
     */
    private Map<Integer, PerChannel> channelQueues = new HashMap<Integer, PerChannel>();
    /**
     * Global queues size
     */
    private long queuesSize = 0;
    /**
     * Max size in the list before proposing to stop writing new objects from next handlers
     * for all channel (global)
     */
    protected long maxGlobalWriteSize = DEFAULT_MAX_SIZE*100; // default 400MB

    private static class PerChannel {
        List<ToSend> messagesQueue;
        long queueSize;
        long lastWrite;
        long lastRead;
    }
    /**
     * Create the global TrafficCounter
     */
    void createGlobalTrafficCounter(ScheduledExecutorService executor) {
        if (executor == null) {
            throw new NullPointerException("executor");
        }
        TrafficCounter tc = new TrafficCounter(this, executor, "GlobalTC",
                    checkInterval);
        setTrafficCounter(tc);
        tc.start();
    }

    /**
     * Create a new instance
     *
     * @param executor
     *            the {@link ScheduledExecutorService} to use for the {@link TrafficCounter}
     * @param writeLimit
     *            0 or a limit in bytes/s
     * @param readLimit
     *            0 or a limit in bytes/s
     * @param checkInterval
     *            The delay between two computations of performances for
     *            channels or 0 if no stats are to be computed
     * @param maxTime
     *            The maximum delay to wait in case of traffic excess
     */
    public GlobalTrafficShapingHandler(ScheduledExecutorService executor, long writeLimit, long readLimit,
            long checkInterval, long maxTime) {
        super(writeLimit, readLimit, checkInterval, maxTime);
        createGlobalTrafficCounter(executor);
    }

    /**
     * Create a new instance
     *
     * @param executor
     *          the {@link ScheduledExecutorService} to use for the {@link TrafficCounter}
     * @param writeLimit
     *          0 or a limit in bytes/s
     * @param readLimit
     *          0 or a limit in bytes/s
     * @param checkInterval
     *          The delay between two computations of performances for
     *            channels or 0 if no stats are to be computed
     */
    public GlobalTrafficShapingHandler(ScheduledExecutorService executor, long writeLimit,
            long readLimit, long checkInterval) {
        super(writeLimit, readLimit, checkInterval);
        createGlobalTrafficCounter(executor);
    }

    /**
     * Create a new instance
     *
     * @param executor
     *          the {@link ScheduledExecutorService} to use for the {@link TrafficCounter}
     * @param writeLimit
     *          0 or a limit in bytes/s
     * @param readLimit
     *          0 or a limit in bytes/s
     */
    public GlobalTrafficShapingHandler(ScheduledExecutorService executor, long writeLimit,
            long readLimit) {
        super(writeLimit, readLimit);
        createGlobalTrafficCounter(executor);
    }

    /**
     * Create a new instance
     *
     * @param executor
     *          the {@link ScheduledExecutorService} to use for the {@link TrafficCounter}
     * @param checkInterval
     *          The delay between two computations of performances for
     *            channels or 0 if no stats are to be computed
     */
    public GlobalTrafficShapingHandler(ScheduledExecutorService executor, long checkInterval) {
        super(checkInterval);
        createGlobalTrafficCounter(executor);
    }

    /**
     * Create a new instance
     *
     * @param executor
     *          the {@link ScheduledExecutorService} to use for the {@link TrafficCounter}
     */
    public GlobalTrafficShapingHandler(EventExecutor executor) {
        createGlobalTrafficCounter(executor);
    }

    /**
     * @return the maxGlobalWriteSize
     */
    public long getMaxGlobalWriteSize() {
        return maxGlobalWriteSize;
    }

    /**
     * @param maxGlobalWriteSize the maximum Global Write Size allowed in the buffer
     *            globally for all channels before write suspended is set
     */
    public void setMaxGlobalWriteSize(long maxGlobalWriteSize) {
        this.maxGlobalWriteSize = maxGlobalWriteSize;
    }

    /**
     * @return the global size of the buffers for all queues
     */
    public long queuesSize() {
        return queuesSize;
    }

    /**
     * Release all internal resources of this instance
     */
    public final void release() {
        if (trafficCounter != null) {
            trafficCounter.stop();
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        Integer key = ctx.channel().hashCode();
        PerChannel perChannel = new PerChannel();
        perChannel.messagesQueue = new LinkedList<ToSend>();;
        perChannel.queueSize = 0L;
        perChannel.lastRead = System.currentTimeMillis();
        perChannel.lastWrite = System.currentTimeMillis();
        channelQueues.put(key, perChannel);
        super.handlerAdded(ctx);
    }

    @Override
    public synchronized void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        Integer key = ctx.channel().hashCode();
        PerChannel perChannel = channelQueues.remove(key);
        if (perChannel != null) {
            for (ToSend toSend : perChannel.messagesQueue) {
                queuesSize -= calculateSize(toSend.toSend);
                if (toSend.toSend instanceof ByteBuf) {
                    ((ByteBuf) toSend.toSend).release();
                }
            }
            perChannel.messagesQueue.clear();
        }
        super.handlerRemoved(ctx);
    }

    @Override
    protected long checkWaitReadTime(final ChannelHandlerContext ctx, long wait) {
        Integer key = ctx.channel().hashCode();
        PerChannel perChannel = channelQueues.get(key);
        if (perChannel != null) {
            if (wait > maxTime && System.currentTimeMillis() + wait - perChannel.lastRead > maxTime) {
                wait = maxTime;
            }
        }
        return wait;
    }
    @Override
    protected void informReadOperation(final ChannelHandlerContext ctx) {
        Integer key = ctx.channel().hashCode();
        PerChannel perChannel = channelQueues.get(key);
        if (perChannel != null) {
            perChannel.lastRead = System.currentTimeMillis();
        }
    }

    private static final class ToSend {
        final long date;
        final Object toSend;
        final ChannelPromise promise;

        private ToSend(final long delay, final Object toSend, final ChannelPromise promise) {
            this.date = System.currentTimeMillis() + delay;
            this.toSend = toSend;
            this.promise = promise;
        }
    }

    @Override
    protected synchronized void submitWrite(final ChannelHandlerContext ctx, final Object msg,
            final long size, final long writedelay,
            final ChannelPromise promise) {
        Integer key = ctx.channel().hashCode();
        PerChannel perChannel = channelQueues.get(key);
        if (writedelay == 0 && (perChannel == null || perChannel.messagesQueue == null || 
                perChannel.messagesQueue.isEmpty())) {
            trafficCounter.bytesRealWriteFlowControl(size);
            ctx.write(msg, promise);
            perChannel.lastWrite = System.currentTimeMillis();
            return;
        }
        long delay = writedelay;
        if (delay > maxTime && System.currentTimeMillis() + delay - perChannel.lastWrite > maxTime) {
            delay = maxTime;
        }
        final ToSend newToSend = new ToSend(delay, msg, promise);
        if (perChannel == null) {
            perChannel = new PerChannel();
            perChannel.messagesQueue = new LinkedList<ToSend>();;
            perChannel.queueSize = 0L;
            perChannel.lastRead = System.currentTimeMillis();
            perChannel.lastWrite = System.currentTimeMillis();
            channelQueues.put(key, perChannel);
        }
        perChannel.messagesQueue.add(newToSend);
        perChannel.queueSize += size;
        queuesSize += size;
        checkWriteSuspend(ctx, delay, perChannel.queueSize);
        if (queuesSize > maxGlobalWriteSize) {
            ctx.channel().attr(WRITE_SUSPENDED).set(true);
        }
        final PerChannel forSchedule = perChannel;
        ctx.executor().schedule(new Runnable() {
            @Override
            public void run() {
                sendAllValid(ctx, forSchedule);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private synchronized void sendAllValid(final ChannelHandlerContext ctx, final PerChannel perChannel) {
        while (!perChannel.messagesQueue.isEmpty()) {
            ToSend newToSend = perChannel.messagesQueue.remove(0);
            if (newToSend.date <= System.currentTimeMillis()) {
                long size = calculateSize(newToSend.toSend);
                trafficCounter.bytesRealWriteFlowControl(size);
                perChannel.queueSize -= size;
                queuesSize -= size;
                ctx.write(newToSend.toSend, newToSend.promise);
                perChannel.lastWrite = System.currentTimeMillis();
            } else {
                perChannel.messagesQueue.add(0, newToSend);
                break;
            }
        }
        if (perChannel.messagesQueue.isEmpty()) {
            releaseWriteSuspended(ctx);
        }
        ctx.flush();
    }
}
