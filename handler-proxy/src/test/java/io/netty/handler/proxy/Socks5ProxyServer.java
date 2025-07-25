/*
 * Copyright 2014 The Netty Project
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

package io.netty.handler.proxy;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialResponse;
import io.netty.handler.codec.socksx.v5.DefaultSocks5PasswordAuthResponse;
import io.netty.handler.codec.socksx.v5.DefaultSocks5PrivateAuthResponse;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.handler.codec.socksx.v5.Socks5CommandType;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequest;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequest;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthStatus;
import io.netty.handler.codec.socksx.v5.Socks5PrivateAuthRequest;
import io.netty.handler.codec.socksx.v5.Socks5PrivateAuthRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5PrivateAuthStatus;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.SocketUtils;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class Socks5ProxyServer extends ProxyServer {

    private static final String ENCODER = "encoder";
    private static final String DECODER = "decoder";
    private byte privateAuthMethod;
    private byte[] privateToken;

    Socks5ProxyServer(boolean useSsl, TestMode testMode, InetSocketAddress destination) {
        super(useSsl, testMode, destination);
    }

    Socks5ProxyServer(
            boolean useSsl, TestMode testMode, InetSocketAddress destination, String username, String password) {
        super(useSsl, testMode, destination, username, password);
    }

    Socks5ProxyServer(
            boolean useSsl, TestMode testMode, InetSocketAddress destination, byte privateAuthMethod,
            byte[] privateToken) {
        super(useSsl, testMode, destination);
        this.privateAuthMethod = privateAuthMethod;
        this.privateToken = privateToken;
    }

    @Override
    protected void configure(SocketChannel ch) throws Exception {
        ChannelPipeline p = ch.pipeline();
        switch (testMode) {
        case INTERMEDIARY:
            p.addLast(DECODER, new Socks5InitialRequestDecoder());
            p.addLast(ENCODER, Socks5ServerEncoder.DEFAULT);
            p.addLast(new Socks5IntermediaryHandler());
            break;
        case TERMINAL:
            p.addLast(DECODER, new Socks5InitialRequestDecoder());
            p.addLast(ENCODER, Socks5ServerEncoder.DEFAULT);
            p.addLast(new Socks5TerminalHandler());
            break;
        case UNRESPONSIVE:
            p.addLast(UnresponsiveHandler.INSTANCE);
            break;
        }
    }

    boolean authenticate(ChannelHandlerContext ctx, Object msg) {
        if (username == null && privateToken == null) {
            ctx.pipeline().replace(DECODER, DECODER, new Socks5CommandRequestDecoder());
            ctx.write(new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH));
            return true;
        }

        if (msg instanceof Socks5InitialRequest) {
            Socks5InitialRequest initialRequest = (Socks5InitialRequest) msg;

            if (privateToken != null) {
                // Check if the client requested our private auth method
                boolean hasPrivateMethod = false;
                for (Socks5AuthMethod method : initialRequest.authMethods()) {
                    if (method.byteValue() == privateAuthMethod) {
                        hasPrivateMethod = true;
                        break;
                    }
                }

                if (hasPrivateMethod) {
                    ctx.pipeline().replace(DECODER, DECODER, new Socks5PrivateAuthRequestDecoder());
                    ctx.write(new DefaultSocks5InitialResponse(Socks5AuthMethod.valueOf(privateAuthMethod)));
                    return false;
                }
            }

            if (username != null) {
                ctx.pipeline().replace(DECODER, DECODER, new Socks5PasswordAuthRequestDecoder());
                ctx.write(new DefaultSocks5InitialResponse(Socks5AuthMethod.PASSWORD));
                return false;
            }

            // Neither private nor password auth was matched
            ctx.pipeline().replace(DECODER, DECODER, new Socks5PasswordAuthRequestDecoder());
            ctx.write(new DefaultSocks5InitialResponse(Socks5AuthMethod.PASSWORD));
            return false;
        }

        if (msg instanceof Socks5PasswordAuthRequest) {
            Socks5PasswordAuthRequest req = (Socks5PasswordAuthRequest) msg;
            if (req.username().equals(username) && req.password().equals(password)) {
                ctx.pipeline().replace(DECODER, DECODER, new Socks5CommandRequestDecoder());
                ctx.write(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS));
                return true;
            }

            ctx.pipeline().replace(DECODER, DECODER, new Socks5PasswordAuthRequestDecoder());
            ctx.write(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.FAILURE));
            return false;
        }

        if (msg instanceof Socks5PrivateAuthRequest) {
            Socks5PrivateAuthRequest req = (Socks5PrivateAuthRequest) msg;
            if (Arrays.equals(req.privateToken(), privateToken)) {
                ctx.pipeline().replace(DECODER, DECODER, new Socks5CommandRequestDecoder());
                ctx.write(new DefaultSocks5PrivateAuthResponse(Socks5PrivateAuthStatus.SUCCESS));
                return true;
            }

            ctx.pipeline().replace(DECODER, DECODER, new Socks5PrivateAuthRequestDecoder());
            ctx.write(new DefaultSocks5PrivateAuthResponse(Socks5PrivateAuthStatus.FAILURE));
            return false;
        }

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

            Socks5CommandRequest req = (Socks5CommandRequest) msg;
            assertEquals(Socks5CommandType.CONNECT, req.type());

            Socks5CommandResponse res =
                    new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, Socks5AddressType.IPv4);
            intermediaryDestination = SocketUtils.socketAddress(req.dstAddr(), req.dstPort());

            ctx.write(res);

            ctx.pipeline().remove(ENCODER);
            ctx.pipeline().remove(DECODER);

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

            Socks5CommandRequest req = (Socks5CommandRequest) msg;
            assertEquals(Socks5CommandType.CONNECT, req.type());

            ctx.pipeline().addBefore(ctx.name(), "lineDecoder", new LineBasedFrameDecoder(64, false, true));

            Socks5CommandResponse res;
            boolean sendGreeting = false;
            if (!req.dstAddr().equals(destination.getHostString()) ||
                       req.dstPort() != destination.getPort()) {
                res = new DefaultSocks5CommandResponse(Socks5CommandStatus.FORBIDDEN, Socks5AddressType.IPv4);
            } else {
                res = new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, Socks5AddressType.IPv4);
                sendGreeting = true;
            }

            ctx.write(res);

            ctx.pipeline().remove(ENCODER);
            ctx.pipeline().remove(DECODER);

            if (sendGreeting) {
                ctx.write(Unpooled.copiedBuffer("0\n", CharsetUtil.US_ASCII));
            }

            return true;
        }
    }
}
