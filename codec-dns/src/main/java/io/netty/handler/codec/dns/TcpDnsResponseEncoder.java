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
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.UnstableApi;

import java.util.List;

@UnstableApi
@ChannelHandler.Sharable
public final class TcpDnsResponseEncoder extends MessageToMessageEncoder<DnsResponse> {
    private final DnsRecordEncoder encoder;

    /**
     * Creates a new encoder with {@linkplain DnsRecordEncoder#DEFAULT the default record encoder}.
     */
    public TcpDnsResponseEncoder() {
        this(DnsRecordEncoder.DEFAULT);
    }

    /**
     * Creates a new encoder with the specified {@code encoder}.
     */
    public TcpDnsResponseEncoder(DnsRecordEncoder encoder) {
        this.encoder = ObjectUtil.checkNotNull(encoder, "encoder");
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, DnsResponse response, List<Object> out) throws Exception {
        ByteBuf buf = ctx.alloc().ioBuffer(1024);

        buf.writerIndex(buf.writerIndex() + 2);
        DnsMessageUtil.encodeDnsResponse(encoder, response, buf);
        buf.setShort(0, buf.readableBytes() - 2);

        out.add(buf);
    }
}
