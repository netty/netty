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
import io.netty.incubator.codec.quic.QuicStreamFrame;
import io.netty.util.internal.ObjectUtil;

import java.util.List;

import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_CANCEL_PUSH_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_DATA_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_GO_AWAY_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_HEADERS_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_MAX_PUSH_ID_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_PUSH_PROMISE_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_SETTINGS_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.numBytesForVariableLengthInteger;
import static io.netty.incubator.codec.http3.Http3CodecUtils.readVariableLengthInteger;

/**
 * Decodes {@link Http3Frame}s.
 */
final class Http3FrameDecoder extends ByteToMessageDecoder {

    private final long maxHeaderListSize;
    private final QpackDecoder qpackDecoder;

    private long type = -1;
    private long payLoadLength = -1;

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
            this.type = type;
            if (!in.isReadable()) {
                return;
            }
        }
        if (payLoadLength == -1) {
            int payloadLen = numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
            if (in.readableBytes() < payloadLen) {
                return;
            }
            payLoadLength = readVariableLengthInteger(in, payloadLen);
        }
        if (in.readableBytes() < payLoadLength) {
            return;
        }

        long type = this.type;
        int payLoadLength = (int) this.payLoadLength;
        int readerIndex = in.readerIndex();
        this.type = -1;
        this.payLoadLength = -1;
        try {
            if (type <= Integer.MAX_VALUE) {
                // See https://tools.ietf.org/html/draft-ietf-quic-http-32#section-11.2.1
                switch ((int) type) {
                    case HTTP3_DATA_FRAME_TYPE:
                        // DATA
                        // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.1
                        out.add(new DefaultHttp3DataFrame(in.readRetainedSlice(payLoadLength)));
                        break;
                    case HTTP3_HEADERS_FRAME_TYPE:
                        // HEADERS
                        // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.2
                        Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
                        if (decodeHeaders(ctx, headersFrame.headers(), in)) {
                            out.add(headersFrame);
                        }
                        break;
                    case HTTP3_CANCEL_PUSH_FRAME_TYPE:
                        // CANCEL_PUSH
                        // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.3
                        int pushIdLen = numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
                        out.add(new DefaultHttp3CancelPushFrame(readVariableLengthInteger(in, pushIdLen)));
                        break;
                    case HTTP3_SETTINGS_FRAME_TYPE:
                        // SETTINGS
                        // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.4
                        Http3SettingsFrame settingsFrame = decodeSettings(ctx, in, payLoadLength);
                        if (settingsFrame == null) {
                            // Decoding failed
                            return;
                        }
                        out.add(settingsFrame);
                        break;
                    case HTTP3_PUSH_PROMISE_FRAME_TYPE:
                        // PUSH_PROMISE
                        // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.5
                        int pushPromiseIdLen = numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
                        Http3PushPromiseFrame pushPromiseFrame = new DefaultHttp3PushPromiseFrame(
                                readVariableLengthInteger(in, pushPromiseIdLen));
                        if (decodeHeaders(ctx, pushPromiseFrame.headers(), in)) {
                            out.add(pushPromiseFrame);
                        }
                        break;
                    case HTTP3_GO_AWAY_FRAME_TYPE:
                        // GO_AWAY
                        // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.6
                        int idLen = numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
                        out.add(new DefaultHttp3GoAwayFrame(readVariableLengthInteger(in, idLen)));
                        break;
                    case HTTP3_MAX_PUSH_ID_FRAME_TYPE:
                        // MAX_PUSH_ID
                        // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.7
                        int pidLen = numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
                        out.add(new DefaultHttp3MaxPushIdFrame(readVariableLengthInteger(in, pidLen)));
                        break;
                    default:
                        // Handling reserved frame types
                        // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.8
                        out.add(new DefaultHttp3UnknownFrame(type, in.readRetainedSlice(payLoadLength)));
                        break;
                }
            }
        } finally {
            in.readerIndex(readerIndex + payLoadLength);
        }
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
