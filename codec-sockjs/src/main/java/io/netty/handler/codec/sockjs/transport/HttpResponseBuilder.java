/*
 * Copyright 2014 The Netty Project
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
import io.netty.handler.codec.AsciiString;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderUtil;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.ServerCookieEncoder;
import io.netty.handler.codec.sockjs.SockJsConfig;

import java.util.Set;

import static io.netty.buffer.Unpooled.*;
import static io.netty.handler.codec.http.HttpHeaders.Names.ALLOW;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaders.Names.COOKIE;
import static io.netty.util.CharsetUtil.*;

/**
 * A builder to help configuring {@link HttpResponse} and {@link FullHttpResponse}
 * instances in the SockJS implementation.
 */
public final class HttpResponseBuilder {

    public static final String CONTENT_TYPE_PLAIN = "text/plain; charset=UTF-8";
    public static final String CONTENT_TYPE_JAVASCRIPT = "application/javascript; charset=UTF-8";
    public static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";
    public static final String CONTENT_TYPE_HTML = "text/html; charset=UTF-8";
    public static final String CONTENT_TYPE_JSON = "application/json; charset=UTF-8";
    public static final String DEFAULT_COOKIE = "JSESSIONID=dummy;path=/";
    public static final String JSESSIONID = "JSESSIONID";
    public static final String NO_CACHE_HEADER =  "no-store, no-cache, must-revalidate, max-age=0";
    private static final ByteBuf NL = unreleasableBuffer(copiedBuffer("\n", UTF_8));

    private final HttpRequest request;
    private HttpResponseStatus status;
    private String content;
    private ByteBuf byteBuf;
    private String contentType;
    private boolean chunked;
    private final HttpHeaders headers = new DefaultHttpHeaders();

    private HttpResponseBuilder(final HttpRequest request) {
        this.request = request;
    }

    /**
     * Configures the HTTP response status as {@link HttpResponseStatus#NOT_MODIFIED}.
     *
     * @return {@link HttpResponseBuilder} to support method chaining.
     */
    public HttpResponseBuilder notModified() {
        status = HttpResponseStatus.NOT_MODIFIED;
        return this;
    }

    /**
     * Configures the HTTP response status as {@link HttpResponseStatus#OK}.
     *
     * @return {@link HttpResponseBuilder} to support method chaining.
     */
    public HttpResponseBuilder ok() {
        status = HttpResponseStatus.OK;
        return this;
    }

    /**
     * Configures the HTTP response status as {@link HttpResponseStatus#NOT_FOUND}.
     *
     * @return {@link HttpResponseBuilder} to support method chaining.
     */
    public HttpResponseBuilder notFound() {
        status = HttpResponseStatus.NOT_FOUND;
        return this;
    }

    /**
     * Configures the HTTP response status as {@link HttpResponseStatus#INTERNAL_SERVER_ERROR}.
     *
     * @return {@link HttpResponseBuilder} to support method chaining.
     */
    public HttpResponseBuilder internalServerError() {
        status = HttpResponseStatus.INTERNAL_SERVER_ERROR;
        return this;
    }

    /**
     * Configures the HTTP response status as {@link HttpResponseStatus#METHOD_NOT_ALLOWED}.
     *
     * @return {@link HttpResponseBuilder} to support method chaining.
     */
    public HttpResponseBuilder methodNotAllowed() {
        status = HttpResponseStatus.METHOD_NOT_ALLOWED;
        return this;
    }

    /**
     * Configures the HTTP response status as {@link HttpResponseStatus#BAD_REQUEST}.
     *
     * @return {@link HttpResponseBuilder} to support method chaining.
     */
    public HttpResponseBuilder badRequest() {
        status = HttpResponseStatus.BAD_REQUEST;
        return this;
    }

    /**
     * Configures the HTTP response status.
     *
     * @param status the {@link HttpResponseStatus} to be set.
     * @return {@link HttpResponseBuilder} to support method chaining.
     */
    public HttpResponseBuilder status(final HttpResponseStatus status) {
        this.status = status;
        return this;
    }

    /**
     * Configures the content for this HTTP response.
     *
     * @param content the content for this response.
     * @return {@link HttpResponseBuilder} to support method chaining.
     */
    public HttpResponseBuilder content(final String content) {
        this.content = content;
        return this;
    }

    /**
     * Configures the content for this HTTP response.
     *
     * @param content the {@link ByteBuf} content for this response.
     * @return {@link HttpResponseBuilder} to support method chaining.
     */
    public HttpResponseBuilder content(final ByteBuf content) {
        byteBuf = content;
        return this;
    }

    /**
     * Configures the content and wraps it with a new line character.
     * Note that this method will release the passed in content.
     *
     * @param content the {@link ByteBuf} content what will be wrapped for this response.
     * @return {@link HttpResponseBuilder} to support method chaining.
     */
    public HttpResponseBuilder contentWrappedWithNL(final ByteBuf content) {
        byteBuf = wrapWithLN(content);
        content.release();
        return this;
    }

    /**
     * Configures the content type for this HTTP response
     *
     * @param contentType the content type.
     * @return {@link HttpResponseBuilder} to support method chaining.
     */
    public HttpResponseBuilder contentType(final String contentType) {
        this.contentType = contentType;
        return this;
    }

    /**
     * Configures that this HTTP response should be chunked, which sets the
     * HTTP transfer encoding header using {@link HttpHeaderUtil#setTransferEncodingChunked(HttpMessage, boolean)}.
     * <p>
     * This will only be set if the HTTP version supports it (which is determined by the version of the
     * HTTP request).
     *
     * @return {@link HttpResponseBuilder} to support method chaining.
     */
    public HttpResponseBuilder chunked() {
        chunked = true;
        return this;
    }

    /**
     * Adds a header to this HTTP response.
     *
     * @param name the name of the HTTP header.
     * @param value the value of hte HTTP header.
     * @return {@link HttpResponseBuilder} to support method chaining.
     */
    public HttpResponseBuilder header(final CharSequence name, final CharSequence value) {
        headers.add(name, value);
        return this;
    }

    /**
     * Adds a header to this HTTP response.
     *
     * @param name the name of the HTTP header.
     * @param value the value of hte HTTP header.
     * @return {@link HttpResponseBuilder} to support method chaining.
     */
    public HttpResponseBuilder header(final CharSequence name, final int value) {
        headers.addInt(name, value);
        return this;
    }

    /**
     * Adds a ALLOW header to this HTTP response.
     *
     * @param method the  HTTP Method.
     * @return {@link HttpResponseBuilder} to support method chaining.
     */
    public HttpResponseBuilder allow(final HttpMethod method) {
        headers.add(ALLOW, method.name());
        return this;
    }

    /**
     * Adds a header to this HTTP response.
     *
     * @param name the name of the HTTP header.
     * @param value the value of hte HTTP header.
     * @return {@link HttpResponseBuilder} to support method chaining.
     */
    public HttpResponseBuilder header(final AsciiString name, AsciiString value) {
        headers.add(name.toString(), value.toString());
        return this;
    }

    /**
     * Adds a header to this HTTP response.
     *
     * @param name the name of the HTTP header.
     * @param values the values of hte HTTP header.
     * @return {@link HttpResponseBuilder} to support method chaining.
     */
    public HttpResponseBuilder header(final CharSequence name, final Iterable<? extends CharSequence> values) {
        headers.add(name, values);
        return this;
    }

    /**
     * Sets the HTTP header {@link HttpHeaders.Names#SET_COOKIE} if {@link SockJsConfig#areCookiesNeeded()}
     * is true.
     *
     * @param config the {@link SockJsConfig}
     * @return {@link HttpResponseBuilder} to support method chaining.
     */
    public HttpResponseBuilder setCookie(final SockJsConfig config) {
        if (config.areCookiesNeeded()) {
            header(HttpHeaders.Names.SET_COOKIE, encodeSessionIdCookie(request));
        }
        return this;
    }

    /**
     * Builds a {@link HttpResponse} response using the configuration options
     * previsouly set.
     *
     * @return {@link HttpResponse} the configured HTTP response.
     */
    public HttpResponse buildResponse() {
        final HttpResponse response = new DefaultHttpResponse(request.protocolVersion(), status);
        addAllHeaders(response);
        return response;
    }

    /**
     * Builds a {@link FullHttpResponse} response using the passed-in {@link ByteBufAllocator} when
     * creating the response body.
     *
     * @return {@link FullHttpResponse} the configured HTTP response.
     */
    public FullHttpResponse buildFullResponse() {
        if (byteBuf == null && content == null) {
            throw new IllegalArgumentException("Content must be supplied to build a FullHttpResponse");
        }
        final ByteBuf buf = byteBuf == null ? copiedBuffer(content, UTF_8) : byteBuf;
        final DefaultFullHttpResponse response = new DefaultFullHttpResponse(request.protocolVersion(), status, buf);
        header(HttpHeaders.Names.CONTENT_LENGTH, String.valueOf(buf.readableBytes()));
        addAllHeaders(response);
        return response;
    }

    /**
     * Creates a builder for the passed in {@link HttpRequest}.
     *
     * @param request the {@link HttpRequest} for which a response should be built.
     * @return {@link HttpResponseBuilder} the builder instance to be further configured.
     */
    public static HttpResponseBuilder responseFor(final HttpRequest request) {
        return new HttpResponseBuilder(request);
    }

    private void addAllHeaders(final HttpResponse response) {
        if (contentType != null) {
            response.headers().set(CONTENT_TYPE, contentType);
        }
        if (chunked) {
            if (response.protocolVersion() == HttpVersion.HTTP_1_1) {
                HttpHeaderUtil.setTransferEncodingChunked(response, true);
            }
        }
        response.headers().add(headers);
    }

    private static String encodeSessionIdCookie(final HttpRequest request) {
        final String cookieHeader = request.headers().getAndConvert(COOKIE);
        if (cookieHeader != null) {
            final Set<Cookie> cookies = CookieDecoder.decode(cookieHeader);
            for (Cookie c : cookies) {
                if ("JSESSIONID".equals(c.name())) {
                    c.setPath("/");
                    return ServerCookieEncoder.encode(c);
                }
            }
        }
        return "JSESSIONID=dummy;path=/";
    }

    /**
     * Will add an new line character to the passed in ByteBuf.
     *
     * @param buf the {@link ByteBuf} for which an '\n', new line, will be added.
     * @return {@code ByteBuf} a copied byte buffer with a '\n' appended.
     */
    public static ByteBuf wrapWithLN(final ByteBuf buf) {
        return copiedBuffer(buf, NL.duplicate());
    }

}
