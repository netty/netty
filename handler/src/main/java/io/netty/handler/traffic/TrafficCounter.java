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

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;


/**
 * TrafficCounter is associated with {@link AbstractTrafficShapingHandler}.
 *
 * <p>A <tt>TrafficCounter</tt> counts the read and written bytes such that the
 * {@link AbstractTrafficShapingHandler} can limit the traffic, globally or per channel.</p>
 *
 * <p>It computes the statistics for both read and written every {@link #checkInterval}, and calls
 * back to its parent {@link AbstractTrafficShapingHandler#doAccounting} method.  If the checkInterval
 * is set to 0, no accounting will be done and statistics will only be computed at each receive or
 * write operation.</p>
 */
public class TrafficCounter {
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(TrafficCounter.class);

    /**
     * 
     * @return the time in ms using nanoTime, so not real EPOCH time but elapsed time in ms
     */
    public static final long milliSecondFromNano() {
        return System.nanoTime() / 1000000;
    }
    /**
     * Current written bytes
     */
    private final AtomicLong currentWrittenBytes = new AtomicLong();

    /**
     * Current read bytes
     */
    private final AtomicLong currentReadBytes = new AtomicLong();

    /**
     * Last writing time during current check interval
     */
    private long writingTime;

    /**
     * Last reading delay during current check interval
     */
    private long readingTime;

    /**
     * Long life written bytes
     */
    private final AtomicLong cumulativeWrittenBytes = new AtomicLong();

    /**
     * Long life read bytes
     */
    private final AtomicLong cumulativeReadBytes = new AtomicLong();

    /**
     * Last Time where cumulative bytes where reset to zero
     */
    private long lastCumulativeTime;

    /**
     * Last writing bandwidth
     */
    private long lastWriteThroughput;

    /**
     * Last reading bandwidth
     */
    private long lastReadThroughput;

    /**
     * Last Time Check taken
     */
    private final AtomicLong lastTime = new AtomicLong();

    /**
     * Last written bytes number during last check interval
     */
    private long lastWrittenBytes;

    /**
     * Last read bytes number during last check interval
     */
    private long lastReadBytes;

    /**
     * Last future writing time during last check interval
     */
    private long lastWritingTime;

    /**
     * Last reading time during last check interval
     */
    private long lastReadingTime;

    /**
     * Real written bytes
     */
    private final AtomicLong realWrittenBytes = new AtomicLong();

    /**
     * Real writing bandwidth
     */
    private long realWriteThroughput;

    /**
     * Delay between two captures
     */
    final AtomicLong checkInterval = new AtomicLong(
            AbstractTrafficShapingHandler.DEFAULT_CHECK_INTERVAL);

    // default 1 s

    /**
     * Name of this Monitor
     */
    final String name;

    /**
     * The associated TrafficShapingHandler
     */
    private final AbstractTrafficShapingHandler trafficShapingHandler;

    /**
     * Executor that will run the monitor
     */
    private final ScheduledExecutorService executor;
    /**
     * Monitor created once in start()
     */
    private Runnable monitor;
    /**
     * used in stop() to cancel the timer
     */
    private volatile ScheduledFuture<?> scheduledFuture;

    /**
     * Is Monitor active
     */
    final AtomicBoolean monitorActive = new AtomicBoolean();

    /**
     * Class to implement monitoring at fix delay
     *
     */
    private static class TrafficMonitoringTask implements Runnable {
        /**
         * The associated TrafficShapingHandler
         */
        private final AbstractTrafficShapingHandler trafficShapingHandler1;

        /**
         * The associated TrafficCounter
         */
        private final TrafficCounter counter;

        /**
         * @param trafficShapingHandler The parent handler to which this task needs to callback to for accounting
         * @param counter The parent TrafficCounter that we need to reset the statistics for
         */
        protected TrafficMonitoringTask(
                AbstractTrafficShapingHandler trafficShapingHandler,
                TrafficCounter counter) {
            trafficShapingHandler1 = trafficShapingHandler;
            this.counter = counter;
        }

        @Override
        public void run() {
            if (!counter.monitorActive.get()) {
                return;
            }
            counter.resetAccounting(milliSecondFromNano());
            if (trafficShapingHandler1 != null) {
                trafficShapingHandler1.doAccounting(counter);
            }
            counter.scheduledFuture = counter.executor.schedule(this, counter.checkInterval.get(),
                                                                TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Start the monitoring process
     */
    public synchronized void start() {
        if (monitorActive.get()) {
            return;
        }
        lastTime.set(milliSecondFromNano());
        if (checkInterval.get() > 0) {
            monitorActive.set(true);
            monitor = new TrafficMonitoringTask(trafficShapingHandler, this);
            scheduledFuture =
                executor.schedule(monitor, checkInterval.get(), TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Stop the monitoring process
     */
    public synchronized void stop() {
        if (!monitorActive.get()) {
            return;
        }
        monitorActive.set(false);
        resetAccounting(milliSecondFromNano());
        if (trafficShapingHandler != null) {
            trafficShapingHandler.doAccounting(this);
        }
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }
    }

    /**
     * Reset the accounting on Read and Write
     *
     * @param newLastTime the milliseconds unix timestamp that we should be considered up-to-date for
     */
    synchronized void resetAccounting(long newLastTime) {
        long interval = newLastTime - lastTime.getAndSet(newLastTime);
        if (interval == 0) {
            // nothing to do
            return;
        }
        if (logger.isDebugEnabled() && (interval > 2 * checkInterval())) {
            logger.debug("Acct schedule not ok: " + interval + " > 2*" + checkInterval() + " from " + name);
        }
        lastReadBytes = currentReadBytes.getAndSet(0);
        lastWrittenBytes = currentWrittenBytes.getAndSet(0);
        lastReadThroughput = lastReadBytes * 1000 / interval;
        // nb byte / checkInterval in ms * 1000 (1s)
        lastWriteThroughput = lastWrittenBytes * 1000 / interval;
        // nb byte / checkInterval in ms * 1000 (1s)
        realWriteThroughput = realWrittenBytes.getAndSet(0) * 1000 / interval;
        lastWritingTime = Math.max(lastWritingTime, writingTime);
        lastReadingTime = Math.max(lastReadingTime, readingTime);
    }

    /**
     * Constructor with the {@link AbstractTrafficShapingHandler} that hosts it, the Timer to use, its
     * name, the checkInterval between two computations in millisecond
     * @param trafficShapingHandler the associated AbstractTrafficShapingHandler
     * @param executor the underlying executor service for scheduling checks
     * @param name  the name given to this monitor
     * @param checkInterval the checkInterval in millisecond between two computations
     */
    public TrafficCounter(AbstractTrafficShapingHandler trafficShapingHandler,
                          ScheduledExecutorService executor, String name, long checkInterval) {
        this.trafficShapingHandler = trafficShapingHandler;
        this.executor = executor;
        this.name = name;
        lastCumulativeTime = System.currentTimeMillis();
        writingTime = milliSecondFromNano();
        readingTime = writingTime;
        lastWritingTime = writingTime;
        lastReadingTime = writingTime;
        configure(checkInterval);
    }

    /**
     * Change checkInterval between two computations in millisecond
     *
     * @param newcheckInterval The new check interval (in milliseconds)
     */
    public void configure(long newcheckInterval) {
        long newInterval = newcheckInterval / 10 * 10;
        if (checkInterval.get() != newInterval) {
            checkInterval.set(newInterval);
            if (newInterval <= 0) {
                stop();
                // No more active monitoring
                lastTime.set(milliSecondFromNano());
            } else {
                // Start if necessary
                start();
            }
        }
    }

    /**
     * Computes counters for Read.
     *
     * @param recv
     *            the size in bytes to read
     */
    void bytesRecvFlowControl(long recv) {
        currentReadBytes.addAndGet(recv);
        cumulativeReadBytes.addAndGet(recv);
    }

    /**
     * Computes counters for Write.
     *
     * @param write
     *            the size in bytes to write
     */
    void bytesWriteFlowControl(long write) {
        currentWrittenBytes.addAndGet(write);
        cumulativeWrittenBytes.addAndGet(write);
    }

    /**
     * Computes counters for Real Write.
     *
     * @param write
     *            the size in bytes to write
     * @param schedule
     *            the time when this write was scheduled
     */
    void bytesRealWriteFlowControl(long write) {
        realWrittenBytes.addAndGet(write);
    }

    /**
     *
     * @return the current checkInterval between two computations of traffic counter
     *         in millisecond
     */
    public long checkInterval() {
        return checkInterval.get();
    }

    /**
     *
     * @return the Read Throughput in bytes/s computes in the last check interval
     */
    public long lastReadThroughput() {
        return lastReadThroughput;
    }

    /**
     *
     * @return the Write Throughput in bytes/s computes in the last check interval
     */
    public long lastWriteThroughput() {
        return lastWriteThroughput;
    }

    /**
     *
     * @return the number of bytes read during the last check Interval
     */
    public long lastReadBytes() {
        return lastReadBytes;
    }

    /**
     *
     * @return the number of bytes written during the last check Interval
     */
    public long lastWrittenBytes() {
        return lastWrittenBytes;
    }

    /**
    *
    * @return the current number of bytes read since the last checkInterval
    */
    public long currentReadBytes() {
        return currentReadBytes.get();
    }

    /**
     *
     * @return the current number of bytes written since the last check Interval
     */
    public long currentWrittenBytes() {
        return currentWrittenBytes.get();
    }

    /**
     * @return the Time in millisecond of the last check as of System.currentTimeMillis()
     */
    public long lastTime() {
        return lastTime.get();
    }

    /**
     * @return the cumulativeWrittenBytes
     */
    public long cumulativeWrittenBytes() {
        return cumulativeWrittenBytes.get();
    }

    /**
     * @return the cumulativeReadBytes
     */
    public long cumulativeReadBytes() {
        return cumulativeReadBytes.get();
    }

    /**
     * @return the lastCumulativeTime in millisecond as of System.currentTimeMillis()
     * when the cumulative counters were reset to 0.
     */
    public long lastCumulativeTime() {
        return lastCumulativeTime;
    }

    /**
     * @return the realWrittenBytes
     */
    public AtomicLong getRealWrittenBytes() {
        return realWrittenBytes;
    }

    /**
     * @return the realWriteThroughput
     */
    public long getRealWriteThroughput() {
        return realWriteThroughput;
    }

    /**
     * Reset both read and written cumulative bytes counters and the associated time.
     */
    public void resetCumulativeTime() {
        lastCumulativeTime = System.currentTimeMillis();
        cumulativeReadBytes.set(0);
        cumulativeWrittenBytes.set(0);
    }

    /**
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the time to wait (if any) for the given length message, using the given limitTraffic and the max wait
     * time
     *
     * @param size
     *            the recv size
     * @param limitTraffic
     *            the traffic limit in bytes per second
     * @param maxTime
     *            the max time in ms to wait in case of excess of traffic
     * @param now the current time
     * @return the current time to wait (in ms) if needed for Read operation
     */
    public long readTimeToWait(final long size, final long limitTraffic, final long maxTime, final long now) {
        bytesRecvFlowControl(size);
        if (size == 0 || limitTraffic == 0) {
            return 0;
        }
        final long lastTimeCheck = lastTime.get();
        final long interval = now - lastTimeCheck;
        long pastDelay = Math.max(lastReadingTime - lastTimeCheck, 0);
        long sum = currentReadBytes.get();
        if (interval > AbstractTrafficShapingHandler.MINIMAL_WAIT) {
            // Enough interval time to compute shaping
            long time = sum * 1000 / limitTraffic - interval + pastDelay;
            if (time > AbstractTrafficShapingHandler.MINIMAL_WAIT) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Time: " + time + ":" + sum + ":" + interval + ":" + pastDelay);
                }
                if (time > maxTime && now + time - readingTime > maxTime) {
                    time = maxTime;
                }
                readingTime = Math.max(readingTime, now + time);
                return time;
            }
            readingTime = Math.max(readingTime, now);
            return 0;
        }
        // take the last read interval check to get enough interval time
        long lastsum = sum + lastReadBytes;
        long lastinterval = interval + checkInterval.get();
        long time = lastsum * 1000 / limitTraffic - lastinterval + pastDelay;
        if (time > AbstractTrafficShapingHandler.MINIMAL_WAIT) {
            if (logger.isDebugEnabled()) {
                logger.debug("Time: " + time + ":" + lastsum + ":" + lastinterval + ":" + pastDelay);
            }
            if (time > maxTime && now + time - readingTime > maxTime) {
                time = maxTime;
            }
            readingTime = Math.max(readingTime, now + time);
            return time;
        }
        readingTime = Math.max(readingTime, now);
        return 0;
    }

    /**
     * Returns the time to wait (if any) for the given length message, using the given limitTraffic and
     * the max wait time
     *
     * @param size
     *            the write size
     * @param limitTraffic
     *            the traffic limit in bytes per second
     * @param maxTime
     *            the max time in ms to wait in case of excess of traffic
     * @param now the current time
     * @return the current time to wait (in ms) if needed for Write operation
     */
    public long writeTimeToWait(final long size, final long limitTraffic, final long maxTime, final long now) {
        bytesWriteFlowControl(size);
        if (size == 0 || limitTraffic == 0) {
            return 0;
        }
        final long lastTimeCheck = lastTime.get();
        final long interval = now - lastTimeCheck;
        long pastDelay = Math.max(lastWritingTime - lastTimeCheck, 0);
        long sum = currentWrittenBytes.get();
        if (interval > AbstractTrafficShapingHandler.MINIMAL_WAIT) {
            // Enough interval time to compute shaping
            long time = sum * 1000 / limitTraffic - interval + pastDelay;
            if (time > AbstractTrafficShapingHandler.MINIMAL_WAIT) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Time: " + time + ":" + sum + ":" + interval + ":" + pastDelay);
                }
                if (time > maxTime && now + time - writingTime > maxTime) {
                    time = maxTime;
                }
                writingTime = Math.max(writingTime, now + time);
                return time;
            }
            writingTime = Math.max(writingTime, now);
            return 0;
        }
        // take the last write interval check to get enough interval time
        long lastsum = sum + lastWrittenBytes;
        long lastinterval = interval + checkInterval.get();
        long time = lastsum * 1000 / limitTraffic - lastinterval + pastDelay;
        if (time > AbstractTrafficShapingHandler.MINIMAL_WAIT) {
            if (logger.isDebugEnabled()) {
                logger.debug("Time: " + time + ":" + lastsum + ":" + lastinterval + ":" + pastDelay);
            }
            if (time > maxTime && now + time - writingTime > maxTime) {
                time = maxTime;
            }
            writingTime = Math.max(writingTime, now + time);
            return time;
        }
        writingTime = Math.max(writingTime, now);
        return 0;
    }

    /**
     * String information
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("Monitor ").append(name);
        builder.append(" Current Speed Read: ").append(lastReadThroughput >> 10).append(" KB/s, ");
        builder.append("Asked Write: ").append(lastWriteThroughput >> 10).append(" KB/s, ");
        builder.append("Real Write: ").append(realWriteThroughput >> 10).append(" KB/s, ");
        builder.append("Current Read: ").append(currentReadBytes.get() >> 10).append(" KB, ");
        builder.append("Current asked Write: ").append(currentWrittenBytes.get() >> 10).append(" KB, ");
        builder.append("Current real Write: ").append(realWrittenBytes.get() >> 10).append(" KB");
        return builder.toString();
    }
}
