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

import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.execution.ExecutionHandler;

/**
 * {@link ExecutionHandler} which submit all downstream and upstream events to the given {@link SedaExecutor}. The {@link SedaExecutor} is responsible for hand of the events
 * to the different {@link Executor}'s and so build up an <a href="http://en.wikipedia.org/wiki/Staged_event-driven_architecture">SEDA</a> architecture.
 * 
 *
 */
public class SedaHandler extends ExecutionHandler {

    public SedaHandler(SedaExecutor executor) {
        super(executor);
    }

    @Override
    public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        handleReadSuspend(ctx, e);
        getExecutor().execute(new ChannelDownstreamEventRunnable(ctx, e));
    }

    @Override
    public void releaseExternalResources() {
        ((SedaExecutor) getExecutor()).releaseExternalResources();
    }

}
