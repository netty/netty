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

import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <p>TrafficCounter is associated with {@link AbstractTrafficShapingHandler}.</p>
 *
 * A TrafficCounter has for goal to count the traffic in order to enable to limit the traffic or not,
 * globally or per channel. It compute statistics on read and written bytes at the specified
 * interval and call back the {@link AbstractTrafficShapingHandler} doAccounting method at every
 * specified interval. If this interval is set to 0, therefore no accounting will be done and only
 * statistics will be computed at each receive or write operations.
 */
public class TrafficCounter {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(TrafficCounter.class);

    /**
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
     * Last Time where cumulative bytes where reset to zero: this time is a real EPOC time (informative only)
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
    final AtomicLong lastTime = new AtomicLong();

    /**
     * Last written bytes number during last check interval
     */
    private volatile long lastWrittenBytes;

    /**
     * Last read bytes number during last check interval
     */
    private volatile long lastReadBytes;

    /**
     * Last future writing time during last check interval
     */
    private volatile long lastWritingTime;

    /**
     * Last reading time during last check interval
     */
    private volatile long lastReadingTime;

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
    final AbstractTrafficShapingHandler trafficShapingHandler;

    /**
     * One Timer for all Counter
     */
    final Timer timer;  // replace executor

    /**
     * Monitor created once in start()
     */
    TimerTask timerTask;

    /**
     * used in stop() to cancel the timer
     */
    volatile Timeout timeout;

    /**
     * Is Monitor active
     */
    volatile boolean monitorActive;

    /**
     * Class to implement monitoring at fix delay
     *
     */
    private static class TrafficMonitoringTask implements TimerTask {
        /**
         * The associated TrafficShapingHandler
         */
        private final AbstractTrafficShapingHandler trafficShapingHandler1;

        /**
         * The associated TrafficCounter
         */
        private final TrafficCounter counter;

        protected TrafficMonitoringTask(
                AbstractTrafficShapingHandler trafficShapingHandler,
                TrafficCounter counter) {
            trafficShapingHandler1 = trafficShapingHandler;
            this.counter = counter;
        }

        public void run(Timeout timeout) throws Exception {
            if (!counter.monitorActive) {
                return;
            }
            counter.resetAccounting(milliSecondFromNano());
            if (trafficShapingHandler1 != null) {
                trafficShapingHandler1.doAccounting(counter);
            }

            counter.timer.newTimeout(this, counter.checkInterval.get(), TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Start the monitoring process
     */
    public void start() {
        if (monitorActive) {
            return;
        }
        lastTime.set(milliSecondFromNano());
        // if executor is null, it means it is piloted by a GlobalChannelTrafficCounter, so no executor
        if (checkInterval.get() > 0 && timer != null) {
            monitorActive = true;
            timerTask = new TrafficMonitoringTask(trafficShapingHandler, this);
            timeout =
                timer.newTimeout(timerTask, checkInterval.get(), TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Stop the monitoring process
     */
    public void stop() {
        if (!monitorActive) {
            return;
        }
        monitorActive = false;
        resetAccounting(milliSecondFromNano());
        if (trafficShapingHandler != null) {
            trafficShapingHandler.doAccounting(this);
        }
        if (timeout != null) {
            timeout.cancel();
        }
    }

    /**
     * Reset the accounting on Read and Write
     */
    void resetAccounting(long newLastTime) {
        long interval = newLastTime - lastTime.getAndSet(newLastTime);
        if (interval == 0) {
            // nothing to do
            return;
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
     * @param timer
     *            Could be a HashedWheelTimer, might be null when used
     *              from {@link GlobalChannelTrafficCounter}
     * @param name
     *            the name given to this monitor
     * @param checkInterval
     *            the checkInterval in millisecond between two computations
     */
    public TrafficCounter(AbstractTrafficShapingHandler trafficShapingHandler,
            Timer timer, String name, long checkInterval) {
        if (trafficShapingHandler == null) {
            throw new IllegalArgumentException("TrafficShapingHandler must not be null");
        }
        this.trafficShapingHandler = trafficShapingHandler;
        this.timer = timer;
        this.name = name;
        // absolute time: informative only
        lastCumulativeTime = System.currentTimeMillis();
        writingTime = milliSecondFromNano();
        readingTime = writingTime;
        lastWritingTime = writingTime;
        lastReadingTime = writingTime;
        configure(checkInterval);
    }

    /**
     * Change checkInterval between
     * two computations in millisecond
     */
    public void configure(long newcheckInterval) {
        long newInterval = newcheckInterval / 10 * 10;
        if (checkInterval.getAndSet(newInterval) != newInterval) {
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
     * @return the current checkInterval between two computations of traffic counter
     *         in millisecond
     */
    public long getCheckInterval() {
        return checkInterval.get();
    }

    /**
     * @return the Read Throughput in bytes/s computes in the last check interval
     */
    public long getLastReadThroughput() {
        return lastReadThroughput;
    }

    /**
     * @return the Write Throughput in bytes/s computes in the last check interval
     */
    public long getLastWriteThroughput() {
        return lastWriteThroughput;
    }

    /**
     * @return the number of bytes read during the last check Interval
     */
    public long getLastReadBytes() {
        return lastReadBytes;
    }

    /**
     * @return the number of bytes written during the last check Interval
     */
    public long getLastWrittenBytes() {
        return lastWrittenBytes;
    }

    /**
    * @return the current number of bytes read since the last checkInterval
    */
    public long getCurrentReadBytes() {
        return currentReadBytes.get();
    }

    /**
     * @return the current number of bytes written since the last check Interval
     */
    public long getCurrentWrittenBytes() {
        return currentWrittenBytes.get();
    }

    /**
     * @return the Time in millisecond of the last check as of System.currentTimeMillis()
     */
    public long getLastTime() {
        return lastTime.get();
    }

    /**
     * @return the cumulativeWrittenBytes
     */
    public long getCumulativeWrittenBytes() {
        return cumulativeWrittenBytes.get();
    }

    /**
     * @return the cumulativeReadBytes
     */
    public long getCumulativeReadBytes() {
        return cumulativeReadBytes.get();
    }

    /**
     * @return the lastCumulativeTime in millisecond as of System.currentTimeMillis()
     * when the cumulative counters were reset to 0.
     */
    public long getLastCumulativeTime() {
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
     * Reset both read and written cumulative bytes counters and the associated absolute time
     * from System.currentTimeMillis().
     */
    public void resetCumulativeTime() {
        lastCumulativeTime = System.currentTimeMillis();
        cumulativeReadBytes.set(0);
        cumulativeWrittenBytes.set(0);
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
     * @return the current time to wait (in ms) if needed for Read operation
     */
    @Deprecated
    public long readTimeToWait(final long size, final long limitTraffic, final long maxTime) {
        return readTimeToWait(size, limitTraffic, maxTime, milliSecondFromNano());
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
        long sum = currentReadBytes.get();
        long localReadingTime = readingTime;
        long lastRB = lastReadBytes;
        final long interval = now - lastTimeCheck;
        long pastDelay = Math.max(lastReadingTime - lastTimeCheck, 0);
        if (interval > AbstractTrafficShapingHandler.MINIMAL_WAIT) {
            // Enough interval time to compute shaping
            long time = (sum * 1000 / limitTraffic - interval + pastDelay) / 10 * 10;
            if (time > AbstractTrafficShapingHandler.MINIMAL_WAIT) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Time: " + time + ":" + sum + ":" + interval + ":" + pastDelay);
                }
                if (time > maxTime && now + time - localReadingTime > maxTime) {
                    time = maxTime;
                }
                readingTime = Math.max(localReadingTime, now + time);
                return time;
            }
            readingTime = Math.max(localReadingTime, now);
            return 0;
        }
        // take the last read interval check to get enough interval time
        long lastsum = sum + lastRB;
        long lastinterval = interval + checkInterval.get();
        long time = (lastsum * 1000 / limitTraffic - lastinterval + pastDelay) / 10 * 10;
        if (time > AbstractTrafficShapingHandler.MINIMAL_WAIT) {
            if (logger.isDebugEnabled()) {
                logger.debug("Time: " + time + ":" + lastsum + ":" + lastinterval + ":" + pastDelay);
            }
            if (time > maxTime && now + time - localReadingTime > maxTime) {
                time = maxTime;
            }
            readingTime = Math.max(localReadingTime, now + time);
            return time;
        }
        readingTime = Math.max(localReadingTime, now);
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
     * @return the current time to wait (in ms) if needed for Write operation
     */
    @Deprecated
    public long writeTimeToWait(final long size, final long limitTraffic, final long maxTime) {
        return writeTimeToWait(size, limitTraffic, maxTime, milliSecondFromNano());
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
        long sum = currentWrittenBytes.get();
        long lastWB = lastWrittenBytes;
        long localWritingTime = writingTime;
        long pastDelay = Math.max(lastWritingTime - lastTimeCheck, 0);
        final long interval = now - lastTimeCheck;
        if (interval > AbstractTrafficShapingHandler.MINIMAL_WAIT) {
            // Enough interval time to compute shaping
            long time = (sum * 1000 / limitTraffic - interval + pastDelay) / 10 * 10;
            if (time > AbstractTrafficShapingHandler.MINIMAL_WAIT) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Time: " + time + ":" + sum + ":" + interval + ":" + pastDelay);
                }
                if (time > maxTime && now + time - localWritingTime > maxTime) {
                    time = maxTime;
                }
                writingTime = Math.max(localWritingTime, now + time);
                return time;
            }
            writingTime = Math.max(localWritingTime, now);
            return 0;
        }
        // take the last write interval check to get enough interval time
        long lastsum = sum + lastWB;
        long lastinterval = interval + checkInterval.get();
        long time = (lastsum * 1000 / limitTraffic - lastinterval + pastDelay) / 10 * 10;
        if (time > AbstractTrafficShapingHandler.MINIMAL_WAIT) {
            if (logger.isDebugEnabled()) {
                logger.debug("Time: " + time + ":" + lastsum + ":" + lastinterval + ":" + pastDelay);
            }
            if (time > maxTime && now + time - localWritingTime > maxTime) {
                time = maxTime;
            }
            writingTime = Math.max(localWritingTime, now + time);
            return time;
        }
        writingTime = Math.max(localWritingTime, now);
        return 0;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * String information
     */
    @Override
    public String toString() {
        return new StringBuilder("Monitor ").append(name)
                .append(" Current Speed Read: ").append(lastReadThroughput >> 10).append(" KB/s, ")
                .append("Asked Write: ").append(lastWriteThroughput >> 10).append(" KB/s, ")
                .append("Real Write: ").append(realWriteThroughput >> 10).append(" KB/s, ")
                .append("Current Read: ").append(currentReadBytes.get() >> 10).append(" KB, ")
                .append("Current asked Write: ").append(currentWrittenBytes.get() >> 10).append(" KB, ")
                .append("Current real Write: ").append(realWrittenBytes.get() >> 10).append(" KB").toString();
    }
}
