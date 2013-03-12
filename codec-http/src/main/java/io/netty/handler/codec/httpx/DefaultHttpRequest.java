/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.httpx;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.stream.Stream;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;

/**
 * The default {@link HttpRequest} implementation.
 */
public class DefaultHttpRequest extends DefaultHttpMessage implements HttpRequest {

    /**
     * Creates a new instance.
     *
     * @param httpVersion the HTTP version of the request
     * @param method      the HTTP getMethod of the request
     * @param uri         the URI or path of the request
     */
    public DefaultHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri) {
        super(httpVersion, method, uri);
    }

    @Override
    public String getUri() {
        return super.getUri();
    }

    @Override
    public HttpRequest setUri(String uri) {
        return super.setUri(uri);
    }

    @Override
    public HttpMethod getMethod() {
        return super.getMethod();
    }

    @Override
    public HttpRequest setMethod(HttpMethod method) {
        return super.setMethod(method);
    }

    @Override
    public HttpRequest setVersion(HttpVersion version) {
        return (HttpRequest) super.setVersion(version);
    }

    @Override
    public HttpRequest setContent(ByteBuf content) {
        return (HttpRequest) super.setContent(content);
    }

    @Override
    public HttpRequest setContent(Stream<ByteBuf> content) {
        return (HttpRequest) super.setContent(content);
    }
}
