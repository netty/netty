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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.sockjs.SockJsConfig;
import io.netty.handler.codec.sockjs.protocol.Frame;
import io.netty.handler.codec.sockjs.util.JsonConverter;
import io.netty.util.ReferenceCountUtil;

import java.nio.CharBuffer;

import static io.netty.handler.codec.http.HttpHeaders.Names.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Values.CLOSE;
import static io.netty.handler.codec.sockjs.transport.HttpResponseBuilder.CONTENT_TYPE_JAVASCRIPT;
import static io.netty.handler.codec.sockjs.transport.HttpResponseBuilder.NO_CACHE_HEADER;
import static io.netty.handler.codec.sockjs.transport.HttpResponseBuilder.responseFor;
import static io.netty.handler.codec.sockjs.util.Arguments.checkNotNull;
import static io.netty.util.CharsetUtil.UTF_8;

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
public class JsonpPollingTransportOutbound extends ChannelOutboundHandlerAdapter {

    private final FullHttpRequest request;
    private final SockJsConfig config;

    public JsonpPollingTransportOutbound(final SockJsConfig config, final FullHttpRequest request) {
        checkNotNull(config, "config");
        checkNotNull(request, "request");
        this.request = request;
        this.config = config;
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
            throws Exception {
        if (msg instanceof Frame) {
            final Frame frame = (Frame) msg;
            final ByteBuf content = wrapWithFunction(frame.content(), ctx);
            ReferenceCountUtil.release(frame);
            ctx.write(responseFor(request)
                    .ok()
                    .content(content)
                    .contentType(CONTENT_TYPE_JAVASCRIPT)
                    .setCookie(config)
                    .header(CONNECTION, CLOSE)
                    .header(CACHE_CONTROL, NO_CACHE_HEADER)
                    .buildFullResponse(),
                    promise);
        } else {
            ctx.write(msg, promise);
        }
    }

    private static ByteBuf wrapWithFunction(final ByteBuf data, final ChannelHandlerContext ctx) {
        final ByteBufAllocator alloc = ctx.alloc();
        final ByteBuf content = alloc.buffer();
        JsonConverter.escapeJson(data, content);
        final String callback = ctx.attr(JsonpPollingTransportInbound.CALLBACK).get();
        final String function = callback + "(\"" + content.toString(UTF_8) + "\");\r\n";
        content.release();
        return ByteBufUtil.encodeString(alloc, CharBuffer.wrap(function), UTF_8);
    }
}

