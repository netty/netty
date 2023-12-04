/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty5.handler.codec.http.cors;

import io.netty5.channel.ChannelFutureListeners;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.http.DefaultFullHttpResponse;
import io.netty5.handler.codec.http.HttpHeaderNames;
import io.netty5.handler.codec.http.HttpHeaderValues;
import io.netty5.handler.codec.http.HttpRequest;
import io.netty5.handler.codec.http.HttpResponse;
import io.netty5.handler.codec.http.HttpUtil;
import io.netty5.handler.codec.http.headers.DefaultHttpHeadersFactory;
import io.netty5.handler.codec.http.headers.HttpHeaders;
import io.netty5.util.AsciiString;
import io.netty5.util.concurrent.Future;
import io.netty5.util.internal.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.StringJoiner;

import static io.netty5.handler.codec.http.HttpMethod.OPTIONS;
import static io.netty5.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty5.handler.codec.http.HttpResponseStatus.OK;
import static io.netty5.util.internal.ObjectUtil.checkNonEmpty;
import static java.util.Objects.requireNonNull;

/**
 * Handles <a href="https://www.w3.org/TR/cors/">Cross Origin Resource Sharing</a> (CORS) requests.
 * <p>
 * This handler can be configured using one or more {@link CorsConfig}, please
 * refer to this class for details about the configuration options available.
 */
public class CorsHandler implements ChannelHandler {

    private static final Logger logger = LoggerFactory.getLogger(CorsHandler.class);
    private static final String ANY_ORIGIN = "*";
    private static final String NULL_ORIGIN = "null";
    private CorsConfig config;

    private HttpRequest request;
    private final List<CorsConfig> configList;
    private final boolean isShortCircuit;

    /**
     * Creates a new instance with a single {@link CorsConfig}.
     */
    public CorsHandler(final CorsConfig config) {
        this(Collections.singletonList(requireNonNull(config, "config")), config.isShortCircuit());
    }

    /**
     * Creates a new instance with the specified config list. If more than one
     * config matches a certain origin, the first in the List will be used.
     *
     * @param configList     List of {@link CorsConfig}
     * @param isShortCircuit Same as {@link CorsConfig#isShortCircuit} but applicable to all supplied configs.
     */
    public CorsHandler(final List<CorsConfig> configList, boolean isShortCircuit) {
        checkNonEmpty(configList, "configList");
        this.configList = configList;
        this.isShortCircuit = isShortCircuit;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            request = (HttpRequest) msg;
            final CharSequence origin = request.headers().get(HttpHeaderNames.ORIGIN);
            config = getForOrigin(origin);
            if (isPreflightRequest(request)) {
                handlePreflight(ctx, request);
                return;
            }
            if (isShortCircuit && !(origin == null || config != null)) {
                forbidden(ctx, request);
                return;
            }
        }
        ctx.fireChannelRead(msg);
    }

    private void handlePreflight(final ChannelHandlerContext ctx, final HttpRequest request) throws Exception {
        final HttpResponse response = new DefaultFullHttpResponse(request.protocolVersion(), OK,
                ctx.bufferAllocator().allocate(0),
                DefaultHttpHeadersFactory.headersFactory(),
                DefaultHttpHeadersFactory.trailersFactory());
        if (setOrigin(response)) {
            setAllowMethods(response);
            setAllowHeaders(response);
            setAllowCredentials(response);
            setMaxAge(response);
            setPreflightHeaders(response);
            setAllowPrivateNetwork(response);
        }
        if (!response.headers().contains(HttpHeaderNames.CONTENT_LENGTH)) {
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, HttpHeaderValues.ZERO);
        }
        if (request instanceof AutoCloseable) {
            ((AutoCloseable) request).close();
        }
        respond(ctx, request, response);
    }

    /**
     * This is a non CORS specification feature which enables the setting of preflight
     * response headers that might be required by intermediaries.
     *
     * @param response the HttpResponse to which the preflight response headers should be added.
     */
    private void setPreflightHeaders(final HttpResponse response) {
        HttpHeaders headers = response.headers();
        HttpHeaders preflight = config.preflightResponseHeaders();
        for (CharSequence name : preflight.names()) {
            headers.add(name, toCsv(preflight.values(name)));
        }
    }

    private CorsConfig getForOrigin(CharSequence requestOrigin) {
        for (CorsConfig corsConfig : configList) {
            if (corsConfig.isAnyOriginSupported()) {
                return corsConfig;
            }
            if (corsConfig.origins().contains(requestOrigin)) {
                return corsConfig;
            }
            if (corsConfig.isNullOriginAllowed() || AsciiString.contentEquals(NULL_ORIGIN, requestOrigin)) {
                return corsConfig;
            }
        }
        return null;
    }

    private boolean setOrigin(final HttpResponse response) {
        final CharSequence origin = request.headers().get(HttpHeaderNames.ORIGIN);
        if (origin != null && config != null) {
            if (AsciiString.contentEquals(NULL_ORIGIN, origin) && config.isNullOriginAllowed()) {
                setNullOrigin(response);
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
            logger.debug("Request origin [{}]] was not among the configured origins [{}]", origin, config.origins());
        }
        return false;
    }

    private void echoRequestOrigin(final HttpResponse response) {
        setOrigin(response, request.headers().get(HttpHeaderNames.ORIGIN));
    }

    private static void setVaryHeader(final HttpResponse response) {
        response.headers().set(HttpHeaderNames.VARY, HttpHeaderNames.ORIGIN);
    }

    private static void setAnyOrigin(final HttpResponse response) {
        setOrigin(response, ANY_ORIGIN);
    }

    private static void setNullOrigin(final HttpResponse response) {
        setOrigin(response, NULL_ORIGIN);
    }

    private static void setOrigin(final HttpResponse response, final CharSequence origin) {
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
    }

    private void setAllowCredentials(final HttpResponse response) {
        if (config.isCredentialsAllowed()
                && !response.headers().contains(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, ANY_ORIGIN)) {
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }
    }

    private static boolean isPreflightRequest(final HttpRequest request) {
        final HttpHeaders headers = request.headers();
        return OPTIONS.equals(request.method()) &&
                headers.contains(HttpHeaderNames.ORIGIN) &&
                headers.contains(HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD);
    }

    private void setExposeHeaders(final HttpResponse response) {
        if (!config.exposedHeaders().isEmpty()) {
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS, toCsv(config.exposedHeaders()));
        }
    }

    private void setAllowMethods(final HttpResponse response) {
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, toCsv(config.allowedRequestMethods()));
    }

    private void setAllowHeaders(final HttpResponse response) {
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, toCsv(config.allowedRequestHeaders()));
    }

    private void setMaxAge(final HttpResponse response) {
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE, String.valueOf(config.maxAge()));
    }

    private void setAllowPrivateNetwork(final HttpResponse response) {
        if (request.headers().contains(HttpHeaderNames.ACCESS_CONTROL_REQUEST_PRIVATE_NETWORK)) {
            if (config.isPrivateNetworkAllowed()) {
                response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK, "true");
            } else {
                response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK, "false");
            }
        }
    }

    private static CharSequence toCsv(Iterable<?> headerValues) {
        Iterator<?> itr = headerValues.iterator();
        if (!itr.hasNext()) {
            return AsciiString.EMPTY_STRING;
        }
        StringJoiner joiner = new StringJoiner(",");
        do {
            joiner.add(StringUtil.escapeCsv(String.valueOf(itr.next()), true));
        } while (itr.hasNext());
        return joiner.toString();
    }

    @Override
    public Future<Void> write(final ChannelHandlerContext ctx, final Object msg) {
        if (config != null && config.isCorsSupportEnabled() && msg instanceof HttpResponse) {
            final HttpResponse response = (HttpResponse) msg;
            if (setOrigin(response)) {
                setAllowCredentials(response);
                setExposeHeaders(response);
            }
        }
        return ctx.write(msg);
    }

    private static void forbidden(final ChannelHandlerContext ctx, final HttpRequest request) throws Exception {
        HttpResponse response = new DefaultFullHttpResponse(
                request.protocolVersion(), FORBIDDEN, ctx.bufferAllocator().allocate(0));
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, HttpHeaderValues.ZERO);
        if (request instanceof AutoCloseable) {
            ((AutoCloseable) request).close();
        }
        respond(ctx, request, response);
    }

    private static void respond(
            final ChannelHandlerContext ctx,
            final HttpRequest request,
            final HttpResponse response) {

        final boolean keepAlive = HttpUtil.isKeepAlive(request);

        HttpUtil.setKeepAlive(response, keepAlive);

        Future<Void> future = ctx.writeAndFlush(response);
        if (!keepAlive) {
            future.addListener(ctx, ChannelFutureListeners.CLOSE);
        }
    }
}
