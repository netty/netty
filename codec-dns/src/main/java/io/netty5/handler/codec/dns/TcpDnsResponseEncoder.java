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
package io.netty5.handler.codec.dns;

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.MessageToMessageEncoder;
import io.netty5.util.internal.UnstableApi;

import java.util.List;

import static java.util.Objects.requireNonNull;

@UnstableApi
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
        this.encoder = requireNonNull(encoder, "encoder");
    }

    @Override
    public boolean isSharable() {
        return true;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, DnsResponse response, List<Object> out) throws Exception {
        Buffer buf = ctx.bufferAllocator().allocate(1024);

        buf.skipWritableBytes(2);
        DnsMessageUtil.encodeDnsResponse(encoder, response, buf);
        buf.setShort(0, (short) (buf.readableBytes() - 2));

        out.add(buf);
    }
}
