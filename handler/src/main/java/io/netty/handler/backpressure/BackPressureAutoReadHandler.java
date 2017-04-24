/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.backpressure;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;

/**
 * {@link ChannelInboundHandlerAdapter} implementation which empowers back-pressure by stop reading from the remote peer
 * once a {@link Channel} becomes unwritable. In this case {@link ChannelHandlerContext#flush()} is called and
 * reads are continueed once the {@link Channel} becomes writable again.
 * This ensures we stop reading from the remote peer if we are writing faster then the remote peer can handle.
 *
 * Use this handler if {@link ChannelOption#AUTO_READ} is set to {@code true}, which is the
 * default. If {@link ChannelOption#AUTO_READ} is set to {@code false} you must use {@link BackPressureHandler}.
 */
public final class BackPressureAutoReadHandler extends ChannelInboundHandlerAdapter {
    // Keep track of the auto read state that we changed things to. This will be used when removing the
    // handler to detect if we need to restore the auto read setting.
    private boolean autoReadState;

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isWritable()) {
            ctx.channel().config().setAutoRead(true);
            autoReadState = true;
        } else {
            ctx.channel().config().setAutoRead(false);
            autoReadState = false;
            ctx.flush();
        }
        // Propergate the event as the user may still want to do something based on it.
        ctx.fireChannelWritabilityChanged();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        if (!ctx.channel().config().isAutoRead()) {
            throw new IllegalStateException(ChannelOption.AUTO_READ.name() + " must be set to true");
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        if (autoReadState) {
            // We should restore the auto-read setting as otherwise we may produce a stale as the user will
            // not know we stopped reading.
            ctx.channel().config().setAutoRead(true);
        }
    }
}
