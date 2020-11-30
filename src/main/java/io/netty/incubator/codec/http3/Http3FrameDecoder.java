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

import static io.netty.incubator.codec.http3.Http3CodecUtils.numBytesForVariableLengthInteger;
import static io.netty.incubator.codec.http3.Http3CodecUtils.readVariableLengthInteger;

/**
 * Decodes {@link Http3Frame}s.
 */
public final class Http3FrameDecoder extends ByteToMessageDecoder {
    private final QpackDecoder qpackDecoder;

    private long type = -1;
    private long payLoadLength = -1;

    // TODO: Do something with this...
    private boolean fin;

    Http3FrameDecoder(QpackDecoder qpackDecoder) {
        this.qpackDecoder = ObjectUtil.checkNotNull(qpackDecoder, "qpackDecoder");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buffer;
        if (msg instanceof QuicStreamFrame) {
            QuicStreamFrame streamFrame = (QuicStreamFrame) msg;
            fin = streamFrame.hasFin();
            buffer = streamFrame.content().retain();
            streamFrame.release();
        } else {
            buffer = (ByteBuf) msg;
        }
        super.channelRead(ctx, buffer);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (!in.isReadable()) {
            return;
        }
        if (type == -1) {
            int typeLen = numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
            if (in.readableBytes() < typeLen) {
                return;
            }
            type = readVariableLengthInteger(in, typeLen);
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
                switch ((int) type) {
                    case 0x0:
                        // DATA
                        // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.1
                        out.add(new DefaultHttp3DataFrame(in.readRetainedSlice(payLoadLength)));
                        break;
                    case 0x1:
                        // HEADERS
                        // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.2
                        Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
                        decodeHeaders(headersFrame.headers(), in);
                        out.add(headersFrame);
                        break;
                    case 0x3:
                        // CANCEL_PUSH
                        // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.3
                        int pushIdLen = numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
                        out.add(new DefaultHttp3CancelPushFrame(readVariableLengthInteger(in, pushIdLen)));
                        break;
                    case 0x4:
                        // SETTINGS
                        // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.4
                        out.add(decodeSettings(in, payLoadLength));
                        break;
                    case 0x5:
                        // PUSH_PROMISE
                        // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.5
                        int pushPromiseIdLen = numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
                        Http3PushPromiseFrame pushPromiseFrame = new DefaultHttp3PushPromiseFrame(
                                readVariableLengthInteger(in, pushPromiseIdLen));
                        decodeHeaders(pushPromiseFrame.headers(), in);
                        out.add(pushPromiseFrame);
                        break;
                    case 0x7:
                        // GO_AWAY
                        // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.6
                        int idLen = numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
                        out.add(new DefaultHttp3GoAwayFrame(readVariableLengthInteger(in, idLen)));
                        break;
                    case 0xd:
                        // MAX_PUSH_ID
                        // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.7
                        int pidLen = numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
                        out.add(new DefaultHttp3MaxPushIdFrame(readVariableLengthInteger(in, pidLen)));
                        break;
                    default:
                        // Handling reserved frame types
                        // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.8
                        break;
                }
            }
        } finally {
            in.readerIndex(readerIndex + payLoadLength);
        }
    }

    @Override
    protected void decodeLast(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        super.decodeLast(ctx, in, out);
        fin = true;
    }

    private static Http3SettingsFrame decodeSettings(ByteBuf in, int payLoadLength) {
        Http3SettingsFrame settingsFrame = new DefaultHttp3SettingsFrame();
        while (payLoadLength > 0) {
            int keyLen = numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
            long key = readVariableLengthInteger(in, keyLen);
            payLoadLength -= keyLen;
            int valueLen = numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
            long value = readVariableLengthInteger(in, valueLen);
            payLoadLength -= valueLen;

            settingsFrame.put(key, value);
        }
        return settingsFrame;
    }

    private void decodeHeaders(Http3Headers headers, ByteBuf in) throws Http3Exception {
        qpackDecoder.decodeHeaders(in, headers, true);
    }
}
