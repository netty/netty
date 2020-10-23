/*
 * Copyright 2020 The Netty Project
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
package io.netty.example.stomp.websocket;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URL;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpHeaderValues.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;

@Sharable
public final class StompWebSocketClientPageHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    static final StompWebSocketClientPageHandler INSTANCE = new StompWebSocketClientPageHandler();

    private StompWebSocketClientPageHandler() {
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (request.headers().contains(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET, true)) {
            ctx.fireChannelRead(request.retain());
            return;
        }

        if (request.decoderResult().isFailure()) {
            FullHttpResponse badRequest = new DefaultFullHttpResponse(request.protocolVersion(), BAD_REQUEST);
            sendResponse(badRequest, ctx, true);
            return;
        }

        if (!sendResource(request, ctx)) {
            FullHttpResponse notFound = new DefaultFullHttpResponse(request.protocolVersion(), NOT_FOUND);
            notFound.headers().set(CONTENT_TYPE, TEXT_PLAIN);
            String payload = "Requested resource " + request.uri() + " not found";
            notFound.content().writeCharSequence(payload, CharsetUtil.UTF_8);
            HttpUtil.setContentLength(notFound, notFound.content().readableBytes());
            sendResponse(notFound, ctx, true);
        }
    }

    private static boolean sendResource(FullHttpRequest request, ChannelHandlerContext ctx) {
        if (request.uri().isEmpty() || !request.uri().startsWith("/")) {
            return false;
        }

        String requestResource = request.uri().substring(1);
        if (requestResource.isEmpty()) {
            requestResource = "index.html";
        }

        URL resourceUrl = INSTANCE.getClass().getResource(requestResource);
        if (resourceUrl == null) {
            return false;
        }

        RandomAccessFile raf = null;
        long fileLength = -1L;
        try {
            raf = new RandomAccessFile(resourceUrl.getFile(), "r");
            fileLength = raf.length();
        } catch (FileNotFoundException fne) {
            System.out.println("File not found " + fne.getMessage());
            return false;
        } catch (IOException io) {
            System.out.println("Cannot read file length " + io.getMessage());
            return false;
        } finally {
            if (fileLength < 0 && raf != null) {
                try {
                    raf.close();
                } catch (IOException io) {
                    // Nothing to do
                }
            }
        }

        HttpResponse response = new DefaultHttpResponse(request.protocolVersion(), OK);
        HttpUtil.setContentLength(response, fileLength);

        String contentType = "application/octet-stream";
        if (requestResource.endsWith("html")) {
            contentType = "text/html; charset=UTF-8";
        } else if (requestResource.endsWith("css")) {
            contentType = "text/css; charset=UTF-8";
        } else if (requestResource.endsWith("js")) {
            contentType = "application/javascript";
        }

        response.headers().set(CONTENT_TYPE, contentType);
        sendResponse(response, ctx, false);
        ctx.write(new DefaultFileRegion(raf.getChannel(), 0, fileLength));
        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        return true;
    }

    private static void sendResponse(HttpResponse response, ChannelHandlerContext ctx, boolean autoFlush) {
        if (HttpUtil.isKeepAlive(response)) {
            if (response.protocolVersion().equals(HTTP_1_0)) {
                response.headers().set(CONNECTION, KEEP_ALIVE);
            }
            ctx.write(response);
        } else {
            response.headers().set(CONNECTION, CLOSE);
            ctx.write(response).addListener(ChannelFutureListener.CLOSE);
        }

        if (autoFlush) {
            ctx.flush();
        }
    }
}
