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
package io.netty.example.traffic;

import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.handler.traffic.TrafficCounter;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

public class ChannelTrafficShapingHandlerWithLog extends ChannelTrafficShapingHandler {
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(ChannelTrafficShapingHandlerWithLog.class);

    public ChannelTrafficShapingHandlerWithLog(long writeLimit, long readLimit, long checkInterval,
            long maxTime) {
        super(writeLimit, readLimit, checkInterval, maxTime);
    }

    public ChannelTrafficShapingHandlerWithLog(long writeLimit, long readLimit, long checkInterval) {
        super(writeLimit, readLimit, checkInterval);
    }

    public ChannelTrafficShapingHandlerWithLog(long writeLimit, long readLimit) {
        super(writeLimit, readLimit);
    }

    public ChannelTrafficShapingHandlerWithLog(long checkInterval) {
        super(checkInterval);
    }

    // Shall be updated to fit the needs
    @Override
    protected void doAccounting(TrafficCounter counter) {
        logger.warn(this.toString() + " QueueSize: " + queueSize());
        super.doAccounting(counter);
    }

    // Shall be updated to fit the needs, in particular to detect other than ByteBuf
    @Override
    protected long calculateSize(Object msg) {
        return super.calculateSize(msg);
    }

}
