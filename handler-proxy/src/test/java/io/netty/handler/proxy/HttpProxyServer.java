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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.SocketUtils;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

final class HttpProxyServer extends ProxyServer {

    HttpProxyServer(boolean useSsl, TestMode testMode, InetSocketAddress destination) {
        super(useSsl, testMode, destination);
    }

    HttpProxyServer(
            boolean useSsl, TestMode testMode, InetSocketAddress destination, String username, String password) {
        super(useSsl, testMode, destination, username, password);
    }

    @Override
    protected void configure(SocketChannel ch) throws Exception {
        ChannelPipeline p = ch.pipeline();
        switch (testMode) {
        case INTERMEDIARY:
            p.addLast(new HttpServerCodec());
            p.addLast(new HttpObjectAggregator(1));
            p.addLast(new HttpIntermediaryHandler());
            break;
        case TERMINAL:
            p.addLast(new HttpServerCodec());
            p.addLast(new HttpObjectAggregator(1));
            p.addLast(new HttpTerminalHandler());
            break;
        case UNRESPONSIVE:
            p.addLast(UnresponsiveHandler.INSTANCE);
            break;
        }
    }

    private boolean authenticate(ChannelHandlerContext ctx, FullHttpRequest req) {
        assertThat(req.method(), is(HttpMethod.CONNECT));

        if (testMode != TestMode.INTERMEDIARY) {
            ctx.pipeline().addBefore(ctx.name(), "lineDecoder", new LineBasedFrameDecoder(64, false, true));
        }

        ctx.pipeline().remove(HttpObjectAggregator.class);
        ctx.pipeline().get(HttpServerCodec.class).removeInboundHandler();

        boolean authzSuccess = false;
        if (username != null) {
            CharSequence authz = req.headers().get(HttpHeaderNames.PROXY_AUTHORIZATION);
            if (authz != null) {
                String[] authzParts = authz.toString().split(" ", 2);
                ByteBuf authzBuf64 = Unpooled.copiedBuffer(authzParts[1], CharsetUtil.US_ASCII);
                ByteBuf authzBuf = Base64.decode(authzBuf64);

                String expectedAuthz = username + ':' + password;
                authzSuccess = "Basic".equals(authzParts[0]) &&
                               expectedAuthz.equals(authzBuf.toString(CharsetUtil.US_ASCII));

                authzBuf64.release();
                authzBuf.release();
            }
        } else {
            authzSuccess = true;
        }

        return authzSuccess;
    }

    private final class HttpIntermediaryHandler extends IntermediaryHandler {

        private SocketAddress intermediaryDestination;

        @Override
        protected boolean handleProxyProtocol(ChannelHandlerContext ctx, Object msg) throws Exception {
            FullHttpRequest req = (FullHttpRequest) msg;
            FullHttpResponse res;
            if (!authenticate(ctx, req)) {
                res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED);
                res.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
            } else {
                res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                String uri = req.uri();
                int lastColonPos = uri.lastIndexOf(':');
                assertThat(lastColonPos, is(greaterThan(0)));
                intermediaryDestination = SocketUtils.socketAddress(
                        uri.substring(0, lastColonPos), Integer.parseInt(uri.substring(lastColonPos + 1)));
            }

            ctx.write(res);
            ctx.pipeline().get(HttpServerCodec.class).removeOutboundHandler();
            return true;
        }

        @Override
        protected SocketAddress intermediaryDestination() {
            return intermediaryDestination;
        }
    }

    private final class HttpTerminalHandler extends TerminalHandler {

        @Override
        protected boolean handleProxyProtocol(ChannelHandlerContext ctx, Object msg) throws Exception {
            FullHttpRequest req = (FullHttpRequest) msg;
            FullHttpResponse res;
            boolean sendGreeting = false;

            if (!authenticate(ctx, req)) {
                res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED);
                res.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
            } else if (!req.uri().equals(destination.getHostString() + ':' + destination.getPort())) {
                res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN);
                res.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
            } else {
                res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                sendGreeting = true;
            }

            ctx.write(res);
            ctx.pipeline().get(HttpServerCodec.class).removeOutboundHandler();

            if (sendGreeting) {
                ctx.write(Unpooled.copiedBuffer("0\n", CharsetUtil.US_ASCII));
            }

            return true;
        }
    }
}
