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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.incubator.codec.quic.QuicStreamChannel;

import java.util.List;

/**
 * {@link ByteToMessageDecoder} which helps to detect the type of unidirectional stream.
 */
abstract class Http3UnidirectionalStreamHandler extends ByteToMessageDecoder {
    private final QpackDecoder qpackDecoder;
    private final QpackEncoder qpackEncoder;

    Http3UnidirectionalStreamHandler(QpackDecoder qpackDecoder, QpackEncoder qpackEncoder) {
        this.qpackDecoder = qpackDecoder;
        this.qpackEncoder = qpackEncoder;
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
            // We need to add the encoder / decoder before calling initControlStream(...) as this may also add handlers
            // to the pipeline.
            streamChannel.pipeline().addLast(
                    new Http3FrameEncoder(qpackEncoder),
                    new Http3FrameDecoder(qpackDecoder));
            initControlStream(streamChannel);
        } else if (type == 0x01) {
            int pushIdLen = Http3CodecUtils.numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
            if (in.readableBytes() < pushIdLen) {
                return;
            }
            long pushId = Http3CodecUtils.readVariableLengthInteger(in, len);
            // We need to add the encoder / decoder before calling initPushStream(...) as this may also add handlers
            // to the pipeline.
            streamChannel.pipeline().addLast(
                    new Http3FrameEncoder(qpackEncoder),
                    new Http3FrameDecoder(qpackDecoder));
            initPushStream(streamChannel, pushId);
        } else if (type == 0x02) {
            // See https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#enc-dec-stream-def
            initQpackEncoderStream(streamChannel);
        } else if (type == 0x03) {
            // See https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#enc-dec-stream-def
            initQpackDecoderStream(streamChannel);
        } else {
            if (initUnknownStream((QuicStreamChannel) ctx.channel(), type, in)) {
                // Ensure we add the encoder / decoder in the right place.
                ctx.pipeline().addAfter(ctx.name(), null, new Http3FrameEncoder(qpackEncoder));
                ctx.pipeline().addAfter(ctx.pipeline().context(Http3FrameEncoder.class).name(), null,
                        new Http3FrameDecoder(qpackDecoder));
            } else {
                return;
            }
        }
        ctx.pipeline().remove(this);
    }

    /**
     * Called if the current {@link QuicStreamChannel} is a
     * <a href="https://tools.ietf.org/html/draft-ietf-quic-http-32#section-6.2.1">control stream</a>.
     */
    protected abstract void initControlStream(QuicStreamChannel channel) throws Exception;

    /**
     * Called if the current {@link QuicStreamChannel} is a
     * <a href="https://tools.ietf.org/html/draft-ietf-quic-http-32#section-6.2.2">push stream</a>.
     */
    protected abstract void initPushStream(QuicStreamChannel channel, long id) throws Exception;
    /**
     * Called if the current {@link QuicStreamChannel} is a
     * <a href="https://www.ietf.org/archive/id/draft-ietf-quic-qpack-19.html#name-encoder-and-decoder-streams">
     *     QPACK encoder stream</a>.
     */
    protected abstract void initQpackEncoderStream(QuicStreamChannel channel) throws Exception;
    /**
     * Called if the current {@link QuicStreamChannel} is a
     * <a href="https://www.ietf.org/archive/id/draft-ietf-quic-qpack-19.html#name-encoder-and-decoder-streams">
     *     QPACK decoder stream</a>.
     */
    protected abstract void initQpackDecoderStream(QuicStreamChannel channel) throws Exception;

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
