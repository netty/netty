/*
 * Copyright 2021 The Netty Project
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
import io.netty.util.internal.ObjectUtil;

public final class TcpDnsQueryDecoder extends LengthFieldBasedFrameDecoder {
    private final DnsRecordDecoder decoder;

    /**
     * Creates a new decoder with {@linkplain DnsRecordDecoder#DEFAULT the default record decoder}.
     */
    public TcpDnsQueryDecoder() {
        this(DnsRecordDecoder.DEFAULT, 65535);
    }

    /**
     * Creates a new decoder with the specified {@code decoder}.
     */
    public TcpDnsQueryDecoder(DnsRecordDecoder decoder, int maxFrameLength) {
        super(maxFrameLength, 0, 2, 0, 2);
        this.decoder = ObjectUtil.checkNotNull(decoder, "decoder");
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        if (frame == null) {
            return null;
        }

        try {
            return DnsMessageUtil.decodeDnsQuery(decoder, frame.slice(), new DnsMessageUtil.DnsQueryFactory() {
                @Override
                public DnsQuery newQuery(int id, DnsOpCode dnsOpCode) {
                    return new DefaultDnsQuery(id, dnsOpCode);
                }
            });
        } finally {
            frame.release();
        }
    }
}
