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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.util.internal.ObjectUtil;

import java.util.function.Supplier;

final class Http3ControlStreamOutboundHandler
        extends Http3FrameTypeValidationHandler<Http3ControlStreamFrame> {
    private final Http3SettingsFrame localSettings;
    private final Supplier<? extends ChannelHandler> codecSupplier;

    Http3ControlStreamOutboundHandler(Http3SettingsFrame localSettings,
                                      Supplier<? extends ChannelHandler> codecSupplier) {
        super(Http3ControlStreamFrame.class);
        this.localSettings = ObjectUtil.checkNotNull(localSettings, "localSettings");
        this.codecSupplier = ObjectUtil.checkNotNull(codecSupplier, "codecSupplier");
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // We need to write 0x00 into the stream before doing anything else.
        // See https://tools.ietf.org/html/draft-ietf-quic-http-32#section-6.2.1
        // Just allocate 8 bytes which would be the max needed.
        ByteBuf buffer = ctx.alloc().directBuffer(8);
        Http3CodecUtils.writeVariableLengthInteger(buffer, 0x00);
        ctx.write(buffer);
        // Add the encoder and decoder in the pipeline so we can handle Http3Frames
        ctx.pipeline().addFirst(codecSupplier.get());
        // If writing of the local settings fails let's just teardown the connection.
        ctx.writeAndFlush(localSettings).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);

        ctx.fireChannelActive();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof ChannelInputShutdownEvent) {
            // See https://tools.ietf.org/html/draft-ietf-quic-http-32#section-6.2.1
            Http3CodecUtils.criticalStreamClosed(ctx);
        }
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        // See https://tools.ietf.org/html/draft-ietf-quic-http-32#section-6.2.1
        Http3CodecUtils.criticalStreamClosed(ctx);
        ctx.fireChannelInactive();
    }
}
