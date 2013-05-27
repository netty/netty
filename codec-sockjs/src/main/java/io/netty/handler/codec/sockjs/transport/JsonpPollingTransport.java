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
package io.netty.handler.codec.sockjs.transport;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.sockjs.SockJsConfig;
import io.netty.handler.codec.sockjs.handler.SessionHandler.Event;
import io.netty.handler.codec.sockjs.protocol.Frame;
import io.netty.handler.codec.sockjs.util.JsonConverter;
import io.netty.util.ReferenceCountUtil;

import java.nio.CharBuffer;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpHeaders.Values.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.sockjs.transport.HttpResponseBuilder.*;
import static io.netty.util.CharsetUtil.*;

/**
 * JSON Padding (JSONP) Polling is a transport where there is no open connection between
 * the client and the server. Instead the client will issue a new request for polling from
 * and sending data to the SockJS service.
 *
 * This handler is responsible for sending data back to the client. Since JSONP is in use
 * it need to inspect the HTTP request to find the callback method which is identified as
 * a query parameter 'c'. The name of the callback method will be used to wrap the data
 * into a javascript function call which is what will returned to the client.
 *
 * @see JsonpSendTransport
 */
public class JsonpPollingTransport extends ChannelHandlerAdapter {

    private final FullHttpRequest request;
    private final SockJsConfig config;
    private String callback;

    public JsonpPollingTransport(final SockJsConfig config, final FullHttpRequest request) {
        this.request = request;
        this.config = config;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            final QueryStringDecoder qsd = new QueryStringDecoder(request.uri());
            final List<String> c = qsd.parameters().get("c");
            if (c == null) {
                ReferenceCountUtil.release(msg);
                ctx.writeAndFlush(HttpResponseBuilder.responseFor(request)
                        .status(INTERNAL_SERVER_ERROR)
                        .content("\"callback\" parameter required")
                        .contentType(HttpResponseBuilder.CONTENT_TYPE_JAVASCRIPT)
                        .buildFullResponse());
                ctx.fireUserEventTriggered(Event.CLOSE_SESSION);
                return;
            } else {
                callback = c.get(0);
            }
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
            throws Exception {
        if (msg instanceof Frame) {
            final Frame frame = (Frame) msg;
            final ByteBuf content = wrapWithFunction(frame.content(), ctx);
            ReferenceCountUtil.release(frame);
            ctx.writeAndFlush(responseFor(request)
                    .ok()
                    .content(content)
                    .contentType(CONTENT_TYPE_JAVASCRIPT)
                    .setCookie(config)
                    .header(CONNECTION, CLOSE)
                    .header(CACHE_CONTROL, NO_CACHE_HEADER)
                    .buildFullResponse(),
                    promise);
        } else {
            ctx.write(ReferenceCountUtil.retain(msg), promise);
        }
    }

    private ByteBuf wrapWithFunction(final ByteBuf data, final ChannelHandlerContext ctx) {
        final ByteBufAllocator alloc = ctx.alloc();
        final ByteBuf content = alloc.buffer();
        JsonConverter.escapeJson(data, content);
        final String function = callback + "(\"" + content.toString(UTF_8) + "\");\r\n";
        content.release();
        return ByteBufUtil.encodeString(alloc, CharBuffer.wrap(function), UTF_8);
    }

}

