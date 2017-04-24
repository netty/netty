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
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;

/**
 * {@link ChannelDuplexHandler} implementation which empowers back-pressure by stop reading from the remote peer
 * once a {@link Channel} becomes unwritable. In this case {@link ChannelHandlerContext#flush()} is called and
 * reads are continueed once the {@link Channel} becomes writable again.
 * This ensures we stop reading from the remote peer if we are writing faster then the remote peer can handle.
 *
 * Use this handler if {@link ChannelOption#AUTO_READ} is set to {@code false}, which is <strong>NOT</strong> the
 * default. If {@link ChannelOption#AUTO_READ} is set to {@code true} use {@link BackPressureAutoReadHandler} for
 * maximal performance.
 */
public final class BackPressureHandler extends ChannelDuplexHandler {

    private boolean readPending;
    private boolean writable = true;

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        if (writable) {
            ctx.read();
        } else {
            readPending = true;
        }
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        writable = ctx.channel().isWritable();
        if (writable) {
            if (readPending) {
                readPending = false;
                ctx.read();
            }
        } else {
            ctx.flush();
        }
        // Propergate the event as the user may still want to do something based on it.
        ctx.fireChannelWritabilityChanged();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        if (readPending) {
            ctx.read();
        }
    }
}
