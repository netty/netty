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
package org.jboss.netty.handler.traffic;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.MemoryAwareThreadPoolExecutor;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.jboss.netty.util.ObjectSizeEstimator;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;


/**
 * This implementation of the {@link AbstractTrafficShapingHandler} is for global
 * traffic shaping, that is to say a global limitation of the bandwidth, whatever
 * the number of opened channels.<br><br>
 *
 * The general use should be as follow:<br>
 * <ul>
 * <li>Create your unique GlobalTrafficShapingHandler like:<br><br>
 * <tt>GlobalTrafficShapingHandler myHandler = new GlobalTrafficShapingHandler(timer);</tt><br><br>
 * timer could be created using <tt>HashedWheelTimer</tt><br>
 * <tt>pipeline.addLast("GLOBAL_TRAFFIC_SHAPING", myHandler);</tt><br><br>
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
 * maxTimeToWait, by default set to 15s, allows to specify an upper bound of time shaping.<br><br>
 * </li>
 * <li>Add it in your pipeline, before a recommended {@link ExecutionHandler} (like
 * {@link OrderedMemoryAwareThreadPoolExecutor} or {@link MemoryAwareThreadPoolExecutor}).<br>
 * <tt>pipeline.addLast("GLOBAL_TRAFFIC_SHAPING", myHandler);</tt><br><br>
 * </li>
 * <li>When you shutdown your application, release all the external resources
 * by calling:<br>
 * <tt>myHandler.releaseExternalResources();</tt><br>
 * </li>
 * </ul><br>
 */
@Sharable
public class GlobalTrafficShapingHandler extends AbstractTrafficShapingHandler {
    private Map<Integer, PerChannel> channelQueues = new HashMap<Integer, PerChannel>();
    /**
     * Global queues size
     */
    private long queuesSize;
    /**
     * Max size in the list before proposing to stop writing new objects from next handlers
     * for all channel (global)
     */
    protected long maxGlobalWriteSize = DEFAULT_MAX_SIZE * 100; // default 400MB

    private static class PerChannel {
        List<ToSend> messagesQueue;
        long queueSize;
        long lastWrite;
        long lastRead;
        ReentrantLock channelLock;
    }
    /**
     * Create the global TrafficCounter
     */
    void createGlobalTrafficCounter() {
        TrafficCounter tc;
        if (timer != null) {
            tc = new TrafficCounter(this, timer, "GlobalTC",
                    checkInterval);
            setTrafficCounter(tc);
            tc.start();
        }
    }

    public GlobalTrafficShapingHandler(Timer timer, long writeLimit,
            long readLimit, long checkInterval) {
        super(timer, writeLimit, readLimit, checkInterval);
        createGlobalTrafficCounter();
    }

    public GlobalTrafficShapingHandler(Timer timer, long writeLimit,
            long readLimit, long checkInterval, long maxTime) {
        super(timer, writeLimit, readLimit, checkInterval, maxTime);
        createGlobalTrafficCounter();
    }

    public GlobalTrafficShapingHandler(Timer timer, long writeLimit,
            long readLimit) {
        super(timer, writeLimit, readLimit);
        createGlobalTrafficCounter();
    }

    public GlobalTrafficShapingHandler(Timer timer, long checkInterval) {
        super(timer, checkInterval);
        createGlobalTrafficCounter();
    }

    public GlobalTrafficShapingHandler(Timer timer) {
        super(timer);
        createGlobalTrafficCounter();
    }

    public GlobalTrafficShapingHandler(ObjectSizeEstimator objectSizeEstimator,
            Timer timer, long writeLimit, long readLimit,
            long checkInterval) {
        super(objectSizeEstimator, timer, writeLimit, readLimit,
                checkInterval);
        createGlobalTrafficCounter();
    }

    public GlobalTrafficShapingHandler(ObjectSizeEstimator objectSizeEstimator,
            Timer timer, long writeLimit, long readLimit,
            long checkInterval, long maxTime) {
        super(objectSizeEstimator, timer, writeLimit, readLimit,
                checkInterval, maxTime);
        createGlobalTrafficCounter();
    }

    public GlobalTrafficShapingHandler(ObjectSizeEstimator objectSizeEstimator,
            Timer timer, long writeLimit, long readLimit) {
        super(objectSizeEstimator, timer, writeLimit, readLimit);
        createGlobalTrafficCounter();
    }

    public GlobalTrafficShapingHandler(ObjectSizeEstimator objectSizeEstimator,
            Timer timer, long checkInterval) {
        super(objectSizeEstimator, timer, checkInterval);
        createGlobalTrafficCounter();
    }

    public GlobalTrafficShapingHandler(ObjectSizeEstimator objectSizeEstimator,
            Timer timer) {
        super(objectSizeEstimator, timer);
        createGlobalTrafficCounter();
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

    private synchronized PerChannel getOrSetPerChannel(Integer key) {
        PerChannel perChannel = channelQueues.get(key);
        if (perChannel == null) {
            perChannel = new PerChannel();
            perChannel.messagesQueue = new LinkedList<ToSend>();
            perChannel.queueSize = 0L;
            perChannel.lastRead = System.currentTimeMillis();
            perChannel.lastWrite = System.currentTimeMillis();
            perChannel.channelLock = new ReentrantLock(true);
            channelQueues.put(key, perChannel);
        }
        return perChannel;
    }
    private static final class ToSend {
        final long date;
        final MessageEvent toSend;

        private ToSend(final long delay, final MessageEvent toSend) {
            this.date = System.currentTimeMillis() + delay;
            this.toSend = toSend;
        }
    }

    @Override
    protected long checkWaitReadTime(final ChannelHandlerContext ctx, long wait) {
        Integer key = ctx.getChannel().hashCode();
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
        Integer key = ctx.getChannel().hashCode();
        PerChannel perChannel = channelQueues.get(key);
        if (perChannel != null) {
            perChannel.lastRead = System.currentTimeMillis();
        }
    }

    @Override
    protected void submitWrite(final ChannelHandlerContext ctx, final MessageEvent evt,
            final long size, final long writedelay)
            throws Exception {
        Integer key = ctx.getChannel().getId();
        PerChannel perChannel = channelQueues.get(key);
        if (perChannel == null) {
            // in case write occurs before handlerAdded is raized for this handler
            // imply a synchronized only if needed
            perChannel = getOrSetPerChannel(key);
        }
        perChannel.channelLock.lock();
        try {
            if (writedelay == 0 && (perChannel == null || perChannel.messagesQueue == null ||
                    perChannel.messagesQueue.isEmpty())) {
                if (trafficCounter != null) {
                    trafficCounter.bytesRealWriteFlowControl(size);
                }
                internalSubmitWrite(ctx, evt);
                perChannel.lastWrite = System.currentTimeMillis();
                return;
            }
            long delay = writedelay;
            if (delay > maxTime && System.currentTimeMillis() + delay - perChannel.lastWrite > maxTime) {
                delay = maxTime;
            }
            if (timer == null) {
                // Sleep since no executor
                Thread.sleep(delay);
                if (trafficCounter != null) {
                    trafficCounter.bytesRealWriteFlowControl(size);
                }
                internalSubmitWrite(ctx, evt);
                perChannel.lastWrite = System.currentTimeMillis();
                return;
            }
            final ToSend newToSend = new ToSend(delay, evt);
            perChannel.messagesQueue.add(newToSend);
            perChannel.queueSize += size;
            queuesSize += size;
            checkWriteSuspend(ctx, delay, perChannel.queueSize);
            if (queuesSize > maxGlobalWriteSize) {
                setWritable(ctx, false);
            }
            final PerChannel forSchedule = perChannel;
            timer.newTimeout(new TimerTask() {
                public void run(Timeout timeout) throws Exception {
                    sendAllValid(ctx, forSchedule);
                }
            }, delay, TimeUnit.MILLISECONDS);
        } finally {
            perChannel.channelLock.unlock();
        }
    }

    private void sendAllValid(ChannelHandlerContext ctx, final PerChannel perChannel)
            throws Exception {
        perChannel.channelLock.lock();
        try {
            while (!perChannel.messagesQueue.isEmpty()) {
                ToSend newToSend = perChannel.messagesQueue.remove(0);
                if (newToSend.date <= System.currentTimeMillis()) {
                    long size = calculateSize(newToSend.toSend.getMessage());
                    if (trafficCounter != null) {
                        trafficCounter.bytesRealWriteFlowControl(size);
                    }
                    perChannel.queueSize -= size;
                    queuesSize -= size;
                    internalSubmitWrite(ctx, newToSend.toSend);
                    perChannel.lastWrite = System.currentTimeMillis();
                } else {
                    perChannel.messagesQueue.add(0, newToSend);
                    break;
                }
            }
            if (perChannel.messagesQueue.isEmpty()) {
                releaseWriteSuspended(ctx);
            }
        } finally {
            perChannel.channelLock.unlock();
        }
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        Integer key = ctx.getChannel().getId();
        getOrSetPerChannel(key);
        super.channelConnected(ctx, e);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        Integer key = ctx.getChannel().hashCode();
        PerChannel perChannel = channelQueues.remove(key);
        if (perChannel != null) {
            perChannel.channelLock.lock();
            try {
                for (ToSend toSend : perChannel.messagesQueue) {
                    queuesSize -= calculateSize(toSend.toSend.getMessage());
                }
                perChannel.messagesQueue.clear();
            } finally {
                perChannel.channelLock.unlock();
            }
        }
        super.channelClosed(ctx, e);
    }
}
