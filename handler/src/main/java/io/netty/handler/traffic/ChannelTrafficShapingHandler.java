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

import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

/**
 * This implementation of the {@link AbstractTrafficShapingHandler} is for channel
 * traffic shaping, that is to say a per channel limitation of the bandwidth.<br><br>
 *
 * The general use should be as follow:<br>
 * <ul>
 * <li>Add in your pipeline a new ChannelTrafficShapingHandler.<br>
 * <tt>ChannelTrafficShapingHandler myHandler = new ChannelTrafficShapingHandler();</tt><br>
 * <tt>pipeline.addLast(myHandler);</tt><br><br>
 *
 * <b>Note that this handler has a Pipeline Coverage of "one" which means a new handler must be created
 * for each new channel as the counter cannot be shared among all channels.</b>.<br><br>
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
 * <li>In your handler, you should consider to use the <code>channel.isWritable()</code> and
 * <code>channelWritabilityChanged(ctx)</code> to handle writability, or through
 * <code>future.addListener(new GenericFutureListener())</code> on the future returned by
 * <code>ctx.write()</code>.</li>
 * <li>You shall also consider to have object size in read or write operations relatively adapted to
 * the bandwidth you required: for instance having 10 MB objects for 10KB/s will lead to burst effect,
 * while having 100 KB objects for 1 MB/s should be smoothly handle by this TrafficShaping handler.<br><br></li>
 * <li>Some configuration methods will be taken as best effort, meaning
 * that all already scheduled traffics will not be
 * changed, but only applied to new traffics.<br>
 * So the expected usage of those methods are to be used not too often,
 * accordingly to the traffic shaping configuration.</li>
 * </ul><br>
 */
public class ChannelTrafficShapingHandler extends AbstractTrafficShapingHandler {
    private ArrayDeque<ToSend> messagesQueue = new ArrayDeque<ToSend>();
    private long queueSize;

    /**
     * Create a new instance
     *
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
    public ChannelTrafficShapingHandler(long writeLimit, long readLimit,
            long checkInterval, long maxTime) {
        super(writeLimit, readLimit, checkInterval, maxTime);
        userDefinedWritabilityIndex = AbstractTrafficShapingHandler.CHANNEL_DEFAULT_USER_DEFINED_WRITABILITY_INDEX;
    }

    /**
     * Create a new instance
     *
     * @param writeLimit
     *          0 or a limit in bytes/s
     * @param readLimit
     *          0 or a limit in bytes/s
     * @param checkInterval
     *          The delay between two computations of performances for
     *            channels or 0 if no stats are to be computed
     */
    public ChannelTrafficShapingHandler(long writeLimit,
            long readLimit, long checkInterval) {
        super(writeLimit, readLimit, checkInterval);
        userDefinedWritabilityIndex = AbstractTrafficShapingHandler.CHANNEL_DEFAULT_USER_DEFINED_WRITABILITY_INDEX;
    }

    /**
     * Create a new instance
     *
     * @param writeLimit
     *          0 or a limit in bytes/s
     * @param readLimit
     *          0 or a limit in bytes/s
     */
    public ChannelTrafficShapingHandler(long writeLimit,
            long readLimit) {
        super(writeLimit, readLimit);
        userDefinedWritabilityIndex = AbstractTrafficShapingHandler.CHANNEL_DEFAULT_USER_DEFINED_WRITABILITY_INDEX;
    }

    /**
     * Create a new instance
     *
     * @param checkInterval
     *          The delay between two computations of performances for
     *            channels or 0 if no stats are to be computed
     */
    public ChannelTrafficShapingHandler(long checkInterval) {
        super(checkInterval);
        userDefinedWritabilityIndex = AbstractTrafficShapingHandler.CHANNEL_DEFAULT_USER_DEFINED_WRITABILITY_INDEX;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        TrafficCounter trafficCounter = new TrafficCounter(this, ctx.executor(), "ChannelTC" +
                ctx.channel().hashCode(), checkInterval);
        setTrafficCounter(trafficCounter);
        trafficCounter.start();
        super.handlerAdded(ctx);
    }

    @Override
    public synchronized void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        trafficCounter.stop();
        if (ctx.channel().isActive()) {
            for (ToSend toSend : messagesQueue) {
                long size = calculateSize(toSend.toSend);
                trafficCounter.bytesRealWriteFlowControl(size);
                queueSize -= size;
                ctx.write(toSend.toSend, toSend.promise);
            }
        } else {
            for (ToSend toSend : messagesQueue) {
                if (toSend.toSend instanceof ByteBuf) {
                    ((ByteBuf) toSend.toSend).release();
                }
            }
        }
        messagesQueue.clear();
        releaseWriteSuspended(ctx);
        releaseReadSuspended(ctx);
        super.handlerRemoved(ctx);
    }

    private static final class ToSend {
        final long date;
        final Object toSend;
        final ChannelPromise promise;

        private ToSend(final long delay, final Object toSend, final ChannelPromise promise) {
            this.date = delay;
            this.toSend = toSend;
            this.promise = promise;
        }
    }

    @Override
    protected synchronized void submitWrite(final ChannelHandlerContext ctx, final Object msg,
            final long size, final long delay, final long now,
            final ChannelPromise promise) {
        if (delay == 0 && messagesQueue.isEmpty()) {
            trafficCounter.bytesRealWriteFlowControl(size);
            ctx.write(msg, promise);
            return;
        }
        final ToSend newToSend = new ToSend(delay + now, msg, promise);
        final long futureNow = newToSend.date;
        messagesQueue.addLast(newToSend);
        queueSize += size;
        checkWriteSuspend(ctx, delay, queueSize);
        ctx.executor().schedule(new Runnable() {
            @Override
            public void run() {
                sendAllValid(ctx, futureNow);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private synchronized void sendAllValid(final ChannelHandlerContext ctx, final long now) {
        ToSend newToSend = messagesQueue.pollFirst();
        for (; newToSend != null; newToSend = messagesQueue.pollFirst()) {
            if (newToSend.date <= now) {
                long size = calculateSize(newToSend.toSend);
                trafficCounter.bytesRealWriteFlowControl(size);
                queueSize -= size;
                ctx.write(newToSend.toSend, newToSend.promise);
            } else {
                messagesQueue.addFirst(newToSend);
                break;
            }
        }
        if (messagesQueue.isEmpty()) {
            releaseWriteSuspended(ctx);
        }
        ctx.flush();
    }

    /**
    *
    * @return current size in bytes of the write buffer
    */
   public long queueSize() {
       return queueSize;
   }
}
