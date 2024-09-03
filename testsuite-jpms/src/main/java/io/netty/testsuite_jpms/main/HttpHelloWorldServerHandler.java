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

package io.netty.testsuite_jpms.main;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.ssl.SslHandler;

import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty.handler.codec.http.HttpHeaderValues.TEXT_PLAIN;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;

public class HttpHelloWorldServerHandler extends SimpleChannelInboundHandler<HttpObject> {

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
        if (msg instanceof HttpRequest) {
            HttpRequest req = (HttpRequest) msg;

            String modulesInfo = ModuleLayer.boot().modules().stream()
                    .map(module -> "- " + module.getName() + " " +
                            (module.getDescriptor().isAutomatic() ? "(automatic)" : ""))
                    .collect(Collectors.joining("\r\n", "Boot layer:\r\n", "\r\n"));

            String channelType = ctx.channel().getClass().getName();

            String sslEngineInfo = "";
            SslHandler sslHandler = ctx.pipeline().get(SslHandler.class);
            if (sslHandler != null) {
                sslEngineInfo = "SSL Engine: " + sslHandler.engine().getClass().getName() + "\r\n";
            }

            String hello = "Hello World\r\n" +
                    "Transport: " + channelType + "\r\n" +
                    sslEngineInfo +
                    modulesInfo;

            boolean keepAlive = HttpUtil.isKeepAlive(req);
            FullHttpResponse response = new DefaultFullHttpResponse(
                    req.protocolVersion(), OK,
                    Unpooled.copiedBuffer(hello, StandardCharsets.UTF_8));
            response.headers()
                    .set(CONTENT_TYPE, TEXT_PLAIN)
                    .setInt(CONTENT_LENGTH, response.content().readableBytes());

            if (keepAlive) {
                if (!req.protocolVersion().isKeepAliveDefault()) {
                    response.headers().set(CONNECTION, KEEP_ALIVE);
                }
            } else {
                // Tell the client we're going to close the connection.
                response.headers().set(CONNECTION, CLOSE);
            }

            ChannelFuture f = ctx.write(response);

            if (!keepAlive) {
                f.addListener(ChannelFutureListener.CLOSE);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
