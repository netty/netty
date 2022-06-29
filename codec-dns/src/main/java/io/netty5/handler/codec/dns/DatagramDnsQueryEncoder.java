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

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.AddressedEnvelope;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.socket.DatagramPacket;
import io.netty5.handler.codec.MessageToMessageEncoder;
import io.netty5.util.internal.UnstableApi;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * Encodes a {@link DatagramDnsQuery} (or an {@link AddressedEnvelope} of {@link DnsQuery}} into a
 * {@link DatagramPacket}.
 */
@UnstableApi
public class DatagramDnsQueryEncoder extends MessageToMessageEncoder<AddressedEnvelope<DnsQuery, InetSocketAddress>> {

    private final DnsQueryEncoder encoder;

    /**
     * Creates a new encoder with {@linkplain DnsRecordEncoder#DEFAULT the default record encoder}.
     */
    public DatagramDnsQueryEncoder() {
        this(DnsRecordEncoder.DEFAULT);
    }

    /**
     * Creates a new encoder with the specified {@code recordEncoder}.
     */
    public DatagramDnsQueryEncoder(DnsRecordEncoder recordEncoder) {
        encoder = new DnsQueryEncoder(recordEncoder);
    }

    @Override
    public boolean isSharable() {
        return true;
    }

    @Override
    protected void encode(
        ChannelHandlerContext ctx,
        AddressedEnvelope<DnsQuery, InetSocketAddress> in, List<Object> out) throws Exception {

        final InetSocketAddress recipient = in.recipient();
        final DnsQuery query = in.content();
        try (Buffer buf = allocateBuffer(ctx, in)) {
            encoder.encode(query, buf);
            out.add(new DatagramPacket(buf.split(), recipient, null));
        }
    }

    /**
     * Allocate a {@link Buffer} which will be used for constructing a datagram packet.
     * Sub-classes may override this method to return a {@link Buffer} with a perfect matching initial capacity.
     */
    protected Buffer allocateBuffer(
        ChannelHandlerContext ctx,
        @SuppressWarnings("unused") AddressedEnvelope<DnsQuery, InetSocketAddress> msg) throws Exception {
        return ctx.bufferAllocator().allocate(1024);
    }
}
