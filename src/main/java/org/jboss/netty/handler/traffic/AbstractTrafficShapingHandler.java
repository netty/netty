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

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.DefaultObjectSizeEstimator;
import org.jboss.netty.util.ExternalResourceReleasable;
import org.jboss.netty.util.ObjectSizeEstimator;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <p>AbstractTrafficShapingHandler allows to limit the global bandwidth
 * (see {@link GlobalTrafficShapingHandler}) or per session
 * bandwidth (see {@link ChannelTrafficShapingHandler}), as traffic shaping.
 * It allows too to implement an almost real time monitoring of the bandwidth using
 * the monitors from {@link TrafficCounter} that will call back every checkInterval
 * the method doAccounting of this handler.</p>
 *
 * <p>An {@link ObjectSizeEstimator} can be passed at construction to specify what
 * is the size of the object to be read or write accordingly to the type of
 * object. If not specified, it will used the {@link DefaultObjectSizeEstimator} implementation.</p>
 *
 * <p>If you want for any particular reasons to stop the monitoring (accounting) or to change
 * the read/write limit or the check interval, several methods allow that for you:</p>
 * <ul>
 * <li><tt>configure</tt> allows you to change read or write limits, or the checkInterval</li>
 * <li><tt>getTrafficCounter</tt> allows you to have access to the TrafficCounter and so to stop
 * or start the monitoring, to change the checkInterval directly, or to have access to its values.</li>
 * </ul>
 */
public abstract class AbstractTrafficShapingHandler extends
        SimpleChannelHandler implements ExternalResourceReleasable {
    /**
     * Internal logger
     */
    static InternalLogger logger = InternalLoggerFactory
            .getInstance(AbstractTrafficShapingHandler.class);

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
    static final long DEFAULT_MAX_SIZE = 4 * 1024 * 1024L;

    /**
     * Default minimal time to wait
     */
    static final long MINIMAL_WAIT = 10;

    static final int CHANNEL_DEFAULT_USER_DEFINED_WRITABILITY_INDEX = 1;
    static final int GLOBAL_DEFAULT_USER_DEFINED_WRITABILITY_INDEX = 2;
    static final int GLOBALCHANNEL_DEFAULT_USER_DEFINED_WRITABILITY_INDEX = 3;

    /**
     * Traffic Counter
     */
    protected TrafficCounter trafficCounter;

    /**
     * ObjectSizeEstimator
     */
    private ObjectSizeEstimator objectSizeEstimator;

    /**
     * Timer associated to any TrafficCounter
     */
    protected Timer timer;

    /**
     * used in releaseExternalResources() to cancel the timer
     */
    volatile Timeout timeout;

    /**
     * Limit in B/s to apply to write
     */
    private volatile long writeLimit;

    /**
     * Limit in B/s to apply to read
     */
    private volatile long readLimit;

    /**
     * Delay between two performance snapshots
     */
    protected volatile long checkInterval = DEFAULT_CHECK_INTERVAL; // default 1 s

    /**
     * Max delay in wait
     */
    protected volatile long maxTime = DEFAULT_MAX_TIME; // default 15 s

    /**
     * Max time to delay before proposing to stop writing new objects from next handlers
     */
    volatile long maxWriteDelay = 4 * DEFAULT_CHECK_INTERVAL; // default 4 s

    /**
     * Max size in the list before proposing to stop writing new objects from next handlers
     */
    volatile long maxWriteSize = DEFAULT_MAX_SIZE; // default 4MB

    /**
     * Boolean associated with the release of this TrafficShapingHandler.
     * It will be true only once when the releaseExternalRessources is called
     * to prevent waiting when shutdown.
     */
    final AtomicBoolean release = new AtomicBoolean(false);
    final int index;

    /**
     * Attachment of ChannelHandlerContext
     *
     */
    static final class ReadWriteStatus {
        volatile boolean readSuspend;
        volatile TimerTask reopenReadTimerTask;
    }

    /**
     * For simple ChannelBuffer, returns the readableBytes, else
     * use standard DefaultObjectSizeEstimator.
     */
    public static class SimpleObjectSizeEstimator extends DefaultObjectSizeEstimator {
        @Override
        public int estimateSize(Object o) {
            int size;
            if (o instanceof ChannelBuffer) {
                size = ((ChannelBuffer) o).readableBytes();
            } else {
                size = super.estimateSize(o);
            }
            return size;
        }
    }

    /**
     * @return the index to be used by the TrafficShapingHandler to manage the user defined
     *         writability. For Channel TSH it is defined as
     *         {@value #CHANNEL_DEFAULT_USER_DEFINED_WRITABILITY_INDEX}, for Global TSH it is
     *         defined as {@value #GLOBAL_DEFAULT_USER_DEFINED_WRITABILITY_INDEX},
     *         for GlobalChannel TSH it is defined as
     *         {@value #GLOBALCHANNEL_DEFAULT_USER_DEFINED_WRITABILITY_INDEX}.
     */
    int userDefinedWritabilityIndex() {
        if (this instanceof GlobalChannelTrafficShapingHandler) {
            return GLOBALCHANNEL_DEFAULT_USER_DEFINED_WRITABILITY_INDEX;
        } else if (this instanceof GlobalTrafficShapingHandler) {
            return GLOBAL_DEFAULT_USER_DEFINED_WRITABILITY_INDEX;
        } else {
            return CHANNEL_DEFAULT_USER_DEFINED_WRITABILITY_INDEX;
        }
    }

    private void init(ObjectSizeEstimator newObjectSizeEstimator,
             Timer newTimer, long newWriteLimit, long newReadLimit,
             long newCheckInterval, long newMaxTime) {
        if (newMaxTime <= 0) {
            throw new IllegalArgumentException("maxTime must be positive");
        }
         objectSizeEstimator = newObjectSizeEstimator;
         timer = newTimer;
         writeLimit = newWriteLimit;
         readLimit = newReadLimit;
         checkInterval = newCheckInterval;
         maxTime = newMaxTime;
         //logger.warn("TSH: "+writeLimit+":"+readLimit+":"+checkInterval);
     }

    /**
     * @param newTrafficCounter the TrafficCounter to set
     */
    void setTrafficCounter(TrafficCounter newTrafficCounter) {
        trafficCounter = newTrafficCounter;
    }

    /**
     * Constructor using default {@link ObjectSizeEstimator} and
     * default max time as delay allowed value of {@value #DEFAULT_MAX_TIME} ms.
     *
     * @param timer
     *          created once for instance like HashedWheelTimer(10, TimeUnit.MILLISECONDS, 1024).
     * @param writeLimit
     *          0 or a limit in bytes/s
     * @param readLimit
     *          0 or a limit in bytes/s
     * @param checkInterval
     *          The delay between two computations of performances for
     *            channels or 0 if no stats are to be computed.
     */
    protected AbstractTrafficShapingHandler(Timer timer, long writeLimit,
                                            long readLimit, long checkInterval) {
        index = userDefinedWritabilityIndex();
        init(new SimpleObjectSizeEstimator(), timer, writeLimit, readLimit, checkInterval,
                DEFAULT_MAX_TIME);
    }

    /**
     * Constructor using the specified ObjectSizeEstimator and
     * default max time as delay allowed value of {@value #DEFAULT_MAX_TIME} ms.
     *
     * @param objectSizeEstimator
     *            the {@link ObjectSizeEstimator} that will be used to compute
     *            the size of the message.
     * @param timer
     *          created once for instance like HashedWheelTimer(10, TimeUnit.MILLISECONDS, 1024).
     * @param writeLimit
     *          0 or a limit in bytes/s
     * @param readLimit
     *          0 or a limit in bytes/s
     * @param checkInterval
     *          The delay between two computations of performances for
     *            channels or 0 if no stats are to be computed.
     */
    protected AbstractTrafficShapingHandler(
            ObjectSizeEstimator objectSizeEstimator, Timer timer,
            long writeLimit, long readLimit, long checkInterval) {
        index = userDefinedWritabilityIndex();
        init(objectSizeEstimator, timer, writeLimit, readLimit, checkInterval, DEFAULT_MAX_TIME);
    }

    /**
     * Constructor using default {@link ObjectSizeEstimator} and using
     * default Check Interval value of {@value #DEFAULT_CHECK_INTERVAL} ms and
     * default max time as delay allowed value of {@value #DEFAULT_MAX_TIME} ms.
     *
     * @param timer
     *          created once for instance like HashedWheelTimer(10, TimeUnit.MILLISECONDS, 1024).
     * @param writeLimit
     *          0 or a limit in bytes/s
     * @param readLimit
     *          0 or a limit in bytes/s
     */
    protected AbstractTrafficShapingHandler(Timer timer, long writeLimit,
                                            long readLimit) {
        index = userDefinedWritabilityIndex();
        init(new SimpleObjectSizeEstimator(), timer, writeLimit, readLimit,
                DEFAULT_CHECK_INTERVAL, DEFAULT_MAX_TIME);
    }

    /**
     * Constructor using the specified ObjectSizeEstimator and using
     * default Check Interval value of {@value #DEFAULT_CHECK_INTERVAL} ms and
     * default max time as delay allowed value of {@value #DEFAULT_MAX_TIME} ms.
     *
     * @param objectSizeEstimator
     *            the {@link ObjectSizeEstimator} that will be used to compute
     *            the size of the message.
     * @param timer
     *          created once for instance like HashedWheelTimer(10, TimeUnit.MILLISECONDS, 1024).
     * @param writeLimit
     *          0 or a limit in bytes/s
     * @param readLimit
     *          0 or a limit in bytes/s
     */
    protected AbstractTrafficShapingHandler(
            ObjectSizeEstimator objectSizeEstimator, Timer timer,
            long writeLimit, long readLimit) {
        index = userDefinedWritabilityIndex();
        init(objectSizeEstimator, timer, writeLimit, readLimit,
                DEFAULT_CHECK_INTERVAL, DEFAULT_MAX_TIME);
    }

    /**
     * Constructor using default {@link ObjectSizeEstimator} and using NO LIMIT and
     * default Check Interval value of {@value #DEFAULT_CHECK_INTERVAL} ms and
     * default max time as delay allowed value of {@value #DEFAULT_MAX_TIME} ms.
     *
     * @param timer
     *          created once for instance like HashedWheelTimer(10, TimeUnit.MILLISECONDS, 1024).
     */
    protected AbstractTrafficShapingHandler(Timer timer) {
        index = userDefinedWritabilityIndex();
        init(new SimpleObjectSizeEstimator(), timer, 0, 0,
                DEFAULT_CHECK_INTERVAL, DEFAULT_MAX_TIME);
    }

    /**
     * Constructor using the specified ObjectSizeEstimator and using NO LIMIT and
     * default Check Interval value of {@value #DEFAULT_CHECK_INTERVAL} ms and
     * default max time as delay allowed value of {@value #DEFAULT_MAX_TIME} ms.
     *
     * @param objectSizeEstimator
     *            the {@link ObjectSizeEstimator} that will be used to compute
     *            the size of the message.
     * @param timer
     *          created once for instance like HashedWheelTimer(10, TimeUnit.MILLISECONDS, 1024).
     */
    protected AbstractTrafficShapingHandler(
            ObjectSizeEstimator objectSizeEstimator, Timer timer) {
        index = userDefinedWritabilityIndex();
        init(objectSizeEstimator, timer, 0, 0,
                DEFAULT_CHECK_INTERVAL, DEFAULT_MAX_TIME);
    }

    /**
     * Constructor using default {@link ObjectSizeEstimator} and using NO LIMIT and
     * default max time as delay allowed value of {@value #DEFAULT_MAX_TIME} ms.
     *
     * @param timer
     *          created once for instance like HashedWheelTimer(10, TimeUnit.MILLISECONDS, 1024).
     * @param checkInterval
     *          The delay between two computations of performances for
     *            channels or 0 if no stats are to be computed.
     */
    protected AbstractTrafficShapingHandler(Timer timer, long checkInterval) {
        index = userDefinedWritabilityIndex();
        init(new SimpleObjectSizeEstimator(), timer, 0, 0, checkInterval, DEFAULT_MAX_TIME);
    }

    /**
     * Constructor using the specified ObjectSizeEstimator and using NO LIMIT and
     * default max time as delay allowed value of {@value #DEFAULT_MAX_TIME} ms.
     *
     * @param objectSizeEstimator
     *            the {@link ObjectSizeEstimator} that will be used to compute
     *            the size of the message.
     * @param timer
     *          created once for instance like HashedWheelTimer(10, TimeUnit.MILLISECONDS, 1024).
     * @param checkInterval
     *          The delay between two computations of performances for
     *            channels or 0 if no stats are to be computed.
     */
    protected AbstractTrafficShapingHandler(
            ObjectSizeEstimator objectSizeEstimator, Timer timer,
            long checkInterval) {
        index = userDefinedWritabilityIndex();
        init(objectSizeEstimator, timer, 0, 0, checkInterval, DEFAULT_MAX_TIME);
    }

    /**
     * Constructor using default {@link ObjectSizeEstimator}.
     *
     * @param timer
     *          created once for instance like HashedWheelTimer(10, TimeUnit.MILLISECONDS, 1024).
     * @param writeLimit
     *          0 or a limit in bytes/s
     * @param readLimit
     *          0 or a limit in bytes/s
     * @param checkInterval
     *          The delay between two computations of performances for
     *            channels or 0 if no stats are to be computed.
     * @param maxTime
     *          The max time to wait in case of excess of traffic (to prevent Time Out event).
     *          Must be positive.
     */
    protected AbstractTrafficShapingHandler(Timer timer, long writeLimit,
                                            long readLimit, long checkInterval, long maxTime) {
        index = userDefinedWritabilityIndex();
        init(new SimpleObjectSizeEstimator(), timer, writeLimit, readLimit, checkInterval,
                maxTime);
    }

    /**
     * Constructor using the specified ObjectSizeEstimator.
     *
     * @param objectSizeEstimator
     *            the {@link ObjectSizeEstimator} that will be used to compute
     *            the size of the message.
     * @param timer
     *          created once for instance like HashedWheelTimer(10, TimeUnit.MILLISECONDS, 1024).
     * @param writeLimit
     *          0 or a limit in bytes/s
     * @param readLimit
     *          0 or a limit in bytes/s
     * @param checkInterval
     *          The delay between two computations of performances for
     *            channels or 0 if no stats are to be computed.
     * @param maxTime
     *          The max time to wait in case of excess of traffic (to prevent Time Out event).
     *          Must be positive.
     */
    protected AbstractTrafficShapingHandler(
            ObjectSizeEstimator objectSizeEstimator, Timer timer,
            long writeLimit, long readLimit, long checkInterval, long maxTime) {
        index = userDefinedWritabilityIndex();
        init(objectSizeEstimator, timer, writeLimit, readLimit, checkInterval, maxTime);
    }

    /**
     * Change the underlying limitations and check interval.
     * <p>Note the change will be taken as best effort, meaning
     * that all already scheduled traffics will not be
     * changed, but only applied to new traffics.</p>
     * So the expected usage of this method is to be used not too often,
     * accordingly to the traffic shaping configuration.
     *
     * @param newWriteLimit
     *            The new write limit (in bytes)
     * @param newReadLimit
     *            The new read limit (in bytes)
     * @param newCheckInterval
     *            The new check interval (in milliseconds)
     */
    public void configure(long newWriteLimit, long newReadLimit,
            long newCheckInterval) {
        configure(newWriteLimit, newReadLimit);
        configure(newCheckInterval);
    }

    /**
     * Change the underlying limitations.
     * <p>Note the change will be taken as best effort, meaning
     * that all already scheduled traffics will not be
     * changed, but only applied to new traffics.</p>
     * So the expected usage of this method is to be used not too often,
     * accordingly to the traffic shaping configuration.
     *
     * @param newWriteLimit
     *            The new write limit (in bytes)
     * @param newReadLimit
     *            The new read limit (in bytes)
     */
    public void configure(long newWriteLimit, long newReadLimit) {
        writeLimit = newWriteLimit;
        readLimit = newReadLimit;
        if (trafficCounter != null) {
            trafficCounter.resetAccounting(TrafficCounter.milliSecondFromNano());
        }
    }

    /**
     * Change the check interval.
     */
    public void configure(long newCheckInterval) {
        setCheckInterval(newCheckInterval);
    }

    /**
     * @return the writeLimit
     */
    public long getWriteLimit() {
        return writeLimit;
    }

    /**
     * <p>Note the change will be taken as best effort, meaning
     * that all already scheduled traffics will not be
     * changed, but only applied to new traffics.</p>
     * So the expected usage of this method is to be used not too often,
     * accordingly to the traffic shaping configuration.
     *
     * @param writeLimit the writeLimit to set
     */
    public void setWriteLimit(long writeLimit) {
        this.writeLimit = writeLimit;
        if (trafficCounter != null) {
            trafficCounter.resetAccounting(TrafficCounter.milliSecondFromNano());
        }
    }

    /**
     * @return the readLimit
     */
    public long getReadLimit() {
        return readLimit;
    }

    /**
     * <p>Note the change will be taken as best effort, meaning
     * that all already scheduled traffics will not be
     * changed, but only applied to new traffics.</p>
     * So the expected usage of this method is to be used not too often,
     * accordingly to the traffic shaping configuration.
     *
     * @param readLimit the readLimit to set
     */
    public void setReadLimit(long readLimit) {
        this.readLimit = readLimit;
        if (trafficCounter != null) {
            trafficCounter.resetAccounting(TrafficCounter.milliSecondFromNano());
        }
    }

    /**
     * @return the checkInterval
     */
    public long getCheckInterval() {
        return checkInterval;
    }

    /**
     * @param newCheckInterval the checkInterval to set
     */
    public void setCheckInterval(long newCheckInterval) {
        checkInterval = newCheckInterval;
        if (trafficCounter != null) {
            trafficCounter.configure(checkInterval);
        }
    }

    /**
     * @return the max delay on wait
     */
    public long getMaxTimeWait() {
        return maxTime;
    }

   /**
    * <p>Note the change will be taken as best effort, meaning
    * that all already scheduled traffics will not be
    * changed, but only applied to new traffics.</p>
    * So the expected usage of this method is to be used not too often,
    * accordingly to the traffic shaping configuration.
    *
    * @param maxTime
    *    Max delay in wait, shall be less than TIME OUT in related protocol.
    *    Must be positive.
    */
   public void setMaxTimeWait(long maxTime) {
       if (maxTime <= 0) {
           throw new IllegalArgumentException("maxTime must be positive");
       }
       this.maxTime = maxTime;
   }

   /**
    * @return the maxWriteDelay
    */
   public long getMaxWriteDelay() {
       return maxWriteDelay;
   }

   /**
    * <p>Note the change will be taken as best effort, meaning
    * that all already scheduled traffics will not be
    * changed, but only applied to new traffics.</p>
    * So the expected usage of this method is to be used not too often,
    * accordingly to the traffic shaping configuration.
    *
    * @param maxWriteDelay the maximum Write Delay in ms in the buffer allowed before write suspended is set.
    *       Must be positive.
    */
   public void setMaxWriteDelay(long maxWriteDelay) {
       if (maxWriteDelay <= 0) {
           throw new IllegalArgumentException("maxWriteDelay must be positive");
       }
       this.maxWriteDelay = maxWriteDelay;
   }

   /**
    * @return the maxWriteSize default being {@value #DEFAULT_MAX_SIZE} bytes
    */
   public long getMaxWriteSize() {
       return maxWriteSize;
   }

   /**
    * <p>Note the change will be taken as best effort, meaning
    * that all already scheduled traffics will not be
    * changed, but only applied to new traffics.</p>
    * So the expected usage of this method is to be used not too often,
    * accordingly to the traffic shaping configuration.
    *
    * @param maxWriteSize the maximum Write Size allowed in the buffer
    *            per channel before write suspended is set,
    *            default being {@value #DEFAULT_MAX_SIZE} bytes
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
     * Class to implement setReadable at fix time.
     */
    class ReopenReadTimerTask implements TimerTask {
        final ChannelHandlerContext ctx;
        ReopenReadTimerTask(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }
        public void run(Timeout timeoutArg) throws Exception {
            //logger.warn("Start RRTT: "+release.get());
            if (release.get()) {
                return;
            }
            ReadWriteStatus rws = checkAttachment(ctx);
            Channel channel = ctx.getChannel();
            if (! channel.isConnected()) {
                // ignore
                return;
            }
            if (!channel.isReadable() && ! rws.readSuspend) {
                // If isReadable is False and Active is True, user make a direct setReadable(false)
                // Then Just reset the status
                if (logger.isDebugEnabled()) {
                    logger.debug("Not unsuspend: " + channel.isReadable() + ':' +
                            rws.readSuspend);
                }
                rws.readSuspend = false;
            } else {
                // Anything else allows the handler to reset the AutoRead
                if (logger.isDebugEnabled()) {
                    if (channel.isReadable() && rws.readSuspend) {
                        logger.debug("Unsuspend: " + channel.isReadable() + ':' +
                                rws.readSuspend);
                    } else {
                        logger.debug("Normal unsuspend: " + channel.isReadable() + ':' +
                                rws.readSuspend);
                    }
                }
                rws.readSuspend = false;
                channel.setReadable(true);
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Unsupsend final status => " + channel.isReadable() + ':' +
                        rws.readSuspend);
            }
        }
    }

   /**
    * Release the Read suspension.
    */
    void releaseReadSuspended(ChannelHandlerContext ctx) {
        ReadWriteStatus rws = checkAttachment(ctx);
        rws.readSuspend = false;
        ctx.getChannel().setReadable(true);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent evt)
            throws Exception {
        long now = TrafficCounter.milliSecondFromNano();
        try {
            ReadWriteStatus rws = checkAttachment(ctx);
            long size = calculateSize(evt.getMessage());
            if (size > 0 && trafficCounter != null) {
                // compute the number of ms to wait before reopening the channel
                long wait = trafficCounter.readTimeToWait(size, readLimit, maxTime, now);
                wait = checkWaitReadTime(ctx, wait, now);
                if (wait >= MINIMAL_WAIT) { // At least 10ms seems a minimal
                    // time in order to try to limit the traffic
                    if (release.get()) {
                        return;
                    }
                    Channel channel = ctx.getChannel();
                    if (channel != null && channel.isConnected()) {
                        // Only AutoRead AND HandlerActive True means Context Active
                        if (logger.isDebugEnabled()) {
                            logger.debug("Read suspend: " + wait + ':' + channel.isReadable() + ':' +
                                    rws.readSuspend);
                        }
                        if (timer == null) {
                            // Sleep since no executor
                            // logger.warn("Read sleep since no timer for "+wait+" ms for "+this);
                            Thread.sleep(wait);
                            return;
                        }
                        if (channel.isReadable() && ! rws.readSuspend) {
                            rws.readSuspend = true;
                            channel.setReadable(false);
                            if (logger.isDebugEnabled()) {
                                logger.debug("Suspend final status => " + channel.isReadable() + ':' +
                                        rws.readSuspend);
                            }
                            // Create a Runnable to reactive the read if needed. If one was create before
                            // it will just be reused to limit object creation
                            if (rws.reopenReadTimerTask == null) {
                                rws.reopenReadTimerTask = new ReopenReadTimerTask(ctx);
                            }
                            timeout = timer.newTimeout(rws.reopenReadTimerTask, wait,
                                    TimeUnit.MILLISECONDS);
                        }
                    }
                }
            }
        } finally {
            informReadOperation(ctx, now);
            // The message is then just passed to the next handler
            ctx.sendUpstream(evt);
        }
    }

    /**
     * Method overridden in GTSH to take into account specific timer for the channel.
     * @param wait the wait delay computed in ms
     * @param now the relative now time in ms
     * @return the wait to use according to the context.
     */
    long checkWaitReadTime(final ChannelHandlerContext ctx, long wait, final long now) {
        // no change by default
        return wait;
    }

    /**
     * Method overridden in GTSH to take into account specific timer for the channel.
     * @param now the relative now time in ms
     */
    void informReadOperation(final ChannelHandlerContext ctx, final long now) {
        // default noop
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent evt)
            throws Exception {
        long wait = 0;
        long size = calculateSize(evt.getMessage());
        long now = TrafficCounter.milliSecondFromNano();
        Channel channel = ctx.getChannel();
        try {
            if (size > 0 && trafficCounter != null) {
                // compute the number of ms to wait before continue with the channel
                wait = trafficCounter.writeTimeToWait(size, writeLimit, maxTime, now);
                if (logger.isDebugEnabled()) {
                    logger.debug("Write suspend: " + wait + ':' + channel.isWritable() + ':' +
                            channel.getUserDefinedWritability(index));
                }
                if (wait < MINIMAL_WAIT || release.get()) {
                    wait = 0;
                }
            }
        } finally {
            // The message is scheduled
            submitWrite(ctx, evt, size, wait, now);
        }
    }

    @Deprecated
    protected void internalSubmitWrite(ChannelHandlerContext ctx, MessageEvent evt) throws Exception {
        ctx.sendDownstream(evt);
    }

    @Deprecated
    protected void submitWrite(final ChannelHandlerContext ctx, final MessageEvent evt,
            final long delay) throws Exception {
        submitWrite(ctx, evt, calculateSize(evt.getMessage()), delay, TrafficCounter.milliSecondFromNano());
    }

    abstract void submitWrite(ChannelHandlerContext ctx, MessageEvent evt, long size,
            long delay, long now) throws Exception;

    void setWritable(ChannelHandlerContext ctx, boolean writable) {
        Channel channel = ctx.getChannel();
        if (channel.isConnected()) {
            channel.setUserDefinedWritability(index, writable);
        }
    }

    /**
     * Check the writability according to delay and size for the channel.
     * Set if necessary the write suspended status.
     * @param delay the computed delai
     * @param queueSize the current queueSize
     */
    void checkWriteSuspend(ChannelHandlerContext ctx, long delay, long queueSize) {
        if (queueSize > maxWriteSize || delay > maxWriteDelay) {
            setWritable(ctx, false);
        }
    }

    /**
     * Explicitly release the Write suspended status.
     */
    void releaseWriteSuspended(ChannelHandlerContext ctx) {
        setWritable(ctx, true);
    }

    /**
     * @return the current TrafficCounter (if
     *         channel is still connected).
     */
    public TrafficCounter getTrafficCounter() {
        return trafficCounter;
    }

    public void releaseExternalResources() {
        if (trafficCounter != null) {
            trafficCounter.stop();
        }
        release.set(true);
        if (timeout != null) {
            timeout.cancel();
        }
        //shall be done outside (since it can be shared): timer.stop();
    }

    static ReadWriteStatus checkAttachment(ChannelHandlerContext ctx) {
        ReadWriteStatus rws = (ReadWriteStatus) ctx.getAttachment();
        if (rws == null) {
            rws = new ReadWriteStatus();
            ctx.setAttachment(rws);
        }
        return rws;
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        checkAttachment(ctx);
        setWritable(ctx, true);
        super.channelConnected(ctx, e);
    }

    protected long calculateSize(Object obj) {
        //logger.debug("Size: "+size);
        return objectSizeEstimator.estimateSize(obj);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(290)
            .append("TrafficShaping with Write Limit: ").append(writeLimit)
            .append(" Read Limit: ").append(readLimit)
            .append(" CheckInterval: ").append(checkInterval)
            .append(" maxDelay: ").append(maxWriteDelay)
            .append(" maxSize: ").append(maxWriteSize)
            .append(" and Counter: ");
        if (trafficCounter != null) {
            builder.append(trafficCounter);
        } else {
            builder.append("none");
        }
        return builder.toString();
    }
}
