/*
 * Copyright 2014 The Netty Project
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

package io.netty.handler.proxy;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.socksx.v4.DefaultSocks4CommandRequest;
import io.netty.handler.codec.socksx.v4.Socks4ClientDecoder;
import io.netty.handler.codec.socksx.v4.Socks4ClientEncoder;
import io.netty.handler.codec.socksx.v4.Socks4CommandResponse;
import io.netty.handler.codec.socksx.v4.Socks4CommandStatus;
import io.netty.handler.codec.socksx.v4.Socks4CommandType;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public final class Socks4ProxyHandler extends ProxyHandler {

    private static final String PROTOCOL = "socks4";
    private static final String AUTH_USERNAME = "username";

    private final String username;

    private String decoderName;
    private String encoderName;

    public Socks4ProxyHandler(SocketAddress proxyAddress) {
        this(proxyAddress, null);
    }

    public Socks4ProxyHandler(SocketAddress proxyAddress, String username) {
        super(proxyAddress);
        if (username != null && username.isEmpty()) {
            username = null;
        }
        this.username = username;
    }

    @Override
    public String protocol() {
        return PROTOCOL;
    }

    @Override
    public String authScheme() {
        return username != null? AUTH_USERNAME : AUTH_NONE;
    }

    public String username() {
        return username;
    }

    @Override
    protected void addCodec(ChannelHandlerContext ctx) throws Exception {
        ChannelPipeline p = ctx.pipeline();
        String name = ctx.name();

        Socks4ClientDecoder decoder = new Socks4ClientDecoder();
        p.addBefore(name, null, decoder);

        decoderName = p.context(decoder).name();
        encoderName = decoderName + ".encoder";

        p.addBefore(name, encoderName, Socks4ClientEncoder.INSTANCE);
    }

    @Override
    protected void removeEncoder(ChannelHandlerContext ctx) throws Exception {
        ChannelPipeline p = ctx.pipeline();
        p.remove(encoderName);
    }

    @Override
    protected void removeDecoder(ChannelHandlerContext ctx) throws Exception {
        ChannelPipeline p = ctx.pipeline();
        p.remove(decoderName);
    }

    @Override
    protected Object newInitialMessage(ChannelHandlerContext ctx) throws Exception {
        InetSocketAddress raddr = destinationAddress();
        String rhost;
        if (raddr.isUnresolved()) {
            rhost = raddr.getHostString();
        } else {
            rhost = raddr.getAddress().getHostAddress();
        }
        return new DefaultSocks4CommandRequest(
                Socks4CommandType.CONNECT, rhost, raddr.getPort(), username != null? username : "");
    }

    @Override
    protected boolean handleResponse(ChannelHandlerContext ctx, Object response) throws Exception {
        final Socks4CommandResponse res = (Socks4CommandResponse) response;
        final Socks4CommandStatus status = res.status();
        if (status == Socks4CommandStatus.SUCCESS) {
            return true;
        }

        throw new ProxyConnectException(exceptionMessage("status: " + status));
    }
}
