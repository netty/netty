/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.traffic;

import java.util.concurrent.Executor;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.MemoryAwareThreadPoolExecutor;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.jboss.netty.util.ObjectSizeEstimator;

/**
 * This implementation of the {@link AbstractTrafficShapingHandler} is for channel
 * traffic shaping, that is to say a per channel limitation of the bandwidth.<br><br>
 *
 * The general use should be as follow:<br>
 * <ul>
 * <li>Add in your pipeline a new ChannelTrafficShapingHandler, before a recommended {@link ExecutionHandler} (like
 * {@link OrderedMemoryAwareThreadPoolExecutor} or {@link MemoryAwareThreadPoolExecutor}).<br>
 * <tt>ChannelTrafficShapingHandler myHandler = new ChannelTrafficShapingHandler(executor);</tt><br>
 * executor could be created using <tt>Executors.newCachedThreadPool();<tt><br>
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
 * <li>When you shutdown your application, release all the external resources like the executor
 * by calling:<br>
 * <tt>myHandler.releaseExternalResources();</tt><br>
 * </li>
 * </ul><br>
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Frederic Bregier
 */
@ChannelPipelineCoverage("one")
public class ChannelTrafficShapingHandler extends AbstractTrafficShapingHandler {

    /**
     * @param executor
     * @param writeLimit
     * @param readLimit
     * @param checkInterval
     */
    public ChannelTrafficShapingHandler(Executor executor, long writeLimit,
            long readLimit, long checkInterval) {
        super(executor, writeLimit, readLimit, checkInterval);
    }

    /**
     * @param executor
     * @param writeLimit
     * @param readLimit
     */
    public ChannelTrafficShapingHandler(Executor executor, long writeLimit,
            long readLimit) {
        super(executor, writeLimit, readLimit);
    }

    /**
     * @param executor
     * @param checkInterval
     */
    public ChannelTrafficShapingHandler(Executor executor, long checkInterval) {
        super(executor, checkInterval);
    }

    /**
     * @param executor
     */
    public ChannelTrafficShapingHandler(Executor executor) {
        super(executor);
    }

    /**
     * @param objectSizeEstimator
     * @param executor
     * @param writeLimit
     * @param readLimit
     * @param checkInterval
     */
    public ChannelTrafficShapingHandler(
            ObjectSizeEstimator objectSizeEstimator, Executor executor,
            long writeLimit, long readLimit, long checkInterval) {
        super(objectSizeEstimator, executor, writeLimit, readLimit,
                checkInterval);
    }

    /**
     * @param objectSizeEstimator
     * @param executor
     * @param writeLimit
     * @param readLimit
     */
    public ChannelTrafficShapingHandler(
            ObjectSizeEstimator objectSizeEstimator, Executor executor,
            long writeLimit, long readLimit) {
        super(objectSizeEstimator, executor, writeLimit, readLimit);
    }

    /**
     * @param objectSizeEstimator
     * @param executor
     * @param checkInterval
     */
    public ChannelTrafficShapingHandler(
            ObjectSizeEstimator objectSizeEstimator, Executor executor,
            long checkInterval) {
        super(objectSizeEstimator, executor, checkInterval);
    }

    /**
     * @param objectSizeEstimator
     * @param executor
     */
    public ChannelTrafficShapingHandler(
            ObjectSizeEstimator objectSizeEstimator, Executor executor) {
        super(objectSizeEstimator, executor);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        if (trafficCounter != null) {
            trafficCounter.stop();
            trafficCounter = null;
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
            trafficCounter = new TrafficCounter(this, executor, "ChannelTC" +
                    ctx.getChannel().getId(), checkInterval);
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
