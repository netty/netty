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

import io.netty.buffer.ByteBuf;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.util.internal.UnstableApi;

import java.net.InetSocketAddress;
import java.util.List;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Encodes a {@link DatagramDnsResponse} (or an {@link AddressedEnvelope} of {@link DnsResponse}} into a
 * {@link DatagramPacket}.
 */
@UnstableApi
@ChannelHandler.Sharable
public class DatagramDnsResponseEncoder
    extends MessageToMessageEncoder<AddressedEnvelope<DnsResponse, InetSocketAddress>> {

    private final DnsRecordEncoder recordEncoder;

    /**
     * Creates a new encoder with {@linkplain DnsRecordEncoder#DEFAULT the default record encoder}.
     */
    public DatagramDnsResponseEncoder() {
        this(DnsRecordEncoder.DEFAULT);
    }

    /**
     * Creates a new encoder with the specified {@code recordEncoder}.
     */
    public DatagramDnsResponseEncoder(DnsRecordEncoder recordEncoder) {
        this.recordEncoder = checkNotNull(recordEncoder, "recordEncoder");
    }

    @Override
    protected void encode(ChannelHandlerContext ctx,
                          AddressedEnvelope<DnsResponse, InetSocketAddress> in, List<Object> out) throws Exception {

        final InetSocketAddress recipient = in.recipient();
        final DnsResponse response = in.content();
        final ByteBuf buf = allocateBuffer(ctx, in);

        DnsMessageUtil.encodeDnsResponse(recordEncoder, response, buf);

        out.add(new DatagramPacket(buf, recipient, null));
    }

    /**
     * Allocate a {@link ByteBuf} which will be used for constructing a datagram packet.
     * Sub-classes may override this method to return a {@link ByteBuf} with a perfect matching initial capacity.
     */
    protected ByteBuf allocateBuffer(
        ChannelHandlerContext ctx,
        @SuppressWarnings("unused") AddressedEnvelope<DnsResponse, InetSocketAddress> msg) throws Exception {
        return ctx.alloc().ioBuffer(1024);
    }
}
