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

import io.netty.channel.ChannelHandler;
import io.netty.incubator.codec.quic.QuicStreamChannel;

import java.util.function.Supplier;

final class Http3UnidirectionalStreamInboundHandler extends Http3UnidirectionalStreamHandler {
    private final boolean server;
    private final ChannelHandler controlStreamHandler;
    private QuicStreamChannel remoteControlStream;
    private QuicStreamChannel qpackEncoderStream;
    private QuicStreamChannel qpackDecoderStream;

    Http3UnidirectionalStreamInboundHandler(boolean server, Supplier<Http3FrameCodec> codecSupplier,
                                            ChannelHandler controlStreamHandler) {
        super(codecSupplier);
        this.server = server;
        this.controlStreamHandler = controlStreamHandler;
    }

    @Override
    protected void initControlStream(QuicStreamChannel channel) {
        if (remoteControlStream == null) {
            remoteControlStream = channel;
            boolean forwardControlStreamFrames = controlStreamHandler != null;
            channel.pipeline().addLast(new Http3ControlStreamInboundHandler(server, forwardControlStreamFrames));
            if (forwardControlStreamFrames) {
                // The user want's to be notified about control frames, add the handler to the pipeline.
                channel.pipeline().addLast(controlStreamHandler);
            }
        } else {
            // Only one control stream is allowed.
            // See https://quicwg.org/base-drafts/draft-ietf-quic-http.html#section-6.2.1
            Http3CodecUtils.closeParent(channel, Http3ErrorCode.H3_STREAM_CREATION_ERROR,
                    "Received multiple control streams.");
        }
    }

    @Override
    protected void initPushStream(QuicStreamChannel channel, long id) {
        if (server) {
            Http3CodecUtils.closeParent(channel, Http3ErrorCode.H3_STREAM_CREATION_ERROR,
                    "Server received push stream.");
        } else {
            // TODO: Handle me
        }
    }

    @Override
    protected void initQpackEncoderStream(QuicStreamChannel channel) {
        if (qpackEncoderStream == null) {
            qpackEncoderStream = channel;
            // Just drop stuff on the floor as we dont support dynamic table atm.
            channel.pipeline().addLast(QpackStreamHandler.INSTANCE);
        } else {
            // Only one stream is allowed.
            // See https://www.ietf.org/archive/id/draft-ietf-quic-qpack-19.html#section-4.2
            Http3CodecUtils.closeParent(channel, Http3ErrorCode.H3_STREAM_CREATION_ERROR,
                    "Received multiple QPACK encoder streams.");
        }
    }

    @Override
    protected void initQpackDecoderStream(QuicStreamChannel channel) {
        if (qpackDecoderStream == null) {
            qpackDecoderStream = channel;
            // Just drop stuff on the floor as we dont support dynamic table atm.
            channel.pipeline().addLast(QpackStreamHandler.INSTANCE);
        } else {
            // Only one stream is allowed.
            // See https://www.ietf.org/archive/id/draft-ietf-quic-qpack-19.html#section-4.2
            Http3CodecUtils.closeParent(channel, Http3ErrorCode.H3_STREAM_CREATION_ERROR,
                    "Received multiple QPACK decoder streams.");
        }
    }
}
