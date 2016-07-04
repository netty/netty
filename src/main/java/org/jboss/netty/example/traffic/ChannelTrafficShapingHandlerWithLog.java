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
package org.jboss.netty.example.traffic;


import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.traffic.ChannelTrafficShapingHandler;
import org.jboss.netty.handler.traffic.TrafficCounter;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.ObjectSizeEstimator;
import org.jboss.netty.util.Timer;

public class ChannelTrafficShapingHandlerWithLog extends ChannelTrafficShapingHandler {
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(ChannelTrafficShapingHandlerWithLog.class);

    public ChannelTrafficShapingHandlerWithLog(Timer timer, long writeLimit, long readLimit,
            long checkInterval) {
        super(timer, writeLimit, readLimit, checkInterval);
    }

    public ChannelTrafficShapingHandlerWithLog(Timer timer, long writeLimit, long readLimit,
            long checkInterval, long maxTime) {
        super(timer, writeLimit, readLimit, checkInterval, maxTime);
    }

    public ChannelTrafficShapingHandlerWithLog(Timer timer, long writeLimit, long readLimit) {
        super(timer, writeLimit, readLimit);
    }

    public ChannelTrafficShapingHandlerWithLog(Timer timer, long checkInterval) {
        super(timer, checkInterval);
    }

    public ChannelTrafficShapingHandlerWithLog(Timer timer) {
        super(timer);
    }

    public ChannelTrafficShapingHandlerWithLog(ObjectSizeEstimator objectSizeEstimator,
            Timer timer, long writeLimit, long readLimit, long checkInterval) {
        super(objectSizeEstimator, timer, writeLimit, readLimit, checkInterval);
    }

    public ChannelTrafficShapingHandlerWithLog(ObjectSizeEstimator objectSizeEstimator,
            Timer timer, long writeLimit, long readLimit, long checkInterval, long maxTime) {
        super(objectSizeEstimator, timer, writeLimit, readLimit, checkInterval, maxTime);
    }

    public ChannelTrafficShapingHandlerWithLog(ObjectSizeEstimator objectSizeEstimator,
            Timer timer, long writeLimit, long readLimit) {
        super(objectSizeEstimator, timer, writeLimit, readLimit);
    }

    public ChannelTrafficShapingHandlerWithLog(ObjectSizeEstimator objectSizeEstimator,
            Timer timer, long checkInterval) {
        super(objectSizeEstimator, timer, checkInterval);
    }

    public ChannelTrafficShapingHandlerWithLog(ObjectSizeEstimator objectSizeEstimator, Timer timer) {
        super(objectSizeEstimator, timer);
    }

    @Override
    protected void doAccounting(TrafficCounter counter) {
        logger.warn(this.toString() + " QueueSize: " + queueSize());
        super.doAccounting(counter);
    }

    @Override
    protected long calculateSize(Object obj) {
        if (obj instanceof ChannelBuffer) {
            return ((ChannelBuffer) obj).readableBytes();
        }
        return super.calculateSize(obj);
    }

}
