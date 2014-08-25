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

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.handler.codec.socksx.v5.Socks5AuthRequest;
import io.netty.handler.codec.socksx.v5.Socks5AuthRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5AuthResponse;
import io.netty.handler.codec.socksx.v5.Socks5AuthScheme;
import io.netty.handler.codec.socksx.v5.Socks5AuthStatus;
import io.netty.handler.codec.socksx.v5.Socks5CmdRequest;
import io.netty.handler.codec.socksx.v5.Socks5CmdRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5CmdResponse;
import io.netty.handler.codec.socksx.v5.Socks5CmdStatus;
import io.netty.handler.codec.socksx.v5.Socks5CmdType;
import io.netty.handler.codec.socksx.v5.Socks5InitRequest;
import io.netty.handler.codec.socksx.v5.Socks5InitRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5InitResponse;
import io.netty.handler.codec.socksx.v5.Socks5MessageEncoder;
import io.netty.util.CharsetUtil;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

final class Socks5ProxyServer extends ProxyServer {

    Socks5ProxyServer(boolean useSsl, TestMode testMode, InetSocketAddress destination) {
        super(useSsl, testMode, destination);
    }

    Socks5ProxyServer(
            boolean useSsl, TestMode testMode, InetSocketAddress destination, String username, String password) {
        super(useSsl, testMode, destination, username, password);
    }

    @Override
    protected void configure(SocketChannel ch) throws Exception {
        ChannelPipeline p = ch.pipeline();
        switch (testMode) {
        case INTERMEDIARY:
            p.addLast("decoder", new Socks5InitRequestDecoder());
            p.addLast("encoder", Socks5MessageEncoder.INSTANCE);
            p.addLast(new Socks5IntermediaryHandler());
            break;
        case TERMINAL:
            p.addLast("decoder", new Socks5InitRequestDecoder());
            p.addLast("encoder", Socks5MessageEncoder.INSTANCE);
            p.addLast(new Socks5TerminalHandler());
            break;
        case UNRESPONSIVE:
            p.addLast(UnresponsiveHandler.INSTANCE);
            break;
        }
    }

    private boolean authenticate(ChannelHandlerContext ctx, Object msg) {
        if (username == null) {
            ctx.pipeline().addBefore("encoder", "decoder", new Socks5CmdRequestDecoder());
            ctx.write(new Socks5InitResponse(Socks5AuthScheme.NO_AUTH));
            return true;
        }

        if (msg instanceof Socks5InitRequest) {
            ctx.pipeline().addBefore("encoder", "decoder", new Socks5AuthRequestDecoder());
            ctx.write(new Socks5InitResponse(Socks5AuthScheme.AUTH_PASSWORD));
            return false;
        }

        Socks5AuthRequest req = (Socks5AuthRequest) msg;
        if (req.username().equals(username) && req.password().equals(password)) {
            ctx.pipeline().addBefore("encoder", "decoder", new Socks5CmdRequestDecoder());
            ctx.write(new Socks5AuthResponse(Socks5AuthStatus.SUCCESS));
            return true;
        }

        ctx.pipeline().addBefore("encoder", "decoder", new Socks5AuthRequestDecoder());
        ctx.write(new Socks5AuthResponse(Socks5AuthStatus.FAILURE));
        return false;
    }

    private final class Socks5IntermediaryHandler extends IntermediaryHandler {

        private boolean authenticated;
        private SocketAddress intermediaryDestination;

        @Override
        protected boolean handleProxyProtocol(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (!authenticated) {
                authenticated = authenticate(ctx, msg);
                return false;
            }

            Socks5CmdRequest req = (Socks5CmdRequest) msg;
            assertThat(req.cmdType(), is(Socks5CmdType.CONNECT));

            Socks5CmdResponse res;
            res = new Socks5CmdResponse(Socks5CmdStatus.SUCCESS, Socks5AddressType.IPv4);
            intermediaryDestination = new InetSocketAddress(req.host(), req.port());

            ctx.write(res);
            ctx.pipeline().remove(Socks5MessageEncoder.class);

            return true;
        }

        @Override
        protected SocketAddress intermediaryDestination() {
            return intermediaryDestination;
        }
    }

    private final class Socks5TerminalHandler extends TerminalHandler {

        private boolean authenticated;

        @Override
        protected boolean handleProxyProtocol(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (!authenticated) {
                authenticated = authenticate(ctx, msg);
                return false;
            }

            Socks5CmdRequest req = (Socks5CmdRequest) msg;
            assertThat(req.cmdType(), is(Socks5CmdType.CONNECT));

            ctx.pipeline().addBefore(ctx.name(), "lineDecoder", new LineBasedFrameDecoder(64, false, true));

            Socks5CmdResponse res;
            boolean sendGreeting = false;
            if (!req.host().equals(destination.getHostString()) ||
                       req.port() != destination.getPort()) {
                res = new Socks5CmdResponse(Socks5CmdStatus.FORBIDDEN, Socks5AddressType.IPv4);
            } else {
                res = new Socks5CmdResponse(Socks5CmdStatus.SUCCESS, Socks5AddressType.IPv4);
                sendGreeting = true;
            }

            ctx.write(res);
            ctx.pipeline().remove(Socks5MessageEncoder.class);

            if (sendGreeting) {
                ctx.write(Unpooled.copiedBuffer("0\n", CharsetUtil.US_ASCII));
            }

            return true;
        }
    }
}
