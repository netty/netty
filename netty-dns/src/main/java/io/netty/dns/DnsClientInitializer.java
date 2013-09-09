/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.dns;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DnsQueryEncoder;
import io.netty.handler.codec.dns.DnsResponseDecoder;
import io.netty.handler.timeout.ReadTimeoutHandler;

/**
 * Initializes a DNS client that can encode and decode DNS response and query packets. Has a default timeout of 30
 * seconds when no data is read, in which case the client is shutdown. Each DNS server gets its own DNS client.
 */
public class DnsClientInitializer extends ChannelInitializer<NioDatagramChannel> {

    private final DnsAsynchronousResolver resolver;

    public DnsClientInitializer(DnsAsynchronousResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    protected void initChannel(NioDatagramChannel channel) throws Exception {
        channel.pipeline().addLast("timeout", new ReadTimeoutHandler(30) {
            @Override
            public void readTimedOut(ChannelHandlerContext ctx) {
                resolver.removeChannel(ctx.channel());
            }
        }).addLast("decoder", new DnsResponseDecoder()).addLast("encoder", new DnsQueryEncoder())
                .addLast("handler", new DnsInboundMessageHandler());
    }

}
