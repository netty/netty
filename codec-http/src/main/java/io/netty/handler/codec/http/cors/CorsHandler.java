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
package io.netty.handler.codec.http.cors;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpMethod.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.util.ReferenceCountUtil.release;

/**
 * Handles <a href="http://www.w3.org/TR/cors/">Cross Origin Resource Sharing</a> (CORS) requests.
 * <p>
 * This handler can be configured using a {@link CorsConfig}, please
 * refer to this class for details about the configuration options available.
 */
public class CorsHandler extends ChannelDuplexHandler {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(CorsHandler.class);
    private static final String ANY_ORIGIN = "*";
    private final CorsConfig config;

    private HttpRequest request;

    public CorsHandler(final CorsConfig config) {
        this.config = config;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (config.isCorsSupportEnabled() && msg instanceof HttpRequest) {
            request = (HttpRequest) msg;
            if (isPreflightRequest(request)) {
                handlePreflight(ctx, request);
                return;
            }
            if (config.isShortCurcuit() && !validateOrigin()) {
                forbidden(ctx, request);
                return;
            }
        }
        ctx.fireChannelRead(msg);
    }

    private void handlePreflight(final ChannelHandlerContext ctx, final HttpRequest request) {
        final HttpResponse response = new DefaultFullHttpResponse(request.getProtocolVersion(), OK);
        if (setOrigin(response)) {
            setAllowMethods(response);
            setAllowHeaders(response);
            setAllowCredentials(response);
            setMaxAge(response);
            setPreflightHeaders(response);
        }
        release(request);
        respond(ctx, request, response);
    }

    /**
     * This is a non CORS specification feature which enables the setting of preflight
     * response headers that might be required by intermediaries.
     *
     * @param response the HttpResponse to which the preflight response headers should be added.
     */
    private void setPreflightHeaders(final HttpResponse response) {
        response.headers().add(config.preflightResponseHeaders());
    }

    private boolean setOrigin(final HttpResponse response) {
        final String origin = request.headers().get(ORIGIN);
        if (origin != null) {
            if ("null".equals(origin) && config.isNullOriginAllowed()) {
                setAnyOrigin(response);
                return true;
            }
            if (config.isAnyOriginSupported()) {
                if (config.isCredentialsAllowed()) {
                    echoRequestOrigin(response);
                    setVaryHeader(response);
                } else {
                    setAnyOrigin(response);
                }
                return true;
            }
            if (config.origins().contains(origin)) {
                setOrigin(response, origin);
                setVaryHeader(response);
                return true;
            }
            logger.debug("Request origin [" + origin + "] was not among the configured origins " + config.origins());
        }
        return false;
    }

    private boolean validateOrigin() {
        if (config.isAnyOriginSupported()) {
            return true;
        }

        final String origin = request.headers().get(ORIGIN);
        if (origin == null) {
            // Not a CORS request so we cannot validate it. It may be a non CORS request.
            return true;
        }

        if ("null".equals(origin) && config.isNullOriginAllowed()) {
            return true;
        }

        return config.origins().contains(origin);
    }

    private void echoRequestOrigin(final HttpResponse response) {
        setOrigin(response, request.headers().get(ORIGIN));
    }

    private static void setVaryHeader(final HttpResponse response) {
        response.headers().set(VARY, ORIGIN);
    }

    private static void setAnyOrigin(final HttpResponse response) {
        setOrigin(response, ANY_ORIGIN);
    }

    private static void setOrigin(final HttpResponse response, final String origin) {
        response.headers().set(ACCESS_CONTROL_ALLOW_ORIGIN, origin);
    }

    private void setAllowCredentials(final HttpResponse response) {
        if (config.isCredentialsAllowed()
                && !response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN).equals(ANY_ORIGIN)) {
            response.headers().set(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }
    }

    private static boolean isPreflightRequest(final HttpRequest request) {
        final HttpHeaders headers = request.headers();
        return request.getMethod().equals(OPTIONS) &&
                headers.contains(ORIGIN) &&
                headers.contains(ACCESS_CONTROL_REQUEST_METHOD);
    }

    private void setExposeHeaders(final HttpResponse response) {
        if (!config.exposedHeaders().isEmpty()) {
            response.headers().set(ACCESS_CONTROL_EXPOSE_HEADERS, config.exposedHeaders());
        }
    }

    private void setAllowMethods(final HttpResponse response) {
        response.headers().set(ACCESS_CONTROL_ALLOW_METHODS, config.allowedRequestMethods());
    }

    private void setAllowHeaders(final HttpResponse response) {
        response.headers().set(ACCESS_CONTROL_ALLOW_HEADERS, config.allowedRequestHeaders());
    }

    private void setMaxAge(final HttpResponse response) {
        response.headers().set(ACCESS_CONTROL_MAX_AGE, config.maxAge());
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
            throws Exception {
        if (config.isCorsSupportEnabled() && msg instanceof HttpResponse) {
            final HttpResponse response = (HttpResponse) msg;
            if (setOrigin(response)) {
                setAllowCredentials(response);
                setExposeHeaders(response);
            }
        }
        ctx.writeAndFlush(msg, promise);
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {
        logger.error("Caught error in CorsHandler", cause);
        ctx.fireExceptionCaught(cause);
    }

    private static void forbidden(final ChannelHandlerContext ctx, final HttpRequest request) {
        respond(ctx, request, new DefaultFullHttpResponse(request.getProtocolVersion(), FORBIDDEN));
        release(request);
    }

    private static void respond(
            final ChannelHandlerContext ctx,
            final HttpRequest request,
            final HttpResponse response) {

        final boolean keepAlive = HttpHeaders.isKeepAlive(request);

        HttpHeaders.setKeepAlive(response, keepAlive);

        final ChannelFuture future = ctx.writeAndFlush(response);
        if (!keepAlive) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }
}

