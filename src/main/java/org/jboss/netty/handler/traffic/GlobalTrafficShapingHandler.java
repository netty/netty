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

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.netty.channel.Channel;
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
import org.jboss.netty.util.internal.ConcurrentHashMap;


/**
 * <p>This implementation of the {@link AbstractTrafficShapingHandler} is for global
 * traffic shaping, that is to say a global limitation of the bandwidth, whatever
 * the number of opened channels.</p>
 *
 * The general use should be as follow:
 * <ul>
 * <li><p>Create your unique GlobalTrafficShapingHandler like:</p>
 * <p><tt>GlobalTrafficShapingHandler myHandler = new GlobalTrafficShapingHandler(timer);</tt></p>
 * <p>timer could be created using <tt>HashedWheelTimer</tt></p>
 * <p><tt>pipeline.addLast("GLOBAL_TRAFFIC_SHAPING", myHandler);</tt></p>
 *
 * <p><b>Note that this handler has a Pipeline Coverage of "all" which means only one such handler must be created
 * and shared among all channels as the counter must be shared among all channels.</b></p>
 *
 * <p>Other arguments can be passed like write or read limitation (in bytes/s where 0 means no limitation)
 * or the check interval (in millisecond) that represents the delay between two computations of the
 * bandwidth and so the call back of the doAccounting method (0 means no accounting at all).</p>
 *
 * <p>A value of 0 means no accounting for checkInterval. If you need traffic shaping but no such accounting,
 * it is recommended to set a positive value, even if it is high since the precision of the
 * Traffic Shaping depends on the period where the traffic is computed. The highest the interval,
 * the less precise the traffic shaping will be. It is suggested as higher value something close
 * to 5 or 10 minutes.</p>
 *
 * <p>maxTimeToWait, by default set to 15s, allows to specify an upper bound of time shaping.</p>
 * </li>
 * <li><p>Add it in your pipeline, before a recommended {@link ExecutionHandler} (like
 * {@link OrderedMemoryAwareThreadPoolExecutor} or {@link MemoryAwareThreadPoolExecutor}).</p>
 * <p><tt>pipeline.addLast("GLOBAL_TRAFFIC_SHAPING", myHandler);</tt></p>
 * </li>
 * <li><p>When you shutdown your application, release all the external resources
 * by calling:</p>
 * <tt>myHandler.releaseExternalResources();</tt>
 * </li>
 * </ul>
 */
@Sharable
public class GlobalTrafficShapingHandler extends AbstractTrafficShapingHandler {
    private final ConcurrentMap<Integer, PerChannel> channelQueues = new ConcurrentHashMap<Integer, PerChannel>();

    /**
     * Global queues size
     */
    private AtomicLong queuesSize = new AtomicLong();

    /**
     * Max size in the list before proposing to stop writing new objects from next handlers
     * for all channel (global)
     */
    long maxGlobalWriteSize = DEFAULT_MAX_SIZE * 100; // default 400MB

    private static class PerChannel {
        List<ToSend> messagesQueue;
        ChannelHandlerContext ctx;
        long queueSize;
        long lastWriteTimestamp;
        long lastReadTimestamp;
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
     * @return the maxGlobalWriteSize default value being 400 MB
     */
    public long getMaxGlobalWriteSize() {
        return maxGlobalWriteSize;
    }

    /**
     * @param maxGlobalWriteSize the maximum Global Write Size allowed in the buffer
     *            globally for all channels before write suspended is set,
     *            default value being 400 MB
     */
    public void setMaxGlobalWriteSize(long maxGlobalWriteSize) {
        this.maxGlobalWriteSize = maxGlobalWriteSize;
    }

    /**
     * @return the global size of the buffers for all queues
     */
    public long queuesSize() {
        return queuesSize.get();
    }

    private synchronized PerChannel getOrSetPerChannel(ChannelHandlerContext ctx) {
        Integer key = ctx.getChannel().hashCode();
        PerChannel perChannel = channelQueues.get(key);
        if (perChannel == null) {
            perChannel = new PerChannel();
            perChannel.messagesQueue = new LinkedList<ToSend>();
            perChannel.ctx = ctx;
            perChannel.queueSize = 0L;
            perChannel.lastReadTimestamp = TrafficCounter.milliSecondFromNano();
            perChannel.lastWriteTimestamp = perChannel.lastReadTimestamp;
            channelQueues.put(key, perChannel);
        }
        return perChannel;
    }

    private static final class ToSend {
        final long relativeTimeAction;
        final MessageEvent toSend;
        final long size;

        private ToSend(final long delay, final MessageEvent toSend, final long size) {
            this.relativeTimeAction = delay;
            this.toSend = toSend;
            this.size = size;
        }
    }

    @Override
    long checkWaitReadTime(final ChannelHandlerContext ctx, long wait, final long now) {
        Integer key = ctx.getChannel().hashCode();
        PerChannel perChannel = channelQueues.get(key);
        if (perChannel != null) {
            if (wait > maxTime && now + wait - perChannel.lastReadTimestamp > maxTime) {
                wait = maxTime;
            }
        }
        return wait;
    }
    @Override
    void informReadOperation(final ChannelHandlerContext ctx, final long now) {
        Integer key = ctx.getChannel().hashCode();
        PerChannel perChannel = channelQueues.get(key);
        if (perChannel != null) {
            perChannel.lastReadTimestamp = now;
        }
    }

    @Override
    void submitWrite(final ChannelHandlerContext ctx, final MessageEvent evt,
            final long size, final long writedelay, final long now)
            throws Exception {
        PerChannel perChannel = getOrSetPerChannel(ctx);
        long delay;
        final ToSend newToSend;
        boolean globalSizeExceeded = false;
        Channel channel = ctx.getChannel();
        synchronized (perChannel) {
            if (writedelay == 0 && perChannel.messagesQueue.isEmpty()) {
                if (! channel.isConnected()) {
                    // ignore
                    return;
                }
                if (trafficCounter != null) {
                    trafficCounter.bytesRealWriteFlowControl(size);
                }
                ctx.sendDownstream(evt);
                perChannel.lastWriteTimestamp = now;
                return;
            }
            delay = writedelay;
            if (delay > maxTime && now + delay - perChannel.lastWriteTimestamp > maxTime) {
                delay = maxTime;
            }
            if (timer == null) {
                // Sleep since no executor
                Thread.sleep(delay);
                if (! ctx.getChannel().isConnected()) {
                    // ignore
                    return;
                }
                if (trafficCounter != null) {
                    trafficCounter.bytesRealWriteFlowControl(size);
                }
                ctx.sendDownstream(evt);
                perChannel.lastWriteTimestamp = now;
                return;
            }
            if (! ctx.getChannel().isConnected()) {
                // ignore
                return;
            }
            newToSend = new ToSend(delay + now, evt, size);
            perChannel.messagesQueue.add(newToSend);
            perChannel.queueSize += size;
            queuesSize.addAndGet(size);
            checkWriteSuspend(ctx, delay, perChannel.queueSize);
            if (queuesSize.get() > maxGlobalWriteSize) {
                globalSizeExceeded = true;
            }
        }
        if (globalSizeExceeded) {
            setWritable(ctx, false);
        }
        final long futureNow = newToSend.relativeTimeAction;
        final PerChannel forSchedule = perChannel;
        timer.newTimeout(new TimerTask() {
            public void run(Timeout timeout) throws Exception {
                sendAllValid(ctx, forSchedule, futureNow);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void sendAllValid(ChannelHandlerContext ctx, final PerChannel perChannel, final long now)
            throws Exception {
        Channel channel = ctx.getChannel();
        if (! channel.isConnected()) {
            // ignore
            return;
        }
        synchronized (perChannel) {
            while (!perChannel.messagesQueue.isEmpty()) {
                ToSend newToSend = perChannel.messagesQueue.remove(0);
                if (newToSend.relativeTimeAction <= now) {
                    if (! channel.isConnected()) {
                        // ignore
                        break;
                    }
                    long size = newToSend.size;
                    if (trafficCounter != null) {
                        trafficCounter.bytesRealWriteFlowControl(size);
                    }
                    perChannel.queueSize -= size;
                    queuesSize.addAndGet(-size);
                    ctx.sendDownstream(newToSend.toSend);
                    perChannel.lastWriteTimestamp = now;
                } else {
                    perChannel.messagesQueue.add(0, newToSend);
                    break;
                }
            }
            if (perChannel.messagesQueue.isEmpty()) {
                releaseWriteSuspended(ctx);
            }
        }
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        getOrSetPerChannel(ctx);
        super.channelConnected(ctx, e);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        Integer key = ctx.getChannel().hashCode();
        PerChannel perChannel = channelQueues.remove(key);
        if (perChannel != null) {
            synchronized (perChannel) {
                queuesSize.addAndGet(-perChannel.queueSize);
                perChannel.messagesQueue.clear();
            }
        }
        super.channelClosed(ctx, e);
    }

    @Override
    public void releaseExternalResources() {
        for (PerChannel perChannel : channelQueues.values()) {
            if (perChannel != null && perChannel.ctx != null && perChannel.ctx.getChannel().isConnected()) {
                Channel channel = perChannel.ctx.getChannel();
                synchronized (perChannel) {
                    for (ToSend toSend : perChannel.messagesQueue) {
                        if (! channel.isConnected()) {
                            // ignore
                            break;
                        }
                        perChannel.ctx.sendDownstream(toSend.toSend);
                    }
                    perChannel.messagesQueue.clear();
                }
            }
        }
        channelQueues.clear();
        queuesSize.set(0);
        super.releaseExternalResources();
    }
}
