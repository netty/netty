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

import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.MemoryAwareThreadPoolExecutor;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.jboss.netty.util.ObjectSizeEstimator;
import org.jboss.netty.util.Timer;

/**
 * This implementation of the {@link AbstractTrafficShapingHandler} is for global
 * traffic shaping, that is to say a global limitation of the bandwidth, whatever
 * the number of opened channels.<br><br>
 *
 * The general use should be as follow:<br>
 * <ul>
 * <li>Create your unique GlobalTrafficShapingHandler like:<br><br>
 * <tt>GlobalTrafficShapingHandler myHandler = new GlobalTrafficShapingHandler(timer);</tt><br><br>
 * timer could be created using <tt>HashedWheelTimer</tt><br>
 * <tt>pipeline.addLast("GLOBAL_TRAFFIC_SHAPING", myHandler);</tt><br><br>
 *
 * <b>Note that this handler has a Pipeline Coverage of "all" which means only one such handler must be created
 * and shared among all channels as the counter must be shared among all channels.</b><br><br>
 *
 * Other arguments can be passed like write or read limitation (in bytes/s where 0 means no limitation)
 * or the check interval (in millisecond) that represents the delay between two computations of the
 * bandwidth and so the call back of the doAccounting method (0 means no accounting at all).<br><br>
 *
 * A value of 0 means no accounting for checkInterval. If you need traffic shaping but no such accounting,
 * it is recommended to set a positive value, even if it is high since the precision of the
 * Traffic Shaping depends on the period where the traffic is computed. The highest the interval,
 * the less precise the traffic shaping will be. It is suggested as higher value something close
 * to 5 or 10 minutes.<br><br>
 *
 * maxTimeToWait, by default set to 15s, allows to specify an upper bound of time shaping.<br><br>
 * </li>
 * <li>Add it in your pipeline, before a recommended {@link ExecutionHandler} (like
 * {@link OrderedMemoryAwareThreadPoolExecutor} or {@link MemoryAwareThreadPoolExecutor}).<br>
 * <tt>pipeline.addLast("GLOBAL_TRAFFIC_SHAPING", myHandler);</tt><br><br>
 * </li>
 * <li>When you shutdown your application, release all the external resources
 * by calling:<br>
 * <tt>myHandler.releaseExternalResources();</tt><br>
 * </li>
 * </ul><br>
 */
@Sharable
public class GlobalTrafficShapingHandler extends AbstractTrafficShapingHandler {
    /**
     * Create the global TrafficCounter
     */
    void createGlobalTrafficCounter() {
        TrafficCounter tc;
        if (timer != null) {
            tc = new TrafficCounter(this, timer, "GlobalTC",
                    checkInterval);
            setTrafficCounter(tc);
            tc.start();
        }
    }

    public GlobalTrafficShapingHandler(Timer timer, long writeLimit,
            long readLimit, long checkInterval) {
        super(timer, writeLimit, readLimit, checkInterval);
        createGlobalTrafficCounter();
    }

    public GlobalTrafficShapingHandler(Timer timer, long writeLimit,
            long readLimit, long checkInterval, long maxTime) {
        super(timer, writeLimit, readLimit, checkInterval, maxTime);
        createGlobalTrafficCounter();
    }

    public GlobalTrafficShapingHandler(Timer timer, long writeLimit,
            long readLimit) {
        super(timer, writeLimit, readLimit);
        createGlobalTrafficCounter();
    }

    public GlobalTrafficShapingHandler(Timer timer, long checkInterval) {
        super(timer, checkInterval);
        createGlobalTrafficCounter();
    }

    public GlobalTrafficShapingHandler(Timer timer) {
        super(timer);
        createGlobalTrafficCounter();
    }

    public GlobalTrafficShapingHandler(ObjectSizeEstimator objectSizeEstimator,
            Timer timer, long writeLimit, long readLimit,
            long checkInterval) {
        super(objectSizeEstimator, timer, writeLimit, readLimit,
                checkInterval);
        createGlobalTrafficCounter();
    }

    public GlobalTrafficShapingHandler(ObjectSizeEstimator objectSizeEstimator,
            Timer timer, long writeLimit, long readLimit,
            long checkInterval, long maxTime) {
        super(objectSizeEstimator, timer, writeLimit, readLimit,
                checkInterval, maxTime);
        createGlobalTrafficCounter();
    }

    public GlobalTrafficShapingHandler(ObjectSizeEstimator objectSizeEstimator,
            Timer timer, long writeLimit, long readLimit) {
        super(objectSizeEstimator, timer, writeLimit, readLimit);
        createGlobalTrafficCounter();
    }

    public GlobalTrafficShapingHandler(ObjectSizeEstimator objectSizeEstimator,
            Timer timer, long checkInterval) {
        super(objectSizeEstimator, timer, checkInterval);
        createGlobalTrafficCounter();
    }

    public GlobalTrafficShapingHandler(ObjectSizeEstimator objectSizeEstimator,
            Timer timer) {
        super(objectSizeEstimator, timer);
        createGlobalTrafficCounter();
    }

}
