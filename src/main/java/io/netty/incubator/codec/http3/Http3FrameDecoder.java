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
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.incubator.codec.quic.QuicStreamFrame;
import io.netty.util.internal.ObjectUtil;

import java.util.List;

import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_CANCEL_PUSH_FRAME_MAX_LEN;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_CANCEL_PUSH_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_DATA_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_GO_AWAY_FRAME_MAX_LEN;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_GO_AWAY_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_HEADERS_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_MAX_PUSH_ID_FRAME_MAX_LEN;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_MAX_PUSH_ID_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_PUSH_PROMISE_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_SETTINGS_FRAME_MAX_LEN;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_SETTINGS_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.numBytesForVariableLengthInteger;
import static io.netty.incubator.codec.http3.Http3CodecUtils.readVariableLengthInteger;

/**
 * Decodes {@link Http3Frame}s.
 */
final class Http3FrameDecoder extends ByteToMessageDecoder {

    private final long maxHeaderListSize;
    private final QpackDecoder qpackDecoder;

    private int type = -1;
    private int payLoadLength = -1;

    Http3FrameDecoder(QpackDecoder qpackDecoder, long maxHeaderListSize) {
        this.qpackDecoder = ObjectUtil.checkNotNull(qpackDecoder, "qpackDecoder");
        this.maxHeaderListSize = ObjectUtil.checkPositive(maxHeaderListSize, "maxHeaderListSize");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buffer;
        if (msg instanceof QuicStreamFrame) {
            QuicStreamFrame streamFrame = (QuicStreamFrame) msg;
            buffer = streamFrame.content().retain();
            streamFrame.release();
        } else {
            buffer = (ByteBuf) msg;
        }
        super.channelRead(ctx, buffer);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (!in.isReadable()) {
            return;
        }
        if (type == -1) {
            int typeLen = numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
            if (in.readableBytes() < typeLen) {
                return;
            }
            long type = readVariableLengthInteger(in, typeLen);
            if (Http3CodecUtils.isReservedHttp2FrameType(type)) {
                // See https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.8
                Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_FRAME_UNEXPECTED,
                        "Reserved type for HTTP/2 received.", true);
                return;
            }
            if (type > Integer.MAX_VALUE) {
                Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_FRAME_ERROR,
                        "Received an invalid frame type.", true);
                return;
            }
            this.type = (int) type;
            if (!in.isReadable()) {
                return;
            }
        }
        if (payLoadLength == -1) {
            int payloadLen = numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
            assert payloadLen <= 8;
            if (in.readableBytes() < payloadLen) {
                return;
            }
            long len = readVariableLengthInteger(in, payloadLen);
            if (len > Integer.MAX_VALUE) {
                Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_EXCESSIVE_LOAD,
                        "Received an invalid frame len.", true);
                return;
            }
            payLoadLength = (int) len;
        }
        int read = decodeFrame(ctx, type, payLoadLength, in, out);
        if (read > 0) {
            if (read == payLoadLength) {
                type = -1;
                payLoadLength = -1;
            } else {
                payLoadLength -= read;
            }
        }
    }

    private int decodeFrame(ChannelHandlerContext ctx, int type, int payLoadLength, ByteBuf in, List<Object> out) {
        // See https://tools.ietf.org/html/draft-ietf-quic-http-32#section-11.2.1
        switch (type) {
            case HTTP3_DATA_FRAME_TYPE:
                // DATA
                // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.1
                int readable = in.readableBytes();
                if (readable == 0 && payLoadLength > 0) {
                    return 0;
                }
                int length = Math.min(readable, payLoadLength);
                out.add(new DefaultHttp3DataFrame(in.readRetainedSlice(length)));
                return length;
            case HTTP3_HEADERS_FRAME_TYPE:
                // HEADERS
                // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.2

                if (!enforceMaxPayloadLength(ctx, in, type, payLoadLength,
                        // Let's use the maxHeaderListSize as a limit as this is this is the decompressed amounts of
                        // bytes which means the once we decompressed the headers we will be bigger then the actual
                        // payload size now.
                        maxHeaderListSize, Http3ErrorCode.H3_EXCESSIVE_LOAD)) {
                    return 0;
                }
                Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
                if (decodeHeaders(ctx, headersFrame.headers(), in.readSlice(payLoadLength))) {
                    if (headersFrame.headers().contains(HttpHeaderNames.CONNECTION)) {
                        ctx.fireExceptionCaught(new Http3Exception(Http3ErrorCode.H3_MESSAGE_ERROR,
                                "connection header included"));
                        // We should close the stream.
                        // See https://quicwg.org/base-drafts/draft-ietf-quic-http.html#section-4.1.1
                        ctx.close();
                        return payLoadLength;
                    }
                    CharSequence value = headersFrame.headers().get(HttpHeaderNames.TE);
                    if (value != null && !HttpHeaderValues.TRAILERS.equals(value)) {
                        ctx.fireExceptionCaught(new Http3Exception(Http3ErrorCode.H3_MESSAGE_ERROR,
                                "te header field included with invalid value: " + value));
                        // We should close the stream.
                        // See https://quicwg.org/base-drafts/draft-ietf-quic-http.html#section-4.1.1
                        ctx.close();
                        return payLoadLength;
                    }
                    out.add(headersFrame);
                }
                return payLoadLength;
            case HTTP3_CANCEL_PUSH_FRAME_TYPE:
                // CANCEL_PUSH
                // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.3
                if (!enforceMaxPayloadLength(ctx, in, type, payLoadLength,
                        HTTP3_CANCEL_PUSH_FRAME_MAX_LEN, Http3ErrorCode.H3_FRAME_ERROR)) {
                    return 0;
                }
                int pushIdLen = numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
                out.add(new DefaultHttp3CancelPushFrame(readVariableLengthInteger(in, pushIdLen)));
                return payLoadLength;
            case HTTP3_SETTINGS_FRAME_TYPE:
                // SETTINGS
                // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.4

                // Use 256 as this gives space for 16 maximal size encoder and 128 minimal size encoded settings.
                if (!enforceMaxPayloadLength(ctx, in, type, payLoadLength, HTTP3_SETTINGS_FRAME_MAX_LEN,
                        Http3ErrorCode.H3_EXCESSIVE_LOAD)) {
                    return 0;
                }
                Http3SettingsFrame settingsFrame = decodeSettings(ctx, in, payLoadLength);
                if (settingsFrame != null) {
                    out.add(settingsFrame);
                }
                return payLoadLength;
            case HTTP3_PUSH_PROMISE_FRAME_TYPE:
                // PUSH_PROMISE
                // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.5
                if (!enforceMaxPayloadLength(ctx, in, type, payLoadLength,
                        // Let's use the maxHeaderListSize as a limit as this is this is the decompressed amounts of
                        // bytes which means the once we decompressed the headers we will be bigger then the actual
                        // payload size now.
                        Math.max(maxHeaderListSize, maxHeaderListSize + 8), Http3ErrorCode.H3_EXCESSIVE_LOAD)) {
                    return 0;
                }
                int pushPromiseIdLen = numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
                Http3PushPromiseFrame pushPromiseFrame = new DefaultHttp3PushPromiseFrame(
                        readVariableLengthInteger(in, pushPromiseIdLen));
                if (decodeHeaders(ctx, pushPromiseFrame.headers(),
                        in.readSlice(payLoadLength - pushPromiseIdLen))) {
                    out.add(pushPromiseFrame);
                }
                return payLoadLength;
            case HTTP3_GO_AWAY_FRAME_TYPE:
                // GO_AWAY
                // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.6
                if (!enforceMaxPayloadLength(ctx, in, type, payLoadLength,
                        HTTP3_GO_AWAY_FRAME_MAX_LEN, Http3ErrorCode.H3_FRAME_ERROR)) {
                    return 0;
                }
                int idLen = numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
                out.add(new DefaultHttp3GoAwayFrame(readVariableLengthInteger(in, idLen)));
                return payLoadLength;
            case HTTP3_MAX_PUSH_ID_FRAME_TYPE:
                // MAX_PUSH_ID
                // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.7
                if (!enforceMaxPayloadLength(ctx, in, type, payLoadLength,
                        HTTP3_MAX_PUSH_ID_FRAME_MAX_LEN, Http3ErrorCode.H3_FRAME_ERROR)) {
                    return 0;
                }
                int pidLen = numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
                out.add(new DefaultHttp3MaxPushIdFrame(readVariableLengthInteger(in, pidLen)));
                return payLoadLength;
            default:
                // Handling reserved frame types
                // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.8
                if (in.readableBytes() < payLoadLength) {
                    return 0;
                }
                out.add(new DefaultHttp3UnknownFrame(type, in.readRetainedSlice(payLoadLength)));
                return payLoadLength;
        }
    }

    private static boolean enforceMaxPayloadLength(
            ChannelHandlerContext ctx, ByteBuf in, int type, int payLoadLength,
            long maxPayLoadLength, Http3ErrorCode error) {
        if (payLoadLength > maxPayLoadLength) {
            Http3CodecUtils.connectionError(ctx, error,
                    "Received an invalid frame len " + payLoadLength + " for frame of type " + type + '.', true);
            return false;
        }
        return in.readableBytes() >= payLoadLength;
    }

    private static Http3SettingsFrame decodeSettings(ChannelHandlerContext ctx, ByteBuf in, int payLoadLength) {
        Http3SettingsFrame settingsFrame = new DefaultHttp3SettingsFrame();
        while (payLoadLength > 0) {
            int keyLen = numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
            long key = readVariableLengthInteger(in, keyLen);
            if (Http3CodecUtils.isReservedHttp2Setting(key)) {
                // This must be treated as a connection error
                // See https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.4.1
                Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_SETTINGS_ERROR,
                        "Received a settings key that is reserved for HTTP/2.", true);
                return null;
            }
            payLoadLength -= keyLen;
            int valueLen = numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
            long value = readVariableLengthInteger(in, valueLen);
            payLoadLength -= valueLen;

            if (settingsFrame.put(key, value) != null) {
                // This must be treated as a connection error
                // See https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.4
                Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_SETTINGS_ERROR,
                        "Received a duplicate settings key.", true);
                return null;
            }
        }
        return settingsFrame;
    }

    /**
     * Decode the header block into header fields.
     * <p>
     * This method assumes the entire header block is contained in {@code in}.
     */
    private boolean decodeHeaders(ChannelHandlerContext ctx, Http3Headers headers, ByteBuf in) {
        try {
            Http3HeadersSink sink = new Http3HeadersSink(headers, maxHeaderListSize, true);
            qpackDecoder.decode(in, sink);
            // Throws exception if detected any problem so far
            sink.finish();
        } catch (Http3Exception e) {
            Http3CodecUtils.connectionError(ctx, e, true);
            return false;
        } catch (QpackException e) {
            // Must be treated as a connection error.
            Http3CodecUtils.connectionError(ctx, Http3ErrorCode.QPACK_DECOMPRESSION_FAILED,
                    "Decompression of header block failed.", true);
            return false;
        } catch (Http3HeadersValidationException e) {
            ctx.fireExceptionCaught(e);
            // We should close the stream.
            // See https://tools.ietf.org/html/draft-ietf-quic-http-32#section-4.1.3
            ctx.close();
            return false;
        }
        return true;
    }
}
