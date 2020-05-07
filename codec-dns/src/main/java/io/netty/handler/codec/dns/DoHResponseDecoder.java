/*
 * Copyright 2015 The Netty Project
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
package io.netty.handler.codec.dns;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpObject;

import java.net.SocketAddress;
import java.util.List;

public class DoHResponseDecoder extends MessageToMessageDecoder<HttpObject> {

    private final DnsResponseDecoder<SocketAddress> dnsResponseDecoder;

    public DoHResponseDecoder() {
        this(DnsRecordDecoder.DEFAULT);
    }

    public DoHResponseDecoder(DnsRecordDecoder dnsRecordDecoder) {
        this.dnsResponseDecoder = new DnsResponseDecoder<SocketAddress>(dnsRecordDecoder) {
            @Override
            protected DnsResponse newResponse(SocketAddress sender, SocketAddress recipient, int id, DnsOpCode opCode,
                                              DnsResponseCode responseCode) throws Exception {
                return new DefaultDnsResponse(id, opCode, responseCode);
            }
        };
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, HttpObject msg, List<Object> out) throws Exception {
        FullHttpResponse fullHttpResponse = (FullHttpResponse) msg;
        out.add(dnsResponseDecoder.decode(ctx.channel().remoteAddress(), ctx.channel().localAddress(),
                fullHttpResponse.content()));
    }
}
