/*
 * Copyright 2016 The Netty Project
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
package io.netty5.handler.codec.http;

import io.netty5.channel.ChannelFutureListeners;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelPipeline;
import io.netty5.util.concurrent.Future;

import static io.netty5.handler.codec.http.HttpUtil.isContentLengthSet;
import static io.netty5.handler.codec.http.HttpUtil.isKeepAlive;
import static io.netty5.handler.codec.http.HttpUtil.isTransferEncodingChunked;
import static io.netty5.handler.codec.http.HttpUtil.setKeepAlive;

/**
 * HttpServerKeepAliveHandler helps close persistent connections when appropriate.
 * <p>
 * The server channel is expected to set the proper 'Connection' header if it can handle persistent connections. {@link
 * HttpServerKeepAliveHandler} will automatically close the channel for any LastHttpContent that corresponds to a client
 * request for closing the connection, or if the HttpResponse associated with that LastHttpContent requested closing the
 * connection or didn't have a self defined message length.
 * <p>
 * Since {@link HttpServerKeepAliveHandler} expects {@link HttpObject}s it should be added after {@link HttpServerCodec}
 * but before any other handlers that might send a {@link HttpResponse}. <blockquote>
 * <pre>
 *  {@link ChannelPipeline} p = ...;
 *  ...
 *  p.addLast("serverCodec", new {@link HttpServerCodec}());
 *  p.addLast("httpKeepAlive", <b>new {@link HttpServerKeepAliveHandler}()</b>);
 *  p.addLast("aggregator", new {@link HttpObjectAggregator}(1048576));
 *  ...
 *  p.addLast("handler", new HttpRequestHandler());
 *  </pre>
 * </blockquote>
 */
public class HttpServerKeepAliveHandler implements ChannelHandler {
    private static final String MULTIPART_PREFIX = "multipart";

    private boolean persistentConnection = true;
    // Track pending responses to support client pipelining: https://tools.ietf.org/html/rfc7230#section-6.3.2
    private int pendingResponses;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // read message and track if it was keepAlive
        if (msg instanceof HttpRequest) {
            final HttpRequest request = (HttpRequest) msg;
            if (persistentConnection) {
                pendingResponses += 1;
                persistentConnection = isKeepAlive(request);
            }
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
        // modify message on way out to add headers if needed
        if (msg instanceof HttpResponse) {
            final HttpResponse response = (HttpResponse) msg;
            trackResponse(response);
            // Assume the response writer knows if they can persist or not and sets isKeepAlive on the response
            if (!isKeepAlive(response) || !isSelfDefinedMessageLength(response)) {
                // No longer keep alive as the client can't tell when the message is done unless we close connection
                pendingResponses = 0;
                persistentConnection = false;
            }
            // Server might think it can keep connection alive, but we should fix response header if we know better
            if (!shouldKeepAlive()) {
                setKeepAlive(response, false);
            }
        }
        boolean shouldClose = msg instanceof LastHttpContent && !shouldKeepAlive();
        Future<Void> future = ctx.write(msg);
        if (shouldClose) {
            future.addListener(ctx, ChannelFutureListeners.CLOSE);
        }
        return future;
    }

    private void trackResponse(HttpResponse response) {
        if (!isInformational(response)) {
            pendingResponses -= 1;
        }
    }

    private boolean shouldKeepAlive() {
        return pendingResponses != 0 || persistentConnection;
    }

    /**
     * Keep-alive only works if the client can detect when the message has ended without relying on the connection being
     * closed.
     * <p>
     * <ul>
     *     <li>See <a href="https://tools.ietf.org/html/rfc7230#section-6.3"/></li>
     *     <li>See <a href="https://tools.ietf.org/html/rfc7230#section-3.3.2"/></li>
     *     <li>See <a href="https://tools.ietf.org/html/rfc7230#section-3.3.3"/></li>
     * </ul>
     *
     * @param response The HttpResponse to check
     *
     * @return true if the response has a self defined message length.
     */
    private static boolean isSelfDefinedMessageLength(HttpResponse response) {
        return isContentLengthSet(response) || isTransferEncodingChunked(response) || isMultipart(response) ||
               isInformational(response) || response.status().code() == HttpResponseStatus.NO_CONTENT.code();
    }

    private static boolean isInformational(HttpResponse response) {
        return response.status().codeClass() == HttpStatusClass.INFORMATIONAL;
    }

    private static boolean isMultipart(HttpResponse response) {
        String contentType = response.headers().get(HttpHeaderNames.CONTENT_TYPE);
        return contentType != null &&
               contentType.regionMatches(true, 0, MULTIPART_PREFIX, 0, MULTIPART_PREFIX.length());
    }
}
