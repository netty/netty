/*
 * Copyright 2012 The Netty Project
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
package io.netty5.testsuite.autobahn;

import io.netty5.channel.ChannelFutureListeners;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.http.DefaultFullHttpResponse;
import io.netty5.handler.codec.http.FullHttpResponse;
import io.netty5.handler.codec.http.HttpHeaderNames;
import io.netty5.handler.codec.http.HttpRequest;
import io.netty5.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty5.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty5.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty5.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty5.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty5.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty5.handler.codec.http.websocketx.WebSocketFrame;
import io.netty5.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty5.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty5.util.CharsetUtil;
import io.netty5.util.concurrent.Future;
import io.netty5.util.internal.StringUtil;

import java.util.logging.Level;
import java.util.logging.Logger;

import static io.netty5.handler.codec.http.HttpMethod.GET;
import static io.netty5.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty5.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty5.handler.codec.http.HttpUtil.isKeepAlive;
import static io.netty5.handler.codec.http.HttpUtil.setContentLength;
import static io.netty5.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Handles handshakes and messages
 */
public class AutobahnServerHandler implements ChannelHandler {
    private static final Logger logger = Logger.getLogger(AutobahnServerHandler.class.getName());

    private WebSocketServerHandshaker handshaker;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest) {
            handleHttpRequest(ctx, (HttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        } else {
            throw new IllegalStateException("unknown message: " + msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, HttpRequest req) {
        // Handle a bad request.
        if (!req.decoderResult().isSuccess()) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST,
                    ctx.bufferAllocator().allocate(0)));
            return;
        }

        // Allow only GET methods.
        if (!GET.equals(req.method())) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, FORBIDDEN,
                    ctx.bufferAllocator().allocate(0)));
            return;
        }

        // Handshake
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                getWebSocketLocation(req), null, false, Integer.MAX_VALUE);
        handshaker = wsFactory.newHandshaker(req);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            handshaker.handshake(ctx.channel(), req);
        }
    }

    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(String.format(
                    "Channel %s received %s", ctx.channel().hashCode(), StringUtil.simpleClassName(frame)));
        }

        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx, (CloseWebSocketFrame) frame);
        } else if (frame instanceof PingWebSocketFrame) {
            ctx.write(new PongWebSocketFrame(frame.isFinalFragment(), frame.rsv(), frame.binaryData()));
        } else if (frame instanceof TextWebSocketFrame ||
                frame instanceof BinaryWebSocketFrame ||
                frame instanceof ContinuationWebSocketFrame) {
            ctx.write(frame);
        } else if (frame instanceof PongWebSocketFrame) {
            frame.close();
            // Ignore
        } else {
            throw new UnsupportedOperationException(String.format("%s frame types not supported", frame.getClass()
                    .getName()));
        }
    }

    private static void sendHttpResponse(
            ChannelHandlerContext ctx, HttpRequest req, FullHttpResponse res) {
        // Generate an error page if response status code is not OK (200).
        if (res.status().code() != 200) {
            res.payload().writeCharSequence(res.status().toString(), CharsetUtil.UTF_8);
            setContentLength(res, res.payload().readableBytes());
        }

        // Send the response and close the connection if necessary.
        Future<Void> f = ctx.writeAndFlush(res);
        if (!isKeepAlive(req) || res.status().code() != 200) {
            f.addListener(ctx, ChannelFutureListeners.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }

    private static String getWebSocketLocation(HttpRequest req) {
        return "ws://" + req.headers().get(HttpHeaderNames.HOST);
    }
}
