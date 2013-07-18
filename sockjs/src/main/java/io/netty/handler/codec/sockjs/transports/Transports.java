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

import static io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.netty.handler.codec.http.HttpHeaders.Names.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaders.Names.SET_COOKIE;
import static io.netty.handler.codec.http.HttpHeaders.Names.ALLOW;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpMethod.GET;
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
import io.netty.handler.codec.sockjs.Config;
import io.netty.util.CharsetUtil;

import java.util.Set;

public final class Transports {

    public static final String CONTENT_TYPE_PLAIN = "text/plain; charset=UTF-8";
    public static final String CONTENT_TYPE_JAVASCRIPT = "application/javascript; charset=UTF-8";
    public static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";
    public static final String CONTENT_TYPE_HTML = "text/html; charset=UTF-8";
    public static final String DEFAULT_COOKIE = "JSESSIONID=dummy;path=/";
    public static final String JSESSIONID = "JSESSIONID";

    public enum Types {
        WEBSOCKET,
        XHR,
        XHR_SEND,
        XHR_STREAMING,
        JSONP,
        JSONP_SEND,
        EVENTSOURCE,
        HTMLFILE;

        public String path() {
            return "/" + name().toLowerCase();
        }
    }

    private Transports() {
    }

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

    public static void writeContent(final FullHttpResponse response, final String content, final String contentType) {
        final ByteBuf buf = Unpooled.copiedBuffer(content, CharsetUtil.UTF_8);
        response.headers().set(CONTENT_LENGTH, buf.readableBytes());
        response.content().writeBytes(buf);
        response.headers().set(CONTENT_TYPE, contentType);
    }

    public static void writeContent(final FullHttpResponse response, final ByteBuf content, final String contentType) {
        response.headers().set(CONTENT_LENGTH, content.readableBytes());
        response.content().writeBytes(content);
        response.headers().set(CONTENT_TYPE, contentType);
    }

    public static void setDefaultHeaders(final HttpResponse response, final Config config) {
        if (config.cookiesNeeded()) {
            response.headers().set(SET_COOKIE, Transports.DEFAULT_COOKIE);
        }
        setNoCacheHeaders(response);
        setCORSHeaders(response);
    }

    public static void setDefaultHeaders(final FullHttpResponse response, final Config config, HttpRequest request) {
        if (config.cookiesNeeded()) {
            response.headers().set(SET_COOKIE, encodeSessionIdCookie(request));
        }
        setNoCacheHeaders(response);
        setCORSHeaders(response);
    }

    public static void setSessionIdCookie(final FullHttpResponse response, final Config config, HttpRequest request) {
        if (config.cookiesNeeded()) {
            response.headers().set(SET_COOKIE, encodeSessionIdCookie(request));
        }
    }

    public static void setNoCacheHeaders(final HttpResponse response) {
        response.headers().set(CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");
    }

    public static void setCORSHeaders(final HttpResponse response) {
        response.headers().set(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        response.headers().set(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    }

    public static ByteBuf wrapWithLN(final ByteBuf buf) {
        return Unpooled.copiedBuffer(buf, Unpooled.copiedBuffer("\n", CharsetUtil.UTF_8));
    }

    public static String escapeCharacters(final char[] value) {
        final StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < value.length; i++) {
            final char ch = value[i];
            if ((ch >= '\u0000' && ch <= '\u001F') ||
                    (ch >= '\uD800' && ch <= '\uDFFF') ||
                    (ch >= '\u200C' && ch <= '\u200F') ||
                    (ch >= '\u2028' && ch <= '\u202F') ||
                    (ch >= '\u2060' && ch <= '\u206F') ||
                    (ch >= '\uFFF0' && ch <= '\uFFFF')) {
                final String ss = Integer.toHexString(ch);
                buffer.append('\\');
                buffer.append('u');
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

    public static ByteBuf escapeJson(final ByteBuf input, final ByteBuf buffer) {
        for (int i = 0; i < input.readableBytes(); i++) {
            final byte ch = input.getByte(i);
            switch(ch) {
                case '"': buffer.writeByte('\\'); buffer.writeByte('\"'); break;
                case '/': buffer.writeByte('\\'); buffer.writeByte('/'); break;
                case '\\': buffer.writeByte('\\'); buffer.writeByte('\\'); break;
                case '\b': buffer.writeByte('\\'); buffer.writeByte('b'); break;
                case '\f': buffer.writeByte('\\'); buffer.writeByte('f'); break;
                case '\n': buffer.writeByte('\\'); buffer.writeByte('n'); break;
                case '\r': buffer.writeByte('\\'); buffer.writeByte('r'); break;
                case '\t': buffer.writeByte('\\'); buffer.writeByte('t'); break;

                default:
                    // Reference: http://www.unicode.org/versions/Unicode5.1.0/
                    if ((ch >= '\u0000' && ch <= '\u001F') ||
                            (ch >= '\uD800' && ch <= '\uDFFF') ||
                            (ch >= '\u200C' && ch <= '\u200F') ||
                            (ch >= '\u2028' && ch <= '\u202F') ||
                            (ch >= '\u2060' && ch <= '\u206F') ||
                            (ch >= '\uFFF0' && ch <= '\uFFFF')) {
                        final String ss = Integer.toHexString(ch);
                        buffer.writeByte('\\');
                        buffer.writeByte('u');
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

    public static FullHttpResponse methodNotAllowedResponse(final HttpVersion version) {
        final FullHttpResponse response = responseWithoutContent(version, METHOD_NOT_ALLOWED);
        response.headers().add(ALLOW, GET);
        return response;
    }

    public static FullHttpResponse badRequestResponse(final HttpVersion version, final String content) {
        return responseWithContent(version, BAD_REQUEST, CONTENT_TYPE_PLAIN, content);
    }

    public static FullHttpResponse internalServerErrorResponse(final HttpVersion version, final String content) {
        return responseWithContent(version, INTERNAL_SERVER_ERROR, CONTENT_TYPE_PLAIN, content);
    }

    public static void respond(final ChannelHandlerContext ctx,
            final HttpVersion version,
            final HttpResponseStatus status,
            final String contentType,
            final String content,
            final ChannelPromise promise) throws Exception {
        final FullHttpResponse response = responseWithContent(version, status, contentType, content);
        writeResponse(ctx, response);
    }

    public static void respond(final ChannelHandlerContext ctx,
            final HttpVersion version,
            final HttpResponseStatus status,
            final String contentType,
            final String content) throws Exception {
        final FullHttpResponse response = responseWithContent(version, status, contentType, content);
        writeResponse(ctx, response);
    }

    public static FullHttpResponse responseWithoutContent(final HttpVersion version, final HttpResponseStatus status) {
        final FullHttpResponse response = new DefaultFullHttpResponse(version, status);
        response.headers().set(CONTENT_LENGTH, 0);
        return response;
    }

    public static FullHttpResponse responseWithContent(final HttpVersion version, final HttpResponseStatus status,
            final String contentType, final String content) {
        final FullHttpResponse response = new DefaultFullHttpResponse(version, status);
        writeContent(response, content, contentType);
        return response;
    }

    public static void writeResponse(final ChannelHandlerContext ctx, final HttpResponse response) {
        if (ctx.channel().isActive() && ctx.channel().isRegistered()) {
            ctx.writeAndFlush(response);
        }
    }

    public static void writeResponse(final ChannelHandlerContext ctx, final ChannelPromise promise,
            final HttpResponse response) {
        if (ctx.channel().isActive() && ctx.channel().isRegistered()) {
            ctx.writeAndFlush(response, promise);
        }
    }
}
