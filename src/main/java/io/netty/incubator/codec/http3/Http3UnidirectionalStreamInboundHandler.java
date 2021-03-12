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
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;

import java.util.List;
import java.util.function.Function;
import java.util.function.LongFunction;

import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_CONTROL_STREAM_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_PUSH_STREAM_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_QPACK_DECODER_STREAM_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_QPACK_ENCODER_STREAM_TYPE;

/**
 * {@link ByteToMessageDecoder} which helps to detect the type of unidirectional stream.
 */
final class Http3UnidirectionalStreamInboundHandler extends ByteToMessageDecoder {
    private static final AttributeKey<Boolean> REMOTE_CONTROL_STREAM = AttributeKey.valueOf("H3_REMOTE_CONTROL_STREAM");
    private static final AttributeKey<Boolean> QPACK_DECODER_STREAM = AttributeKey.valueOf("H3_QPACK_DECODER_STREAM");
    private static final AttributeKey<Boolean> QPACK_ENCODER_STREAM = AttributeKey.valueOf("H3_QPACK_ENCODER_STREAM");

    private final Function<Http3FrameTypeValidator, ? extends ChannelHandler> codecFactory;
    private final Http3ControlStreamInboundHandler localControlStreamHandler;
    private final Http3ControlStreamOutboundHandler remoteControlStreamHandler;
    private final LongFunction<ChannelHandler> unknownStreamHandlerFactory;

    Http3UnidirectionalStreamInboundHandler(Function<Http3FrameTypeValidator, ? extends ChannelHandler> codecFactory,
                                            Http3ControlStreamInboundHandler localControlStreamHandler,
                                            Http3ControlStreamOutboundHandler remoteControlStreamHandler,
                                            LongFunction<ChannelHandler> unknownStreamHandlerFactory) {
        this.codecFactory = codecFactory;
        this.localControlStreamHandler = localControlStreamHandler;
        this.remoteControlStreamHandler = remoteControlStreamHandler;
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
                long pushId = Http3CodecUtils.readVariableLengthInteger(in, len);
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
                    codecFactory.apply(Http3ControlStreamFrameTypeValidator.INSTANCE));
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
    private void initPushStream(ChannelHandlerContext ctx, long id) {
        if (localControlStreamHandler.isServer()) {
            Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_STREAM_CREATION_ERROR,
                    "Server received push stream.", false);
        } else {
            // See https://tools.ietf.org/html/draft-ietf-quic-http-32#section-4.4
            Long maxPushId = remoteControlStreamHandler.sentMaxPushId();
            if (maxPushId == null) {
                Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_ID_ERROR,
                        "Received push stream before send MAX_PUSH_ID frame.", false);
            } else if (maxPushId < id) {
                Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_ID_ERROR,
                        "Received push stream with id " + id + " while the max push id is " + maxPushId + '.', false);
            } else {
                // TODO: Handle push streams correctly
                ctx.pipeline().addLast(Http3PushStreamValidationHandler.INSTANCE);
                // For now add a handler that will just release the frames so we dont leak.
                ctx.pipeline().addLast(ReleaseHandler.INSTANCE);

                // Replace this handler with the codec which also validates that only valid frames will be decoded.
                ctx.pipeline().replace(this, null,
                        codecFactory.apply(Http3PushStreamFrameTypeValidator.NO_VALIDATION));
            }
        }
    }

    /**
     * Called if the current {@link Channel} is a
     * <a href="https://www.ietf.org/archive/id/draft-ietf-quic-qpack-19.html#name-encoder-and-decoder-streams">
     *     QPACK encoder stream</a>.
     */
    private void initQpackEncoderStream(ChannelHandlerContext ctx) {
        if (ensureStreamNotExistsYet(ctx, QPACK_ENCODER_STREAM)) {
            // Just drop stuff on the floor as we dont support dynamic table atm.
            ctx.pipeline().replace(this, null,
                    new QpackEncoderHandler(remoteControlStreamHandler.localSettings()
                            .get(Http3SettingsFrame.HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY)));
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
        if (ensureStreamNotExistsYet(ctx, QPACK_DECODER_STREAM)) {
            // Just drop stuff on the floor as we dont support dynamic table atm.
            ctx.pipeline().replace(this, null, new QpackDecoderHandler());
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
