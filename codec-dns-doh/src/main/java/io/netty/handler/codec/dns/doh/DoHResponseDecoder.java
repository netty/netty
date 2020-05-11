/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.codec.dns.doh;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsRecordDecoder;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsResponseDecoder;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpObject;
import io.netty.util.internal.UnstableApi;

import java.io.InvalidObjectException;
import java.net.SocketAddress;
import java.util.List;

@UnstableApi
public class DoHResponseDecoder extends MessageToMessageDecoder<HttpObject> {

    private final DnsResponseDecoder<SocketAddress> dnsResponseDecoder;

    /**
     * Creates a new decoder with {@linkplain DnsRecordDecoder#DEFAULT the default record decoder}.
     */
    public DoHResponseDecoder() {
        this(DnsRecordDecoder.DEFAULT);
    }

    /**
     * Creates a new decoder with the specified {@code recordDecoder}
     */
    public DoHResponseDecoder(DnsRecordDecoder dnsRecordDecoder) {
        this.dnsResponseDecoder = new DnsResponseDecoder<SocketAddress>(dnsRecordDecoder) {
            @Override
            protected DnsResponse newResponse(SocketAddress sender, SocketAddress recipient, int id,
                                              DnsOpCode opCode, DnsResponseCode responseCode) {
                return new DefaultDnsResponse(id, opCode, responseCode);
            }
        };
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, HttpObject msg, List<Object> out) throws Exception {

        // We don't any other HttpObject except FullHttpResponse
        if (!(msg instanceof FullHttpResponse)) {
            throw new InvalidObjectException("HttpObject is not FullHttpResponse");
        }

        FullHttpResponse fullHttpResponse = (FullHttpResponse) msg;
        out.add(dnsResponseDecoder.decode(ctx.channel().remoteAddress(), ctx.channel().localAddress(),
                fullHttpResponse.content()));
    }
}
