/*
 * Copyright 2024 The Netty Project
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
package io.netty.handler.codec.http3;

import java.util.UUID;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AsciiString;
import io.netty.util.internal.ObjectUtil;
/**
 * WebTransport 会话的处理器，负责处理 HTTP/3 CONNECT 请求并建立 WebTransport 会话。
 */
@ChannelHandler.Sharable
public class WebTransportHandler extends ChannelInboundHandlerAdapter {

    private static final AsciiString WEB_TRANSPORT = AsciiString.cached("webtransport");
    private static final AsciiString WEB_TRANSPORT_VERSION = AsciiString.cached("webtransport-http3-draft02");

    private final WebTransportObserver observer;

    public WebTransportHandler() {
        this(NoopWebTransportObserver.INSTANCE);
    }

    public WebTransportHandler(WebTransportObserver observer) {
        this.observer = ObjectUtil.checkNotNull(observer, "observer");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            if (isWebTransportConnect(request)) {
                handleWebTransportConnect(ctx, request);
                return;
            }
        }
        super.channelRead(ctx, msg);
    }

    private boolean isWebTransportConnect(HttpRequest request) {
        return request.method() == HttpMethod.CONNECT &&
               request.headers().get("sec-web-transport-version") != null &&
               request.headers().get("sec-web-transport-version").equals(WEB_TRANSPORT_VERSION);
    }

    private void handleWebTransportConnect(ChannelHandlerContext ctx, HttpRequest request) {
        // 生成会话 ID
        String sessionId = UUID.randomUUID().toString();
        
        // 创建 WebTransport 会话
        WebTransportSession session = new DefaultWebTransportSession(sessionId, ctx.channel(), observer);
        
        // 发送 200 OK 响应
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().add("sec-web-transport-version", WEB_TRANSPORT_VERSION);
        ctx.writeAndFlush(response);
        
        // 通知观测者会话已创建
        observer.onSessionCreated(session);
        
        // 将会话绑定到通道属性，方便后续访问
        ctx.channel().attr(WebTransportSession.SESSION_KEY).set(session);
        
        System.out.println("WebTransport session created and bound to channel: " + sessionId);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        observer.onError(ctx.channel(), cause);
        super.exceptionCaught(ctx, cause);
    }
}