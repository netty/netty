/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.handler.traffic;

import java.util.concurrent.Executor;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.DefaultObjectSizeEstimator;
import org.jboss.netty.util.ExternalResourceReleasable;
import org.jboss.netty.util.ObjectSizeEstimator;
import org.jboss.netty.util.internal.ExecutorUtil;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Frederic Bregier (fredbregier@free.fr)
 * @version $Rev$, $Date$
 *
 * AbstractTrafficShapingHandler allows to limit the global bandwidth
 * (see {@link GlobalTrafficShapingHandler}) or per session
 * bandwidth (see {@link ChannelTrafficShapingHandler}), as traffic shaping. 
 * It allows too to implement a real time monitoring of the bandwidth using
 * the monitors from {@link TrafficCounter2} that will call back every checkInterval
 * the method doAccounting of this handler.<br>
 * <br>
 *
 * An {@link ObjectSizeEstimator} can be passed at construction to specify what
 * is the size of the object to be read or write accordingly to the type of
 * object. If not specified, it will used the {@link DefaultObjectSizeEstimator} implementation.<br><br>
 * 
 * If you want for any particular reasons to stop the monitoring (accounting) or to change
 * the read/write limit or the check Interval, several methods allow that for you:<br>
 * <ul>
 * <li><tt>configure</tt> allows you to change read or write limits, or the checkInterval</li>
 * <li><tt>getTrafficCounter</tt> allows you to have access to the TrafficCounter and so to stop
 * or start the monitoring, to change the checkInterval directly, or to have access to its values.</li>
 * <li></li>
 * </ul>

 * <br>
 *
 */
public abstract class AbstractTrafficShapingHandler extends SimpleChannelHandler implements ExternalResourceReleasable {
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
    protected TrafficCounter2 trafficCounter = null;
    /**
     * ObjectSizeEstimator
     */
    private ObjectSizeEstimator objectSizeEstimator = null;

    /**
     * Executor to associated to any TrafficCounter
     */
    protected Executor executor = null;

    /**
     * Limit in B/s to apply to write
     */
    private long writeLimit = 0;

    /**
     * Limit in B/s to apply to read
     */
    private long readLimit = 0;

    /**
     * Delay between two performance snapshots
     */
    protected long checkInterval = DEFAULT_CHECK_INTERVAL; // default 1 s
    /**
    * @param newObjectSizeEstimator
    * @param newExecutor
    * @param newActive
    * @param newWriteLimit
    * @param newReadLimit
    * @param newCheckInterval
    */
   private void init(ObjectSizeEstimator newObjectSizeEstimator,
            Executor newExecutor, long newWriteLimit, long newReadLimit, long newCheckInterval) {
        objectSizeEstimator = newObjectSizeEstimator;
        executor = newExecutor;
        writeLimit = newWriteLimit;
        readLimit = newReadLimit;
        checkInterval = newCheckInterval;
        //logger.info("TSH: "+writeLimit+":"+readLimit+":"+checkInterval+":"+isPerChannel());
    }
   /**
    * 
    * @param newTrafficCounter the TrafficCounter to set
    */
   void setTrafficCounter(TrafficCounter2 newTrafficCounter) {
       trafficCounter = newTrafficCounter;
   }
    /**
     * Constructor using default {@link ObjectSizeEstimator}
     *
     * @param executor 
     *          created for instance like Executors.newCachedThreadPool
     * @param writeLimit 
     *          0 or a limit in bytes/s
     * @param readLimit 
     *          0 or a limit in bytes/s
     * @param checkInterval 
     *          The delay between two computations of performances for
     *            channels or 0 if no stats are to be computed
     */
    public AbstractTrafficShapingHandler(Executor executor, long writeLimit, long readLimit, long checkInterval) {
        super();
        this.init(new DefaultObjectSizeEstimator(), 
                executor, writeLimit, readLimit, checkInterval);
    }
    /**
     * Constructor using the specified ObjectSizeEstimator
     *
     * @param objectSizeEstimator
     *            the {@link ObjectSizeEstimator} that will be used to compute
     *            the size of the message
     * @param executor 
     *          created for instance like Executors.newCachedThreadPool
     * @param writeLimit 
     *          0 or a limit in bytes/s
     * @param readLimit 
     *          0 or a limit in bytes/s
     * @param checkInterval 
     *          The delay between two computations of performances for
     *            channels or 0 if no stats are to be computed
     */
    public AbstractTrafficShapingHandler(ObjectSizeEstimator objectSizeEstimator,
            Executor executor, long writeLimit, long readLimit, long checkInterval) {
        super();
        this.init(objectSizeEstimator, 
                executor, writeLimit, readLimit, checkInterval);
    }
    /**
     * Constructor using default {@link ObjectSizeEstimator} and using default Check Interval
     *
     * @param executor 
     *          created for instance like Executors.newCachedThreadPool
     * @param writeLimit 
     *          0 or a limit in bytes/s
     * @param readLimit 
     *          0 or a limit in bytes/s
     */
    public AbstractTrafficShapingHandler(Executor executor, long writeLimit, long readLimit) {
        super();
        this.init(new DefaultObjectSizeEstimator(), 
                executor, writeLimit, readLimit, DEFAULT_CHECK_INTERVAL);
    }
    /**
     * Constructor using the specified ObjectSizeEstimator and using default Check Interval
     *
     * @param objectSizeEstimator
     *            the {@link ObjectSizeEstimator} that will be used to compute
     *            the size of the message
     * @param executor 
     *          created for instance like Executors.newCachedThreadPool
     * @param writeLimit 
     *          0 or a limit in bytes/s
     * @param readLimit 
     *          0 or a limit in bytes/s
     */
    public AbstractTrafficShapingHandler(ObjectSizeEstimator objectSizeEstimator,
            Executor executor, long writeLimit, long readLimit) {
        super();
        this.init(objectSizeEstimator, 
                executor, writeLimit, readLimit, DEFAULT_CHECK_INTERVAL);
    }
    /**
     * Constructor using default {@link ObjectSizeEstimator} and using NO LIMIT and default Check Interval
     *
     * @param executor 
     *          created for instance like Executors.newCachedThreadPool
     */
    public AbstractTrafficShapingHandler(Executor executor) {
        super();
        this.init(new DefaultObjectSizeEstimator(), 
                executor, 0, 0, DEFAULT_CHECK_INTERVAL);
    }
    /**
     * Constructor using the specified ObjectSizeEstimator and using NO LIMIT and default Check Interval
     *
     * @param objectSizeEstimator
     *            the {@link ObjectSizeEstimator} that will be used to compute
     *            the size of the message
     * @param executor 
     *          created for instance like Executors.newCachedThreadPool
     */
    public AbstractTrafficShapingHandler(ObjectSizeEstimator objectSizeEstimator,
            Executor executor) {
        super();
        this.init(objectSizeEstimator, 
                executor, 0, 0, DEFAULT_CHECK_INTERVAL);
    }
    /**
     * Constructor using default {@link ObjectSizeEstimator} and using NO LIMIT
     *
     * @param executor 
     *          created for instance like Executors.newCachedThreadPool
     * @param checkInterval 
     *          The delay between two computations of performances for
     *            channels or 0 if no stats are to be computed
     */
    public AbstractTrafficShapingHandler(Executor executor, long checkInterval) {
        super();
        this.init(new DefaultObjectSizeEstimator(), 
                executor, 0, 0, checkInterval);
    }
    /**
     * Constructor using the specified ObjectSizeEstimator and using NO LIMIT
     *
     * @param objectSizeEstimator
     *            the {@link ObjectSizeEstimator} that will be used to compute
     *            the size of the message
     * @param executor 
     *          created for instance like Executors.newCachedThreadPool
     * @param checkInterval 
     *          The delay between two computations of performances for
     *            channels or 0 if no stats are to be computed
     */
    public AbstractTrafficShapingHandler(ObjectSizeEstimator objectSizeEstimator,
            Executor executor, long checkInterval) {
        super();
        this.init(objectSizeEstimator, 
                executor, 0, 0, checkInterval);
    }
    /**
     * Change the underlying limitations and check interval.
     *
     * @param newWriteLimit
     * @param newReadLimit
     * @param newCheckInterval
     */
    public void configure(long newWriteLimit,
            long newReadLimit, long newCheckInterval) {
        this.configure(newWriteLimit, newReadLimit);
        this.configure(newCheckInterval);
    } 
    /**
     * Change the underlying limitations.
     *
     * @param newWriteLimit
     * @param newReadLimit
     */
    public void configure(long newWriteLimit,
            long newReadLimit) {
        writeLimit = newWriteLimit;
        readLimit = newReadLimit;
    } 
    /**
     * Change the check interval.
     *
     * @param newCheckInterval
     */
    public void configure(long newCheckInterval) {
        checkInterval = newCheckInterval;
        if (this.trafficCounter != null) {
            this.trafficCounter.configure(checkInterval);
        }
    }
    /**
     * Called each time the accounting is computed for the TrafficCounters.
     * This method could be used for instance to implement almost real time accounting.
     *
     * @param counter
     *            the TrafficCounter that computes its performance
     */
    protected void doAccounting(TrafficCounter2 counter) {
        // NOOP by default
    }
    /**
     * Class to implement setReadable at fix time
     *
     */
    private class ReopenRead implements Runnable {
        /**
         * Associated ChannelHandlerContext
         */
        private ChannelHandlerContext ctx = null;
        /**
         * Time to wait before clearing the channel
         */
        private long timeToWait = 0;

        /**
         * @param ctx
         *            the associated channelHandlerContext
         * @param timeToWait
         */
        protected ReopenRead(ChannelHandlerContext ctx, long timeToWait) {
            this.ctx = ctx;
            this.timeToWait = timeToWait;
        }

        /**
         * Truly run the waken up of the channel
         */
        public void run() {
            try {
                Thread.sleep(timeToWait);
            } catch (InterruptedException e) {
                // interruption so exit
                return;
            }
            // logger.info("WAKEUP!");
            if (ctx != null &&
                    ctx.getChannel() != null &&
                    ctx.getChannel().isConnected()) {
                //logger.info(" setReadable TRUE: "+timeToWait);
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
    private long getTimeToWait(long limit, long bytes, long lastTime, long curtime) {
       long interval = curtime - lastTime;
       if (interval == 0) {
           // Time is too short, so just lets continue
           return 0;
       }
       long wait = bytes * 1000 / limit -
               interval;
       return wait;
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
                        trafficCounter.getCurrentReadBytes(), trafficCounter.getLastTime(), curtime);
                if (wait > MINIMAL_WAIT) { // At least 10ms seems a minimal time in order to
                    Channel channel = arg0.getChannel();
                    // try to limit the traffic
                    if (channel != null &&
                            channel.isConnected()) {
                        // Channel version
                        if (executor == null) {
                            // Sleep since no executor
                            //logger.info("Read sleep since no executor for "+wait+" ms for "+this);
                            Thread.sleep(wait);
                            return;
                        }
                        if (arg0.getAttachment() == null) {
                            // readSuspended = true;
                            arg0.setAttachment(Boolean.TRUE);
                            channel.setReadable(false);
                            //logger.info("Read will wakeup after "+wait+" ms "+this);
                            executor.execute(new ReopenRead(arg0, wait));
                        } else {
                            // should be waiting: but can occurs sometime so as a FIX
                            //logger.info("Read sleep ok but should not be here: "+wait+" "+this);
                            Thread.sleep(wait);
                        }
                    } else {
                        // Not connected or no channel
                        //logger.info("Read sleep "+wait+" ms for "+this);
                        Thread.sleep(wait);
                    }
                }            
            }
        } finally {
            // The message is then just passed to the next Codec
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
            }
            if (writeLimit == 0) {
                return;
            }
            // compute the number of ms to wait before continue with the channel
            long wait = getTimeToWait(writeLimit, 
                    trafficCounter.getCurrentWrittenBytes(), trafficCounter.getLastTime(), curtime);
            if (wait > MINIMAL_WAIT) {
                // Global or Channel
                Thread.sleep(wait);
            }
        } finally {
            // The message is then just passed to the next Codec
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
                    // Drop the request silently if PerformanceCounter has
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
    public TrafficCounter2 getTrafficCounter() {
        return trafficCounter;
    }
    /* (non-Javadoc)
     * @see org.jboss.netty.util.ExternalResourceReleasable#releaseExternalResources()
     */
    public void releaseExternalResources() {
        if (trafficCounter != null) {
            trafficCounter.stop();
        }
        ExecutorUtil.terminate(this.executor);
    }
    
    @Override
    public String toString() {
        return "TrafficShaping with Write Limit: "+
            this.writeLimit+" Read Limit: "+
            this.readLimit+" and Counter: "+
            (this.trafficCounter != null ? this.trafficCounter.toString() : "none");
    }
}
