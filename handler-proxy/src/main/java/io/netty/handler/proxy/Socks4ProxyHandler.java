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
import io.netty.handler.codec.socksx.v4.Socks4CmdRequest;
import io.netty.handler.codec.socksx.v4.Socks4CmdResponse;
import io.netty.handler.codec.socksx.v4.Socks4CmdResponseDecoder;
import io.netty.handler.codec.socksx.v4.Socks4CmdStatus;
import io.netty.handler.codec.socksx.v4.Socks4CmdType;
import io.netty.handler.codec.socksx.v4.Socks4MessageEncoder;

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
        if (username != null && username.length() == 0) {
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

        Socks4CmdResponseDecoder decoder = new Socks4CmdResponseDecoder();
        p.addBefore(name, null, decoder);

        decoderName = p.context(decoder).name();
        encoderName = decoderName + ".encoder";

        p.addBefore(name, encoderName, Socks4MessageEncoder.INSTANCE);
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
        return new Socks4CmdRequest(
                username != null? username : "", Socks4CmdType.CONNECT, rhost, raddr.getPort());
    }

    @Override
    protected boolean handleResponse(ChannelHandlerContext ctx, Object response) throws Exception {
        final Socks4CmdResponse res = (Socks4CmdResponse) response;
        final Socks4CmdStatus status = res.cmdStatus();
        if (status == Socks4CmdStatus.SUCCESS) {
            return true;
        }

        throw new ProxyConnectException(exceptionMessage("cmdStatus: " + status));
    }
}
