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


import org.jboss.netty.handler.traffic.GlobalTrafficShapingHandler;
import org.jboss.netty.handler.traffic.TrafficCounter;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.ObjectSizeEstimator;
import org.jboss.netty.util.Timer;

public class GlobalTrafficShapingHandlerWithLog extends GlobalTrafficShapingHandler {
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(GlobalTrafficShapingHandlerWithLog.class);

    public GlobalTrafficShapingHandlerWithLog(Timer timer, long writeLimit, long readLimit,
            long checkInterval) {
        super(timer, writeLimit, readLimit, checkInterval);
    }

    public GlobalTrafficShapingHandlerWithLog(Timer timer, long writeLimit, long readLimit,
            long checkInterval, long maxTime) {
        super(timer, writeLimit, readLimit, checkInterval, maxTime);
    }

    public GlobalTrafficShapingHandlerWithLog(Timer timer, long writeLimit, long readLimit) {
        super(timer, writeLimit, readLimit);
    }

    public GlobalTrafficShapingHandlerWithLog(Timer timer, long checkInterval) {
        super(timer, checkInterval);
    }

    public GlobalTrafficShapingHandlerWithLog(Timer timer) {
        super(timer);
    }

    public GlobalTrafficShapingHandlerWithLog(ObjectSizeEstimator objectSizeEstimator, Timer timer,
            long writeLimit, long readLimit, long checkInterval) {
        super(objectSizeEstimator, timer, writeLimit, readLimit, checkInterval);
    }

    public GlobalTrafficShapingHandlerWithLog(ObjectSizeEstimator objectSizeEstimator, Timer timer,
            long writeLimit, long readLimit, long checkInterval, long maxTime) {
        super(objectSizeEstimator, timer, writeLimit, readLimit, checkInterval, maxTime);
    }

    public GlobalTrafficShapingHandlerWithLog(ObjectSizeEstimator objectSizeEstimator, Timer timer,
            long writeLimit, long readLimit) {
        super(objectSizeEstimator, timer, writeLimit, readLimit);
    }

    public GlobalTrafficShapingHandlerWithLog(ObjectSizeEstimator objectSizeEstimator, Timer timer,
            long checkInterval) {
        super(objectSizeEstimator, timer, checkInterval);
    }

    public GlobalTrafficShapingHandlerWithLog(ObjectSizeEstimator objectSizeEstimator, Timer timer) {
        super(objectSizeEstimator, timer);
    }

    @Override
    protected void doAccounting(TrafficCounter counter) {
        logger.warn(this.toString() + " QueuesSize: " + queuesSize());
        super.doAccounting(counter);
    }

}
