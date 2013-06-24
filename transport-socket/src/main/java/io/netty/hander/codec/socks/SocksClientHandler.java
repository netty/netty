/*
 * Copyright 2012 The Netty Project
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
package io.netty.hander.codec.socks;


import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socks.SocksAddressType;
import io.netty.handler.codec.socks.SocksAuthRequest;
import io.netty.handler.codec.socks.SocksAuthResponse;
import io.netty.handler.codec.socks.SocksCmdRequest;
import io.netty.handler.codec.socks.SocksCmdResponse;
import io.netty.handler.codec.socks.SocksCmdResponseDecoder;
import io.netty.handler.codec.socks.SocksCmdType;
import io.netty.handler.codec.socks.SocksInitResponse;
import io.netty.handler.codec.socks.SocksResponse;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.util.Arrays;

public class SocksClientHandler extends SimpleChannelInboundHandler<SocksResponse> {
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(SocksClientHandler.class);

    private static final String name = "SOCKS_SERVER_HANDLER";
    private final String host;
    private final int port;
    private SocksAddressType remoteAddressType = SocksAddressType.UNKNOWN;

    private final PasswordAuthentication passwordAuthentication;

    private final ChannelPromise promise;

    public static String getName() {
        return name;
    }

    public SocksClientHandler(InetSocketAddress remoteAddress, ChannelPromise promise) {
        this(remoteAddress, null, promise);
    }

    public SocksClientHandler(InetSocketAddress remoteAddress,
                              PasswordAuthentication passwordAuthentication,
                              ChannelPromise promise) {
        InetAddress address = remoteAddress.getAddress();
        if (remoteAddress.isUnresolved()) {
            remoteAddressType = SocksAddressType.DOMAIN;
            this.host = remoteAddress.getHostName();
        } else {
            if (address instanceof Inet4Address) {
                this.remoteAddressType = SocksAddressType.IPv4;

            } else if (address instanceof Inet6Address) {
                this.remoteAddressType = SocksAddressType.IPv6;
            }
            this.host = new String(address.getHostAddress());
        }
        if (remoteAddressType.equals(SocksAddressType.UNKNOWN)) {
            throw new IllegalArgumentException();
        }
        this.port = remoteAddress.getPort();
        this.passwordAuthentication = passwordAuthentication;
        this.promise = promise;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, SocksResponse socksResponse) throws Exception {
        switch (socksResponse.responseType()) {
            case INIT:
                logger.debug("Received init response");
                SocksInitResponse initResponse = (SocksInitResponse) socksResponse;
                switch (initResponse.authScheme()) {
                    case NO_AUTH:
                        ctx.pipeline().addFirst(SocksCmdResponseDecoder.getName(), new SocksCmdResponseDecoder());
                        logger.debug("Sending cmd request:");
                        logger.debug("\t SocksCmdType.CONNECT");
                        logger.debug("\t " + remoteAddressType);
                        logger.debug("\t " + host);
                        logger.debug("\t " + port);
                        ctx.write(new SocksCmdRequest(SocksCmdType.CONNECT,
                                this.remoteAddressType,
                                this.host,
                                this.port));
                        break;
                    case AUTH_GSSAPI:
                        throw new Exception();
                    case AUTH_PASSWORD:
                        ctx.pipeline().addFirst(SocksCmdResponseDecoder.getName(), new SocksCmdResponseDecoder());
                        logger.debug("Sending auth request");
                        ctx.write(
                                new SocksAuthRequest(
                                        passwordAuthentication.getUserName(),
                                        Arrays.toString(passwordAuthentication.getPassword())));
                        break;
                    case UNKNOWN:
                        throw new Exception();
                }
                break;
            case AUTH:
                logger.debug("Received auth response");
                SocksAuthResponse authResponse = (SocksAuthResponse) socksResponse;
                switch (authResponse.authStatus()) {
                    case SUCCESS:
                        break;
                    case FAILURE:
                        break;
                }
                break;
            case CMD:
                logger.debug("Received cmd response");
                SocksCmdResponse cmdResponse = (SocksCmdResponse) socksResponse;
                ctx.pipeline().remove(this);
                switch (cmdResponse.cmdStatus()) {
                    case SUCCESS:
                        promise.setSuccess();
                        break;
                    case FAILURE:
                        promise.setFailure(new Exception("FAILURE"));
                        break;
                    case FORBIDDEN:
                        promise.setFailure(new Exception("FORBIDDEN"));
                        break;
                    case NETWORK_UNREACHABLE:
                        promise.setFailure(new Exception("NETWORK_UNREACHABLE"));
                        break;
                    case HOST_UNREACHABLE:
                        promise.setFailure(new Exception("HOST_UNREACHABLE"));
                        break;
                    case REFUSED:
                        promise.setFailure(new Exception("REFUSED"));
                        break;
                    case TTL_EXPIRED:
                        promise.setFailure(new Exception("TTL_EXPIRED"));
                        break;
                    case COMMAND_NOT_SUPPORTED:
                        promise.setFailure(new Exception("COMMAND_NOT_SUPPORTED"));
                        break;
                    case ADDRESS_NOT_SUPPORTED:
                        promise.setFailure(new Exception("ADDRESS_NOT_SUPPORTED"));
                        break;
                    case UNASSIGNED:
                        promise.setFailure(new Exception("UNASSIGNED"));
                        break;
                }
                break;
            case UNKNOWN:
                break;
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable throwable) throws Exception {
        throwable.printStackTrace();
        promise.setFailure(throwable);
    }
}

