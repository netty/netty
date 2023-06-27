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
package io.netty.handler.codec.dns;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.util.internal.UnstableApi;

import java.util.List;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Decodes a {@link DatagramPacket} into a {@link DatagramDnsQuery}.
 */
@UnstableApi
@ChannelHandler.Sharable
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
        this.recordDecoder = checkNotNull(recordDecoder, "recordDecoder");
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, final DatagramPacket packet, List<Object> out) throws Exception {
        DnsQuery query = DnsMessageUtil.decodeDnsQuery(recordDecoder, packet.content(),
                new DnsMessageUtil.DnsQueryFactory() {
            @Override
            public DnsQuery newQuery(int id, DnsOpCode dnsOpCode) {
                return new DatagramDnsQuery(packet.sender(), packet.recipient(), id, dnsOpCode);
            }
        });
        out.add(query);
    }
}
