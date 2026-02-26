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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialRequest;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandRequest;
import io.netty.handler.codec.socksx.v5.DefaultSocks5PasswordAuthRequest;
import io.netty.handler.codec.socksx.v5.DefaultSocks5PrivateAuthRequest;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequest;
import io.netty.handler.codec.socksx.v5.Socks5InitialResponse;
import io.netty.handler.codec.socksx.v5.Socks5InitialResponseDecoder;
import io.netty.handler.codec.socksx.v5.Socks5ClientEncoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponseDecoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.handler.codec.socksx.v5.Socks5CommandType;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthResponse;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthResponseDecoder;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthStatus;
import io.netty.handler.codec.socksx.v5.Socks5PrivateAuthResponse;
import io.netty.handler.codec.socksx.v5.Socks5PrivateAuthResponseDecoder;
import io.netty.handler.codec.socksx.v5.Socks5PrivateAuthStatus;
import io.netty.util.NetUtil;
import io.netty.util.internal.StringUtil;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.Collections;

/**
 * Handler that establishes a blind forwarding proxy tunnel using
 * <a href="https://www.rfc-editor.org/rfc/rfc1928">SOCKS Protocol Version 5</a>.
 */
public final class Socks5ProxyHandler extends ProxyHandler {

    private static final String PROTOCOL = "socks5";
    private static final String AUTH_PASSWORD = "password";
    private static final String AUTH_PRIVATE = "private";

    private static final byte NO_PRIVATE_AUTH_METHOD =
        Socks5AuthMethod.NO_AUTH.byteValue();

    private static final Socks5InitialRequest INIT_REQUEST_NO_AUTH =
            new DefaultSocks5InitialRequest(Collections.singletonList(Socks5AuthMethod.NO_AUTH));

    private static final Socks5InitialRequest INIT_REQUEST_PASSWORD =
            new DefaultSocks5InitialRequest(Arrays.asList(Socks5AuthMethod.NO_AUTH, Socks5AuthMethod.PASSWORD));

    private final String username;
    private final String password;
    private final byte privateAuthMethod;
    private final byte[] privateToken;
    private final Socks5ClientEncoder clientEncoder;

    private String decoderName;
    private String encoderName;

    public Socks5ProxyHandler(SocketAddress proxyAddress) {
        this(proxyAddress, null, null);
    }

    public Socks5ProxyHandler(SocketAddress proxyAddress, String username, String password) {
        super(proxyAddress);
        if (username != null && username.isEmpty()) {
            username = null;
        }
        if (password != null && password.isEmpty()) {
            password = null;
        }
        this.username = username;
        this.password = password;
        this.privateToken = null;
        this.privateAuthMethod = NO_PRIVATE_AUTH_METHOD; // No private authentication method specified
        this.clientEncoder = Socks5ClientEncoder.DEFAULT;
    }

    /**
     * Creates a new SOCKS5 proxy handler with a custom private authentication method.
     *
     * @param proxyAddress     The address of the SOCKS5 proxy server
     * @param privateAuthMethod The private authentication method code (must be in range 0x80-0xFE)
     * @param privateToken     The token to use for private authentication
     * @param customEncoder    The custom encoder to use for encoding SOCKS5 messages, if {@code null} the
     *                         {@link Socks5ClientEncoder#DEFAULT} will be used
     * @throws IllegalArgumentException If privateAuthMethod is not in the valid range
     */
    public Socks5ProxyHandler(SocketAddress proxyAddress, byte privateAuthMethod, byte[] privateToken,
                              Socks5ClientEncoder customEncoder) {
        super(proxyAddress);
        if (!Socks5AuthMethod.isPrivateMethod(privateAuthMethod)) {
            throw new IllegalArgumentException(
                    "privateAuthMethod: " + (privateAuthMethod & 0xFF) + " (expected: 0x80-0xFE)");
        }
        this.username = this.password = null;
        this.privateToken = privateToken;
        this.privateAuthMethod = privateAuthMethod;
        this.clientEncoder = customEncoder != null ? customEncoder : Socks5ClientEncoder.DEFAULT;
    }

    @Override
    public String protocol() {
        return PROTOCOL;
    }

    @Override
    public String authScheme() {
        Socks5AuthMethod authMethod = socksAuthMethod();
        if (Socks5AuthMethod.isPrivateMethod(authMethod.byteValue())) {
            return AUTH_PRIVATE;
        }
        if (authMethod == Socks5AuthMethod.PASSWORD) {
            return AUTH_PASSWORD;
        }
        return AUTH_NONE;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    @Override
    protected void addCodec(ChannelHandlerContext ctx) throws Exception {
        ChannelPipeline p = ctx.pipeline();
        String name = ctx.name();

        Socks5InitialResponseDecoder decoder = new Socks5InitialResponseDecoder();
        p.addBefore(name, null, decoder);

        decoderName = p.context(decoder).name();
        encoderName = decoderName + ".encoder";

        p.addBefore(name, encoderName, clientEncoder);
    }

    @Override
    protected void removeEncoder(ChannelHandlerContext ctx) throws Exception {
        ctx.pipeline().remove(encoderName);
    }

    @Override
    protected void removeDecoder(ChannelHandlerContext ctx) throws Exception {
        ChannelPipeline p = ctx.pipeline();
        if (p.context(decoderName) != null) {
            p.remove(decoderName);
        }
    }

    @Override
    protected Object newInitialMessage(ChannelHandlerContext ctx) throws Exception {
        Socks5AuthMethod authMethod = socksAuthMethod();
        if (authMethod == Socks5AuthMethod.PASSWORD) {
            return INIT_REQUEST_PASSWORD;
        }
        if (Socks5AuthMethod.isPrivateMethod(authMethod.byteValue())) {
            return new DefaultSocks5InitialRequest(Arrays.asList(Socks5AuthMethod.NO_AUTH,
                authMethod));
        }
        return INIT_REQUEST_NO_AUTH;
    }

    @Override
    protected boolean handleResponse(ChannelHandlerContext ctx, Object response) throws Exception {
        if (response instanceof Socks5InitialResponse) {
            Socks5InitialResponse res = (Socks5InitialResponse) response;
            Socks5AuthMethod authMethod = socksAuthMethod();
            Socks5AuthMethod resAuthMethod = res.authMethod();
            if (resAuthMethod != Socks5AuthMethod.NO_AUTH && resAuthMethod != authMethod
                && !Socks5AuthMethod.isPrivateMethod(resAuthMethod.byteValue())) {
                // Server did not allow unauthenticated access nor accept the requested authentication scheme.
                throw new ProxyConnectException(exceptionMessage("unexpected authMethod: " + res.authMethod()));
            }

            if (resAuthMethod == Socks5AuthMethod.NO_AUTH) {
                sendConnectCommand(ctx);
            } else if (resAuthMethod == Socks5AuthMethod.PASSWORD) {
                // In case of password authentication, send an authentication request.
                ctx.pipeline().replace(decoderName, decoderName, new Socks5PasswordAuthResponseDecoder());
                sendToProxyServer(new DefaultSocks5PasswordAuthRequest(
                        username != null? username : "", password != null? password : ""));
            } else if (Socks5AuthMethod.isPrivateMethod(resAuthMethod.byteValue())) {
                ctx.pipeline().replace(decoderName, decoderName, new Socks5PrivateAuthResponseDecoder());
                sendToProxyServer(new DefaultSocks5PrivateAuthRequest(privateToken));
            } else {
                // Should never reach here.
                throw new Error("Unexpected authMethod: " + resAuthMethod);
            }

            return false;
        }

        if (response instanceof Socks5PasswordAuthResponse) {
            // Received an authentication response from the server.
            Socks5PasswordAuthResponse res = (Socks5PasswordAuthResponse) response;
            if (res.status() != Socks5PasswordAuthStatus.SUCCESS) {
                throw new ProxyConnectException(exceptionMessage("authStatus: " + res.status()));
            }

            sendConnectCommand(ctx);
            return false;
        }

        if (response instanceof Socks5PrivateAuthResponse) {
            Socks5PrivateAuthResponse res = (Socks5PrivateAuthResponse) response;
            if (res.status() != Socks5PrivateAuthStatus.SUCCESS) {
                throw new ProxyConnectException(exceptionMessage("privateAuthStatus: " + res.status()));
            }

            sendConnectCommand(ctx);
            return false;
        }

        // This should be the last message from the server.
        Socks5CommandResponse res = (Socks5CommandResponse) response;
        if (res.status() != Socks5CommandStatus.SUCCESS) {
            throw new ProxyConnectException(exceptionMessage("status: " + res.status()));
        }

        return true;
    }

    private Socks5AuthMethod socksAuthMethod() {
        Socks5AuthMethod authMethod;
        if (privateToken != null && privateToken.length > 0) {
            authMethod = new Socks5AuthMethod(privateAuthMethod & 0xFF, "PRIVATE_" + (privateAuthMethod & 0xFF));
        } else if (username == null && password == null) {
            authMethod = Socks5AuthMethod.NO_AUTH;
        } else {
            authMethod = Socks5AuthMethod.PASSWORD;
        }
        return authMethod;
    }

    private void sendConnectCommand(ChannelHandlerContext ctx) throws Exception {
        InetSocketAddress raddr = destinationAddress();
        Socks5AddressType addrType;
        String rhost;
        if (raddr.isUnresolved()) {
            addrType = Socks5AddressType.DOMAIN;
            rhost = raddr.getHostString();
        } else {
            rhost = raddr.getAddress().getHostAddress();
            if (NetUtil.isValidIpV4Address(rhost)) {
                addrType = Socks5AddressType.IPv4;
            } else if (NetUtil.isValidIpV6Address(rhost)) {
                addrType = Socks5AddressType.IPv6;
            } else {
                throw new ProxyConnectException(
                        exceptionMessage("unknown address type: " + StringUtil.simpleClassName(rhost)));
            }
        }

        ctx.pipeline().replace(decoderName, decoderName, new Socks5CommandResponseDecoder());
        sendToProxyServer(new DefaultSocks5CommandRequest(Socks5CommandType.CONNECT, addrType, rhost, raddr.getPort()));
    }
}
