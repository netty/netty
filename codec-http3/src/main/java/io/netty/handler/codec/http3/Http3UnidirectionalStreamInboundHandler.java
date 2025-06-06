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
package io.netty.handler.codec.http3;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.http3.Http3FrameCodec.Http3FrameCodecFactory;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.LongFunction;
import java.util.function.Supplier;

import static io.netty.handler.codec.http3.Http3CodecUtils.HTTP3_CONTROL_STREAM_TYPE;
import static io.netty.handler.codec.http3.Http3CodecUtils.HTTP3_PUSH_STREAM_TYPE;
import static io.netty.handler.codec.http3.Http3CodecUtils.HTTP3_QPACK_DECODER_STREAM_TYPE;
import static io.netty.handler.codec.http3.Http3CodecUtils.HTTP3_QPACK_ENCODER_STREAM_TYPE;
import static io.netty.handler.codec.http3.Http3RequestStreamCodecState.NO_STATE;

/**
 * {@link ByteToMessageDecoder} which helps to detect the type of unidirectional stream.
 */
abstract class Http3UnidirectionalStreamInboundHandler extends ByteToMessageDecoder {
    private static final AttributeKey<Boolean> REMOTE_CONTROL_STREAM = AttributeKey.valueOf("H3_REMOTE_CONTROL_STREAM");
    private static final AttributeKey<Boolean> REMOTE_QPACK_DECODER_STREAM =
            AttributeKey.valueOf("H3_REMOTE_QPACK_DECODER_STREAM");
    private static final AttributeKey<Boolean> REMOTE_QPACK_ENCODER_STREAM =
            AttributeKey.valueOf("H3_REMOTE_QPACK_ENCODER_STREAM");

    final Http3FrameCodecFactory codecFactory;
    final Http3ControlStreamInboundHandler localControlStreamHandler;
    final Http3ControlStreamOutboundHandler remoteControlStreamHandler;
    final Supplier<ChannelHandler> qpackEncoderHandlerFactory;
    final Supplier<ChannelHandler> qpackDecoderHandlerFactory;
    final LongFunction<ChannelHandler> unknownStreamHandlerFactory;

    Http3UnidirectionalStreamInboundHandler(Http3FrameCodecFactory codecFactory,
                                            Http3ControlStreamInboundHandler localControlStreamHandler,
                                            Http3ControlStreamOutboundHandler remoteControlStreamHandler,
                                            @Nullable LongFunction<ChannelHandler> unknownStreamHandlerFactory,
                                            Supplier<ChannelHandler> qpackEncoderHandlerFactory,
                                            Supplier<ChannelHandler> qpackDecoderHandlerFactory) {
        this.codecFactory = codecFactory;
        this.localControlStreamHandler = localControlStreamHandler;
        this.remoteControlStreamHandler = remoteControlStreamHandler;
        this.qpackEncoderHandlerFactory = qpackEncoderHandlerFactory;
        this.qpackDecoderHandlerFactory = qpackDecoderHandlerFactory;
        if (unknownStreamHandlerFactory == null) {
            // If the user did not specify an own factory just drop all bytes on the floor.
            unknownStreamHandlerFactory = type -> ReleaseHandler.INSTANCE;
        }
        this.unknownStreamHandlerFactory = unknownStreamHandlerFactory;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (!in.isReadable()) {
            return;
        }
        int len = Http3CodecUtils.numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
        if (in.readableBytes() < len) {
            return;
        }
        long type = Http3CodecUtils.readVariableLengthInteger(in, len);
        switch ((int) type) {
            case HTTP3_CONTROL_STREAM_TYPE:
                initControlStream(ctx);
                break;
            case HTTP3_PUSH_STREAM_TYPE:
                int pushIdLen = Http3CodecUtils.numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
                if (in.readableBytes() < pushIdLen) {
                    return;
                }
                long pushId = Http3CodecUtils.readVariableLengthInteger(in, pushIdLen);
                initPushStream(ctx, pushId);
                break;
            case HTTP3_QPACK_ENCODER_STREAM_TYPE:
                // See https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#enc-dec-stream-def
                initQpackEncoderStream(ctx);
                break;
            case HTTP3_QPACK_DECODER_STREAM_TYPE:
                // See https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#enc-dec-stream-def
                initQpackDecoderStream(ctx);
                break;
            default:
                initUnknownStream(ctx, type);
                break;
        }
    }

    /**
     * Called if the current {@link Channel} is a
     * <a href="https://tools.ietf.org/html/draft-ietf-quic-http-32#section-6.2.1">control stream</a>.
     */
    private void initControlStream(ChannelHandlerContext ctx) {
        if (ctx.channel().parent().attr(REMOTE_CONTROL_STREAM).setIfAbsent(true) == null) {
            ctx.pipeline().addLast(localControlStreamHandler);
            // Replace this handler with the codec now.
            ctx.pipeline().replace(this, null,
                    codecFactory.newCodec(Http3ControlStreamFrameTypeValidator.INSTANCE, NO_STATE,
                            NO_STATE));
        } else {
            // Only one control stream is allowed.
            // See https://quicwg.org/base-drafts/draft-ietf-quic-http.html#section-6.2.1
            Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_STREAM_CREATION_ERROR,
                    "Received multiple control streams.", false);
        }
    }

    private boolean ensureStreamNotExistsYet(ChannelHandlerContext ctx, AttributeKey<Boolean> key) {
        return ctx.channel().parent().attr(key).setIfAbsent(true) == null;
    }

    /**
     * Called if the current {@link Channel} is a
     * <a href="https://tools.ietf.org/html/draft-ietf-quic-http-32#section-6.2.2">push stream</a>.
     */
    abstract void initPushStream(ChannelHandlerContext ctx, long id);

    /**
     * Called if the current {@link Channel} is a
     * <a href="https://www.ietf.org/archive/id/draft-ietf-quic-qpack-19.html#name-encoder-and-decoder-streams">
     *     QPACK encoder stream</a>.
     */
    private void initQpackEncoderStream(ChannelHandlerContext ctx) {
        if (ensureStreamNotExistsYet(ctx, REMOTE_QPACK_ENCODER_STREAM)) {
            // Just drop stuff on the floor as we dont support dynamic table atm.
            ctx.pipeline().replace(this, null, qpackEncoderHandlerFactory.get());
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
        if (ensureStreamNotExistsYet(ctx, REMOTE_QPACK_DECODER_STREAM)) {
            ctx.pipeline().replace(this, null, qpackDecoderHandlerFactory.get());
        } else {
            // Only one stream is allowed.
            // See https://www.ietf.org/archive/id/draft-ietf-quic-qpack-19.html#section-4.2
            Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_STREAM_CREATION_ERROR,
                    "Received multiple QPACK decoder streams.", false);
        }
    }

    /**
     * Called if we couldn't detect the stream type of the current {@link Channel}. Let's release everything that
     * we receive on this stream.
     */
    private void initUnknownStream(ChannelHandlerContext ctx, long streamType) {
        ctx.pipeline().replace(this, null, unknownStreamHandlerFactory.apply(streamType));
    }

    static final class ReleaseHandler extends ChannelInboundHandlerAdapter {
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
