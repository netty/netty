/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.socksx.v4;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;

/**
 * Decodes a single {@link Socks4CommandRequest} from the inbound {@link ByteBuf}s.
 * On successful decode, this decoder will forward the received data to the next handler, so that
 * other handler can remove this decoder later.  On failed decode, this decoder will discard the
 * received data, so that other handler closes the connection later.
 */
public class Socks4ServerDecoder extends ByteToMessageDecoder {

    private static final int MAX_FIELD_LENGTH = 255;

    private enum State {
        START,
        READ_USERID,
        READ_DOMAIN,
        SUCCESS,
        FAILURE
    }

    private State state = State.START;
    private Socks4CommandType type;
    private String dstAddr;
    private int dstPort;
    private String userId;

    public Socks4ServerDecoder() {
        setSingleDecode(true);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        try {
            switch (state) {
            case START: {
                if (in.readableBytes() < 8) {
                    return;
                }
                final int version = in.readUnsignedByte();
                if (version != SocksVersion.SOCKS4a.byteValue()) {
                    throw new DecoderException("unsupported protocol version: " + version);
                }

                type = Socks4CommandType.valueOf(in.readByte());
                dstPort = in.readUnsignedShort();
                dstAddr = NetUtil.intToIpAddress(in.readInt());
                state = State.READ_USERID;
            }
            case READ_USERID: {
                String id = readString("userid", in);
                if (id == null) {
                    return;
                }
                userId = id;
                state = State.READ_DOMAIN;
            }
            case READ_DOMAIN: {
                // Check for Socks4a protocol marker 0.0.0.x
                if (!"0.0.0.0".equals(dstAddr) && dstAddr.startsWith("0.0.0.")) {
                    String addr = readString("dstAddr", in);
                    if (addr == null) {
                        return;
                    }
                    dstAddr = addr;
                }
                ctx.fireChannelRead(new DefaultSocks4CommandRequest(type, dstAddr, dstPort, userId));
                state = State.SUCCESS;
            }
            case SUCCESS: {
                int readableBytes = actualReadableBytes();
                if (readableBytes > 0) {
                    ctx.fireChannelRead(in.readRetainedSlice(readableBytes));
                }
                break;
            }
            case FAILURE: {
                in.skipBytes(actualReadableBytes());
                break;
            }
            }
        } catch (Exception e) {
            fail(ctx, e);
        }
    }

    private void fail(ChannelHandlerContext ctx, Exception cause) {
        if (!(cause instanceof DecoderException)) {
            cause = new DecoderException(cause);
        }

        Socks4CommandRequest m = new DefaultSocks4CommandRequest(
                type != null? type : Socks4CommandType.CONNECT,
                dstAddr != null? dstAddr : "",
                dstPort != 0? dstPort : 65535,
                userId != null? userId : "");

        m.setDecoderResult(DecoderResult.failure(cause));
        ctx.fireChannelRead(m);

        state = State.FAILURE;
    }

    /**
     * Reads a variable-length NUL-terminated string as defined in SOCKS4.
     */
    private static String readString(String fieldName, ByteBuf in) {
        int length = in.bytesBefore(Math.min(in.readableBytes(), MAX_FIELD_LENGTH + 1), (byte) 0);
        if (length >= 0) {
            if (in.readableBytes() < length + 1) {
                return null;
            }
            String value = in.readSlice(length).toString(CharsetUtil.US_ASCII);
            in.skipBytes(1); // Skip the NUL.

            return value;
        }
        if (in.readableBytes() > MAX_FIELD_LENGTH + 1) {
            throw new DecoderException("field '" + fieldName + "' longer than " + MAX_FIELD_LENGTH + " chars");
        }
        return null;
    }
}
