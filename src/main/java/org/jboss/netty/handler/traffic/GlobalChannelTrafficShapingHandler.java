/*
 * Copyright 2014 The Netty Project
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
package org.jboss.netty.handler.traffic;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.ObjectSizeEstimator;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;
import org.jboss.netty.util.internal.ConcurrentHashMap;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This implementation of the {@link AbstractTrafficShapingHandler} is for global
 * and per channel traffic shaping, that is to say a global limitation of the bandwidth, whatever
 * the number of opened channels and a per channel limitation of the bandwidth.<br><br>
 * This version shall not be in the same pipeline than other TrafficShapingHandler.<br><br>
 *
 * The general use should be as follow:<br>
 * <ul>
 * <li>Create your unique GlobalChannelTrafficShapingHandler like:<br><br>
 * <tt>GlobalChannelTrafficShapingHandler myHandler = new GlobalChannelTrafficShapingHandler(executor);</tt><br><br>
 * The executor could be the underlying IO worker pool<br>
 * <tt>pipeline.addLast(myHandler);</tt><br><br>
 *
 * <b>Note that this handler has a Pipeline Coverage of "all" which means only one such handler must be created
 * and shared among all channels as the counter must be shared among all channels.</b><br><br>
 *
 * Other arguments can be passed like write or read limitation (in bytes/s where 0 means no limitation)
 * or the check interval (in millisecond) that represents the delay between two computations of the
 * bandwidth and so the call back of the doAccounting method (0 means no accounting at all).<br>
 * Note that as this is a fusion of both Global and Channel Traffic Shaping, limits are in 2 sets,
 * respectively Global and Channel.<br><br>
 *
 * A value of 0 means no accounting for checkInterval. If you need traffic shaping but no such accounting,
 * it is recommended to set a positive value, even if it is high since the precision of the
 * Traffic Shaping depends on the period where the traffic is computed. The highest the interval,
 * the less precise the traffic shaping will be. It is suggested as higher value something close
 * to 5 or 10 minutes.<br><br>
 *
 * maxTimeToWait, by default set to 15s, allows to specify an upper bound of time shaping.<br><br>
 * </li>
 * <li>In your handler, you should consider to use the {@code channel.isWritable()} and
 * {@code channelWritabilityChanged(ctx)} to handle writability, or through
 * {@code future.addListener(new GenericFutureListener())} on the future returned by
 * {@code ctx.write()}.</li>
 * <li>You shall also consider to have object size in read or write operations relatively adapted to
 * the bandwidth you required: for instance having 10 MB objects for 10KB/s will lead to burst effect,
 * while having 100 KB objects for 1 MB/s should be smoothly handle by this TrafficShaping handler.<br><br></li>
 * <li>Some configuration methods will be taken as best effort, meaning
 * that all already scheduled traffics will not be
 * changed, but only applied to new traffics.<br>
 * So the expected usage of those methods are to be used not too often,
 * accordingly to the traffic shaping configuration.</li>
 * </ul><br>
 *
 * Be sure to call {@link #release()} once this handler is not needed anymore to release all internal resources.
 * This will not shutdown the {@link Timer} as it may be shared, so you need to do this by your own.
 */
@Sharable
public class GlobalChannelTrafficShapingHandler extends AbstractTrafficShapingHandler {
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(GlobalChannelTrafficShapingHandler.class);
    /**
     * All queues per channel
     */
    final ConcurrentMap<Integer, PerChannel> channelQueues = new ConcurrentHashMap<Integer, PerChannel>();

    /**
     * Global queues size
     */
    private final AtomicLong queuesSize = new AtomicLong();

    /**
     * Maximum cumulative writing bytes for one channel among all (as long as channels stay the same)
     */
    private final AtomicLong cumulativeWrittenBytes = new AtomicLong();

    /**
     * Maximum cumulative read bytes for one channel among all (as long as channels stay the same)
     */
    private final AtomicLong cumulativeReadBytes = new AtomicLong();

    /**
     * Max size in the list before proposing to stop writing new objects from next handlers
     * for all channel (global)
     */
    long maxGlobalWriteSize = DEFAULT_MAX_SIZE * 100; // default 400MB

    /**
     * Limit in B/s to apply to write
     */
    private volatile long writeChannelLimit;

    /**
     * Limit in B/s to apply to read
     */
    private volatile long readChannelLimit;

    private static final float DEFAULT_DEVIATION = 0.1F;
    private static final float MAX_DEVIATION = 0.4F;
    private static final float DEFAULT_SLOWDOWN = 0.4F;
    private static final float DEFAULT_ACCELERATION = -0.1F;
    private volatile float maxDeviation;
    private volatile float accelerationFactor;
    private volatile float slowDownFactor;
    private volatile boolean readDeviationActive;
    private volatile boolean writeDeviationActive;

    static final class PerChannel {
        List<ToSend> messagesQueue;
        TrafficCounter channelTrafficCounter;
        long queueSize;
        long lastWriteTimestamp;
        long lastReadTimestamp;
    }

    /**
     * Create the global TrafficCounter.
     */
    void createGlobalTrafficCounter(Timer timer) {
        // Default
        setMaxDeviation(DEFAULT_DEVIATION, DEFAULT_SLOWDOWN, DEFAULT_ACCELERATION);
        if (timer == null) {
            throw new IllegalArgumentException("Timer must not be null");
        }
        TrafficCounter tc = new GlobalChannelTrafficCounter(this, timer, "GlobalChannelTC", checkInterval);
        setTrafficCounter(tc);
        tc.start();
    }

    @Override
    int userDefinedWritabilityIndex() {
        return AbstractTrafficShapingHandler.GLOBALCHANNEL_DEFAULT_USER_DEFINED_WRITABILITY_INDEX;
    }

    /**
     * Create a new instance.
     *
     * @param timer
     *            the {@link Timer} to use for the {@link TrafficCounter}.
     * @param writeGlobalLimit
     *            0 or a limit in bytes/s
     * @param readGlobalLimit
     *            0 or a limit in bytes/s
     * @param writeChannelLimit
     *            0 or a limit in bytes/s
     * @param readChannelLimit
     *            0 or a limit in bytes/s
     * @param checkInterval
     *            The delay between two computations of performances for
     *            channels or 0 if no stats are to be computed.
     * @param maxTime
     *            The maximum delay to wait in case of traffic excess.
     */
    public GlobalChannelTrafficShapingHandler(Timer timer,
            long writeGlobalLimit, long readGlobalLimit,
            long writeChannelLimit, long readChannelLimit,
            long checkInterval, long maxTime) {
        super(timer, writeGlobalLimit, readGlobalLimit, checkInterval, maxTime);
        createGlobalTrafficCounter(timer);
        this.writeChannelLimit = writeChannelLimit;
        this.readChannelLimit = readChannelLimit;
    }

    /**
     * Create a new instance.
     *
     * @param timer
     *          the {@link Timer} to use for the {@link TrafficCounter}.
     * @param writeGlobalLimit
     *            0 or a limit in bytes/s
     * @param readGlobalLimit
     *            0 or a limit in bytes/s
     * @param writeChannelLimit
     *            0 or a limit in bytes/s
     * @param readChannelLimit
     *            0 or a limit in bytes/s
     * @param checkInterval
     *          The delay between two computations of performances for
     *            channels or 0 if no stats are to be computed.
     */
    public GlobalChannelTrafficShapingHandler(Timer timer,
            long writeGlobalLimit, long readGlobalLimit,
            long writeChannelLimit, long readChannelLimit,
            long checkInterval) {
        super(timer, writeGlobalLimit, readGlobalLimit, checkInterval);
        this.writeChannelLimit = writeChannelLimit;
        this.readChannelLimit = readChannelLimit;
        createGlobalTrafficCounter(timer);
    }

    /**
     * Create a new instance.
     *
     * @param timer
     *          the {@link Timer} to use for the {@link TrafficCounter}.
     * @param writeGlobalLimit
     *            0 or a limit in bytes/s
     * @param readGlobalLimit
     *            0 or a limit in bytes/s
     * @param writeChannelLimit
     *            0 or a limit in bytes/s
     * @param readChannelLimit
     *            0 or a limit in bytes/s
     */
    public GlobalChannelTrafficShapingHandler(Timer timer,
            long writeGlobalLimit, long readGlobalLimit,
            long writeChannelLimit, long readChannelLimit) {
        super(timer, writeGlobalLimit, readGlobalLimit);
        this.writeChannelLimit = writeChannelLimit;
        this.readChannelLimit = readChannelLimit;
        createGlobalTrafficCounter(timer);
    }

    /**
     * Create a new instance.
     *
     * @param timer
     *          the {@link Timer} to use for the {@link TrafficCounter}.
     * @param checkInterval
     *          The delay between two computations of performances for
     *            channels or 0 if no stats are to be computed.
     */
    public GlobalChannelTrafficShapingHandler(Timer timer, long checkInterval) {
        super(timer, checkInterval);
        createGlobalTrafficCounter(timer);
    }

    /**
     * Create a new instance.
     *
     * @param timer
     *          the {@link Timer} to use for the {@link TrafficCounter}.
     */
    public GlobalChannelTrafficShapingHandler(Timer timer) {
        super(timer);
        createGlobalTrafficCounter(timer);
    }

    /**
     * @param objectSizeEstimator ObjectSizeEstimator to use
     * @param timer
     *            the {@link Timer} to use for the {@link TrafficCounter}.
     * @param writeLimit write Global Limit
     *            0 or a limit in bytes/s
     * @param readLimit read Global Limit
     *            0 or a limit in bytes/s
     * @param writeChannelLimit
     *            0 or a limit in bytes/s
     * @param readChannelLimit
     *            0 or a limit in bytes/s
     * @param checkInterval
     *            The delay between two computations of performances for
     *            channels or 0 if no stats are to be computed.
     * @param maxTime
     *            The maximum delay to wait in case of traffic excess.
     */
    public GlobalChannelTrafficShapingHandler(ObjectSizeEstimator objectSizeEstimator, Timer timer, long writeLimit,
            long readLimit, long writeChannelLimit, long readChannelLimit, long checkInterval, long maxTime) {
        super(objectSizeEstimator, timer, writeLimit, readLimit, checkInterval, maxTime);
        this.writeChannelLimit = writeChannelLimit;
        this.readChannelLimit = readChannelLimit;
        createGlobalTrafficCounter(timer);
    }

    /**
     * @param objectSizeEstimator ObjectSizeEstimator to use
     * @param timer
     *            the {@link Timer} to use for the {@link TrafficCounter}.
     * @param writeLimit write Global Limit
     *            0 or a limit in bytes/s
     * @param readLimit read Global Limit
     *            0 or a limit in bytes/s
     * @param writeChannelLimit
     *            0 or a limit in bytes/s
     * @param readChannelLimit
     *            0 or a limit in bytes/s
     * @param checkInterval
     *            The delay between two computations of performances for
     *            channels or 0 if no stats are to be computed.
     */
    public GlobalChannelTrafficShapingHandler(ObjectSizeEstimator objectSizeEstimator, Timer timer, long writeLimit,
            long readLimit, long writeChannelLimit, long readChannelLimit, long checkInterval) {
        super(objectSizeEstimator, timer, writeLimit, readLimit, checkInterval);
        this.writeChannelLimit = writeChannelLimit;
        this.readChannelLimit = readChannelLimit;
        createGlobalTrafficCounter(timer);
    }

    /**
     * @param objectSizeEstimator ObjectSizeEstimator to use
     * @param timer
     *            the {@link Timer} to use for the {@link TrafficCounter}.
     * @param writeLimit write Global Limit
     *            0 or a limit in bytes/s
     * @param readLimit read Global Limit
     *            0 or a limit in bytes/s
     * @param writeChannelLimit
     *            0 or a limit in bytes/s
     * @param readChannelLimit
     *            0 or a limit in bytes/s
     */
    public GlobalChannelTrafficShapingHandler(ObjectSizeEstimator objectSizeEstimator, Timer timer, long writeLimit,
            long readLimit, long writeChannelLimit, long readChannelLimit) {
        super(objectSizeEstimator, timer, writeLimit, readLimit);
        this.writeChannelLimit = writeChannelLimit;
        this.readChannelLimit = readChannelLimit;
        createGlobalTrafficCounter(timer);
    }

    /**
     * @param objectSizeEstimator ObjectSizeEstimator to use
     * @param timer
     *            the {@link Timer} to use for the {@link TrafficCounter}.
     * @param checkInterval
     *            The delay between two computations of performances for
     *            channels or 0 if no stats are to be computed.
     */
    public GlobalChannelTrafficShapingHandler(ObjectSizeEstimator objectSizeEstimator, Timer timer,
            long checkInterval) {
        super(objectSizeEstimator, timer, checkInterval);
        createGlobalTrafficCounter(timer);
    }

    /**
     * @param objectSizeEstimator ObjectSizeEstimator to use
     * @param timer
     *            the {@link Timer} to use for the {@link TrafficCounter}.
     */
    public GlobalChannelTrafficShapingHandler(ObjectSizeEstimator objectSizeEstimator, Timer timer) {
        super(objectSizeEstimator, timer);
        createGlobalTrafficCounter(timer);
    }

    /**
     * @return the current max deviation.
     */
    public float maxDeviation() {
        return maxDeviation;
    }

    /**
     * @return the current acceleration factor.
     */
    public float accelerationFactor() {
        return accelerationFactor;
    }

    /**
     * @return the current slow down factor.
     */
    public float slowDownFactor() {
        return slowDownFactor;
    }

    /**
     * @param maxDeviation
     *            the maximum deviation to allow during computation of average, default deviation
     *            being 0.1, so +/-10% of the desired bandwidth. Maximum being 0.4.
     * @param slowDownFactor
     *            the factor set as +x% to the too fast client (minimal value being 0, meaning no
     *            slow down factor), default being 40% (0.4).
     * @param accelerationFactor
     *            the factor set as -x% to the too slow client (maximal value being 0, meaning no
     *            acceleration factor), default being -10% (-0.1).
     */
    public void setMaxDeviation(float maxDeviation, float slowDownFactor, float accelerationFactor) {
        if (maxDeviation > MAX_DEVIATION) {
            throw new IllegalArgumentException("maxDeviation must be <= " + MAX_DEVIATION);
        }
        if (slowDownFactor < 0) {
            throw new IllegalArgumentException("slowDownFactor must be >= 0");
        }
        if (accelerationFactor > 0) {
            throw new IllegalArgumentException("accelerationFactor must be <= 0");
        }
        this.maxDeviation = maxDeviation;
        this.accelerationFactor = 1 + accelerationFactor;
        this.slowDownFactor = 1 + slowDownFactor;
    }

    private void computeDeviationCumulativeBytes() {
        // compute the maximum cumulativeXxxxBytes among still connected Channels
        long maxWrittenBytes = 0;
        long maxReadBytes = 0;
        long minWrittenBytes = Long.MAX_VALUE;
        long minReadBytes = Long.MAX_VALUE;
        for (PerChannel perChannel : channelQueues.values()) {
            long value = perChannel.channelTrafficCounter.getCumulativeWrittenBytes();
            if (maxWrittenBytes < value) {
                maxWrittenBytes = value;
            }
            if (minWrittenBytes > value) {
                minWrittenBytes = value;
            }
            value = perChannel.channelTrafficCounter.getCumulativeReadBytes();
            if (maxReadBytes < value) {
                maxReadBytes = value;
            }
            if (minReadBytes > value) {
                minReadBytes = value;
            }
        }
        boolean multiple = channelQueues.size() > 1;
        readDeviationActive = multiple && minReadBytes < maxReadBytes / 2;
        writeDeviationActive = multiple && minWrittenBytes < maxWrittenBytes / 2;
        cumulativeWrittenBytes.set(maxWrittenBytes);
        cumulativeReadBytes.set(maxReadBytes);
    }

    @Override
    protected void doAccounting(TrafficCounter counter) {
        computeDeviationCumulativeBytes();
        super.doAccounting(counter);
    }

    private long computeBalancedWait(float maxLocal, float maxGlobal, long wait) {
        if (maxGlobal == 0) {
            // no change
            return wait;
        }
        float ratio = maxLocal / maxGlobal;
        // if in the boundaries, same value
        if (ratio > maxDeviation) {
            if (ratio < 1 - maxDeviation) {
                return wait;
            } else {
                ratio = slowDownFactor;
                if (wait < MINIMAL_WAIT) {
                    wait = MINIMAL_WAIT;
                }
            }
        } else {
            ratio = accelerationFactor;
        }
        return (long) (wait * ratio);
    }

    /**
     * @return the maxGlobalWriteSize
     */
    public long getMaxGlobalWriteSize() {
        return maxGlobalWriteSize;
    }

    /**
     * Note the change will be taken as best effort, meaning
     * that all already scheduled traffics will not be
     * changed, but only applied to new traffics.<br>
     * So the expected usage of this method is to be used not too often,
     * accordingly to the traffic shaping configuration.
     *
     * @param maxGlobalWriteSize the maximum Global Write Size allowed in the buffer
     *            globally for all channels before write suspended is set.
     */
    public void setMaxGlobalWriteSize(long maxGlobalWriteSize) {
        this.maxGlobalWriteSize = maxGlobalWriteSize;
    }

    /**
     * @return the global size of the buffers for all queues.
     */
    public long queuesSize() {
        return queuesSize.get();
    }

    /**
     * @param newWriteLimit Channel write limit
     * @param newReadLimit Channel read limit
     */
    public void configureChannel(long newWriteLimit, long newReadLimit) {
        writeChannelLimit = newWriteLimit;
        readChannelLimit = newReadLimit;
        long now = TrafficCounter.milliSecondFromNano();
        for (PerChannel perChannel : channelQueues.values()) {
            perChannel.channelTrafficCounter.resetAccounting(now);
        }
    }

    /**
     * @return Channel write limit.
     */
    public long getWriteChannelLimit() {
        return writeChannelLimit;
    }

    /**
     * @param writeLimit Channel write limit
     */
    public void setWriteChannelLimit(long writeLimit) {
        writeChannelLimit = writeLimit;
        long now = TrafficCounter.milliSecondFromNano();
        for (PerChannel perChannel : channelQueues.values()) {
            perChannel.channelTrafficCounter.resetAccounting(now);
        }
    }

    /**
     * @return Channel read limit.
     */
    public long getReadChannelLimit() {
        return readChannelLimit;
    }

    /**
     * @param readLimit Channel read limit
     */
    public void setReadChannelLimit(long readLimit) {
        readChannelLimit = readLimit;
        long now = TrafficCounter.milliSecondFromNano();
        for (PerChannel perChannel : channelQueues.values()) {
            perChannel.channelTrafficCounter.resetAccounting(now);
        }
    }

    /**
     * Release all internal resources of this instance.
     */
    public final void release() {
        trafficCounter.stop();
    }

    private PerChannel getOrSetPerChannel(ChannelHandlerContext ctx) {
        // ensure creation is limited to one thread per channel
        Channel channel = ctx.getChannel();
        Integer key = channel.hashCode();
        PerChannel perChannel = channelQueues.get(key);
        if (perChannel == null) {
            perChannel = new PerChannel();
            perChannel.messagesQueue = new LinkedList<ToSend>();
            // Don't start it since managed through the Global one
            perChannel.channelTrafficCounter = new TrafficCounter(this, null, "ChannelTC" +
                    ctx.getChannel().hashCode(), checkInterval);
            perChannel.queueSize = 0L;
            perChannel.lastReadTimestamp = TrafficCounter.milliSecondFromNano();
            perChannel.lastWriteTimestamp = perChannel.lastReadTimestamp;
            channelQueues.put(key, perChannel);
        }
        return perChannel;
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        getOrSetPerChannel(ctx);
        trafficCounter.resetCumulativeTime();
        super.channelConnected(ctx, e);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        trafficCounter.resetCumulativeTime();
        Channel channel = ctx.getChannel();
        Integer key = channel.hashCode();
        PerChannel perChannel = channelQueues.remove(key);
        if (perChannel != null) {
            // write operations need synchronization
            synchronized (perChannel) {
                queuesSize.addAndGet(-perChannel.queueSize);
                perChannel.messagesQueue.clear();
            }
        }
        releaseWriteSuspended(ctx);
        releaseReadSuspended(ctx);
        super.channelClosed(ctx, e);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent evt)
            throws Exception {
        long now = TrafficCounter.milliSecondFromNano();
        try {
            ReadWriteStatus rws = checkAttachment(ctx);
            long size = calculateSize(evt.getMessage());
            if (size > 0) {
                // compute the number of ms to wait before reopening the channel
                // compute the number of ms to wait before reopening the channel
                long waitGlobal = trafficCounter.readTimeToWait(size, getReadLimit(), maxTime, now);
                Integer key = ctx.getChannel().hashCode();
                PerChannel perChannel = channelQueues.get(key);
                long wait = 0;
                if (perChannel != null) {
                    wait = perChannel.channelTrafficCounter.readTimeToWait(size, readChannelLimit, maxTime, now);
                    if (readDeviationActive) {
                        // now try to balance between the channels
                        long maxLocalRead;
                        maxLocalRead = perChannel.channelTrafficCounter.getCumulativeReadBytes();
                        long maxGlobalRead = cumulativeReadBytes.get();
                        if (maxLocalRead <= 0) {
                            maxLocalRead = 0;
                        }
                        if (maxGlobalRead < maxLocalRead) {
                            maxGlobalRead = maxLocalRead;
                        }
                        wait = computeBalancedWait(maxLocalRead, maxGlobalRead, wait);
                    }
                }
                if (wait < waitGlobal) {
                    wait = waitGlobal;
                }
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
            // The message is then forcedly passed to the next handler (not to super)
            ctx.sendUpstream(evt);
        }
    }

    @Override
    protected long checkWaitReadTime(final ChannelHandlerContext ctx, long wait, final long now) {
        Integer key = ctx.getChannel().hashCode();
        PerChannel perChannel = channelQueues.get(key);
        if (perChannel != null) {
            if (wait > maxTime && now + wait - perChannel.lastReadTimestamp > maxTime) {
                wait = maxTime;
            }
        }
        return wait;
    }

    @Override
    protected void informReadOperation(final ChannelHandlerContext ctx, final long now) {
        Integer key = ctx.getChannel().hashCode();
        PerChannel perChannel = channelQueues.get(key);
        if (perChannel != null) {
            perChannel.lastReadTimestamp = now;
        }
    }

    private static final class ToSend {
        final long relativeTimeAction;
        final MessageEvent toSend;
        final long size;

        private ToSend(final long delay, final MessageEvent toSend, final long size) {
            relativeTimeAction = delay;
            this.toSend = toSend;
            this.size = size;
        }
    }

    protected long maximumCumulativeWrittenBytes() {
        return cumulativeWrittenBytes.get();
    }

    protected long maximumCumulativeReadBytes() {
        return cumulativeReadBytes.get();
    }

    /**
     * To allow for instance doAccounting to use the TrafficCounter per channel.
     * @return the list of TrafficCounters that exists at the time of the call.
     */
    public Collection<TrafficCounter> channelTrafficCounters() {
        return new AbstractCollection<TrafficCounter>() {
            @Override
            public Iterator<TrafficCounter> iterator() {
                return new Iterator<TrafficCounter>() {
                    final Iterator<PerChannel> iter = channelQueues.values().iterator();
                    public boolean hasNext() {
                        return iter.hasNext();
                    }
                    public TrafficCounter next() {
                        return iter.next().channelTrafficCounter;
                    }
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
            @Override
            public int size() {
                return channelQueues.size();
            }
        };
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent evt)
            throws Exception {
        long wait = 0;
        long size = calculateSize(evt.getMessage());
        long now = TrafficCounter.milliSecondFromNano();
        try {
            if (size > 0) {
                // compute the number of ms to wait before continue with the channel
                long waitGlobal = trafficCounter.writeTimeToWait(size, getWriteLimit(), maxTime, now);
                Integer key = ctx.getChannel().hashCode();
                PerChannel perChannel = channelQueues.get(key);
                if (perChannel != null) {
                    wait = perChannel.channelTrafficCounter.writeTimeToWait(size, writeChannelLimit, maxTime, now);
                    if (writeDeviationActive) {
                        // now try to balance between the channels
                        long maxLocalWrite;
                        maxLocalWrite = perChannel.channelTrafficCounter.getCumulativeWrittenBytes();
                        long maxGlobalWrite = cumulativeWrittenBytes.get();
                        if (maxLocalWrite <= 0) {
                            maxLocalWrite = 0;
                        }
                        if (maxGlobalWrite < maxLocalWrite) {
                            maxGlobalWrite = maxLocalWrite;
                        }
                        wait = computeBalancedWait(maxLocalWrite, maxGlobalWrite, wait);
                    }
                }
                if (wait < waitGlobal) {
                    wait = waitGlobal;
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

    @Override
    protected void submitWrite(final ChannelHandlerContext ctx, final MessageEvent evt,
            final long size, final long writedelay, final long now) throws Exception {
        Channel channel = ctx.getChannel();
        Integer key = channel.hashCode();
        PerChannel perChannel = channelQueues.get(key);
        if (perChannel == null) {
            // in case write occurs before handlerAdded is raized for this handler
            // imply a synchronized only if needed
            perChannel = getOrSetPerChannel(ctx);
        }
        final ToSend newToSend;
        long delay = writedelay;
        boolean globalSizeExceeded = false;
        // write operations need synchronization
        synchronized (perChannel) {
            if (writedelay == 0 && perChannel.messagesQueue.isEmpty()) {
                if (!channel.isConnected()) {
                    // ignore
                    return;
                }
                trafficCounter.bytesRealWriteFlowControl(size);
                perChannel.channelTrafficCounter.bytesRealWriteFlowControl(size);
                ctx.sendDownstream(evt);
                perChannel.lastWriteTimestamp = now;
                return;
            }
            if (delay > maxTime && now + delay - perChannel.lastWriteTimestamp > maxTime) {
                delay = maxTime;
            }
            if (timer == null) {
                // Sleep since no executor
                Thread.sleep(delay);
                if (!ctx.getChannel().isConnected()) {
                    // ignore
                    return;
                }
                trafficCounter.bytesRealWriteFlowControl(size);
                perChannel.channelTrafficCounter.bytesRealWriteFlowControl(size);
                ctx.sendDownstream(evt);
                perChannel.lastWriteTimestamp = now;
                return;
            }
            if (!ctx.getChannel().isConnected()) {
                // ignore
                return;
            }
            newToSend = new ToSend(delay + now, evt, size);
            perChannel.messagesQueue.add(newToSend);
            perChannel.queueSize += size;
            queuesSize.addAndGet(size);
            checkWriteSuspend(ctx, delay, perChannel.queueSize);
            if (queuesSize.get() > maxGlobalWriteSize) {
                globalSizeExceeded = true;
            }
        }
        if (globalSizeExceeded) {
            setWritable(ctx, false);
        }
        final long futureNow = newToSend.relativeTimeAction;
        final PerChannel forSchedule = perChannel;
        timer.newTimeout(new TimerTask() {
            public void run(Timeout timeout) throws Exception {
                sendAllValid(ctx, forSchedule, futureNow);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void sendAllValid(final ChannelHandlerContext ctx, final PerChannel perChannel, final long now)
            throws Exception {
        // write operations need synchronization
        synchronized (perChannel) {
            while (!perChannel.messagesQueue.isEmpty()) {
                ToSend newToSend = perChannel.messagesQueue.remove(0);
                if (newToSend.relativeTimeAction <= now) {
                    if (! ctx.getChannel().isConnected()) {
                        // ignore
                        break;
                    }
                    long size = newToSend.size;
                    trafficCounter.bytesRealWriteFlowControl(size);
                    perChannel.channelTrafficCounter.bytesRealWriteFlowControl(size);
                    perChannel.queueSize -= size;
                    queuesSize.addAndGet(-size);
                    ctx.sendDownstream(newToSend.toSend);
                    perChannel.lastWriteTimestamp = now;
                } else {
                    perChannel.messagesQueue.add(0, newToSend);
                    break;
                }
            }
            if (perChannel.messagesQueue.isEmpty()) {
                releaseWriteSuspended(ctx);
            }
        }
    }

    @Override
    public String toString() {
        return new StringBuilder(340).append(super.toString())
            .append(" Write Channel Limit: ").append(writeChannelLimit)
            .append(" Read Channel Limit: ").append(readChannelLimit).toString();
    }
}
