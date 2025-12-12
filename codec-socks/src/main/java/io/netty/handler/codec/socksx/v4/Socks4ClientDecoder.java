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
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.DecoderResult;
import io.netty.util.NetUtil;

import java.util.List;

/**
 * Decodes a single {@link Socks4CommandResponse} from the inbound {@link ByteBuf}s.
 * On successful decode, this decoder will forward the received data to the next handler, so that
 * other handler can remove this decoder later.  On failed decode, this decoder will discard the
 * received data, so that other handler closes the connection later.
 */
public class Socks4ClientDecoder extends ByteToMessageDecoder {

    private enum State {
        START,
        SUCCESS,
        FAILURE
    }
    private State state = State.START;

    public Socks4ClientDecoder() {
        setSingleDecode(true);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        try {
            switch (state) {
            case START: {
                if (in.readableBytes() < 8) {
                    // Not enough data to read.
                    return;
                }
                final int version = in.readUnsignedByte();
                if (version != 0) {
                    throw new DecoderException("unsupported reply version: " + version + " (expected: 0)");
                }

                final Socks4CommandStatus status = Socks4CommandStatus.valueOf(in.readByte());
                final int dstPort = ByteBufUtil.readUnsignedShortBE(in);
                final String dstAddr = NetUtil.intToIpAddress(ByteBufUtil.readIntBE(in));

                out.add(new DefaultSocks4CommandResponse(status, dstAddr, dstPort));
                state = State.SUCCESS;
            }
            case SUCCESS: {
                int readableBytes = in.readableBytes();
                if (readableBytes > 0) {
                    out.add(in.readRetainedSlice(readableBytes));
                }
                break;
            }
            case FAILURE: {
                in.skipBytes(in.readableBytes());
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

        Socks4CommandResponse m = new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED);
        m.setDecoderResult(DecoderResult.failure(cause));
        out.add(m);

        state = State.FAILURE;
    }
}
