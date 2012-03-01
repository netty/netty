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
package org.jboss.netty.handler.execution;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.util.ObjectSizeEstimator;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * {@link Executor} which should be used for downstream {@link ChannelEvent}'s. This implementation will take care of preserve the order of the events in a {@link Channel}.
 * If you don't need to preserve the order just use one of the {@link Executor} implementations provided by the static methods of {@link Executors}.
 * <br>
 * <br>
 * 
 * For more informations about how the order is preserved see {@link OrderedMemoryAwareThreadPoolExecutor}
 *
 */
public final class OrderedDownstreamThreadPoolExecutor extends OrderedMemoryAwareThreadPoolExecutor {
    
    /**
     * Creates a new instance.
     *
     * @param corePoolSize          the maximum number of active threads
     */
    public OrderedDownstreamThreadPoolExecutor(int corePoolSize) {
        super(corePoolSize, 0L, 0L);
    }

    /**
     * Creates a new instance.
     *
     * @param corePoolSize          the maximum number of active threads
     * @param keepAliveTime         the amount of time for an inactive thread to shut itself down
     * @param unit                  the {@link TimeUnit} of {@code keepAliveTime}
     */
    public OrderedDownstreamThreadPoolExecutor(
            int corePoolSize, long keepAliveTime, TimeUnit unit) {
        super(corePoolSize, 0L, 0L, keepAliveTime, unit);
    }

    /**
     * Creates a new instance.
     *
     * @param corePoolSize          the maximum number of active threads
     * @param keepAliveTime         the amount of time for an inactive thread to shut itself down
     * @param unit                  the {@link TimeUnit} of {@code keepAliveTime}
     * @param threadFactory         the {@link ThreadFactory} of this pool
     */
    public OrderedDownstreamThreadPoolExecutor(
            int corePoolSize, long keepAliveTime, TimeUnit unit, ThreadFactory threadFactory) {
        super(corePoolSize, 0L, 0L,
                keepAliveTime, unit, threadFactory);
    }

    
    /**
     * Return <code>null</code>
     */
    @Override
    public ObjectSizeEstimator getObjectSizeEstimator() {
        return null;
    }

    /**
     * Throws {@link UnsupportedOperationException} as there is not support for limit the memory size in this implementation
     */
    @Override
    public void setObjectSizeEstimator(ObjectSizeEstimator objectSizeEstimator) {
        throw new UnsupportedOperationException("Not supported by this implementation");
    }

    /**
     * Returns <code>0L</code>
     */
    @Override
    public long getMaxChannelMemorySize() {
        return 0L;
    }

    /**
     * Throws {@link UnsupportedOperationException} as there is not support for limit the memory size in this implementation
     */
    @Override
    public void setMaxChannelMemorySize(long maxChannelMemorySize) {
        throw new UnsupportedOperationException("Not supported by this implementation");
    }

    /**
     * Returns <code>0L</code>
     */
    @Override
    public long getMaxTotalMemorySize() {
        return 0L;
    }

    /**
     * Throws {@link UnsupportedOperationException} as there is not support for limit the memory size in this implementation
     */
    @Override
    public void setMaxTotalMemorySize(long maxTotalMemorySize) {
        throw new UnsupportedOperationException("Not supported by this implementation");
    }

    /**
     * Return <code>false</code> as we not need to cound the memory in this implementation
     */
    @Override
    protected boolean shouldCount(Runnable task) {
        return false;
    }
    
    @Override
    public void execute(Runnable command) {
        
        // check if the Runnable was of an unsupported type
        if (command instanceof ChannelUpstreamEventRunnable) {
            throw new RejectedExecutionException("command must be enclosed with an downstream event.");
        }
        doExecute(command);
    }
    
    @Override
    protected Executor getChildExecutor(ChannelEvent e) {
        final Object key = getChildExecutorKey(e);
        Executor executor = childExecutors.get(key);
        if (executor == null) {
            executor = new ChildExecutor();
            Executor oldExecutor = childExecutors.putIfAbsent(key, executor);
            if (oldExecutor != null) {
                executor = oldExecutor;
            } else {
                
                // register a listener so that the ChildExecutor will get removed once the channel was closed
                e.getChannel().getCloseFuture().addListener(new ChannelFutureListener() {
                    
                    public void operationComplete(ChannelFuture future) throws Exception {
                        removeChildExecutor(key);
                    }
                });
            }
        }

        return executor;
    }

    
}
