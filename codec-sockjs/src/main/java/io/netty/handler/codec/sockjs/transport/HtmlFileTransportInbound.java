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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.sockjs.handler.SessionHandler.Event;
import io.netty.util.AttributeKey;

import java.util.List;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.sockjs.transport.HttpResponseBuilder.*;
import static io.netty.util.ReferenceCountUtil.release;

/**
 * A streaming transport for SockJS.
 *
 * This transport is intended to be used in an iframe, where the src of
 * the iframe will have the an url looking something like this:
 *
 * http://server/echo/serverId/sessionId/htmlfile?c=callback
 * The server will respond with a html snipped containing a html header
 * and a script element. When data is available on the server this classes
 * write method will write a script to the connection that will invoke the
 * callback.
 */
public class HtmlFileTransportInbound extends ChannelInboundHandlerAdapter {

    static final AttributeKey<String> CALLBACK = AttributeKey.valueOf(JsonpPollingTransportInbound.class, "callback");

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            final HttpRequest request = (HttpRequest) msg;
            final String callbackParam = callbackFromRequest(request);
            if (callbackParam.isEmpty()) {
                release(msg);
                respondCallbackRequired(ctx, request);
                ctx.fireUserEventTriggered(Event.CLOSE_SESSION);
                return;
            } else {
                ctx.attr(CALLBACK).set(callbackParam);
            }
        }
        ctx.fireChannelRead(msg);
    }

    private static String callbackFromRequest(final HttpRequest request) {
        final QueryStringDecoder qsd = new QueryStringDecoder(request.uri());
        final List<String> c = qsd.parameters().get("c");
        return c == null || c.isEmpty() ? "" : c.get(0);
    }

    private static void respondCallbackRequired(final ChannelHandlerContext ctx, final HttpRequest request) {
        ctx.writeAndFlush(responseFor(request)
                .internalServerError()
                .content("\"callback\" parameter required")
                .contentType(CONTENT_TYPE_PLAIN)
                .header(CACHE_CONTROL, NO_CACHE_HEADER)
                .buildFullResponse());
    }

}
