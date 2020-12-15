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
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.ObjectUtil;

final class Http3ControlStreamOutboundHandler
        extends Http3FrameTypeValidationHandler<Http3ControlStreamFrame> {
    private final boolean server;
    private final Http3SettingsFrame localSettings;
    private final ChannelHandler codec;
    private Long sentMaxPushId;
    private Long sendGoAwayId;

    Http3ControlStreamOutboundHandler(boolean server, Http3SettingsFrame localSettings, ChannelHandler codec) {
        super(Http3ControlStreamFrame.class);
        this.server = server;
        this.localSettings = ObjectUtil.checkNotNull(localSettings, "localSettings");
        this.codec = ObjectUtil.checkNotNull(codec, "codec");
    }

    /**
     * Returns the last id that was sent in a MAX_PUSH_ID frame or {@code null} if none was sent yet.
     *
     * @return the id.
     */
    Long sentMaxPushId() {
        return sentMaxPushId;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // We need to write 0x00 into the stream before doing anything else.
        // See https://tools.ietf.org/html/draft-ietf-quic-http-32#section-6.2.1
        // Just allocate 8 bytes which would be the max needed.
        ByteBuf buffer = ctx.alloc().buffer(8);
        Http3CodecUtils.writeVariableLengthInteger(buffer, Http3CodecUtils.HTTP3_CONTROL_STREAM_TYPE);
        ctx.write(buffer);
        // Add the encoder and decoder in the pipeline so we can handle Http3Frames. This needs to happen after
        // we did write the type via a ByteBuf.
        ctx.pipeline().addFirst(codec);
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

    @Override
    public void write(ChannelHandlerContext ctx, Http3ControlStreamFrame msg, ChannelPromise promise) throws Exception {
        if (msg instanceof Http3MaxPushIdFrame) {
            sentMaxPushId = ((Http3MaxPushIdFrame) msg).id();
        }
        if (msg instanceof Http3GoAwayFrame) {
            Http3GoAwayFrame goAwayFrame = (Http3GoAwayFrame) msg;
            if (server) {
                // See https://tools.ietf.org/html/draft-ietf-quic-http-32#section-5.2
                long id = goAwayFrame.id();
                if (id % 4 != 0) {
                    ReferenceCountUtil.release(msg);
                    promise.setFailure(new Http3Exception(Http3ErrorCode.H3_ID_ERROR,
                            "GOAWAY id not valid : " + id));
                    return;
                }
                if (sendGoAwayId != null && id > sendGoAwayId) {
                    ReferenceCountUtil.release(msg);
                    promise.setFailure(new Http3Exception(Http3ErrorCode.H3_ID_ERROR,
                            "GOAWAY id is bigger then the last sent: " + id + " > " + sendGoAwayId));
                    return;
                }
                sendGoAwayId = id;
            } else {
                // TODO: Add logic for the client side as well.
            }
        }
        ctx.write(msg, promise);
    }

    @Override
    public boolean isSharable() {
        // This handle keeps state so we cant reuse it.
        return false;
    }
}
