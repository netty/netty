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
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.incubator.codec.quic.QuicStreamChannel;

import java.util.List;
import java.util.function.Supplier;

/**
 * {@link ByteToMessageDecoder} which helps to detect the type of unidirectional stream.
 */
final class Http3UnidirectionalStreamInboundHandler extends ByteToMessageDecoder {
    private final Supplier<Http3FrameCodec> codecSupplier;
    private final boolean server;
    private final ChannelHandler controlStreamHandler;
    private QuicStreamChannel remoteControlStream;
    private QuicStreamChannel qpackEncoderStream;
    private QuicStreamChannel qpackDecoderStream;

    Http3UnidirectionalStreamInboundHandler(boolean server, Supplier<Http3FrameCodec> codecSupplier,
                                            ChannelHandler controlStreamHandler) {
        this.server = server;
        this.codecSupplier = codecSupplier;
        this.controlStreamHandler = controlStreamHandler;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (!in.isReadable()) {
            return;
        }
        int len = Http3CodecUtils.numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
        if (in.readableBytes() < len) {
            return;
        }
        long type = Http3CodecUtils.readVariableLengthInteger(in, len);
        QuicStreamChannel streamChannel = (QuicStreamChannel) ctx.channel();
        if (type == 0x00) {
            initControlStream(streamChannel);
            // Replace this handler with the codec now.
            replaceThisWithCodec(ctx.pipeline());
        } else if (type == 0x01) {
            int pushIdLen = Http3CodecUtils.numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
            if (in.readableBytes() < pushIdLen) {
                return;
            }
            long pushId = Http3CodecUtils.readVariableLengthInteger(in, len);
            initPushStream(streamChannel, pushId);
            // Replace this handler with the codec now.
            replaceThisWithCodec(ctx.pipeline());
        } else if (type == 0x02) {
            // See https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#enc-dec-stream-def
            initQpackEncoderStream(streamChannel);
            // Replace this handler with the codec now.
            replaceThisWithCodec(ctx.pipeline());
        } else if (type == 0x03) {
            // See https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#enc-dec-stream-def
            initQpackDecoderStream(streamChannel);
            // Replace this handler with the codec now.
            replaceThisWithCodec(ctx.pipeline());
        } else if (initUnknownStream((QuicStreamChannel) ctx.channel(), type, in)) {
            // Ensure we add the encoder / decoder in the right place.
            replaceThisWithCodec(ctx.pipeline());
        }
    }

    private void replaceThisWithCodec(ChannelPipeline pipeline) {
        // Replace this handler with the codec now.
       pipeline.replace(this, null, codecSupplier.get());
    }

    /**
     * Called if the current {@link QuicStreamChannel} is a
     * <a href="https://tools.ietf.org/html/draft-ietf-quic-http-32#section-6.2.1">control stream</a>.
     */
    private void initControlStream(QuicStreamChannel channel) {
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

    /**
     * Called if the current {@link QuicStreamChannel} is a
     * <a href="https://tools.ietf.org/html/draft-ietf-quic-http-32#section-6.2.2">push stream</a>.
     */
    private void initPushStream(QuicStreamChannel channel, long id) {
        if (server) {
            Http3CodecUtils.closeParent(channel, Http3ErrorCode.H3_STREAM_CREATION_ERROR,
                    "Server received push stream.");
        } else {
            // TODO: Handle me
        }
    }
    /**
     * Called if the current {@link QuicStreamChannel} is a
     * <a href="https://www.ietf.org/archive/id/draft-ietf-quic-qpack-19.html#name-encoder-and-decoder-streams">
     *     QPACK encoder stream</a>.
     */
    private void initQpackEncoderStream(QuicStreamChannel channel) {
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
    /**
     * Called if the current {@link QuicStreamChannel} is a
     * <a href="https://www.ietf.org/archive/id/draft-ietf-quic-qpack-19.html#name-encoder-and-decoder-streams">
     *     QPACK decoder stream</a>.
     */
    private void initQpackDecoderStream(QuicStreamChannel channel) {
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

    /**
     * Called if we couldn't detect the stream type of the current  {@link QuicStreamChannel}.
     */
    protected boolean initUnknownStream(QuicStreamChannel channel,
                                     @SuppressWarnings("unused") long streamType,
                                     @SuppressWarnings("unused") ByteBuf in) throws Exception {
        channel.close();
        return true;
    }
}
