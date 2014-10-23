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
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

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
public abstract class AbstractTrafficShapingHandler extends ChannelHandlerAdapter {
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(AbstractTrafficShapingHandler.class);
    /**
     * Default delay between two checks: 1s
     */
    public static final long DEFAULT_CHECK_INTERVAL = 1000;

   /**
    * Default max delay in case of traffic shaping
    * (during which no communication will occur).
    * Shall be less than TIMEOUT. Here half of "standard" 30s
    */
    public static final long DEFAULT_MAX_TIME = 15000;

    /**
     * Default max size to not exceed in buffer (write only).
     */
    public static final long DEFAULT_MAX_SIZE = 4 * 1024 * 1024L;

    /**
     * Default minimal time to wait
     */
    static final long MINIMAL_WAIT = 10;

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
    * Max delay in wait
    */
    protected long maxTime = DEFAULT_MAX_TIME; // default 15 s

    /**
     * Delay between two performance snapshots
     */
    protected long checkInterval = DEFAULT_CHECK_INTERVAL; // default 1 s

    /**
     * Max time to delay before proposing to stop writing new objects from next handlers
     */
    protected long maxWriteDelay = 4 * DEFAULT_CHECK_INTERVAL; // default 4 s
    /**
     * Max size in the list before proposing to stop writing new objects from next handlers
     */
    protected long maxWriteSize = DEFAULT_MAX_SIZE; // default 4MB

    /**
     * Rank in UserDefinedWritability (1 for Channel, 2 for Global TrafficShapingHandler).
     * Set in final constructor. Must be between 1 and 31
     */
    protected int userDefinedWritabilityIndex;
    public static int CHANNEL_DEFAULT_USER_DEFINED_WRITABILITY_INDEX = 1;
    public static int GLOBAL_DEFAULT_USER_DEFINED_WRITABILITY_INDEX = 2;

    private static final AttributeKey<Boolean> READ_SUSPENDED =
            AttributeKey.valueOf(AbstractTrafficShapingHandler.class, "READ_SUSPENDED");
    private static final AttributeKey<Runnable> REOPEN_TASK =
            AttributeKey.valueOf(AbstractTrafficShapingHandler.class, "REOPEN_TASK");

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
     * @param maxTime
     *          The maximum delay to wait in case of traffic excess
     */
    protected AbstractTrafficShapingHandler(long writeLimit, long readLimit,
                                            long checkInterval, long maxTime) {
        this.writeLimit = writeLimit;
        this.readLimit = readLimit;
        this.checkInterval = checkInterval;
        this.maxTime = maxTime;
    }

   /**
    * @param writeLimit
    *           0 or a limit in bytes/s
    * @param readLimit
    *           0 or a limit in bytes/s
    * @param checkInterval
    *           The delay between two computations of performances for
    *           channels or 0 if no stats are to be computed
    */
    protected AbstractTrafficShapingHandler(long writeLimit, long readLimit,
            long checkInterval) {
        this(writeLimit, readLimit, checkInterval, DEFAULT_MAX_TIME);
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
        this(writeLimit, readLimit, DEFAULT_CHECK_INTERVAL, DEFAULT_MAX_TIME);
    }

    /**
     * Constructor using NO LIMIT and default Check Interval
     */
    protected AbstractTrafficShapingHandler() {
        this(0, 0, DEFAULT_CHECK_INTERVAL, DEFAULT_MAX_TIME);
    }

    /**
     * Constructor using NO LIMIT
     *
     * @param checkInterval
     *          The delay between two computations of performances for
     *            channels or 0 if no stats are to be computed
     */
    protected AbstractTrafficShapingHandler(long checkInterval) {
        this(0, 0, checkInterval, DEFAULT_MAX_TIME);
    }

    /**
     * Change the underlying limitations and check interval.
     *<br>
     * Note the change will be taken as best effort, meaning
     * that all already scheduled traffics will not be
     * changed, but only applied to new traffics.<br>
     * So the expected usage of this method is to be used not too often,
     * accordingly to the traffic shaping configuration.
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
     *<br>
     * Note the change will be taken as best effort, meaning
     * that all already scheduled traffics will not be
     * changed, but only applied to new traffics.<br>
     * So the expected usage of this method is to be used not too often,
     * accordingly to the traffic shaping configuration.
     *
     * @param newWriteLimit The new write limit (in bytes)
     * @param newReadLimit The new read limit (in bytes)
     */
    public void configure(long newWriteLimit, long newReadLimit) {
        writeLimit = newWriteLimit;
        readLimit = newReadLimit;
        if (trafficCounter != null) {
            trafficCounter.resetAccounting(TrafficCounter.milliSecondFromNano() + 1);
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
     * @return the writeLimit
     */
    public long getWriteLimit() {
        return writeLimit;
    }

    /**
     * Note the change will be taken as best effort, meaning
     * that all already scheduled traffics will not be
     * changed, but only applied to new traffics.<br>
     * So the expected usage of this method is to be used not too often,
     * accordingly to the traffic shaping configuration.
     *
     * @param writeLimit the writeLimit to set
     */
    public void setWriteLimit(long writeLimit) {
        this.writeLimit = writeLimit;
        if (trafficCounter != null) {
            trafficCounter.resetAccounting(TrafficCounter.milliSecondFromNano() + 1);
        }
    }

    /**
     * @return the readLimit
     */
    public long getReadLimit() {
        return readLimit;
    }

    /**
     * Note the change will be taken as best effort, meaning
     * that all already scheduled traffics will not be
     * changed, but only applied to new traffics.<br>
     * So the expected usage of this method is to be used not too often,
     * accordingly to the traffic shaping configuration.
     *
     * @param readLimit the readLimit to set
     */
    public void setReadLimit(long readLimit) {
        this.readLimit = readLimit;
        if (trafficCounter != null) {
            trafficCounter.resetAccounting(TrafficCounter.milliSecondFromNano() + 1);
        }
    }

    /**
     * @return the checkInterval
     */
    public long getCheckInterval() {
        return checkInterval;
    }

    /**
     * @param checkInterval the interval in ms between each step check to set
     */
    public void setCheckInterval(long checkInterval) {
        this.checkInterval = checkInterval;
        if (trafficCounter != null) {
            trafficCounter.configure(checkInterval);
        }
    }

    /**
     * Note the change will be taken as best effort, meaning
     * that all already scheduled traffics will not be
     * changed, but only applied to new traffics.<br>
     * So the expected usage of this method is to be used not too often,
     * accordingly to the traffic shaping configuration.
     *
     * @param maxTime
     *            Max delay in wait, shall be less than TIME OUT in related protocol
     */
    public void setMaxTimeWait(long maxTime) {
        this.maxTime = maxTime;
    }

    /**
     * @return the max delay in wait to prevent TIME OUT
     */
    public long getMaxTimeWait() {
        return maxTime;
    }

    /**
     * @return the maxWriteDelay
     */
    public long getMaxWriteDelay() {
        return maxWriteDelay;
    }

    /**
     * Note the change will be taken as best effort, meaning
     * that all already scheduled traffics will not be
     * changed, but only applied to new traffics.<br>
     * So the expected usage of this method is to be used not too often,
     * accordingly to the traffic shaping configuration.
     *
     * @param maxWriteDelay the maximum Write Delay in ms in the buffer allowed before write suspension is set
     */
    public void setMaxWriteDelay(long maxWriteDelay) {
        this.maxWriteDelay = maxWriteDelay;
    }

    /**
     * @return the maxWriteSize
     */
    public long getMaxWriteSize() {
        return maxWriteSize;
    }

    /**
     * Note that this limit is a best effort on memory limitation to prevent Out Of
     * Memory Exception. To ensure it works, the handler generating the write should
     * use one of the way provided by Netty to handle the capacity:</br>
     * - the <code>Channel.isWritable()</code> property and the corresponding <code>channelWritabilityChanged()</code></br>
     * - the <code>ChannelFuture.addListener(new GenericFutureListener())</code>
     *
     * @param maxWriteSize the maximum Write Size allowed in the buffer
     *            per channel before write suspended is set
     */
    public void setMaxWriteSize(long maxWriteSize) {
        this.maxWriteSize = maxWriteSize;
    }

    /**
     * Called each time the accounting is computed from the TrafficCounters.
     * This method could be used for instance to implement almost real time accounting.
     *
     * @param counter
     *            the TrafficCounter that computes its performance
     */
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

        public void run() {
            if (!ctx.channel().config().isAutoRead() && isHandlerReadActive(ctx)) {
                // If AutoRead is False and Active is True, user make a direct setAutoRead(false)
                // Then Just reset the status
                if (logger.isDebugEnabled()) {
                    logger.debug("Not Unsuspend: " + ctx.channel().config().isAutoRead() + ":" +
                            isHandlerReadActive(ctx));
                }
                ctx.attr(READ_SUSPENDED).set(false);
            } else {
                // Anything else allows the handler to reset the AutoRead
                if (logger.isDebugEnabled()) {
                    if (ctx.channel().config().isAutoRead() && !isHandlerReadActive(ctx)) {
                        logger.debug("Unsuspend: " + ctx.channel().config().isAutoRead() + ":" +
                                isHandlerReadActive(ctx));
                    } else {
                        logger.debug("Normal Unsuspend: " + ctx.channel().config().isAutoRead() + ":"
                                + isHandlerReadActive(ctx));
                    }
                }
                ctx.attr(READ_SUSPENDED).set(false);
                ctx.channel().config().setAutoRead(true);
                ctx.channel().read();
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Unsupsend final status => " + ctx.channel().config().isAutoRead() + ":"
                        + isHandlerReadActive(ctx));
            }
        }
    }

    /**
     * Release the Read suspension
     * @param ctx
     */
    protected void releaseReadSuspended(ChannelHandlerContext ctx) {
        ctx.attr(READ_SUSPENDED).set(false);
        ctx.channel().config().setAutoRead(true);
    }
    
    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        long size = calculateSize(msg);
        long now = TrafficCounter.milliSecondFromNano();
        if (size > 0 && trafficCounter != null) {
            // compute the number of ms to wait before reopening the channel
            long wait = trafficCounter.readTimeToWait(size, readLimit, maxTime, now);
            wait = checkWaitReadTime(ctx, wait, now);
            if (wait >= MINIMAL_WAIT) { // At least 10ms seems a minimal
                // time in order to try to limit the traffic
                // Only AutoRead AND HandlerActive True means Context Active
                if (logger.isDebugEnabled()) {
                    logger.debug("Read Suspend: " + wait + ":" + ctx.channel().config().isAutoRead() + ":"
                            + isHandlerReadActive(ctx));
                }
                if (ctx.channel().config().isAutoRead() && isHandlerReadActive(ctx)) {
                    ctx.channel().config().setAutoRead(false);
                    ctx.attr(READ_SUSPENDED).set(true);
                    // Create a Runnable to reactive the read if needed. If one was create before it will just be
                    // reused to limit object creation
                    Attribute<Runnable> attr = ctx.attr(REOPEN_TASK);
                    Runnable reopenTask = attr.get();
                    if (reopenTask == null) {
                        reopenTask = new ReopenReadTimerTask(ctx);
                        attr.set(reopenTask);
                    }
                    ctx.executor().schedule(reopenTask, wait, TimeUnit.MILLISECONDS);
                    if (logger.isDebugEnabled()) {
                        logger.debug("Suspend final status => " + ctx.channel().config().isAutoRead() + ":"
                                + isHandlerReadActive(ctx) + " will reopened at: " + wait);
                    }
                }
            }
        }
        informReadOperation(ctx, now);
        ctx.fireChannelRead(msg);
    }

    /**
     * Method overridden in GTSH to take into account specific timer for the channel
     * @param ctx
     * @param wait
     * @param now
     * @return the wait to use according to the context
     */
    protected long checkWaitReadTime(final ChannelHandlerContext ctx, long wait, final long now) {
        // no change by default
        return wait;
    }

    /**
     * Method overridden in GTSH to take into account specific timer for the channel
     * @param ctx
     * @param now
     */
    protected void informReadOperation(final ChannelHandlerContext ctx, final long now) {
        // default noop
    }

    protected static boolean isHandlerReadActive(ChannelHandlerContext ctx) {
        Boolean suspended = ctx.attr(READ_SUSPENDED).get();
        return suspended == null || Boolean.FALSE.equals(suspended);
    }

    @Override
    public void read(ChannelHandlerContext ctx) {
        if (isHandlerReadActive(ctx)) {
            // For Global Traffic (and Read when using EventLoop in pipeline) : check if READ_SUSPENDED is False
            ctx.read();
        }
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
            throws Exception {
        long size = calculateSize(msg);
        long now = TrafficCounter.milliSecondFromNano();
        if (size > 0 && trafficCounter != null) {
            // compute the number of ms to wait before continue with the channel
            long wait = trafficCounter.writeTimeToWait(size, writeLimit, maxTime, now);
            if (wait >= MINIMAL_WAIT) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Write suspend: " + wait + ":" + ctx.channel().config().isAutoRead() + ":"
                            + isHandlerReadActive(ctx));
                }
                submitWrite(ctx, msg, size, wait, now, promise);
                return;
            }
        }
        // to maintain order of write
        submitWrite(ctx, msg, size, 0, now, promise);
    }

    protected abstract void submitWrite(final ChannelHandlerContext ctx, final Object msg, final long size,
            final long delay, final long now, final ChannelPromise promise);

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        ChannelOutboundBuffer cob = ctx.channel().unsafe().outboundBuffer();
        if (cob != null) {
            cob.setUserDefinedWritability(userDefinedWritabilityIndex, true);
        }
        super.channelRegistered(ctx);
    }

    /**
     * Check the writability according to delay and size for the channel.
     * Set if necessary setUserDefinedWritability status.
     * @param ctx
     * @param delay
     * @param queueSize
     */
    protected void checkWriteSuspend(ChannelHandlerContext ctx, long delay, long queueSize) {
        if (queueSize > maxWriteSize || delay > maxWriteDelay) {
            ChannelOutboundBuffer cob = ctx.channel().unsafe().outboundBuffer();
            if (cob != null) {
                cob.setUserDefinedWritability(userDefinedWritabilityIndex, false);
            }
        }
    }
    /**
     * Explicitly release the Write suspended status
     * @param ctx
     */
    protected void releaseWriteSuspended(ChannelHandlerContext ctx) {
        ChannelOutboundBuffer cob = ctx.channel().unsafe().outboundBuffer();
        if (cob != null) {
            cob.setUserDefinedWritability(userDefinedWritabilityIndex, true);
        }
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
        StringBuilder builder = new StringBuilder("TrafficShaping with Write Limit: ").append(writeLimit);
        builder.append(" Read Limit: ").append(readLimit);
        builder.append(" CheckInterval: ").append(checkInterval);
        builder.append(" maxDelay: ").append(maxWriteDelay);
        builder.append(" maxSize: ").append(maxWriteSize);
        builder.append(" and Counter: ");
        if (trafficCounter != null) {
            builder.append(trafficCounter.toString());
        } else {
            builder.append("none");
        }
        return builder.toString();
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
