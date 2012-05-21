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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.netty.channel.Channel;
import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelState;
import io.netty.channel.ChannelStateEvent;
import io.netty.channel.MessageEvent;
import io.netty.channel.SimpleChannelHandler;
import io.netty.handler.execution.ObjectSizeEstimator;
import io.netty.handler.execution.DefaultObjectSizeEstimator;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;
import io.netty.util.ExternalResourceReleasable;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;

/**
 * AbstractTrafficShapingHandler allows to limit the global bandwidth
 * (see {@link GlobalTrafficShapingHandler}) or per session
 * bandwidth (see {@link ChannelTrafficShapingHandler}), as traffic shaping.
 * It allows too to implement an almost real time monitoring of the bandwidth using
 * the monitors from {@link TrafficCounter} that will call back every checkInterval
 * the method doAccounting of this handler.<br>
 * <br>
 *
 * An {@link ObjectSizeEstimator} can be passed at construction to specify what
 * is the size of the object to be read or write accordingly to the type of
 * object. If not specified, it will used the {@link DefaultObjectSizeEstimator} implementation.<br><br>
 *
 * If you want for any particular reasons to stop the monitoring (accounting) or to change
 * the read/write limit or the check interval, several methods allow that for you:<br>
 * <ul>
 * <li><tt>configure</tt> allows you to change read or write limits, or the checkInterval</li>
 * <li><tt>getTrafficCounter</tt> allows you to have access to the TrafficCounter and so to stop
 * or start the monitoring, to change the checkInterval directly, or to have access to its values.</li>
 * <li></li>
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
     * Default minimal time to wait
     */
    private static final long MINIMAL_WAIT = 10;

    /**
     * Traffic Counter
     */
    protected TrafficCounter trafficCounter;

    /**
     * ObjectSizeEstimator
     */
    private ObjectSizeEstimator objectSizeEstimator;

    /**
     * Timer to associated to any TrafficCounter
     */
    protected Timer timer;
    /**
     * used in releaseExternalResources() to cancel the timer
     */
    private volatile Timeout timeout;
    
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

    /**
     * Boolean associated with the release of this TrafficShapingHandler.
     * It will be true only once when the releaseExternalRessources is called
     * to prevent waiting when shutdown.
     */
    final AtomicBoolean release = new AtomicBoolean(false);

     private void init(ObjectSizeEstimator newObjectSizeEstimator,
             Timer newTimer, long newWriteLimit, long newReadLimit,
             long newCheckInterval) {
         objectSizeEstimator = newObjectSizeEstimator;
         timer = newTimer;
         writeLimit = newWriteLimit;
         readLimit = newReadLimit;
         checkInterval = newCheckInterval;
         //logger.warn("TSH: "+writeLimit+":"+readLimit+":"+checkInterval);
     }

    /**
     *
     * @param newTrafficCounter the TrafficCounter to set
     */
    void setTrafficCounter(TrafficCounter newTrafficCounter) {
        trafficCounter = newTrafficCounter;
    }

    /**
     * Constructor using default {@link ObjectSizeEstimator}
     *
     * @param timer
     *          created once for instance like HashedWheelTimer(10, TimeUnit.MILLISECONDS, 1024)
     * @param writeLimit
     *          0 or a limit in bytes/s
     * @param readLimit
     *          0 or a limit in bytes/s
     * @param checkInterval
     *          The delay between two computations of performances for
     *            channels or 0 if no stats are to be computed
     */
    public AbstractTrafficShapingHandler(Timer timer, long writeLimit,
            long readLimit, long checkInterval) {
        init(new DefaultObjectSizeEstimator(), timer, writeLimit, readLimit, checkInterval);
    }

    /**
     * Constructor using the specified ObjectSizeEstimator
     *
     * @param objectSizeEstimator
     *            the {@link ObjectSizeEstimator} that will be used to compute
     *            the size of the message
     * @param timer
     *          created once for instance like HashedWheelTimer(10, TimeUnit.MILLISECONDS, 1024)
     * @param writeLimit
     *          0 or a limit in bytes/s
     * @param readLimit
     *          0 or a limit in bytes/s
     * @param checkInterval
     *          The delay between two computations of performances for
     *            channels or 0 if no stats are to be computed
     */
    public AbstractTrafficShapingHandler(
            ObjectSizeEstimator objectSizeEstimator, Timer timer,
            long writeLimit, long readLimit, long checkInterval) {
        init(objectSizeEstimator, timer, writeLimit, readLimit, checkInterval);
    }

    /**
     * Constructor using default {@link ObjectSizeEstimator} and using default Check Interval
     *
     * @param timer
     *          created once for instance like HashedWheelTimer(10, TimeUnit.MILLISECONDS, 1024)
     * @param writeLimit
     *          0 or a limit in bytes/s
     * @param readLimit
     *          0 or a limit in bytes/s
     */
    public AbstractTrafficShapingHandler(Timer timer, long writeLimit,
            long readLimit) {
        init(new DefaultObjectSizeEstimator(), timer, writeLimit, readLimit, DEFAULT_CHECK_INTERVAL);
    }

    /**
     * Constructor using the specified ObjectSizeEstimator and using default Check Interval
     *
     * @param objectSizeEstimator
     *            the {@link ObjectSizeEstimator} that will be used to compute
     *            the size of the message
     * @param timer
     *          created once for instance like HashedWheelTimer(10, TimeUnit.MILLISECONDS, 1024)
     * @param writeLimit
     *          0 or a limit in bytes/s
     * @param readLimit
     *          0 or a limit in bytes/s
     */
    public AbstractTrafficShapingHandler(
            ObjectSizeEstimator objectSizeEstimator, Timer timer,
            long writeLimit, long readLimit) {
        init(objectSizeEstimator, timer, writeLimit, readLimit, DEFAULT_CHECK_INTERVAL);
    }

    /**
     * Constructor using default {@link ObjectSizeEstimator} and using NO LIMIT and default Check Interval
     *
     * @param timer
     *          created once for instance like HashedWheelTimer(10, TimeUnit.MILLISECONDS, 1024)
     */
    public AbstractTrafficShapingHandler(Timer timer) {
        init(new DefaultObjectSizeEstimator(), timer, 0, 0, DEFAULT_CHECK_INTERVAL);
    }

    /**
     * Constructor using the specified ObjectSizeEstimator and using NO LIMIT and default Check Interval
     *
     * @param objectSizeEstimator
     *            the {@link ObjectSizeEstimator} that will be used to compute
     *            the size of the message
     * @param timer
     *          created once for instance like HashedWheelTimer(10, TimeUnit.MILLISECONDS, 1024)
     */
    public AbstractTrafficShapingHandler(
            ObjectSizeEstimator objectSizeEstimator, Timer timer) {
        init(objectSizeEstimator, timer, 0, 0, DEFAULT_CHECK_INTERVAL);
    }

    /**
     * Constructor using default {@link ObjectSizeEstimator} and using NO LIMIT
     *
     * @param timer
     *          created once for instance like HashedWheelTimer(10, TimeUnit.MILLISECONDS, 1024)
     * @param checkInterval
     *          The delay between two computations of performances for
     *            channels or 0 if no stats are to be computed
     */
    public AbstractTrafficShapingHandler(Timer timer, long checkInterval) {
        init(new DefaultObjectSizeEstimator(), timer, 0, 0, checkInterval);
    }

    /**
     * Constructor using the specified ObjectSizeEstimator and using NO LIMIT
     *
     * @param objectSizeEstimator
     *            the {@link ObjectSizeEstimator} that will be used to compute
     *            the size of the message
     * @param timer
     *          created once for instance like HashedWheelTimer(10, TimeUnit.MILLISECONDS, 1024)
     * @param checkInterval
     *          The delay between two computations of performances for
     *            channels or 0 if no stats are to be computed
     */
    public AbstractTrafficShapingHandler(
            ObjectSizeEstimator objectSizeEstimator, Timer timer,
            long checkInterval) {
        init(objectSizeEstimator, timer, 0, 0, checkInterval);
    }

    /**
     * Change the underlying limitations and check interval.
     *
     * @param newWriteLimit
     * @param newReadLimit
     * @param newCheckInterval
     */
    public void configure(long newWriteLimit, long newReadLimit,
            long newCheckInterval) {
        this.configure(newWriteLimit, newReadLimit);
        this.configure(newCheckInterval);
    }

    /**
     * Change the underlying limitations.
     *
     * @param newWriteLimit
     * @param newReadLimit
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
     * @param newCheckInterval
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
    protected void doAccounting(TrafficCounter counter) {
        // NOOP by default
    }

    /**
     * Class to implement setReadable at fix time
     */
    private class ReopenReadTimerTask implements TimerTask {
        ChannelHandlerContext ctx;
        ReopenReadTimerTask(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }
        public void run(Timeout timeoutArg) throws Exception {
            //logger.warn("Start RRTT: "+release.get());
            if (release.get()) {
                return;
            }
            /*
            logger.warn("WAKEUP! "+
                    (ctx != null && ctx.getChannel() != null &&
                            ctx.getChannel().isConnected()));
             */
            if (ctx != null && ctx.getChannel() != null &&
                    ctx.getChannel().isConnected()) {
                //logger.warn(" setReadable TRUE: ");
                // readSuspended = false;
                ctx.setAttachment(null);
                ctx.getChannel().setReadable(true);
            }
        }
    }

    /**
    *
    * @return the time that should be necessary to wait to respect limit. Can
    *         be negative time
    */
    private long getTimeToWait(long limit, long bytes, long lastTime,
            long curtime) {
        long interval = curtime - lastTime;
        if (interval == 0) {
            // Time is too short, so just lets continue
            return 0;
        }
        return ((bytes * 1000 / limit - interval) / 10) * 10;
    }

    @Override
    public void messageReceived(ChannelHandlerContext arg0, MessageEvent arg1)
            throws Exception {
        try {
            long curtime = System.currentTimeMillis();
            long size = objectSizeEstimator.estimateSize(arg1.getMessage());
            if (trafficCounter != null) {
                trafficCounter.bytesRecvFlowControl(arg0, size);
                if (readLimit == 0) {
                    // no action
                    return;
                }
                // compute the number of ms to wait before reopening the channel
                long wait = getTimeToWait(readLimit,
                        trafficCounter.getCurrentReadBytes(),
                        trafficCounter.getLastTime(), curtime);
                if (wait >= MINIMAL_WAIT) { // At least 10ms seems a minimal
                                            // time in order to
                    Channel channel = arg0.getChannel();
                    // try to limit the traffic
                    if (channel != null && channel.isConnected()) {
                        // Channel version
                        if (timer == null) {
                            // Sleep since no executor
                            // logger.warn("Read sleep since no timer for "+wait+" ms for "+this);
                            if (release.get()) {
                                return;
                            }
                            Thread.sleep(wait);
                            return;
                        }
                        if (arg0.getAttachment() == null) {
                            // readSuspended = true;
                            arg0.setAttachment(Boolean.TRUE);
                            channel.setReadable(false);
                            // logger.warn("Read will wakeup after "+wait+" ms "+this);
                            TimerTask timerTask = new ReopenReadTimerTask(arg0);
                            timeout = timer.newTimeout(timerTask, wait,
                                    TimeUnit.MILLISECONDS);
                        } else {
                            // should be waiting: but can occurs sometime so as
                            // a FIX
                            // logger.warn("Read sleep ok but should not be here: "+wait+" "+this);
                            if (release.get()) {
                                return;
                            }
                            Thread.sleep(wait);
                        }
                    } else {
                        // Not connected or no channel
                        // logger.warn("Read sleep "+wait+" ms for "+this);
                        if (release.get()) {
                            return;
                        }
                        Thread.sleep(wait);
                    }
                }
            }
        } finally {
            // The message is then just passed to the next handler
            super.messageReceived(arg0, arg1);
        }
    }

    @Override
    public void writeRequested(ChannelHandlerContext arg0, MessageEvent arg1)
            throws Exception {
        try {
            long curtime = System.currentTimeMillis();
            long size = objectSizeEstimator.estimateSize(arg1.getMessage());
            if (trafficCounter != null) {
                trafficCounter.bytesWriteFlowControl(size);
                if (writeLimit == 0) {
                    return;
                }
                // compute the number of ms to wait before continue with the
                // channel
                long wait = getTimeToWait(writeLimit,
                        trafficCounter.getCurrentWrittenBytes(),
                        trafficCounter.getLastTime(), curtime);
                if (wait >= MINIMAL_WAIT) {
                    // Global or Channel
                    if (release.get()) {
                        return;
                    }
                    Thread.sleep(wait);
                }
            }
        } finally {
            // The message is then just passed to the next handler
            super.writeRequested(arg0, arg1);
        }
    }
    @Override
    public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent e)
            throws Exception {
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent cse = (ChannelStateEvent) e;
            if (cse.getState() == ChannelState.INTEREST_OPS &&
                    (((Integer) cse.getValue()).intValue() & Channel.OP_READ) != 0) {

                // setReadable(true) requested
                boolean readSuspended = ctx.getAttachment() != null;
                if (readSuspended) {
                    // Drop the request silently if this handler has
                    // set the flag.
                    e.getFuture().setSuccess();
                    return;
                }
            }
        }
        super.handleDownstream(ctx, e);
    }

    /**
     *
     * @return the current TrafficCounter (if
     *         channel is still connected)
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
    }

    @Override
    public String toString() {
        return "TrafficShaping with Write Limit: " + writeLimit +
                " Read Limit: " + readLimit + " and Counter: " +
                (trafficCounter != null? trafficCounter.toString() : "none");
    }
}
