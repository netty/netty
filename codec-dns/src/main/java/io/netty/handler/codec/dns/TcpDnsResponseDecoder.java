/*
 * Copyright 2019 The Netty Project
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
package io.netty.handler.codec.dns;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.net.SocketAddress;

public final class TcpDnsResponseDecoder extends LengthFieldBasedFrameDecoder {

    private final DnsResponseDecoder<SocketAddress> responseDecoder;

    /**
     * Creates a new decoder with {@linkplain DnsRecordDecoder#DEFAULT the default record decoder}.
     */
    public TcpDnsResponseDecoder() {
        this(DnsRecordDecoder.DEFAULT, 64 * 1024);
    }

    /**
     * Creates a new decoder with the specified {@code recordDecoder} and {@code maxFrameLength}
     */
    public TcpDnsResponseDecoder(DnsRecordDecoder recordDecoder, int maxFrameLength) {
        // Length is two octets as defined by RFC-7766
        // See https://tools.ietf.org/html/rfc7766#section-8
        super(maxFrameLength, 0, 2, 0, 2);

        this.responseDecoder = new DnsResponseDecoder<SocketAddress>(recordDecoder) {
            @Override
            protected DnsResponse newResponse(SocketAddress sender, SocketAddress recipient,
                                              int id, DnsOpCode opCode, DnsResponseCode responseCode) {
                return new DefaultDnsResponse(id, opCode, responseCode);
            }
        };
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        if (frame == null) {
            return null;
        }

        try {
            return responseDecoder.decode(ctx.channel().remoteAddress(), ctx.channel().localAddress(), frame.slice());
        } finally {
            frame.release();
        }
    }

    @Override
    protected ByteBuf extractFrame(ChannelHandlerContext ctx, ByteBuf buffer, int index, int length) {
        return buffer.copy(index, length);
    }
}
