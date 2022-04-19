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

import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.socket.BufferDatagramPacket;
import io.netty5.handler.codec.CorruptedFrameException;
import io.netty5.handler.codec.MessageToMessageDecoder;
import io.netty5.util.internal.UnstableApi;

import java.net.InetSocketAddress;

/**
 * Decodes a {@link BufferDatagramPacket} into a {@link DatagramDnsResponse}.
 */
@UnstableApi
@ChannelHandler.Sharable
public class DatagramDnsResponseDecoder extends MessageToMessageDecoder<BufferDatagramPacket> {

    private final DnsResponseDecoder<InetSocketAddress> responseDecoder;

    /**
     * Creates a new decoder with {@linkplain DnsRecordDecoder#DEFAULT the default record decoder}.
     */
    public DatagramDnsResponseDecoder() {
        this(DnsRecordDecoder.DEFAULT);
    }

    /**
     * Creates a new decoder with the specified {@code recordDecoder}.
     */
    public DatagramDnsResponseDecoder(DnsRecordDecoder recordDecoder) {
        responseDecoder = new DnsResponseDecoder<InetSocketAddress>(recordDecoder) {
            @Override
            protected DnsResponse newResponse(InetSocketAddress sender, InetSocketAddress recipient,
                                              int id, DnsOpCode opCode, DnsResponseCode responseCode) {
                return new DatagramDnsResponse(sender, recipient, id, opCode, responseCode);
            }
        };
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, BufferDatagramPacket packet) throws Exception {
        final DnsResponse response;
        try {
            response = decodeResponse(ctx, packet);
        } catch (IndexOutOfBoundsException e) {
            throw new CorruptedFrameException("Unable to decode response", e);
        }
        ctx.fireChannelRead(response);
    }

    protected DnsResponse decodeResponse(ChannelHandlerContext ctx, BufferDatagramPacket packet) throws Exception {
        return responseDecoder.decode(packet.sender(), packet.recipient(), ctx.bufferAllocator(), packet.content());
    }
}
