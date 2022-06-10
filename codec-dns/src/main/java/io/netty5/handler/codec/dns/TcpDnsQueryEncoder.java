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
package io.netty5.handler.codec.dns;

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.MessageToByteEncoderForBuffer;
import io.netty5.util.internal.UnstableApi;

@ChannelHandler.Sharable
@UnstableApi
public final class TcpDnsQueryEncoder extends MessageToByteEncoderForBuffer<DnsQuery> {

    private final DnsQueryEncoder encoder;

    /**
     * Creates a new encoder with {@linkplain DnsRecordEncoder#DEFAULT the default record encoder}.
     */
    public TcpDnsQueryEncoder() {
        this(DnsRecordEncoder.DEFAULT);
    }

    /**
     * Creates a new encoder with the specified {@code recordEncoder}.
     */
    public TcpDnsQueryEncoder(DnsRecordEncoder recordEncoder) {
        encoder = new DnsQueryEncoder(recordEncoder);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, DnsQuery msg, Buffer out) throws Exception {
        // Length is two octets as defined by RFC-7766
        // See https://tools.ietf.org/html/rfc7766#section-8
        int initialOffset = out.writerOffset();
        out.skipWritableBytes(2);
        encoder.encode(msg, out);

        // Now fill in the correct length based on the amount of data that we wrote the ByteBuf.
        out.setShort(initialOffset, (short) (out.writerOffset() - initialOffset - 2));
    }

    @Override
    protected Buffer allocateBuffer(ChannelHandlerContext ctx, @SuppressWarnings("unused") DnsQuery msg) {
        return ctx.bufferAllocator().allocate(1024);
    }
}
