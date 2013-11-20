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

import static io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.netty.handler.codec.http.HttpHeaders.Names.ALLOW;
import static io.netty.handler.codec.http.HttpHeaders.Names.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaders.Names.SET_COOKIE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.ServerCookieEncoder;
import io.netty.handler.codec.sockjs.SockJsConfig;
import io.netty.util.CharsetUtil;

import java.util.Set;

/**
 * Transports contains constants, enums, and utility methods that are
 * common across transport implementations.
 */
public final class Transports {

    public static final String CONTENT_TYPE_PLAIN = "text/plain; charset=UTF-8";
    public static final String CONTENT_TYPE_JAVASCRIPT = "application/javascript; charset=UTF-8";
    public static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";
    public static final String CONTENT_TYPE_HTML = "text/html; charset=UTF-8";
    public static final String DEFAULT_COOKIE = "JSESSIONID=dummy;path=/";
    public static final String JSESSIONID = "JSESSIONID";
    private static final String NO_CACHE_HEADER =  "no-store, no-cache, must-revalidate, max-age=0";

    public enum Type {
        WEBSOCKET,
        XHR,
        XHR_SEND,
        XHR_STREAMING,
        JSONP,
        JSONP_SEND,
        EVENTSOURCE,
        HTMLFILE;

        public String path() {
            return '/' + name().toLowerCase();
        }
    }

    private Transports() {
    }

    /**
     * Encodes the passes in requests JSESSIONID, if one exists, setting it to path of '/'.
     *
     * @param request the {@link HttpRequest} to parse for a JSESSIONID cookie.
     * @return {@code String} the encoded cookie or {@value Transports#DEFAULT_COOKIE} if no JSESSIONID cookie exits.
     */
    public static String encodeSessionIdCookie(final HttpRequest request) {
        final String cookieHeader = request.headers().get(HttpHeaders.Names.COOKIE);
        if (cookieHeader != null) {
            final Set<Cookie> cookies = CookieDecoder.decode(cookieHeader);
            for (Cookie c : cookies) {
                if (c.getName().equals(JSESSIONID)) {
                    c.setPath("/");
                    return ServerCookieEncoder.encode(c);
                }
            }
        }
        return DEFAULT_COOKIE;
    }

    /**
     * Writes the passed in String content to the response and also sets te content-type and content-lenght headaers.
     *
     * @param response the {@link FullHttpResponse} to write the content to.
     * @param content the content to be written.
     * @param contentType the content-type that will be set as the Content-Type Http response header.
     */
    public static void writeContent(final FullHttpResponse response, final String content, final String contentType) {
        final ByteBuf buf = Unpooled.copiedBuffer(content, CharsetUtil.UTF_8);
        response.headers().set(CONTENT_LENGTH, buf.readableBytes());
        response.content().writeBytes(buf);
        response.headers().set(CONTENT_TYPE, contentType);
        buf.release();
    }

    /**
     * Sets the following Http response headers
     * - SET_COOKIE  if {@link SockJsConfig#areCookiesNeeded()} is true
     * - CACHE_CONTROL to {@link Transports#setNoCacheHeaders(HttpResponse)}
     * - CORS Headers to {@link Transports#setCORSHeaders(HttpResponse)}
     *
     * @param response the Http Response.
     * @param config the SockJS configuration.
     */
    public static void setDefaultHeaders(final HttpResponse response, final SockJsConfig config) {
        if (config.areCookiesNeeded()) {
            response.headers().set(SET_COOKIE, DEFAULT_COOKIE);
        }
        setNoCacheHeaders(response);
        setCORSHeaders(response);
    }

    /**
     * Sets the following Http response headers
     * - SET_COOKIE  if {@link SockJsConfig#areCookiesNeeded()} is true, and uses the requests cookie.
     * - CACHE_CONTROL to {@link Transports#setNoCacheHeaders(HttpResponse)}
     * - CORS Headers to {@link Transports#setCORSHeaders(HttpResponse)}
     *
     * @param response the Http Response.
     * @param config the SockJS configuration.
     */
    public static void setDefaultHeaders(final FullHttpResponse response, final SockJsConfig config,
            final HttpRequest request) {
        if (config.areCookiesNeeded()) {
            response.headers().set(SET_COOKIE, encodeSessionIdCookie(request));
        }
        setNoCacheHeaders(response);
        setCORSHeaders(response);
    }

    /**
     * Sets the Http response header SET_COOKIE if {@link SockJsConfig#areCookiesNeeded()} is true.
     *
     * @param response the Http Response.
     * @param config the SockJS configuration.
     * @param request the Http request which will be inspected for the existence of a JSESSIONID cookie.
     */
    public static void setSessionIdCookie(final FullHttpResponse response, final SockJsConfig config,
            final HttpRequest request) {
        if (config.areCookiesNeeded()) {
            response.headers().set(SET_COOKIE, encodeSessionIdCookie(request));
        }
    }

    /**
     * Sets the Http response header CACHE_CONTROL to {@link Transports#NO_CACHE_HEADER}.
     *
     * @param response the Http response for which the CACHE_CONTROL header will be set.
     */
    public static void setNoCacheHeaders(final HttpResponse response) {
        response.headers().set(CACHE_CONTROL, NO_CACHE_HEADER);
    }

    /**
     * Sets the CORS Http response headers ACCESS_CONTROLL_ALLOW_ORIGIN to '*', and ACCESS_CONTROL_ALLOW_CREDENTIALS to
     * 'true".
     *
     * @param response the Http response for which the CORS headers will be set.
     */
    public static void setCORSHeaders(final HttpResponse response) {
        response.headers().set(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        response.headers().set(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    }

    /**
     * Will add an new line character to the passed in ByteBuf.
     *
     * @param buf the {@link ByteBuf} for which an '\n', new line, will be added.
     * @return {@code ByteBuf} a copied byte buffer with a '\n' appended.
     */
    public static ByteBuf wrapWithLN(final ByteBuf buf) {
        return Unpooled.copiedBuffer(buf, Unpooled.copiedBuffer("\n", CharsetUtil.UTF_8));
    }

    /**
     * Escapes unicode characters in the passed in char array to a Java string with
     * Java style escaped charaters.
     *
     * @param value the char[] for which unicode characters should be escaped
     * @return {@code String} Java style escaped unicode characters.
     */
    public static String escapeCharacters(final char[] value) {
        final StringBuilder buffer = new StringBuilder();
        for (char ch : value) {
            if (ch >= '\u0000' && ch <= '\u001F' ||
                    ch >= '\uD800' && ch <= '\uDFFF' ||
                    ch >= '\u200C' && ch <= '\u200F' ||
                    ch >= '\u2028' && ch <= '\u202F' ||
                    ch >= '\u2060' && ch <= '\u206F' ||
                    ch >= '\uFFF0' && ch <= '\uFFFF') {
                final String ss = Integer.toHexString(ch);
                buffer.append('\\').append('u');
                for (int k = 0; k < 4 - ss.length(); k++) {
                    buffer.append('0');
                }
                buffer.append(ss.toLowerCase());
            } else {
                buffer.append(ch);
            }
        }
        return buffer.toString();
    }

    /**
     * Processes the input ByteBuf and escapes the any control characters, quotes, slashes,
     * and unicode characters.
     *
     * @param input the bytes of characters to process.
     * @param buffer the {@link ByteBuf} into which the result of processing will be added.
     * @return {@code ByteBuf} which is the same ByteBuf as passed in as the buffer param. This is done to
     *                         simplify method invocation where possible which might require a return value.
     */
    public static ByteBuf escapeJson(final ByteBuf input, final ByteBuf buffer) {
        final int count = input.readableBytes();
        for (int i = 0; i < count; i++) {
            final byte ch = input.getByte(i);
            switch(ch) {
                case '"': buffer.writeByte('\\').writeByte('\"'); break;
                case '/': buffer.writeByte('\\').writeByte('/'); break;
                case '\\': buffer.writeByte('\\').writeByte('\\'); break;
                case '\b': buffer.writeByte('\\').writeByte('b'); break;
                case '\f': buffer.writeByte('\\').writeByte('f'); break;
                case '\n': buffer.writeByte('\\').writeByte('n'); break;
                case '\r': buffer.writeByte('\\').writeByte('r'); break;
                case '\t': buffer.writeByte('\\').writeByte('t'); break;

                default:
                    // Reference: http://www.unicode.org/versions/Unicode5.1.0/
                    if (ch >= '\u0000' && ch <= '\u001F' ||
                            ch >= '\uD800' && ch <= '\uDFFF' ||
                            ch >= '\u200C' && ch <= '\u200F' ||
                            ch >= '\u2028' && ch <= '\u202F' ||
                            ch >= '\u2060' && ch <= '\u206F' ||
                            ch >= '\uFFF0' && ch <= '\uFFFF') {
                        final String ss = Integer.toHexString(ch);
                        buffer.writeByte('\\').writeByte('u');
                        for (int k = 0; k < 4 - ss.length(); k++) {
                            buffer.writeByte('0');
                        }
                        buffer.writeBytes(ss.toLowerCase().getBytes());
                    } else {
                        buffer.writeByte(ch);
                    }
            }
        }
        return buffer;
    }

    /**
     * Creates a {@code FullHttpResponse} with the {@code METHOD_NOT_ALLOWED} status.
     *
     * @param version the HttpVersion to be used.
     * @return {@link FullHttpResponse} with the {@link HttpResponseStatus#METHOD_NOT_ALLOWED}.
     */
    public static FullHttpResponse methodNotAllowedResponse(final HttpVersion version) {
        final FullHttpResponse response = responseWithoutContent(version, METHOD_NOT_ALLOWED);
        response.headers().add(ALLOW, GET);
        return response;
    }

    /**
     * Creates a {@code FullHttpResponse} with the {@code BAD_REQUEST} status and a body.
     *
     * @param version the HttpVersion to be used.
     * @param content the content that will become the response body.
     * @return {@link FullHttpResponse} with the {@link HttpResponseStatus#BAD_REQUEST}.
     */
    public static FullHttpResponse badRequestResponse(final HttpVersion version, final String content) {
        return responseWithContent(version, BAD_REQUEST, CONTENT_TYPE_PLAIN, content);
    }

    /**
     * Creates a {@code FullHttpResponse} with the {@code INTERNAL_SERVER_ERROR} status and a body.
     *
     * @param version the HttpVersion to be used.
     * @param content the content that will become the response body.
     * @return {@link FullHttpResponse} with the {@link HttpResponseStatus#INTERNAL_SERVER_ERROR}.
     */
    public static FullHttpResponse internalServerErrorResponse(final HttpVersion version, final String content) {
        return responseWithContent(version, INTERNAL_SERVER_ERROR, CONTENT_TYPE_PLAIN, content);
    }

    /**
     * Sends a HttpResponse using the ChannelHandlerContext passed in.
     *
     * @param ctx the {@link ChannelHandlerContext} to use.
     * @param version the {@link HttpVersion} that the response should have.
     * @param status the status of the HTTP response
     * @param contentType the value for the 'Content-Type' HTTP response header.
     * @param content the content that will become the body of the HTTP response.
     * @param promise the {@link ChannelPromise}
     * @throws Exception if an error occurs while trying to send the response.
     */
    public static void respond(final ChannelHandlerContext ctx,
            final HttpVersion version,
            final HttpResponseStatus status,
            final String contentType,
            final String content,
            final ChannelPromise promise) throws Exception {
        final FullHttpResponse response = responseWithContent(version, status, contentType, content);
        writeResponse(ctx, response);
    }

    /**
     * Sends a HttpResponse using the ChannelHandlerContext passed in.
     *
     * @param ctx the {@link ChannelHandlerContext} to use.
     * @param version the {@link HttpVersion} that the response should have.
     * @param status the status of the HTTP response
     * @param contentType the value for the 'Content-Type' HTTP response header.
     * @param content the content that will become the body of the HTTP response.
     * @throws Exception if an error occurs while trying to send the response.
     */
    public static void respond(final ChannelHandlerContext ctx,
            final HttpVersion version,
            final HttpResponseStatus status,
            final String contentType,
            final String content) throws Exception {
        final FullHttpResponse response = responseWithContent(version, status, contentType, content);
        writeResponse(ctx, response);
    }

    /**
     * Creates FullHttpResponse without a response body.
     *
     * @param version the {@link HttpVersion} that the response should have.
     * @param status the status of the HTTP response
     */
    public static FullHttpResponse responseWithoutContent(final HttpVersion version, final HttpResponseStatus status) {
        final FullHttpResponse response = new DefaultFullHttpResponse(version, status);
        response.headers().set(CONTENT_LENGTH, 0);
        return response;
    }

    /**
     * Creates FullHttpResponse with a response body.
     *
     * @param version the {@link HttpVersion} that the response should have.
     * @param status the status of the HTTP response
     * @param contentType the value for the 'Content-Type' HTTP response header.
     * @param content the content that will become the body of the HTTP response.
     */
    public static FullHttpResponse responseWithContent(final HttpVersion version, final HttpResponseStatus status,
            final String contentType, final String content) {
        final FullHttpResponse response = new DefaultFullHttpResponse(version, status);
        writeContent(response, content, contentType);
        return response;
    }

    /**
     * Writes the passed in respone to the {@link ChannelHandlerContext} if it is active.
     *
     * @param ctx the {@link ChannelHandlerContext} to write the response to.
     * @param response the {@link HttpResponseStatus} to be written.
     */
    public static void writeResponse(final ChannelHandlerContext ctx, final HttpResponse response) {
        if (ctx.channel().isActive() && ctx.channel().isRegistered()) {
            ctx.writeAndFlush(response);
        }
    }

    /**
     * Writes the passed in respone to the {@link ChannelHandlerContext} if it is active.
     *
     * @param ctx the {@link ChannelHandlerContext} to write the response to.
     * @param promise the {@link ChannelPromise}
     * @param response the {@link HttpResponseStatus} to be written.
     */
    public static void writeResponse(final ChannelHandlerContext ctx, final ChannelPromise promise,
            final HttpResponse response) {
        if (ctx.channel().isActive() && ctx.channel().isRegistered()) {
            ctx.writeAndFlush(response, promise);
        }
    }
}
