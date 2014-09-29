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

import java.util.concurrent.ScheduledExecutorService;

import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import io.netty.handler.traffic.TrafficCounter;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

public class GlobalTrafficShapingHandlerWithLog extends GlobalTrafficShapingHandler {
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(GlobalTrafficShapingHandlerWithLog.class);

    public GlobalTrafficShapingHandlerWithLog(ScheduledExecutorService executor, long writeLimit,
            long readLimit, long checkInterval, long maxTime) {
        super(executor, writeLimit, readLimit, checkInterval, maxTime);
    }

    public GlobalTrafficShapingHandlerWithLog(ScheduledExecutorService executor, long writeLimit,
            long readLimit, long checkInterval) {
        super(executor, writeLimit, readLimit, checkInterval);
    }

    public GlobalTrafficShapingHandlerWithLog(ScheduledExecutorService executor, long writeLimit,
            long readLimit) {
        super(executor, writeLimit, readLimit);
    }

    public GlobalTrafficShapingHandlerWithLog(ScheduledExecutorService executor, long checkInterval) {
        super(executor, checkInterval);
    }

    public GlobalTrafficShapingHandlerWithLog(EventExecutor executor) {
        super(executor);
    }

    // Shall be updated to fit the needs
    @Override
    protected void doAccounting(TrafficCounter counter) {
        logger.warn(this.toString() + " QueuesSize: "+queuesSize());
        super.doAccounting(counter);
    }

    // Shall be updated to fit the needs, in particular to detect other than ByteBuf
    @Override
    protected long calculateSize(Object msg) {
        return super.calculateSize(msg);
    }

}
