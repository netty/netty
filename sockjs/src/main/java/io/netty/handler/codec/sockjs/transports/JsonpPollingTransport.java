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

import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.MessageList;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.sockjs.Config;
import io.netty.handler.codec.sockjs.handlers.SessionHandler.Events;
import io.netty.handler.codec.sockjs.protocol.Frame;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.List;

public class JsonpPollingTransport extends ChannelOutboundHandlerAdapter implements ChannelInboundHandler {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(JsonpPollingTransport.class);
    private final FullHttpRequest request;
    private final Config config;
    private String callback;

    public JsonpPollingTransport(final Config config, final FullHttpRequest request) {
        this.request = request;
        this.request.retain();
        this.config = config;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageList<Object> msgs) throws Exception {
        final int size = msgs.size();
        for (int i = 0; i < size; i ++) {
            final Object obj = msgs.get(i);
            if (obj instanceof HttpRequest) {
                final QueryStringDecoder qsd = new QueryStringDecoder(request.getUri());
                final List<String> c = qsd.parameters().get("c");
                if (c == null) {
                    respond(ctx, request.getProtocolVersion(), INTERNAL_SERVER_ERROR,
                            "\"callback\" parameter required");
                    ctx.fireUserEventTriggered(Events.CLOSE_SESSION);
                    return;
                } else {
                    callback = c.get(0);
                }
            }
        }
        ctx.fireMessageReceived(msgs);
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final MessageList<Object> msgs, final ChannelPromise promise)
            throws Exception {
        final int size = msgs.size();
        for (int i = 0; i < size; i ++) {
            final Object obj = msgs.get(i);
            if (obj instanceof Frame) {
                final Frame frame = (Frame) obj;
                logger.debug("flush : " + frame);
                final FullHttpResponse response = new DefaultFullHttpResponse(request.getProtocolVersion(), OK);
                final ByteBuf content = frame.content();

                final ByteBuf buffer = Unpooled.buffer();
                Transports.escapeJson(content, buffer);
                final String function = callback + "(\"" + buffer.toString(CharsetUtil.UTF_8) + "\");\r\n";

                final ByteBuf responseContent = Unpooled.copiedBuffer(function, CharsetUtil.UTF_8);
                response.headers().set(CONTENT_TYPE, Transports.CONTENT_TYPE_JAVASCRIPT);
                response.headers().set(CONTENT_LENGTH, responseContent.readableBytes());
                response.content().writeBytes(responseContent);
                response.headers().set(CONNECTION, HttpHeaders.Values.CLOSE);
                Transports.setNoCacheHeaders(response);
                Transports.setSessionIdCookie(response, config, request);
                ctx.write(response, promise);
            }
        }
    }

    private void respond(final ChannelHandlerContext ctx,
            final HttpVersion httpVersion,
            final HttpResponseStatus status,
            final String message) throws Exception {
        final FullHttpResponse response = new DefaultFullHttpResponse(httpVersion, status);
        Transports.writeContent(response, message, Transports.CONTENT_TYPE_JAVASCRIPT);
        if (ctx.channel().isActive() && ctx.channel().isRegistered()) {
            ctx.write(response);
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        logger.debug("Added [" + ctx + "]");
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelReadSuspended();
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelUnregistered();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelInactive();
    }

    @Override
    public void channelReadSuspended(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelReadSuspended();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelWritabilityChanged();
    }

}

