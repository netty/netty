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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public final class HttpProxyHandler extends ProxyHandler {

    private static final String PROTOCOL = "http";
    private static final String AUTH_BASIC = "basic";

    private final HttpClientCodec codec = new HttpClientCodec();
    private final String username;
    private final String password;
    private final CharSequence authorization;
    private final boolean ignoreDefaultPortsInConnectHostHeader;
    private HttpResponseStatus status;
    private HttpHeaders headers;

    public HttpProxyHandler(SocketAddress proxyAddress) {
        this(proxyAddress, null);
    }

    public HttpProxyHandler(SocketAddress proxyAddress, HttpHeaders headers) {
        this(proxyAddress, headers, false);
    }

    public HttpProxyHandler(SocketAddress proxyAddress,
                            HttpHeaders headers,
                            boolean ignoreDefaultPortsInConnectHostHeader) {
        super(proxyAddress);
        username = null;
        password = null;
        authorization = null;
        this.headers = headers;
        this.ignoreDefaultPortsInConnectHostHeader = ignoreDefaultPortsInConnectHostHeader;
    }

    public HttpProxyHandler(SocketAddress proxyAddress, String username, String password) {
        this(proxyAddress, username, password, null);
    }

    public HttpProxyHandler(SocketAddress proxyAddress, String username, String password,
                            HttpHeaders headers) {
        this(proxyAddress, username, password, headers, false);
    }

    public HttpProxyHandler(SocketAddress proxyAddress,
                            String username,
                            String password,
                            HttpHeaders headers,
                            boolean ignoreDefaultPortsInConnectHostHeader) {
        super(proxyAddress);
        if (username == null) {
            throw new NullPointerException("username");
        }
        if (password == null) {
            throw new NullPointerException("password");
        }
        this.username = username;
        this.password = password;

        ByteBuf authz = Unpooled.copiedBuffer(username + ':' + password, CharsetUtil.UTF_8);
        ByteBuf authzBase64 = Base64.encode(authz, false);

        authorization = new AsciiString("Basic " + authzBase64.toString(CharsetUtil.US_ASCII));

        authz.release();
        authzBase64.release();

        this.headers = headers;
        this.ignoreDefaultPortsInConnectHostHeader = ignoreDefaultPortsInConnectHostHeader;
    }

    @Override
    public String protocol() {
        return PROTOCOL;
    }

    @Override
    public String authScheme() {
        return authorization != null? AUTH_BASIC : AUTH_NONE;
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
        p.addBefore(name, null, codec);
    }

    @Override
    protected void removeEncoder(ChannelHandlerContext ctx) throws Exception {
        codec.removeOutboundHandler();
    }

    @Override
    protected void removeDecoder(ChannelHandlerContext ctx) throws Exception {
        codec.removeInboundHandler();
    }

    @Override
    protected Object newInitialMessage(ChannelHandlerContext ctx) throws Exception {
        InetSocketAddress raddr = destinationAddress();

        String hostString = HttpUtil.formatHostnameForHttp(raddr);
        int port = raddr.getPort();
        String url = hostString + ":" + port;
        String hostHeader = (ignoreDefaultPortsInConnectHostHeader && (port == 80 || port == 443)) ?
                hostString :
                url;

        FullHttpRequest req = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.CONNECT,
                url,
                Unpooled.EMPTY_BUFFER, false);

        req.headers().set(HttpHeaderNames.HOST, hostHeader);

        if (authorization != null) {
            req.headers().set(HttpHeaderNames.PROXY_AUTHORIZATION, authorization);
        }

        if (headers != null) {
            req.headers().add(headers);
        }

        return req;
    }

    @Override
    protected boolean handleResponse(ChannelHandlerContext ctx, Object response) throws Exception {
        if (response instanceof HttpResponse) {
            if (status != null) {
                throw new ProxyConnectException(exceptionMessage("too many responses"));
            }
            status = ((HttpResponse) response).status();
        }

        boolean finished = response instanceof LastHttpContent;
        if (finished) {
            if (status == null) {
                throw new ProxyConnectException(exceptionMessage("missing response"));
            }
            if (status.code() != 200) {
                throw new ProxyConnectException(exceptionMessage("status: " + status));
            }
        }

        return finished;
    }
}
