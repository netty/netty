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
    private Map<Integer, List<ToSend>> messagesQueues = new HashMap<Integer, List<ToSend>>();

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
        List<ToSend> mq = new LinkedList<ToSend>();
        messagesQueues.put(key, mq);
    }

    @Override
    public synchronized void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        Integer key = ctx.channel().hashCode();
        List<ToSend> mq = messagesQueues.remove(key);
        if (mq != null) {
            for (ToSend toSend : mq) {
                if (toSend.toSend instanceof ByteBuf) {
                    ((ByteBuf) toSend.toSend).release();
                }
            }
            mq.clear();
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
    protected synchronized void submitWrite(final ChannelHandlerContext ctx, final Object msg, final long delay,
            final ChannelPromise promise) {
        Integer key = ctx.channel().hashCode();
        List<ToSend> messagesQueue = messagesQueues.get(key);
        if (delay == 0 && (messagesQueue == null || messagesQueue.isEmpty())) {
            ctx.write(msg, promise);
            return;
        }
        final ToSend newToSend = new ToSend(delay, msg, promise);
        if (messagesQueue == null) {
            messagesQueue = new LinkedList<ToSend>();
            messagesQueues.put(key, messagesQueue);
        }
        messagesQueue.add(newToSend);
        final List<ToSend> mqfinal = messagesQueue;
        ctx.executor().schedule(new Runnable() {
            @Override
            public void run() {
                sendAllValid(ctx, mqfinal);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private synchronized void sendAllValid(final ChannelHandlerContext ctx, final List<ToSend> messagesQueue) {
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
