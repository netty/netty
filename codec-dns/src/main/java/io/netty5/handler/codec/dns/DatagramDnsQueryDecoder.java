/*
 * Copyright 2015 The Netty Project
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

import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.socket.DatagramPacket;
import io.netty5.handler.codec.MessageToMessageDecoder;
import io.netty5.util.internal.UnstableApi;

import static java.util.Objects.requireNonNull;

/**
 * Decodes a {@link DatagramPacket} into a {@link DatagramDnsQuery}.
 */
@UnstableApi
public class DatagramDnsQueryDecoder extends MessageToMessageDecoder<DatagramPacket> {

    private final DnsRecordDecoder recordDecoder;

    /**
     * Creates a new decoder with {@linkplain DnsRecordDecoder#DEFAULT the default record decoder}.
     */
    public DatagramDnsQueryDecoder() {
        this(DnsRecordDecoder.DEFAULT);
    }

    /**
     * Creates a new decoder with the specified {@code recordDecoder}.
     */
    public DatagramDnsQueryDecoder(DnsRecordDecoder recordDecoder) {
        this.recordDecoder = requireNonNull(recordDecoder, "recordDecoder");
    }

    @Override
    public boolean isSharable() {
        return true;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
        DnsQuery query = DnsMessageUtil.decodeDnsQuery(recordDecoder, ctx.bufferAllocator(), packet.content(),
                (id, dnsOpCode) -> new DatagramDnsQuery(packet.sender(), packet.recipient(), id, dnsOpCode));
        ctx.fireChannelRead(query);
    }
}
