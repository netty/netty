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
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.handler.codec.socksx.v4.Socks4ServerDecoder.State;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;

import java.util.List;

/**
 * Decodes a single {@link Socks4CommandRequest} from the inbound {@link ByteBuf}s.
 * On successful decode, this decoder will forward the received data to the next handler, so that
 * other handler can remove this decoder later.  On failed decode, this decoder will discard the
 * received data, so that other handler closes the connection later.
 */
public class Socks4ServerDecoder extends ReplayingDecoder<State> {

    private static final int MAX_FIELD_LENGTH = 255;

    enum State {
        START,
        READ_USERID,
        READ_DOMAIN,
        SUCCESS,
        FAILURE
    }

    private Socks4CommandType type;
    private String dstAddr;
    private int dstPort;
    private String userId;

    public Socks4ServerDecoder() {
        super(State.START);
        setSingleDecode(true);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        try {
            switch (state()) {
            case START: {
                final int version = in.readUnsignedByte();
                if (version != SocksVersion.SOCKS4a.byteValue()) {
                    throw new DecoderException("unsupported protocol version: " + version);
                }

                type = Socks4CommandType.valueOf(in.readByte());
                dstPort = in.readUnsignedShort();
                dstAddr = NetUtil.intToIpAddress(in.readInt());
                checkpoint(State.READ_USERID);
            }
            case READ_USERID: {
                userId = readString("userid", in);
                checkpoint(State.READ_DOMAIN);
            }
            case READ_DOMAIN: {
                // Check for Socks4a protocol marker 0.0.0.x
                if (!"0.0.0.0".equals(dstAddr) && dstAddr.startsWith("0.0.0.")) {
                    dstAddr = readString("dstAddr", in);
                }
                out.add(new DefaultSocks4CommandRequest(type, dstAddr, dstPort, userId));
                checkpoint(State.SUCCESS);
            }
            case SUCCESS: {
                int readableBytes = actualReadableBytes();
                if (readableBytes > 0) {
                    out.add(in.readRetainedSlice(readableBytes));
                }
                break;
            }
            case FAILURE: {
                in.skipBytes(actualReadableBytes());
                break;
            }
            }
        } catch (Exception e) {
            fail(out, e);
        }
    }

    private void fail(List<Object> out, Exception cause) {
        if (!(cause instanceof DecoderException)) {
            cause = new DecoderException(cause);
        }

        Socks4CommandRequest m = new DefaultSocks4CommandRequest(
                type != null? type : Socks4CommandType.CONNECT,
                dstAddr != null? dstAddr : "",
                dstPort != 0? dstPort : 65535,
                userId != null? userId : "");

        m.setDecoderResult(DecoderResult.failure(cause));
        out.add(m);

        checkpoint(State.FAILURE);
    }

    /**
     * Reads a variable-length NUL-terminated string as defined in SOCKS4.
     */
    private static String readString(String fieldName, ByteBuf in) {
        int length = in.bytesBefore(MAX_FIELD_LENGTH + 1, (byte) 0);
        if (length < 0) {
            throw new DecoderException("field '" + fieldName + "' longer than " + MAX_FIELD_LENGTH + " chars");
        }

        String value = in.readSlice(length).toString(CharsetUtil.US_ASCII);
        in.skipBytes(1); // Skip the NUL.

        return value;
    }
}
