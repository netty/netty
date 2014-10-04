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
package org.jboss.netty.example.discard;

import java.util.LinkedList;
import java.util.List;

import org.jboss.netty.handler.traffic.GlobalChannelTrafficShapingHandler;
import org.jboss.netty.handler.traffic.TrafficCounter;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.Timer;

public class GlobalChannelTrafficShapingHandlerWithLog extends GlobalChannelTrafficShapingHandler {
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(GlobalChannelTrafficShapingHandlerWithLog.class);
    private final List<Long> cumulativeWrittenBytesPercent = new LinkedList<Long>();
    private final List<Long> cumulativeReadBytesPercent = new LinkedList<Long>();
    private final List<Long> cumulativeWrittenBytes = new LinkedList<Long>();
    private final List<Long> cumulativeReadBytes = new LinkedList<Long>();
    private final List<Long> throughputWrittenBytes = new LinkedList<Long>();
    private final List<Long> throughputReadBytes = new LinkedList<Long>();

    public GlobalChannelTrafficShapingHandlerWithLog(Timer timer, long writeGlobalLimit, long readGlobalLimit,
            long writeChannelLimit, long readChannelLimit, long checkInterval, long maxTime) {
        super(timer, writeGlobalLimit, readGlobalLimit, writeChannelLimit, readChannelLimit, checkInterval, maxTime);
    }

    public GlobalChannelTrafficShapingHandlerWithLog(Timer timer, long writeGlobalLimit, long readGlobalLimit,
            long writeChannelLimit, long readChannelLimit, long checkInterval) {
        super(timer, writeGlobalLimit, readGlobalLimit, writeChannelLimit, readChannelLimit, checkInterval);
    }

    public GlobalChannelTrafficShapingHandlerWithLog(Timer timer, long writeGlobalLimit, long readGlobalLimit,
            long writeChannelLimit, long readChannelLimit) {
        super(timer, writeGlobalLimit, readGlobalLimit, writeChannelLimit, readChannelLimit);
    }

    public GlobalChannelTrafficShapingHandlerWithLog(Timer timer, long checkInterval) {
        super(timer, checkInterval);
    }

    public GlobalChannelTrafficShapingHandlerWithLog(Timer timer) {
        super(timer);
    }

    /**
     * Override to compute average of bandwidth between all channels
     */
    @Override
    protected void doAccounting(TrafficCounter counter) {
        long maxWrittenNonZero = this.maximumCumulativeWrittenBytes();
        if (maxWrittenNonZero == 0) {
            maxWrittenNonZero = 1;
        }
        long maxReadNonZero = this.maximumCumulativeReadBytes();
        if (maxReadNonZero == 0) {
            maxReadNonZero = 1;
        }
        for (TrafficCounter tc : this.channelTrafficCounters()) {
            long cumulativeWritten = tc.getCumulativeWrittenBytes();
            cumulativeWrittenBytes.add(cumulativeWritten);
            if (cumulativeWritten > maxWrittenNonZero) {
                cumulativeWritten = maxWrittenNonZero;
            }
            cumulativeWrittenBytesPercent.add((maxWrittenNonZero - cumulativeWritten) * 100 / maxWrittenNonZero);
            throughputWrittenBytes.add(tc.getRealWriteThroughput() >> 10);
            long cumulativeRead = tc.getCumulativeReadBytes();
            cumulativeReadBytes.add(cumulativeRead);
            if (cumulativeRead > maxReadNonZero) {
                cumulativeRead = maxReadNonZero;
            }
            cumulativeReadBytesPercent.add((maxReadNonZero - cumulativeRead) * 100 / maxReadNonZero);
            throughputReadBytes.add(tc.getLastReadThroughput() >> 10);
        }
        logger.info(this.toString() + " QueuesSize: " + queuesSize()
                + "\nWrittenBytesPercentage: " + cumulativeWrittenBytesPercent
                + "\nWrittenThroughputBytes: " + throughputWrittenBytes
                + "\nWrittenBytes:           " + cumulativeWrittenBytes
                + "\nReadBytesPercentage:    " + cumulativeReadBytesPercent
                + "\nReadThroughputBytes:    " + throughputReadBytes
                + "\nReadBytes:              " + cumulativeReadBytes);
        cumulativeWrittenBytesPercent.clear();
        cumulativeReadBytesPercent.clear();
        cumulativeWrittenBytes.clear();
        cumulativeReadBytes.clear();
        throughputWrittenBytes.clear();
        throughputReadBytes.clear();
        super.doAccounting(counter);
    }
}
