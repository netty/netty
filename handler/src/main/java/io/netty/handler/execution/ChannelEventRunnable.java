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
package io.netty.handler.execution;

import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.EstimatableObjectWrapper;

public abstract class ChannelEventRunnable implements Runnable, EstimatableObjectWrapper {
    
    protected final ChannelHandlerContext ctx;
    protected final ChannelEvent e;
    int estimatedSize;

    /**
     * Creates a {@link Runnable} which sends the specified {@link ChannelEvent}
     * upstream via the specified {@link ChannelHandlerContext}.
     */
    public ChannelEventRunnable(ChannelHandlerContext ctx, ChannelEvent e) {
        this.ctx = ctx;
        this.e = e;
    }

    /**
     * Returns the {@link ChannelHandlerContext} which will be used to
     * send the {@link ChannelEvent} upstream.
     */
    public ChannelHandlerContext getContext() {
        return ctx;
    }

    /**
     * Returns the {@link ChannelEvent} which will be sent upstream.
     */
    public ChannelEvent getEvent() {
        return e;
    }

    @Override
    public Object unwrap() {
        return e;
    }
}
