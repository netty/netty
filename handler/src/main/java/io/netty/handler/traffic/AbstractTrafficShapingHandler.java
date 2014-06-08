/*
 * Copyright 2011 The Netty Project
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
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import java.util.concurrent.TimeUnit;

/**
 * AbstractTrafficShapingHandler allows to limit the global bandwidth
 * (see {@link GlobalTrafficShapingHandler}) or per session
 * bandwidth (see {@link ChannelTrafficShapingHandler}), as traffic shaping.
 * It allows you to implement an almost real time monitoring of the bandwidth using
 * the monitors from {@link TrafficCounter} that will call back every checkInterval
 * the method doAccounting of this handler.<br>
 * <br>
 *
 * If you want for any particular reasons to stop the monitoring (accounting) or to change
 * the read/write limit or the check interval, several methods allow that for you:<br>
 * <ul>
 * <li><tt>configure</tt> allows you to change read or write limits, or the checkInterval</li>
 * <li><tt>getTrafficCounter</tt> allows you to have access to the TrafficCounter and so to stop
 * or start the monitoring, to change the checkInterval directly, or to have access to its values.</li>
 * </ul>
 */
public abstract class AbstractTrafficShapingHandler extends ChannelDuplexHandler {
    /**
     * Default delay between two checks: 1s
     */
    public static final long DEFAULT_CHECK_INTERVAL = 1000;

    /**
     * Default minimal time to wait
     */
    private static final long MINIMAL_WAIT = 10;

    /**
     * Traffic Counter
     */
    protected TrafficCounter trafficCounter;

    /**
     * Limit in B/s to apply to write
     */
    private long writeLimit;

    /**
     * Limit in B/s to apply to read
     */
    private long readLimit;

    /**
     * Delay between two performance snapshots
     */
    protected long checkInterval = DEFAULT_CHECK_INTERVAL; // default 1 s

    private static final AttributeKey<Boolean> READ_SUSPENDED = AttributeKey.valueOf(
            AbstractTrafficShapingHandler.class.getName() + ".READ_SUSPENDED");
    private static final AttributeKey<Runnable> REOPEN_TASK = AttributeKey.valueOf(
            AbstractTrafficShapingHandler.class.getName() + ".REOPEN_TASK");

    /**
     *
     * @param newTrafficCounter the TrafficCounter to set
     */
    void setTrafficCounter(TrafficCounter newTrafficCounter) {
        trafficCounter = newTrafficCounter;
    }

    /**
     * @param writeLimit
     *          0 or a limit in bytes/s
     * @param readLimit
     *          0 or a limit in bytes/s
     * @param checkInterval
     *          The delay between two computations of performances for
     *            channels or 0 if no stats are to be computed
     */
    protected AbstractTrafficShapingHandler(long writeLimit, long readLimit,
                                            long checkInterval) {
        this.writeLimit = writeLimit;
        this.readLimit = readLimit;
        this.checkInterval = checkInterval;
    }

    /**
     * Constructor using default Check Interval
     *
     * @param writeLimit
     *          0 or a limit in bytes/s
     * @param readLimit
     *          0 or a limit in bytes/s
     */
    protected AbstractTrafficShapingHandler(long writeLimit, long readLimit) {
        this(writeLimit, readLimit, DEFAULT_CHECK_INTERVAL);
    }

    /**
     * Constructor using NO LIMIT and default Check Interval
     */
    protected AbstractTrafficShapingHandler() {
        this(0, 0, DEFAULT_CHECK_INTERVAL);
    }

    /**
     * Constructor using NO LIMIT
     *
     * @param checkInterval
     *          The delay between two computations of performances for
     *            channels or 0 if no stats are to be computed
     */
    protected AbstractTrafficShapingHandler(long checkInterval) {
        this(0, 0, checkInterval);
    }

    /**
     * Change the underlying limitations and check interval.
     *
     * @param newWriteLimit The new write limit (in bytes)
     * @param newReadLimit The new read limit (in bytes)
     * @param newCheckInterval The new check interval (in milliseconds)
     */
    public void configure(long newWriteLimit, long newReadLimit,
            long newCheckInterval) {
        configure(newWriteLimit, newReadLimit);
        configure(newCheckInterval);
    }

    /**
     * Change the underlying limitations.
     *
     * @param newWriteLimit The new write limit (in bytes)
     * @param newReadLimit The new read limit (in bytes)
     */
    public void configure(long newWriteLimit, long newReadLimit) {
        writeLimit = newWriteLimit;
        readLimit = newReadLimit;
        if (trafficCounter != null) {
            trafficCounter.resetAccounting(System.currentTimeMillis() + 1);
        }
    }

    /**
     * Change the check interval.
     *
     * @param newCheckInterval The new check interval (in milliseconds)
     */
    public void configure(long newCheckInterval) {
        checkInterval = newCheckInterval;
        if (trafficCounter != null) {
            trafficCounter.configure(checkInterval);
        }
    }

    /**
     * Called each time the accounting is computed from the TrafficCounters.
     * This method could be used for instance to implement almost real time accounting.
     *
     * @param counter
     *            the TrafficCounter that computes its performance
     */
    @SuppressWarnings("unused")
    protected void doAccounting(TrafficCounter counter) {
        // NOOP by default
    }

    /**
     * Class to implement setReadable at fix time
     */
    private static final class ReopenReadTimerTask implements Runnable {
        final ChannelHandlerContext ctx;
        ReopenReadTimerTask(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void run() {
            ctx.attr(READ_SUSPENDED).set(false);
            ctx.read();
        }
    }

    /**
     * @return the time that should be necessary to wait to respect limit. Can be negative time
     */
    private static long getTimeToWait(long limit, long bytes, long lastTime, long curtime) {
        long interval = curtime - lastTime;
        if (interval <= 0) {
            // Time is too short, so just lets continue
            return 0;
        }
        return (bytes * 1000 / limit - interval) / 10 * 10;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        long size = calculateSize(msg);
        long curtime = System.currentTimeMillis();

        if (trafficCounter != null) {
            trafficCounter.bytesRecvFlowControl(size);
            if (readLimit == 0) {
                // no action
                ctx.fireChannelRead(msg);

                return;
            }

            // compute the number of ms to wait before reopening the channel
            long wait = getTimeToWait(readLimit,
                    trafficCounter.currentReadBytes(),
                    trafficCounter.lastTime(), curtime);
            if (wait >= MINIMAL_WAIT) { // At least 10ms seems a minimal
                // time in order to
                // try to limit the traffic
                if (!isSuspended(ctx)) {
                    ctx.attr(READ_SUSPENDED).set(true);

                    // Create a Runnable to reactive the read if needed. If one was create before it will just be
                    // reused to limit object creation
                    Attribute<Runnable> attr  = ctx.attr(REOPEN_TASK);
                    Runnable reopenTask = attr.get();
                    if (reopenTask == null) {
                        reopenTask = new ReopenReadTimerTask(ctx);
                        attr.set(reopenTask);
                    }
                    ctx.executor().schedule(reopenTask, wait,
                            TimeUnit.MILLISECONDS);
                }
            }
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void read(ChannelHandlerContext ctx) {
        if (!isSuspended(ctx)) {
            ctx.read();
        }
    }

    private static boolean isSuspended(ChannelHandlerContext ctx) {
        Boolean suspended = ctx.attr(READ_SUSPENDED).get();
        if (suspended == null || Boolean.FALSE.equals(suspended)) {
            return false;
        }
        return true;
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
            throws Exception {
        long curtime = System.currentTimeMillis();
        long size = calculateSize(msg);

        if (size > -1 && trafficCounter != null) {
            trafficCounter.bytesWriteFlowControl(size);
            if (writeLimit == 0) {
                ctx.write(msg, promise);
                return;
            }
            // compute the number of ms to wait before continue with the
            // channel
            long wait = getTimeToWait(writeLimit,
                    trafficCounter.currentWrittenBytes(),
                    trafficCounter.lastTime(), curtime);
            if (wait >= MINIMAL_WAIT) {
                ctx.executor().schedule(new Runnable() {
                    @Override
                    public void run() {
                        ctx.write(msg, promise);
                    }
                }, wait, TimeUnit.MILLISECONDS);
                return;
            }
        }
        ctx.write(msg, promise);
    }

    /**
     *
     * @return the current TrafficCounter (if
     *         channel is still connected)
     */
    public TrafficCounter trafficCounter() {
        return trafficCounter;
    }

    @Override
    public String toString() {
        return "TrafficShaping with Write Limit: " + writeLimit +
                " Read Limit: " + readLimit + " and Counter: " +
                (trafficCounter != null? trafficCounter.toString() : "none");
    }

    /**
     * Calculate the size of the given {@link Object}.
     *
     * This implementation supports {@link ByteBuf} and {@link ByteBufHolder}. Sub-classes may override this.
     * @param msg       the msg for which the size should be calculated
     * @return size     the size of the msg or {@code -1} if unknown.
     */
    protected long calculateSize(Object msg) {
        if (msg instanceof ByteBuf) {
            return ((ByteBuf) msg).readableBytes();
        }
        if (msg instanceof ByteBufHolder) {
            return ((ByteBufHolder) msg).content().readableBytes();
        }
        return -1;
    }
}
