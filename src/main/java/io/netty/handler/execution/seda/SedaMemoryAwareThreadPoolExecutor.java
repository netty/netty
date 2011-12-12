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
package io.netty.handler.execution.seda;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import io.netty.handler.execution.MemoryAwareThreadPoolExecutor;
import io.netty.util.ObjectSizeEstimator;

/**
 * Subclass of {@link MemoryAwareThreadPoolExecutor} which has all the same semantics as {@link MemoryAwareThreadPoolExecutor}. The only difference is that it will not keep track of the memory usage
 * of downstream events.
 * 
 * 
 * For more details see {@link MemoryAwareThreadPoolExecutor}
 *
 */
public class SedaMemoryAwareThreadPoolExecutor extends MemoryAwareThreadPoolExecutor{

    /**
     * 
     * @see MemoryAwareThreadPoolExecutor#MemoryAwareThreadPoolExecutor(int, long, long, long, TimeUnit, ObjectSizeEstimator, ThreadFactory)
     */
    public SedaMemoryAwareThreadPoolExecutor(int corePoolSize, long maxChannelMemorySize, long maxTotalMemorySize, long keepAliveTime, TimeUnit unit, ObjectSizeEstimator objectSizeEstimator, ThreadFactory threadFactory) {
        super(corePoolSize, maxChannelMemorySize, maxTotalMemorySize, keepAliveTime, unit, objectSizeEstimator, threadFactory);
    }

    /**
     * @see MemoryAwareThreadPoolExecutor#MemoryAwareThreadPoolExecutor(int, long, long, long, TimeUnit, ThreadFactory)
     */
    public SedaMemoryAwareThreadPoolExecutor(int corePoolSize, long maxChannelMemorySize, long maxTotalMemorySize, long keepAliveTime, TimeUnit unit, ThreadFactory threadFactory) {
        super(corePoolSize, maxChannelMemorySize, maxTotalMemorySize, keepAliveTime, unit, threadFactory);
    }

    /**
     * 
     * @see MemoryAwareThreadPoolExecutor#MemoryAwareThreadPoolExecutor(int, long, long, long, TimeUnit)
     */
    public SedaMemoryAwareThreadPoolExecutor(int corePoolSize, long maxChannelMemorySize, long maxTotalMemorySize, long keepAliveTime, TimeUnit unit) {
        super(corePoolSize, maxChannelMemorySize, maxTotalMemorySize, keepAliveTime, unit);
    }

    /**
     * 
     * @see MemoryAwareThreadPoolExecutor#MemoryAwareThreadPoolExecutor(int, long, long)
     */
    public SedaMemoryAwareThreadPoolExecutor(int corePoolSize, long maxChannelMemorySize, long maxTotalMemorySize) {
        super(corePoolSize, maxChannelMemorySize, maxTotalMemorySize);
    }

    /**
     * Don't count if {@link Runnable} is an instance of {@link ChannelDownstreamEventRunnable}
     */
    @Override
    protected boolean shouldCount(Runnable task) {
        if (!(task instanceof ChannelDownstreamEventRunnable)) {
            return super.shouldCount(task);
        }
        return false;
    }

}
