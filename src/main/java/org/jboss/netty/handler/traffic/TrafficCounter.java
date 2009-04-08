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
    private final AtomicLong cumulativeWrittenBytes = new AtomicLong(0);

    /**
     * Long life reading bytes
     */
    private final AtomicLong cumulativeReadBytes = new AtomicLong(0);

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
    private long lastWrittenBytes = 0;

    /**
     * Last read bytes number
     */
    private long lastReadBytes = 0;

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
            checkInterval1 = checkInterval;
            factory1 = factory;
            this.counter = counter;
        }

        /**
         * Default run
         */
        public void run() {
            try {
                for (;;) {
                    if (checkInterval1 > 0) {
                        Thread.sleep(checkInterval1);
                    } else {
                        // Delay goes to TrafficCounterFactory.NO_STAT, so exit
                        return;
                    }
                    long endTime = System.currentTimeMillis();
                    counter.resetAccounting(endTime);
                    if (factory1 != null) {
                        factory1.accounting(counter);
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
        synchronized (lastTime) {
            if (monitorFuture != null) {
                return;
            }
            lastTime.set(System.currentTimeMillis());
            if (checkInterval > 0) {
                monitorFuture =
                    executorService.submit(new TrafficMonitoring(checkInterval,
                        factory, this));
            }
        }
    }

    /**
     * Stop the monitoring process
     *
     */
    public void stop() {
        synchronized (lastTime) {
            if (monitorFuture == null) {
                return;
            }
            monitorFuture.cancel(true);
            monitorFuture = null;
            resetAccounting(System.currentTimeMillis());
            if (factory != null) {
                factory.accounting(this);
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
        synchronized (lastTime) {
            long interval = newLastTime - lastTime.getAndSet(newLastTime);
            if (interval == 0) {
                // nothing to do
                return;
            }
            lastReadBytes = currentReadingBytes.getAndSet(0);
            lastWrittenBytes = currentWritingBytes.getAndSet(0);
            lastReadingThroughput = lastReadBytes / interval * 1000;
            // nb byte / checkInterval in ms * 1000 (1s)
            lastWritingThroughput = lastWrittenBytes / interval * 1000;
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
    void setMonitoredChannel(Channel channel) {
        if (channel != null) {
            monitoredChannel = channel;
            isPerChannel = true;
        } else {
            isPerChannel = false;
            monitoredChannel = null;
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
        limitWrite = writeLimit;
        limitRead = readLimit;
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
        if (checkInterval != delayToSet) {
            checkInterval = delayToSet;
            if (monitorFuture == null) {
                this.configure(channel, writeLimit, readLimit);
                return;
            }
            stop();
            if (checkInterval > 0) {
                start();
            } else {
                // No more active monitoring
                lastTime.set(System.currentTimeMillis());
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
        synchronized (lastTime) {
            long interval = System.currentTimeMillis() - lastTime.get();
            if (interval == 0) {
                // Time is too short, so just lets continue
                return 0;
            }
            long wait = currentReadingBytes.get() * 1000 / limitRead -
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
        synchronized (lastTime) {
            long interval = System.currentTimeMillis() - lastTime.get();
            if (interval == 0) {
                // Time is too short, so just lets continue
                return 0;
            }
            long wait = currentWritingBytes.get() * 1000 /
                limitWrite - interval;
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
                Thread.sleep(timeToWait);
            } catch (InterruptedException e) {
                // interruption so exit
                return;
            }
            // logger.info("WAKEUP!");
            if (monitor != null &&
                    monitor.monitoredChannel != null &&
                    monitor.monitoredChannel.isConnected()) {
                // logger.warn(" setReadable TRUE: "+timeToWait);
                if (ctx.getHandler() instanceof TrafficShapingHandler) {
                    // readSuspended = false;
                    ctx.setAttachment(null);
                }
                monitor.monitoredChannel.setReadable(true);
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
        currentReadingBytes.addAndGet(recv);
        cumulativeReadBytes.addAndGet(recv);
        if (limitRead == 0) {
            // no action
            return;
        }
        if (isPerChannel && monitoredChannel != null &&
                !monitoredChannel.isConnected()) {
            // no action can be taken since setReadable will throw a
            // NotYetConnected
            return;
        }
        // compute the number of ms to wait before reopening the channel
        long wait = getReadTimeToWait();
        if (wait > 20) { // At least 20ms seems a minimal time in order to
            // try to limit the traffic
            if (isPerChannel && monitoredChannel != null &&
                    monitoredChannel.isConnected()) {
                // Channel version
                if (executorService == null) {
                    // Sleep since no executor
                    Thread.sleep(wait);
                    return;
                }
                if (ctx.getAttachment() == null) {
                    if (ctx.getHandler() instanceof TrafficShapingHandler) {
                        // readSuspended = true;
                        ctx.setAttachment(Boolean.TRUE);
                    }
                    monitoredChannel.setReadable(false);
                    // logger.info("Read will wakeup after "+wait+" ms "+this);
                    executorService
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
        currentWritingBytes.addAndGet(write);
        cumulativeWrittenBytes.addAndGet(write);
        if (limitWrite == 0) {
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
        return checkInterval;
    }

    /**
     *
     * @return the current Read Throughput in byte/s
     */
    public long getLastReadThroughput() {
        return lastReadingThroughput;
    }

    /**
     *
     * @return the current Write Throughput in byte/s
     */
    public long getLastWriteThroughput() {
        return lastWritingThroughput;
    }

    /**
     *
     * @return the current number of byte read since last checkInterval
     */
    public long getLastReadBytes() {
        return lastReadBytes;
    }

    /**
     *
     * @return the current number of byte written since last checkInterval
     */
    public long getLastWrittenBytes() {
        return lastWrittenBytes;
    }

    /**
     * @return the cumulativeWritingBytes
     */
    public long getCumulativeWrittenBytes() {
        return cumulativeWrittenBytes.get();
    }

    /**
     * @return the cumulativeReadingBytes
     */
    public long getCumulativeReadBytes() {
        return cumulativeReadBytes.get();
    }

    /**
     * String information
     */
    @Override
    public String toString() {
        return "Monitor " + name + " Current Speed Read: " +
                (lastReadingThroughput >> 10) + " KB/s, Write: " +
                (lastWritingThroughput >> 10) + " KB/s Current Read: " +
                (currentReadingBytes.get() >> 10) + " KB Current Write: " +
                (currentWritingBytes.get() >> 10) + " KB";
    }

}
