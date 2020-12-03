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

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.incubator.codec.quic.QuicStreamChannel;

/**
 * {@link ChannelInboundHandlerAdapter} which makes it easy to handle
 * <a href="https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7">HTTP3 request streams</a>.
 */
public abstract class Http3RequestStreamHandler extends ChannelInboundHandlerAdapter {
    private static final Http3DataFrame EMPTY = new DefaultHttp3DataFrame(Unpooled.EMPTY_BUFFER);
    private boolean lastFrameDetected;
    private boolean firstFrameReceived;

    /**
     * Always returns {@code true} as this handler and sub-types are not sharable, due internal state.
     */
    @Override
    public final boolean isSharable() {
        return false;
    }

    @Override
    public final void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        firstFrameReceived = true;
        boolean inputShutdown = ((QuicStreamChannel) ctx.channel()).isInputShutdown();
        if (inputShutdown) {
            lastFrameDetected = true;
        }
        channelRead(ctx, (Http3RequestStreamFrame) msg, inputShutdown);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt == ChannelInputShutdownEvent.INSTANCE) {
            if (!lastFrameDetected && firstFrameReceived) {
                lastFrameDetected = true;
                channelRead(ctx, EMPTY, true);
            }
        }
        ctx.fireUserEventTriggered(evt);
    }

    /**
     * Called once a {@link Http3RequestStreamFrame} is ready for this stream to process.
     *
     * @param ctx           the {@link ChannelHandlerContext} of this handler.
     * @param frame         the {@link Http3RequestStreamFrame} that was read
     * @param isLast        {@code true} if this is the last frame that will be read for this stream.
     * @throws Exception    thrown if an error happens during processing.
     */
    public abstract void channelRead(ChannelHandlerContext ctx, Http3RequestStreamFrame frame, boolean isLast)
            throws Exception;
}
