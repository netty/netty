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

import java.util.concurrent.Executor;

import io.netty.handler.execution.ChannelEventRunnable;
import io.netty.util.internal.ExecutorUtil;

/**
 * {@link SedaExecutor} which use two different {@link Executor}'s. One is used for upstream events and one for downstream events.
 * 
 * You should use a {@link SedaOrderedMemoryAwareThreadPoolExecutor} if you care about the order of thread-execution. In most cases this should be the case
 * 
 * 
 *
 */
public class SimpleSedaExecutor extends SedaExecutor{

    private final Executor upstreamExecutor;
    private final Executor downstreamExecutor;

    /**
     * Construct an {@link SimpleSedaExecutor} which use two different {@link Executor}'s. One is used for upstream events and one for downstream events.
     * 
     * @param upstreamExecutor the {@link Executor} which is used for upstream events
     * @param downstreamExecutor the {@link Executor} which is used for downstream events
     */
    public SimpleSedaExecutor(Executor upstreamExecutor, Executor downstreamExecutor) {
        this.upstreamExecutor = upstreamExecutor;
        this.downstreamExecutor = downstreamExecutor;
    }

    /**
     * Construct an {@link SimpleSedaExecutor} which uses the same {@link Executor} for downstream and upstream events
     * 
     * @param executor the {@link Executor} for events
     */
    public SimpleSedaExecutor(Executor executor) {
        this(executor, executor);
    }

    @Override
    public void releaseExternalResources() {
        ExecutorUtil.terminate(upstreamExecutor, downstreamExecutor);
    }

    @Override
    protected void executeDownstream(ChannelDownstreamEventRunnable runnable) throws Exception {
        downstreamExecutor.execute(runnable);        
    }

    @Override
    protected void executeUpstream(ChannelEventRunnable runnable) throws Exception {
        upstreamExecutor.execute(runnable);        
    }

}
