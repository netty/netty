/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.handler.traffic;

import java.util.concurrent.Executor;

import org.jboss.netty.channel.ChannelPipelineCoverage;
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
 * <tt>pipeline.addLast("GLOBAL_TRAFFIC_SHAPING", myHandler);</tt><br><br>
 * executor could be created using <tt>Executors.newCachedThreadPool();<tt><br>
 * 
 * Other arguments can be passed like write or read limitation (in bytes/s where 0 means no limitation)
 * or the check interval (in millisecond) that represents the delay between two computations of the
 * bandwidth and so the call back of the doAccounting method (0 means no accounting at all).<br><br>
 * 
 * Even if 0 means no accounting for checkInterval and you don't need such accounting, 
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
        super(objectSizeEstimator, executor, writeLimit, readLimit, checkInterval);
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
    protected boolean isPerChannel() {
        return true;
    }

}
