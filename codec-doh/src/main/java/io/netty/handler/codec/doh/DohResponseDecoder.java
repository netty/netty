/*
 * Copyright 2024 The Netty Project
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
package io.netty.handler.codec.doh;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.dns.*;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpResponseDecoder;

import java.net.SocketAddress;

/**
 * Decodes DNS-over-HTTPS (DoH) responses to extract DNS records and other relevant information.
 */
public final class DohResponseDecoder extends HttpResponseDecoder {

    private final DnsResponseDecoder<SocketAddress> responseDecoder;

    /**
     * Creates a new instance.
     */
    public DohResponseDecoder() {
        responseDecoder = new DnsResponseDecoder<SocketAddress>(DnsRecordDecoder.DEFAULT) {
            @Override
            protected DnsResponse newResponse(SocketAddress sender, SocketAddress recipient, int id, DnsOpCode opCode,
                                              DnsResponseCode responseCode) {
                return new DefaultDnsResponse(id, opCode, responseCode);
            }
        };
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof DefaultLastHttpContent) {
            DefaultLastHttpContent response = (DefaultLastHttpContent) msg;
            ByteBuf content = response.content();

            SocketAddress sender = ctx.channel().remoteAddress();
            SocketAddress recipient = ctx.channel().localAddress();

            DnsResponse dnsResponse = responseDecoder.decode(sender, recipient, content);

            ctx.fireChannelRead(dnsResponse);
        } else {
            super.channelRead(ctx, msg);
        }
    }
}
