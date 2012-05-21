/*
 * Copyright 2011 The Netty Project
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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipelineFactory;
import io.netty.channel.ChannelStateEvent;
import io.netty.handler.execution.ExecutionHandler;
import io.netty.handler.execution.MemoryAwareThreadPoolExecutor;
import io.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import io.netty.handler.execution.ObjectSizeEstimator;
import io.netty.util.Timer;

/**
 * This implementation of the {@link AbstractTrafficShapingHandler} is for channel
 * traffic shaping, that is to say a per channel limitation of the bandwidth.<br><br>
 *
 * The general use should be as follow:<br>
 * <ul>
 * <li>Add in your pipeline a new ChannelTrafficShapingHandler, before a recommended {@link ExecutionHandler} (like
 * {@link OrderedMemoryAwareThreadPoolExecutor} or {@link MemoryAwareThreadPoolExecutor}).<br>
 * <tt>ChannelTrafficShapingHandler myHandler = new ChannelTrafficShapingHandler(timer);</tt><br>
 * timer could be created using <tt>HashedWheelTimer</tt><br>
 * <tt>pipeline.addLast("CHANNEL_TRAFFIC_SHAPING", myHandler);</tt><br><br>
 *
 * <b>Note that this handler has a Pipeline Coverage of "one" which means a new handler must be created
 * for each new channel as the counter cannot be shared among all channels.</b> For instance, if you have a
 * {@link ChannelPipelineFactory}, you should create a new ChannelTrafficShapingHandler in this
 * {@link ChannelPipelineFactory} each time getPipeline() method is called.<br><br>
 *
 * Other arguments can be passed like write or read limitation (in bytes/s where 0 means no limitation)
 * or the check interval (in millisecond) that represents the delay between two computations of the
 * bandwidth and so the call back of the doAccounting method (0 means no accounting at all).<br><br>
 *
 * A value of 0 means no accounting for checkInterval. If you need traffic shaping but no such accounting,
 * it is recommended to set a positive value, even if it is high since the precision of the
 * Traffic Shaping depends on the period where the traffic is computed. The highest the interval,
 * the less precise the traffic shaping will be. It is suggested as higher value something close
 * to 5 or 10 minutes.<br>
 * </li>
 * <li>When you shutdown your application, release all the external resources (except the timer internal itself)
 * by calling:<br>
 * <tt>myHandler.releaseExternalResources();</tt><br>
 * </li>
 * </ul><br>
 */
public class ChannelTrafficShapingHandler extends AbstractTrafficShapingHandler {

    public ChannelTrafficShapingHandler(Timer timer, long writeLimit,
            long readLimit, long checkInterval) {
        super(timer, writeLimit, readLimit, checkInterval);
    }

    public ChannelTrafficShapingHandler(Timer timer, long writeLimit,
            long readLimit) {
        super(timer, writeLimit, readLimit);
    }

    public ChannelTrafficShapingHandler(Timer timer, long checkInterval) {
        super(timer, checkInterval);
    }

    public ChannelTrafficShapingHandler(Timer timer) {
        super(timer);
    }

    public ChannelTrafficShapingHandler(
            ObjectSizeEstimator objectSizeEstimator, Timer timer,
            long writeLimit, long readLimit, long checkInterval) {
        super(objectSizeEstimator, timer, writeLimit, readLimit,
                checkInterval);
    }

    public ChannelTrafficShapingHandler(
            ObjectSizeEstimator objectSizeEstimator, Timer timer,
            long writeLimit, long readLimit) {
        super(objectSizeEstimator, timer, writeLimit, readLimit);
    }

    public ChannelTrafficShapingHandler(
            ObjectSizeEstimator objectSizeEstimator, Timer timer,
            long checkInterval) {
        super(objectSizeEstimator, timer, checkInterval);
    }

    public ChannelTrafficShapingHandler(
            ObjectSizeEstimator objectSizeEstimator, Timer timer) {
        super(objectSizeEstimator, timer);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        if (trafficCounter != null) {
            trafficCounter.stop();
        }
        super.channelClosed(ctx, e);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        // readSuspended = true;
        ctx.setAttachment(Boolean.TRUE);
        ctx.getChannel().setReadable(false);
        if (trafficCounter == null) {
            // create a new counter now
            if (timer != null) {
                trafficCounter = new TrafficCounter(this, timer, "ChannelTC" +
                        ctx.getChannel().getId(), checkInterval);
            }
        }
        if (trafficCounter != null) {
            trafficCounter.start();
        }
        super.channelConnected(ctx, e);
        // readSuspended = false;
        ctx.setAttachment(null);
        ctx.getChannel().setReadable(true);
    }

}
