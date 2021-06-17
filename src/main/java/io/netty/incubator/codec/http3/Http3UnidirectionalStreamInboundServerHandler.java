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

final class Http3UnidirectionalStreamInboundServerHandler extends Http3UnidirectionalStreamInboundHandler {

    Http3UnidirectionalStreamInboundServerHandler(Http3FrameCodecFactory codecFactory,
                                                  Http3ControlStreamInboundHandler localControlStreamHandler,
                                                  Http3ControlStreamOutboundHandler remoteControlStreamHandler,
                                                  LongFunction<ChannelHandler> unknownStreamHandlerFactory,
                                                  Supplier<ChannelHandler> qpackEncoderHandlerFactory,
                                                  Supplier<ChannelHandler> qpackDecoderHandlerFactory) {
        super(codecFactory, localControlStreamHandler, remoteControlStreamHandler, unknownStreamHandlerFactory,
                qpackEncoderHandlerFactory, qpackDecoderHandlerFactory);
    }

    @Override
    void initPushStream(ChannelHandlerContext ctx, long id) {
        Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_STREAM_CREATION_ERROR,
                "Server received push stream.", false);
    }
}
