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
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;

import java.util.List;
import java.util.function.Supplier;

/**
 * {@link ByteToMessageDecoder} which helps to detect the type of unidirectional stream.
 */
final class Http3UnidirectionalStreamInboundHandler extends ByteToMessageDecoder {
    private static final AttributeKey<Boolean> REMOTE_CONTROL_STREAM = AttributeKey.valueOf("H3_REMOTE_CONTROL_STREAM");
    private static final AttributeKey<Boolean> QPACK_DECODER_STREAM = AttributeKey.valueOf("H3_QPACK_DECODER_STREAM");
    private static final AttributeKey<Boolean> QPACK_ENCODER_STREAM = AttributeKey.valueOf("H3_QPACK_ENCODER_STREAM");

    private final Supplier<? extends ChannelHandler> codecSupplier;
    private final boolean server;
    private final ChannelHandler controlStreamHandler;

    Http3UnidirectionalStreamInboundHandler(boolean server, Supplier<? extends ChannelHandler> codecSupplier,
                                            ChannelHandler controlStreamHandler) {
        this.server = server;
        this.codecSupplier = codecSupplier;
        this.controlStreamHandler = controlStreamHandler;
    }

    private boolean isForwardingEvents() {
        return controlStreamHandler != null;
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
        if (type == 0x00) {
            initControlStream(ctx);
        } else if (type == 0x01) {
            int pushIdLen = Http3CodecUtils.numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
            if (in.readableBytes() < pushIdLen) {
                return;
            }
            long pushId = Http3CodecUtils.readVariableLengthInteger(in, len);
            initPushStream(ctx, pushId);
        } else if (type == 0x02) {
            // See https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#enc-dec-stream-def
            initQpackEncoderStream(ctx);
        } else if (type == 0x03) {
            // See https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#enc-dec-stream-def
            initQpackDecoderStream(ctx);
        } else {
            initUnknownStream(ctx, type, in);
        }
    }

    private void replaceThisWithCodec(ChannelPipeline pipeline) {
        // Replace this handler with the codec now.
       pipeline.replace(this, null, codecSupplier.get());
    }

    /**
     * Called if the current {@link Channel} is a
     * <a href="https://tools.ietf.org/html/draft-ietf-quic-http-32#section-6.2.1">control stream</a>.
     */
    private void initControlStream(ChannelHandlerContext ctx) {
        if (ctx.channel().parent().attr(REMOTE_CONTROL_STREAM).setIfAbsent(true) == null) {
            boolean forwardControlStreamFrames = isForwardingEvents();
            ctx.pipeline().addLast(new Http3ControlStreamInboundHandler(server, forwardControlStreamFrames));
            if (forwardControlStreamFrames) {
                // The user want's to be notified about control frames, add the handler to the pipeline.
                ctx.pipeline().addLast(controlStreamHandler);
            }
            // Replace this handler with the codec now.
            replaceThisWithCodec(ctx.pipeline());
        } else {
            // Only one control stream is allowed.
            // See https://quicwg.org/base-drafts/draft-ietf-quic-http.html#section-6.2.1
            Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_STREAM_CREATION_ERROR,
                    "Received multiple control streams.", false);
        }
    }

    /**
     * Called if the current {@link Channel} is a
     * <a href="https://tools.ietf.org/html/draft-ietf-quic-http-32#section-6.2.2">push stream</a>.
     */
    private void initPushStream(ChannelHandlerContext ctx, long id) {
        if (server) {
            Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_STREAM_CREATION_ERROR,
                    "Server received push stream.", false);
        } else {
            // TODO: Handle push streams correctly
            // Replace this handler with a handler that just drops bytes on the floor.
            ctx.pipeline().replace(this, null, ReleaseHandler.INSTANCE);
        }
    }

    /**
     * Called if the current {@link Channel} is a
     * <a href="https://www.ietf.org/archive/id/draft-ietf-quic-qpack-19.html#name-encoder-and-decoder-streams">
     *     QPACK encoder stream</a>.
     */
    private void initQpackEncoderStream(ChannelHandlerContext ctx) {
        if (ctx.channel().parent().attr(QPACK_ENCODER_STREAM).setIfAbsent(true) == null) {
            // Just drop stuff on the floor as we dont support dynamic table atm.
            ctx.pipeline().replace(this, null, QpackStreamHandler.INSTANCE);
        } else {
            // Only one stream is allowed.
            // See https://www.ietf.org/archive/id/draft-ietf-quic-qpack-19.html#section-4.2
            Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_STREAM_CREATION_ERROR,
                    "Received multiple QPACK encoder streams.", false);
        }
    }
    /**
     * Called if the current {@link Channel} is a
     * <a href="https://www.ietf.org/archive/id/draft-ietf-quic-qpack-19.html#name-encoder-and-decoder-streams">
     *     QPACK decoder stream</a>.
     */
    private void initQpackDecoderStream(ChannelHandlerContext ctx) {
        if (ctx.channel().parent().attr(QPACK_DECODER_STREAM).setIfAbsent(true) == null) {
            // Just drop stuff on the floor as we dont support dynamic table atm.
            ctx.pipeline().replace(this, null, QpackStreamHandler.INSTANCE);
        } else {
            // Only one stream is allowed.
            // See https://www.ietf.org/archive/id/draft-ietf-quic-qpack-19.html#section-4.2
            Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_STREAM_CREATION_ERROR,
                    "Received multiple QPACK decoder streams.", false);
        }
    }

    /**
     * Called if we couldn't detect the stream type of the current {@link Channel}.
     */
    protected void initUnknownStream(ChannelHandlerContext ctx,
                                     @SuppressWarnings("unused") long streamType,
                                     @SuppressWarnings("unused") ByteBuf in) throws Exception {
        ctx.close();
    }

    private static final class ReleaseHandler extends ChannelInboundHandlerAdapter {
        static final ReleaseHandler INSTANCE = new ReleaseHandler();

        @Override
        public boolean isSharable() {
            return true;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ReferenceCountUtil.release(msg);
        }
    }
}
