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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Frederic Bregier (fredbregier@free.fr)
 * @version $Rev$, $Date$
 *
 * TrafficCounter is associated with {@link TrafficShapingHandler} and
 * should be created through a {@link TrafficCounterFactory}.<br>
 * <br>
 * A TrafficCounter can limit the traffic or not, globally or per channel,
 * and always compute statistics on read and written bytes at the specified
 * interval.
 *
 */
public class TrafficCounter {
    /**
     * Internal logger
     */
    private static InternalLogger logger = InternalLoggerFactory
            .getInstance(TrafficCounter.class);

    /**
     * Current writing bytes
     */
    private final AtomicLong currentWritingBytes = new AtomicLong(0);

    /**
     * Current reading bytes
     */
    private final AtomicLong currentReadingBytes = new AtomicLong(0);

    /**
     * Long life writing bytes
     */
    private final AtomicLong cumulativeWritingBytes = new AtomicLong(0);

    /**
     * Long life reading bytes
     */
    private final AtomicLong cumulativeReadingBytes = new AtomicLong(0);

    /**
     * Last writing bandwidth
     */
    private long lastWritingThroughput = 0;

    /**
     * Last reading bandwidth
     */
    private long lastReadingThroughput = 0;

    /**
     * Last Time Check taken
     */
    private final AtomicLong lastTime = new AtomicLong(0);

    /**
     * Last written bytes number
     */
    private long lastWritingBytes = 0;

    /**
     * Last read bytes number
     */
    private long lastReadingBytes = 0;

    /**
     * Current Limit in B/s to apply to write
     */
    private long limitWrite = 0;

    /**
     * Current Limit in B/s to apply to read
     */
    private long limitRead = 0;

    /**
     * Delay between two capture
     */
    private long checkInterval = TrafficCounterFactory.DEFAULT_DELAY;

    // default 1 s

    /**
     * Name of this Monitor
     */
    private final String name;

    /**
     * Is this monitor for a channel monitoring or for global monitoring
     */
    private boolean isPerChannel = false;

    /**
     * Associated monitoredChannel if any (global MUST NOT have any)
     */
    protected Channel monitoredChannel = null;

    /**
     * The associated TrafficCounterFactory
     */
    private TrafficCounterFactory factory = null;

    /**
     * Default ExecutorService
     */
    private ExecutorService executorService = null;

    /**
     * Thread that will host this monitor
     */
    private Future<?> monitorFuture = null;

    /**
     * Class to implement monitoring at fix delay
     *
     */
    private class TrafficMonitoring implements Runnable {
        /**
         * Delay between two capture
         */
        private final long checkInterval1;
        /**
         * The associated TrafficCounterFactory
         */
        private final TrafficCounterFactory factory1;
        /**
         * The associated TrafficCounter
         */
        private final TrafficCounter counter;
        
        /**
         * @param checkInterval
         * @param factory
         * @param counter
         */
        protected TrafficMonitoring(long checkInterval,
                TrafficCounterFactory factory, TrafficCounter counter) {
            this.checkInterval1 = checkInterval;
            this.factory1 = factory;
            this.counter = counter;
        }

        /**
         * Default run
         */
        public void run() {
            try {
                for (;;) {
                    if (this.checkInterval1 > 0) {
                        Thread.sleep(this.checkInterval1);
                    } else {
                        // Delay goes to TrafficCounterFactory.NO_STAT, so exit
                        return;
                    }
                    long endTime = System.currentTimeMillis();
                    this.counter.resetAccounting(endTime);
                    if (this.factory1 != null) {
                        this.factory1.accounting(this.counter);
                    }
                }
            } catch (InterruptedException e) {
                // End of computations
            }
        }        
    }
    /**
     * Start the monitoring process
     *
     */
    public void start() {
        synchronized (this.lastTime) {
            if (this.monitorFuture != null) {
                return;
            }
            this.lastTime.set(System.currentTimeMillis());
            if (this.checkInterval > 0) {
                this.monitorFuture = 
                    this.executorService.submit(new TrafficMonitoring(this.checkInterval,
                        this.factory, this));
            }
        }
    }

    /**
     * Stop the monitoring process
     *
     */
    public void stop() {
        synchronized (this.lastTime) {
            if (this.monitorFuture == null) {
                return;
            }
            this.monitorFuture.cancel(true);
            this.monitorFuture = null;
            resetAccounting(System.currentTimeMillis());
            if (this.factory != null) {
                this.factory.accounting(this);
            }
            setMonitoredChannel(null);
        }
    }

    /**
     * Set the accounting on Read and Write
     *
     * @param newLastTime
     */
    protected void resetAccounting(long newLastTime) {
        synchronized (this.lastTime) {
            long interval = newLastTime - this.lastTime.getAndSet(newLastTime);
            if (interval == 0) {
                // nothing to do
                return;
            }
            this.lastReadingBytes = this.currentReadingBytes.getAndSet(0);
            this.lastWritingBytes = this.currentWritingBytes.getAndSet(0);
            this.lastReadingThroughput = this.lastReadingBytes / interval * 1000;
            // nb byte / checkInterval in ms * 1000 (1s)
            this.lastWritingThroughput = this.lastWritingBytes / interval * 1000;
            // nb byte / checkInterval in ms * 1000 (1s)
        }
    }

    /**
     * Constructor with the executorService to use, the channel if any, its
     * name, the limits in Byte/s (not Bit/s) and the checkInterval between two
     * computations in ms
     *
     * @param factory
     *            the associated TrafficCounterFactory
     * @param executorService
     *            Should be a CachedThreadPool for efficiency
     * @param channel
     *            Not null means this monitors will be for this channel only,
     *            else it will be for global monitoring. Channel can be set
     *            later on therefore changing its behavior from global to per
     *            channel
     * @param name
     *            the name given to this monitor
     * @param writeLimit
     *            the write limit in Byte/s
     * @param readLimit
     *            the read limit in Byte/s
     * @param checkInterval
     *            the checkInterval in ms between two computations
     */
    public TrafficCounter(TrafficCounterFactory factory,
            ExecutorService executorService, Channel channel, String name,
            long writeLimit, long readLimit, long checkInterval) {
        this.factory = factory;
        this.executorService = executorService;
        this.name = name;
        this.configure(channel, writeLimit, readLimit, checkInterval);
    }

    /**
     * Set the Session monitoredChannel (not for Global Monitor)
     *
     * @param channel
     *            Not null means this monitors will be for this channel only,
     *            else it will be for global monitoring. Channel can be set
     *            later on therefore changing its behavior from global to per
     *            channel
     */
    protected void setMonitoredChannel(Channel channel) {
        if (channel != null) {
            this.monitoredChannel = channel;
            this.isPerChannel = true;
        } else {
            this.isPerChannel = false;
            this.monitoredChannel = null;
        }
    }

    /**
     * Specifies limits in Byte/s (not Bit/s) but do not changed the checkInterval
     *
     * @param channel
     *            Not null means this monitors will be for this channel only,
     *            else it will be for global monitoring. Channel can be set
     *            later on therefore changing its behavior from global to per
     *            channel
     * @param writeLimit
     * @param readLimit
     */
    public void configure(Channel channel, long writeLimit,
            long readLimit) {
        this.limitWrite = writeLimit;
        this.limitRead = readLimit;
        setMonitoredChannel(channel);
    }

    /**
     * Specifies limits in Byte/s (not Bit/s) and the specified checkInterval between
     * two computations in ms
     *
     * @param channel
     *            Not null means this monitors will be for this channel only,
     *            else it will be for global monitoring. Channel can be set
     *            later on therefore changing its behavior from global to per
     *            channel
     * @param writeLimit
     * @param readLimit
     * @param delayToSet
     */
    public void configure(Channel channel, long writeLimit,
            long readLimit, long delayToSet) {
        if (this.checkInterval != delayToSet) {
            this.checkInterval = delayToSet;
            if (this.monitorFuture == null) {
                this.configure(channel, writeLimit, readLimit);
                return;
            }
            stop();
            if (this.checkInterval > 0) {
                start();
            } else {
                // No more active monitoring
                this.lastTime.set(System.currentTimeMillis());
            }
        }
        this.configure(channel, writeLimit, readLimit);
    }

    /**
     *
     * @return the time that should be necessary to wait to respect limit. Can
     *         be negative time
     */
    private long getReadTimeToWait() {
        synchronized (this.lastTime) {
            long interval = System.currentTimeMillis() - this.lastTime.get();
            if (interval == 0) {
                // Time is too short, so just lets continue
                return 0;
            }
            long wait = this.currentReadingBytes.get() * 1000 / this.limitRead -
                    interval;
            return wait;
        }
    }

    /**
     *
     * @return the time that should be necessary to wait to respect limit. Can
     *         be negative time
     */
    private long getWriteTimeToWait() {
        synchronized (this.lastTime) {
            long interval = System.currentTimeMillis() - this.lastTime.get();
            if (interval == 0) {
                // Time is too short, so just lets continue
                return 0;
            }
            long wait = this.currentWritingBytes.get() * 1000 /
                this.limitWrite - interval;
            return wait;
        }
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
         * Monitor
         */
        private TrafficCounter monitor = null;

        /**
         * Time to wait before clearing the channel
         */
        private long timeToWait = 0;

        /**
         * @param monitor
         * @param ctx
         *            the associated channelHandlerContext
         * @param timeToWait
         */
        protected ReopenRead(ChannelHandlerContext ctx,
                TrafficCounter monitor, long timeToWait) {
            this.ctx = ctx;
            this.monitor = monitor;
            this.timeToWait = timeToWait;
        }

        /**
         * Truly run the waken up of the channel
         */
        public void run() {
            try {
                Thread.sleep(this.timeToWait);
            } catch (InterruptedException e) {
                // interruption so exit
                return;
            }
            // logger.info("WAKEUP!");
            if ((this.monitor != null) &&
                    (this.monitor.monitoredChannel != null) &&
                    this.monitor.monitoredChannel.isConnected()) {
                // logger.warn(" setReadable TRUE: "+timeToWait);
                if (this.ctx.getHandler() instanceof TrafficShapingHandler) {
                    // readSuspended = false;
                    this.ctx.setAttachment(null);
                }
                this.monitor.monitoredChannel.setReadable(true);
            }
        }
    }

    /**
     * If Read is in excess, it will block the read on channel or block until it
     * will be ready again.
     *
     * @param ctx
     *            the associated channelHandlerContext
     * @param recv
     *            the size in bytes to read
     * @throws InterruptedException
     */
    protected void bytesRecvFlowControl(ChannelHandlerContext ctx, long recv)
            throws InterruptedException {
        this.currentReadingBytes.addAndGet(recv);
        this.cumulativeReadingBytes.addAndGet(recv);
        if (this.limitRead == 0) {
            // no action
            return;
        }
        if (this.isPerChannel && (this.monitoredChannel != null) &&
                (!this.monitoredChannel.isConnected())) {
            // no action can be taken since setReadable will throw a
            // NotYetConnected
            return;
        }
        // compute the number of ms to wait before reopening the channel
        long wait = getReadTimeToWait();
        if (wait > 20) { // At least 20ms seems a minimal time in order to
            // try to limit the traffic
            if (this.isPerChannel && (this.monitoredChannel != null) &&
                    this.monitoredChannel.isConnected()) {
                // Channel version
                if (this.executorService == null) {
                    // Sleep since no executor
                    Thread.sleep(wait);
                    return;
                }
                if (ctx.getAttachment() == null) {
                    if (ctx.getHandler() instanceof TrafficShapingHandler) {
                        // readSuspended = true;
                        ctx.setAttachment(Boolean.TRUE);
                    }
                    this.monitoredChannel.setReadable(false);
                    // logger.info("Read will wakeup after "+wait+" ms "+this);
                    this.executorService
                            .submit(new ReopenRead(ctx, this, wait));
                } else {
                    // should be waiting: but can occurs sometime so as a FIX
                    logger.info("Read sleep ok but should not be here");
                    Thread.sleep(wait);
                }
            } else {
                // Global version
                // logger.info("Read sleep "+wait+" ms for "+this);
                Thread.sleep(wait);
            }
        }
    }

    /**
     * If Write is in excess, it will block the write operation until it will be
     * ready again.
     *
     * @param write
     *            the size in bytes to write
     * @throws InterruptedException
     */
    protected void bytesWriteFlowControl(long write) throws InterruptedException {
        this.currentWritingBytes.addAndGet(write);
        this.cumulativeWritingBytes.addAndGet(write);
        if (this.limitWrite == 0) {
            return;
        }
        // compute the number of ms to wait before continue with the channel
        long wait = getWriteTimeToWait();
        if (wait > 20) {
            // Global or Session
            Thread.sleep(wait);
        }
    }

    /**
     *
     * @return the current checkInterval between two computations of performance counter
     *         in ms
     */
    public long getCheckInterval() {
        return this.checkInterval;
    }

    /**
     *
     * @return the current Read Throughput in byte/s
     */
    public long getLastReadThroughput() {
        return this.lastReadingThroughput;
    }

    /**
     *
     * @return the current Write Throughput in byte/s
     */
    public long getLastWriteThroughput() {
        return this.lastWritingThroughput;
    }

    /**
     *
     * @return the current number of byte read since last checkInterval
     */
    public long getLastBytesRead() {
        return this.lastReadingBytes;
    }

    /**
     *
     * @return the current number of byte written since last checkInterval
     */
    public long getLastBytesWritten() {
        return this.lastWritingBytes;
    }

    /**
     * @return the cumulativeWritingBytes
     */
    public long getCumulativeWritingBytes() {
        return this.cumulativeWritingBytes.get();
    }

    /**
     * @return the cumulativeReadingBytes
     */
    public long getCumulativeReadingBytes() {
        return this.cumulativeReadingBytes.get();
    }

    /**
     * String information
     */
    @Override
    public String toString() {
        return "Monitor " + this.name + " Current Speed Read: " +
                (this.lastReadingThroughput >> 10) + " KB/s, Write: " +
                (this.lastWritingThroughput >> 10) + " KB/s Current Read: " +
                (this.currentReadingBytes.get() >> 10) + " KB Current Write: " +
                (this.currentWritingBytes.get() >> 10) + " KB";
    }

}
