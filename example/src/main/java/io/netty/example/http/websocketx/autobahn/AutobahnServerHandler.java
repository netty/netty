/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.http.websocketx.autobahn;

import static io.netty.handler.codec.http.HttpHeaders.*;
import static io.netty.handler.codec.http.HttpMethod.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;

import io.netty.buffer.ChannelBuffers;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ExceptionEvent;
import io.netty.channel.MessageEvent;
import io.netty.channel.SimpleChannelUpstreamHandler;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;
import io.netty.util.CharsetUtil;

/**
 * Handles handshakes and messages
 */
public class AutobahnServerHandler extends SimpleChannelUpstreamHandler {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AutobahnServerHandler.class);

    private WebSocketServerHandshaker handshaker;

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Object msg = e.getMessage();
        if (msg instanceof HttpRequest) {
            handleHttpRequest(ctx, (HttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, HttpRequest req) throws Exception {
        // Allow only GET methods.
        if (req.getMethod() != GET) {
            sendHttpResponse(ctx, req, new DefaultHttpResponse(HTTP_1_1, FORBIDDEN));
            return;
        }

        // Handshake
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                this.getWebSocketLocation(req), null, false);
        this.handshaker = wsFactory.newHandshaker(req);
        if (this.handshaker == null) {
            wsFactory.sendUnsupportedWebSocketVersionResponse(ctx.channel());
        } else {
            this.handshaker.handshake(ctx.channel(), req);
        }
    }

    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (logger.isDebugEnabled()) {
            logger.debug(String.format("Channel %s received %s", ctx.channel().getId(), frame.getClass()
                    .getSimpleName()));
        }

        if (frame instanceof CloseWebSocketFrame) {
            this.handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame);
        } else if (frame instanceof PingWebSocketFrame) {
            ctx.channel().write(
                    new PongWebSocketFrame(frame.isFinalFragment(), frame.getRsv(), frame.getBinaryData()));
        } else if (frame instanceof TextWebSocketFrame) {
            // String text = ((TextWebSocketFrame) frame).getText();
            ctx.channel().write(
                    new TextWebSocketFrame(frame.isFinalFragment(), frame.getRsv(), frame.getBinaryData()));
        } else if (frame instanceof BinaryWebSocketFrame) {
            ctx.channel().write(
                    new BinaryWebSocketFrame(frame.isFinalFragment(), frame.getRsv(), frame.getBinaryData()));
        } else if (frame instanceof ContinuationWebSocketFrame) {
            ctx.channel().write(
                    new ContinuationWebSocketFrame(frame.isFinalFragment(), frame.getRsv(), frame.getBinaryData()));
        } else if (frame instanceof PongWebSocketFrame) {
            // Ignore
        } else {
            throw new UnsupportedOperationException(String.format("%s frame types not supported", frame.getClass()
                    .getName()));
        }
    }

    private void sendHttpResponse(ChannelHandlerContext ctx, HttpRequest req, HttpResponse res) {
        // Generate an error page if response status code is not OK (200).
        if (res.getStatus().getCode() != 200) {
            res.setContent(ChannelBuffers.copiedBuffer(res.getStatus().toString(), CharsetUtil.UTF_8));
            setContentLength(res, res.getContent().readableBytes());
        }

        // Send the response and close the connection if necessary.
        ChannelFuture f = ctx.channel().write(res);
        if (!isKeepAlive(req) || res.getStatus().getCode() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        e.cause().printStackTrace();
        e.channel().close();
    }

    private String getWebSocketLocation(HttpRequest req) {
        return "ws://" + req.getHeader(HttpHeaders.Names.HOST);
    }
}
