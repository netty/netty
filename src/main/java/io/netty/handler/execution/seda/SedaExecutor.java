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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.Channels;
import io.netty.handler.execution.ChannelEventRunnable;
import io.netty.util.ExternalResourceReleasable;

import java.util.concurrent.Executor;

/**
 * Abstract base class for SEDA bases {@link Executor} logic. 
 * 
 *
 */
public abstract class SedaExecutor implements Executor, ExternalResourceReleasable{
    
    @Override
    public void execute(Runnable command) {
        ChannelEventRunnable runnable = (ChannelEventRunnable) command;
        ChannelHandlerContext ctx = runnable.getContext();
        try {
            // check if the event was down or upstream
            if (runnable instanceof ChannelDownstreamEventRunnable) {
                executeDownstream((ChannelDownstreamEventRunnable) runnable);
            } else {
                executeUpstream(runnable);
            }
        } catch (Exception e1) {
            // handle exceptions
            Channels.fireExceptionCaught(ctx, e1);
        }
    }

    /**
     * Execute the given {@link ChannelDownstreamEventRunnable} which was triggerd by a downstream event
     * 
     * @param runnable
     * @throws Exception
     */
    protected abstract void executeDownstream(ChannelDownstreamEventRunnable runnable) throws Exception;
    
    /**
     * Execute the given {@link ChannelEventRunnable} which was triggered by an upstream event
     * 
     * @param runnable
     * @throws Exception
     */
    protected abstract void executeUpstream(ChannelEventRunnable runnable) throws Exception;

}
