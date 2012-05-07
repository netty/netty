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
package io.netty.channel.socket;

import io.netty.channel.Channel;
import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelStateEvent;
import io.netty.channel.SimpleChannelUpstreamHandler;

/**
 * A {@link SimpleChannelUpstreamHandler} which provides an individual handler method
 * for each {@link SocketChannel} specific event type and also for the {@link Channel} specific event types.
 * 
 * See the {@link SimpleChannelUpstreamHandler} docs for for informations.
 * 
 */
public class SimpleSocketChannelUpstreamHandler extends SimpleChannelUpstreamHandler {

    @Override
    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent evt = (ChannelStateEvent) e;
            switch (evt.getState()) {
            case OPEN_INPUT:
                if (Boolean.FALSE.equals(evt.getValue())) {
                    channelInputClosed(ctx, evt);
                }
                return;
            case OPEN_OUTPUT:
                if (Boolean.FALSE.equals(evt.getValue())) {
                    channelOutputClosed(ctx, evt);
                }
                return;
                

            }
        }
        super.handleUpstream(ctx, e);
    }

    /**
     * Invoked when {@link SocketChannel#closeOutput()} was called.
     */
    public void channelOutputClosed(ChannelHandlerContext ctx, ChannelStateEvent evt) throws Exception { 
        ctx.sendUpstream(evt);
    }

    /**
     * Invoked when {@link SocketChannel#closeInput()} was called.
     */
    public void channelInputClosed(ChannelHandlerContext ctx, ChannelStateEvent evt) throws Exception {
        ctx.sendUpstream(evt);
    }
}
