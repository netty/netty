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
package io.netty.handler.codec.sockjs.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.AsciiString;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;

import static io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_REQUEST_HEADERS;
import static io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_REQUEST_METHOD;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaders.Names.COOKIE;
import static io.netty.handler.codec.http.HttpHeaders.Names.HOST;
import static io.netty.handler.codec.http.HttpHeaders.Names.ORIGIN;
import static io.netty.handler.codec.http.HttpHeaders.Names.SEC_WEBSOCKET_KEY;
import static io.netty.handler.codec.http.HttpHeaders.Names.SEC_WEBSOCKET_KEY1;
import static io.netty.handler.codec.http.HttpHeaders.Names.SEC_WEBSOCKET_KEY2;
import static io.netty.handler.codec.http.HttpHeaders.Names.SEC_WEBSOCKET_ORIGIN;
import static io.netty.handler.codec.http.HttpHeaders.Names.SEC_WEBSOCKET_VERSION;
import static io.netty.handler.codec.http.HttpHeaders.Names.UPGRADE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.OPTIONS;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.netty.util.CharsetUtil.US_ASCII;
import static io.netty.util.CharsetUtil.UTF_8;
import static io.netty.util.ReferenceCountUtil.release;

public final class HttpRequestBuilder {

    private final FullHttpRequest request;

    private HttpRequestBuilder(final FullHttpRequest request) {
        this.request = request;
    }

    public static HttpRequestBuilder getRequest(final String path) {
        return new HttpRequestBuilder(new DefaultFullHttpRequest(HTTP_1_1, GET, path));
    }

    public static HttpRequestBuilder getRequest(final String path, final HttpVersion version) {
        return new HttpRequestBuilder(new DefaultFullHttpRequest(version, GET, path));
    }

    public static HttpRequestBuilder postRequest(final String path) {
        return new HttpRequestBuilder(new DefaultFullHttpRequest(HTTP_1_1, POST, path));
    }

    public static HttpRequestBuilder postRequest(final String path, final HttpVersion version) {
        return new HttpRequestBuilder(new DefaultFullHttpRequest(version, POST, path));
    }

    public static HttpRequestBuilder wsUpgradeRequest(final String url, final WebSocketVersion version) {
        final FullHttpRequest req = new DefaultFullHttpRequest(HTTP_1_1, GET, url);
        req.headers().set(HOST, "server.test.com");
        req.headers().set(UPGRADE, HttpHeaders.Values.WEBSOCKET.toString());
        req.headers().set(CONNECTION, "Upgrade");

        if (version == WebSocketVersion.V00) {
            req.headers().set(CONNECTION, "Upgrade");
            req.headers().set(SEC_WEBSOCKET_KEY1, "4 @1  46546xW%0l 1 5");
            req.headers().set(SEC_WEBSOCKET_KEY2, "12998 5 Y3 1  .P00");
            req.headers().set(ORIGIN, "http://example.com");
            final ByteBuf byteBuf = Unpooled.copiedBuffer("^n:ds[4U", US_ASCII);
            req.content().writeBytes(byteBuf);
            release(byteBuf);
        } else {
            req.headers().set(SEC_WEBSOCKET_KEY, "dGhlIHNhbXBsZSBub25jZQ==");
            req.headers().set(SEC_WEBSOCKET_ORIGIN, "http://test.com");
            req.headers().set(SEC_WEBSOCKET_VERSION, version.toHttpHeaderValue());
        }
        return new HttpRequestBuilder(req);
    }

    public static HttpRequestBuilder optionsRequest(final String path) {
        return new HttpRequestBuilder(new DefaultFullHttpRequest(HTTP_1_1, OPTIONS, path));
    }

    public HttpRequestBuilder header(final String name, final String value) {
        request.headers().set(name, value);
        return this;
    }

    public HttpRequestBuilder header(final AsciiString name, final AsciiString value) {
        request.headers().set(name, value);
        return this;
    }

    public HttpRequestBuilder header(final AsciiString name, final String value) {
        request.headers().set(name, value);
        return this;
    }

    public HttpRequestBuilder defaultCors() {
        origin("http://localhost").accessControlRequestMethod();
        return this;
    }

    public HttpRequestBuilder method(HttpMethod method) {
        request.setMethod(method);
        return this;
    }

    public HttpRequestBuilder clearContent() {
        request.content().clear();
        return this;
    }

    public HttpRequestBuilder accessControlRequestHeader(final String header) {
        request.headers().add(ACCESS_CONTROL_REQUEST_HEADERS, header);
        return this;
    }

    public HttpRequestBuilder accessControlRequestHeader(final AsciiString header) {
        request.headers().add(ACCESS_CONTROL_REQUEST_HEADERS, header);
        return this;
    }

    public HttpRequestBuilder origin(final String origin) {
        request.headers().set(ORIGIN, origin);
        return this;
    }

    public HttpRequestBuilder accessControlRequestMethod(final HttpMethod method) {
        request.headers().set(ACCESS_CONTROL_REQUEST_METHOD, method.name());
        return this;
    }

    public HttpRequestBuilder accessControlRequestMethod() {
        request.headers().set(ACCESS_CONTROL_REQUEST_METHOD, request.method().name());
        return this;
    }

    public HttpRequestBuilder cookie(final String cookie) {
        request.headers().set(COOKIE, cookie);
        return this;
    }

    public HttpRequestBuilder content(final String content) {
        request.content().writeBytes(Unpooled.copiedBuffer(content, UTF_8));
        return this;
    }

    public HttpRequestBuilder contentType(final String contentType) {
        request.headers().set(CONTENT_TYPE, contentType);
        return this;
    }

    public FullHttpRequest build() {
        return request;
    }
}
