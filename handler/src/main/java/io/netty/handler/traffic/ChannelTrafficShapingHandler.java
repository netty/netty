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

import java.util.LinkedList;
import java.util.List;
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
 * maxTimeToWait, by default set to 15s, allows to specify an upper bound of time shaping.<br>
 * </li>
 * </ul><br>
 */
public class ChannelTrafficShapingHandler extends AbstractTrafficShapingHandler {
    private List<ToSend> messagesQueue = new LinkedList<ToSend>();

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
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        TrafficCounter trafficCounter = new TrafficCounter(this, ctx.executor(), "ChannelTC" +
                ctx.channel().hashCode(), checkInterval);
        setTrafficCounter(trafficCounter);
        trafficCounter.start();
    }

    @Override
    public synchronized void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        if (trafficCounter != null) {
            trafficCounter.stop();
        }
        for (ToSend toSend : messagesQueue) {
            if (toSend.toSend instanceof ByteBuf) {
                ((ByteBuf) toSend.toSend).release();
            }
        }
        messagesQueue.clear();
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
    protected synchronized void submitWrite(final ChannelHandlerContext ctx, final Object msg, final long delay,
            final ChannelPromise promise) {
        if (delay == 0 && messagesQueue.isEmpty()) {
            ctx.write(msg, promise);
            return;
        }
        final ToSend newToSend = new ToSend(delay, msg, promise);
        messagesQueue.add(newToSend);
        ctx.executor().schedule(new Runnable() {
            @Override
            public void run() {
                sendAllValid(ctx);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private synchronized void sendAllValid(ChannelHandlerContext ctx) {
        while (!messagesQueue.isEmpty()) {
            ToSend newToSend = messagesQueue.remove(0);
            if (newToSend.date <= System.currentTimeMillis()) {
                ctx.write(newToSend.toSend, newToSend.promise);
            } else {
                messagesQueue.add(0, newToSend);
                break;
            }
        }
        ctx.flush();
    }
}
