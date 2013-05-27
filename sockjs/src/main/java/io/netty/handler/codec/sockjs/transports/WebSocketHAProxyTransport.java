/*
 * Copyright 2012 The Netty Project
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
import io.netty.buffer.ByteBufInputStream;
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
import io.netty.handler.codec.sockjs.handlers.SessionHandler;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.ObjectMapper;

/**
 * WebSocketTransport is responsible for the WebSocket handshake and
 * also for receiving WebSocket frames.
 */
public class WebSocketHAProxyTransport extends SimpleChannelInboundHandler<Object> {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(WebSocketHAProxyTransport.class);
    private static final AttributeKey<HttpRequest> REQUEST_KEY = new AttributeKey<HttpRequest>("ha-request.key");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private WebSocketServerHandshaker handshaker;
    private final WebSocketHAProxyHandshaker haHandshaker;

    public WebSocketHAProxyTransport(final WebSocketHAProxyHandshaker haHandshaker) {
        this.haHandshaker = haHandshaker;
    }

    @Override
    protected void messageReceived(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        logger.info("messageReceived : " + msg);
        if (msg instanceof ByteBuf) {
            handleContent(ctx, (ByteBuf) msg);
        } else if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
        ctx.fireUserEventTriggered(SessionHandler.Events.HANDLE_SESSION);
    }

    private void handleContent(final ChannelHandlerContext ctx, final ByteBuf nounce) throws Exception {
        logger.info("Nounce: " + nounce.toString(CharsetUtil.UTF_8));
        final ByteBuf key = haHandshaker.calculateLastKey(nounce);
        final ChannelFuture channelFuture = ctx.write(key);
        haHandshaker.addWsCodec(channelFuture);
    }

    private void handleWebSocketFrame(final ChannelHandlerContext ctx, final WebSocketFrame wsFrame) throws Exception {
        if (wsFrame instanceof CloseWebSocketFrame) {
            wsFrame.retain();
            logger.debug("Closing WebSocket...send close frame to SockJS service");
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) wsFrame);
            ctx.close();
            return;
        }
        if (wsFrame instanceof PingWebSocketFrame) {
            wsFrame.content().retain();
            ctx.channel().write(new PongWebSocketFrame(wsFrame.content()));
            return;
        }
        if (!(wsFrame instanceof TextWebSocketFrame)) {
            throw new UnsupportedOperationException(String.format("%s frame types not supported",
                    wsFrame.getClass().getName()));
        }
        final String[] messages = toString((TextWebSocketFrame) wsFrame);
        for (String message : messages) {
            logger.debug("fire recieved message : " + message);
            ctx.fireMessageReceived(message);
        }
    }

    @SuppressWarnings("resource")
    private String[] toString(final TextWebSocketFrame textFrame) throws Exception {
        final ByteBuf content = textFrame.content();
        if (content.readableBytes() == 0) {
            return new String[]{};
        }
        final ByteBufInputStream byteBufInputStream = new ByteBufInputStream(content);
        final byte firstByte = content.getByte(0);
        if (firstByte == '[') {
            return OBJECT_MAPPER.readValue(byteBufInputStream, String[].class);
        } else {
            return new String[]{OBJECT_MAPPER.readValue(byteBufInputStream, String.class)};
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        logger.info("Added [" + ctx + "]");
    }

    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {
        if (cause instanceof JsonParseException) {
            logger.debug("Could not parse json", cause);
            ctx.close();
        } else if (cause instanceof WebSocketHandshakeException) {
            final HttpRequest request = ctx.attr(REQUEST_KEY).get();
            logger.error("Failed with ws handshake for request: " + request, cause);
            ctx.write(internalServerErrorResponse(request.getProtocolVersion(), cause.getMessage()))
            .addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.fireExceptionCaught(cause);
        }
    }

}
