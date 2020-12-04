/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.incubator.codec.http3;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelPromiseNotifier;
import io.netty.util.internal.ObjectUtil;

/**
 * {@link ChannelOutboundHandlerAdapter} which will intercept writes and dispatch {@link Http3ControlStreamFrame}s
 * to the local control stream. This allows to write {@link Http3ControlStreamFrame}s also from other streams.
 * that are no the control stream itself.
 */
final class Http3ControlStreamFrameDispatcher extends ChannelOutboundHandlerAdapter {
    private final Channel localControlStream;

    Http3ControlStreamFrameDispatcher(Channel localControlStream) {
        this.localControlStream = ObjectUtil.checkNotNull(localControlStream, "localControlStream");
    }

    /**
     * Shared per HTTP3 connection.
     */
    @Override
    public boolean isSharable() {
        return true;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        // Check if we are already on the local control frame or not.
        if (ctx.channel() != localControlStream && msg instanceof Http3ControlStreamFrame) {
            // write and flush as otherwise we may never flush the control stream for the write.
            localControlStream.writeAndFlush(msg).addListener(new ChannelPromiseNotifier(promise));
            return;
        }
        ctx.write(msg, promise);
    }
}
