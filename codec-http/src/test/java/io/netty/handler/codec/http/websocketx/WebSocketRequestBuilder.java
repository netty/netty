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
package io.netty.handler.codec.http.websocketx;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;

import static io.netty.handler.codec.http.HttpHeaders.Values.*;
import static io.netty.handler.codec.http.HttpVersion.*;

public class WebSocketRequestBuilder {

    private HttpVersion httpVersion;
    private HttpMethod method;
    private String uri;
    private String host;
    private String upgrade;
    private String connection;
    private String key;
    private String origin;
    private WebSocketVersion version;

    public WebSocketRequestBuilder httpVersion(HttpVersion httpVersion) {
        this.httpVersion = httpVersion;
        return this;
    }

    public WebSocketRequestBuilder method(HttpMethod method) {
        this.method = method;
        return this;
    }

    public WebSocketRequestBuilder uri(String uri) {
        this.uri = uri;
        return this;
    }

    public WebSocketRequestBuilder host(String host) {
        this.host = host;
        return this;
    }

    public WebSocketRequestBuilder upgrade(String upgrade) {
        this.upgrade = upgrade;
        return this;
    }

    public WebSocketRequestBuilder connection(String connection) {
        this.connection = connection;
        return this;
    }

    public WebSocketRequestBuilder key(String key) {
        this.key = key;
        return this;
    }

    public WebSocketRequestBuilder origin(String origin) {
        this.origin = origin;
        return this;
    }

    public WebSocketRequestBuilder version13() {
        version = WebSocketVersion.V13;
        return this;
    }

    public WebSocketRequestBuilder version8() {
        version = WebSocketVersion.V08;
        return this;
    }

    public WebSocketRequestBuilder version00() {
        version = null;
        return this;
    }

    public WebSocketRequestBuilder noVersion() {
        return this;
    }

    public FullHttpRequest build() {
        FullHttpRequest req = new DefaultFullHttpRequest(httpVersion, method, uri);
        HttpHeaders headers = req.headers();

        if (host != null) {
            headers.set(Names.HOST, host);
        }
        if (upgrade != null) {
            headers.set(Names.UPGRADE, upgrade);
        }
        if (connection != null) {
            headers.set(Names.CONNECTION, connection);
        }
        if (key != null) {
            headers.set(Names.SEC_WEBSOCKET_KEY, key);
        }
        if (origin != null) {
            headers.set(Names.SEC_WEBSOCKET_ORIGIN, origin);
        }
        if (version != null) {
            headers.set(Names.SEC_WEBSOCKET_VERSION, version.toHttpHeaderValue());
        }
        return req;
    }

    public static HttpRequest sucessful() {
        return new WebSocketRequestBuilder().httpVersion(HTTP_1_1)
                .method(HttpMethod.GET)
                .uri("/test")
                .host("server.example.com")
                .upgrade(WEBSOCKET.toLowerCase())
                .key("dGhlIHNhbXBsZSBub25jZQ==")
                .origin("http://example.com")
                .version13()
                .build();
    }
}
