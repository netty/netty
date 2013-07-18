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
package io.netty.handler.codec.sockjs.handlers;

import static io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_ALLOW_HEADERS;
import static io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_ALLOW_METHODS;
import static io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_MAX_AGE;
import static io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_REQUEST_HEADERS;
import static io.netty.handler.codec.http.HttpHeaders.Names.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaders.Names.EXPIRES;
import static io.netty.handler.codec.http.HttpHeaders.Names.ORIGIN;
import static io.netty.handler.codec.http.HttpHeaders.Names.SET_COOKIE;
import static io.netty.handler.codec.http.HttpResponseStatus.NO_CONTENT;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.sockjs.transports.Transports;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;

/**
 * Handles CORS preflight requests for the sockjs-protocol.
 *
 */
public class CorsInboundHandler extends SimpleChannelInboundHandler<HttpRequest> {

    static final AttributeKey<CorsMetadata> CORS = new AttributeKey<CorsMetadata>("cors.metadata");

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final HttpRequest request) throws Exception {
        final CorsMetadata metadata = extractCorsMetadata(request);
        if (isPreflightRequest(request)) {
            handlePreflight(ctx, metadata, request);
        } else {
            ctx.channel().attr(CORS).set(metadata);
            ctx.fireChannelRead(ReferenceCountUtil.retain(request));
        }
    }

    private void handlePreflight(final ChannelHandlerContext ctx, final CorsMetadata md, final HttpRequest request) {
        final HttpResponse response = new DefaultHttpResponse(request.getProtocolVersion(), NO_CONTENT);
        final HttpHeaders headers = response.headers();
        headers.set(CONTENT_TYPE, Transports.CONTENT_TYPE_PLAIN);
        headers.set(CACHE_CONTROL, "max-age=31536000, public");
        headers.set(ACCESS_CONTROL_ALLOW_ORIGIN, md.origin());
        headers.set(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        headers.set(ACCESS_CONTROL_MAX_AGE, "31536000");
        if (isPollingTransport(request.getUri())) {
            headers.set(ACCESS_CONTROL_ALLOW_METHODS, "OPTIONS, POST");
        } else {
            headers.set(ACCESS_CONTROL_ALLOW_METHODS, "OPTIONS, GET");
        }
        headers.set(ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type");
        headers.set(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        headers.set(EXPIRES, "dummy");
        headers.set(SET_COOKIE, Transports.DEFAULT_COOKIE);
        ctx.writeAndFlush(response);
    }

    private boolean isPollingTransport(final String uri) {
        return uri.contains(Transports.Types.XHR.path());
    }

    private boolean isPreflightRequest(final HttpRequest request) {
        return request.getMethod().equals(HttpMethod.OPTIONS);
    }

    private CorsMetadata extractCorsMetadata(final HttpRequest request) {
        final String origin = request.headers().get(ORIGIN);
        final String headers = request.headers().get(ACCESS_CONTROL_REQUEST_HEADERS);
        return new CorsMetadata(origin, headers);
    }

}
