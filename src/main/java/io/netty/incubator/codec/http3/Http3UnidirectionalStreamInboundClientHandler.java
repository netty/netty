/*
 * Copyright 2021 The Netty Project
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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.incubator.codec.http3.Http3FrameCodec.Http3FrameCodecFactory;

import java.util.function.LongFunction;
import java.util.function.Supplier;

final class Http3UnidirectionalStreamInboundClientHandler extends Http3UnidirectionalStreamInboundHandler {
    private final LongFunction<ChannelHandler> pushStreamHandlerFactory;

    Http3UnidirectionalStreamInboundClientHandler(
            Http3FrameCodecFactory codecFactory,
            Http3ControlStreamInboundHandler localControlStreamHandler,
            Http3ControlStreamOutboundHandler remoteControlStreamHandler,
            LongFunction<ChannelHandler> unknownStreamHandlerFactory,
            LongFunction<ChannelHandler> pushStreamHandlerFactory,
            Supplier<ChannelHandler> qpackEncoderHandlerFactory, Supplier<ChannelHandler> qpackDecoderHandlerFactory) {
        super(codecFactory, localControlStreamHandler, remoteControlStreamHandler, unknownStreamHandlerFactory,
                qpackEncoderHandlerFactory, qpackDecoderHandlerFactory);
        this.pushStreamHandlerFactory = pushStreamHandlerFactory == null ? __ -> ReleaseHandler.INSTANCE :
                pushStreamHandlerFactory;
    }

    @Override
    void initPushStream(ChannelHandlerContext ctx, long pushId) {
        // See https://tools.ietf.org/html/draft-ietf-quic-http-32#section-4.4
        Long maxPushId = remoteControlStreamHandler.sentMaxPushId();
        if (maxPushId == null) {
            Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_ID_ERROR,
                    "Received push stream before sending MAX_PUSH_ID frame.", false);
        } else if (maxPushId < pushId) {
            Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_ID_ERROR,
                    "Received push stream with ID " + pushId + " while the max push id is " + maxPushId + '.', false);
        } else {
            // Replace this handler with the actual push stream handlers.
            final ChannelHandler pushStreamHandler = pushStreamHandlerFactory.apply(pushId);
            ctx.pipeline().replace(this, null, pushStreamHandler);
        }
    }
}
