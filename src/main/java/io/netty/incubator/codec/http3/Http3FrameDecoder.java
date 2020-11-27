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
import io.netty.handler.codec.DecoderException;
import io.netty.incubator.codec.quic.QuicStreamFrame;

import java.util.List;

/**
 * Decodes {@link Http3Frame}s.
 */
public final class Http3FrameDecoder extends ByteToMessageDecoder {

    private long type;
    private long payLoadLength;

    // TODO: Do something with this...
    private boolean fin;

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
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (!in.isReadable()) {
            return;
        }
        if (type == -1) {
            int typeLen = decodeVariableLengthInteger(in.getByte(in.readerIndex()));
            if (in.readableBytes() < typeLen) {
                return;
            }
            type = readVariableLength(in, typeLen);
            if (!in.isReadable()) {
                return;
            }
        }
        if (payLoadLength == -1) {
            int payloadLen = decodeVariableLengthInteger(in.getByte(in.readerIndex()));
            if (in.readableBytes() < payloadLen) {
                return;
            }
            payLoadLength = payloadLen;
        }
        if (payLoadLength < in.readableBytes()) {
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
                        decodeHeaders(headersFrame.headers(), in, payLoadLength);
                        out.add(headersFrame);
                        break;
                    case 0x3:
                        // CANCEL_PUSH
                        // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.3
                        int pushIdLen = decodeVariableLengthInteger(in.getByte(in.readerIndex()));
                        out.add(new DefaultHttp3CancelPushFrame(readVariableLength(in, pushIdLen)));
                        break;
                    case 0x4:
                        // SETTINGS
                        // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.4
                        out.add(decodeSettings(in, payLoadLength));
                        break;
                    case 0x5:
                        // PUSH_PROMISE
                        // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.5
                        Http3PushPromiseFrame pushPromiseFrame = new DefaultHttp3PushPromiseFrame();
                        decodeHeaders(pushPromiseFrame.headers(), in, payLoadLength);
                        out.add(pushPromiseFrame);
                        break;
                    case 0x7:
                        // GO_AWAY
                        // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.6
                        int idLen = decodeVariableLengthInteger(in.getByte(in.readerIndex()));
                        out.add(new DefaultHttp3GoAwayFrame(readVariableLength(in, idLen)));
                        break;
                    case 0xd:
                        // MAX_PUSH_ID
                        // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.7
                        int pidLen = decodeVariableLengthInteger(in.getByte(in.readerIndex()));
                        out.add(new DefaultHttp3MaxPushIdFrame(readVariableLength(in, pidLen)));
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
            int keyLen = decodeVariableLengthInteger(in.getByte(in.readerIndex()));
            long key = readVariableLength(in, keyLen);
            payLoadLength -= keyLen;
            int valueLen = decodeVariableLengthInteger(in.getByte(in.readerIndex()));
            long value = readVariableLength(in, valueLen);
            payLoadLength -= valueLen;

            settingsFrame.put(key, value);
        }
        return settingsFrame;
    }

    private static void decodeHeaders(Http3Headers headers, ByteBuf in, int payLoadLength) {
        // TODO: implement me, for this we will need QPACK.
    }

    /**
     * See <a href="https://tools.ietf.org/html/draft-ietf-quic-transport-32#section-16">
     *     Variable-Length Integer Encoding </a>
     */
    private static long readVariableLength(ByteBuf in, int len) {
        switch (len) {
            case 1:
                return in.readUnsignedByte();
            case 2:
                return in.readUnsignedShort() & 0x3fff;
            case 4:
                return in.readUnsignedInt() & 0x3fffffff;
            case 8:
                return in.readLong() & 0x3fffffffffffffffL;
            default:
                throw new DecoderException("FIX ME");
        }
    }

    /**
     * See <a href="https://tools.ietf.org/html/draft-ietf-quic-transport-32#section-16">
     *     Variable-Length Integer Encoding </a>
     */
    private static int decodeVariableLengthInteger(byte b) {
        byte val = (byte) (b >> 6);

        if ((val & 1) != 0) {
            if (val != 0) {
                return 8;
            }
            return 4;
        }
        if (val != 0) {
            return 2;
        }
        return 1;
    }
}
