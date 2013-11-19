/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.sockjs.transports;

import static io.netty.handler.codec.sockjs.transports.Transports.internalServerErrorResponse;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.sockjs.handler.SessionHandler;
import io.netty.handler.codec.sockjs.util.JsonUtil;
import io.netty.util.AttributeKey;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import org.codehaus.jackson.JsonParseException;

/**
 * WebSocketTransport is responsible for the WebSocket handshake and
 * also for receiving WebSocket frames.
 */
public class WebSocketHAProxyTransport extends SimpleChannelInboundHandler<Object> {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(WebSocketHAProxyTransport.class);
    private static final AttributeKey<HttpRequest> REQUEST_KEY = AttributeKey.valueOf("ha-request.key");
    private WebSocketServerHandshaker handshaker;
    private final WebSocketHAProxyHandshaker haHandshaker;

    public WebSocketHAProxyTransport(final WebSocketHAProxyHandshaker haHandshaker) {
        this.haHandshaker = haHandshaker;
    }

    @Override
    protected void messageReceived(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            handleContent(ctx, (ByteBuf) msg);
        } else if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
        ctx.fireUserEventTriggered(SessionHandler.Events.HANDLE_SESSION);
    }

    private void handleContent(final ChannelHandlerContext ctx, final ByteBuf nounce) throws Exception {
        final ByteBuf key = haHandshaker.calculateLastKey(nounce);
        final ChannelFuture channelFuture = ctx.write(key);
        haHandshaker.addWsCodec(channelFuture);
    }

    private void handleWebSocketFrame(final ChannelHandlerContext ctx, final WebSocketFrame wsFrame) throws Exception {
        if (wsFrame instanceof CloseWebSocketFrame) {
            wsFrame.retain();
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) wsFrame);
            ctx.close();
            return;
        }
        if (wsFrame instanceof PingWebSocketFrame) {
            wsFrame.content().retain();
            ctx.channel().writeAndFlush(new PongWebSocketFrame(wsFrame.content()));
            return;
        }
        if (!(wsFrame instanceof TextWebSocketFrame)) {
            throw new UnsupportedOperationException(String.format("%s frame types not supported",
                    wsFrame.getClass().getName()));
        }
        final String[] messages = JsonUtil.decode((TextWebSocketFrame) wsFrame);
        for (String message : messages) {
            ctx.fireChannelRead(message);
        }
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {
        if (cause instanceof JsonParseException) {
            logger.error("Could not parse json", cause);
            ctx.close();
        } else if (cause instanceof WebSocketHandshakeException) {
            final HttpRequest request = ctx.attr(REQUEST_KEY).get();
            logger.error("Failed with ws handshake for request: " + request, cause);
            ctx.writeAndFlush(internalServerErrorResponse(request.getProtocolVersion(), cause.getMessage()))
            .addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.fireExceptionCaught(cause);
        }
    }

}
