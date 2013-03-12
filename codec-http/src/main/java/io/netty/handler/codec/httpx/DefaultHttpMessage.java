/*
 * Copyright 2013 The Netty Project
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
import io.netty.buffer.Unpooled;
import io.netty.buffer.stream.Stream;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.internal.StringUtil;

/**
 * Default implementation of {@link HttpMessage}.
 */
class DefaultHttpMessage implements HttpMessage {

    private final HttpHeaders headers;
    private HttpHeaders trailingHeaders;
    private Object content = Unpooled.EMPTY_BUFFER;

    protected DefaultHttpMessage(HttpVersion httpVersion, HttpMethod method, String path) {
        headers = new DefaultHttpHeaders(HttpMessageType.REQUEST);
        headers.setVersion(httpVersion);
        headers.setMethod(method);
        headers.setUri(path);
    }

    protected DefaultHttpMessage(HttpVersion httpVersion, HttpResponseStatus status) {
        headers = new DefaultHttpHeaders(HttpMessageType.RESPONSE);
        headers.setVersion(httpVersion);
        headers.setStatus(status);
    }

    @Override
    public HttpMessageType getType() {
        return getHeaders().getType();
    }

    @Override
    public HttpHeaders getHeaders() {
        return headers;
    }

    @Override
    public HttpHeaders getTrailingHeaders() {
        HttpHeaders trailingHeaders = this.trailingHeaders;
        if (trailingHeaders == null) {
            this.trailingHeaders = trailingHeaders = new DefaultHttpHeaders(HttpMessageType.TRAILER);
        }
        return trailingHeaders;
    }

    @Override
    public boolean hasTrailingHeaders() {
        return trailingHeaders != null && !trailingHeaders.isEmpty();
    }

    @Override
    public boolean isContentStream() {
        return content instanceof Stream;
    }

    @Override
    public Object getContent() {
        return content;
    }

    @Override
    public HttpMessage setContent(Stream<ByteBuf> content) {
        setContent0(content);
        return this;
    }

    @Override
    public HttpMessage setContent(ByteBuf content) {
        setContent0(content);
        return this;
    }

    private void setContent0(Object content) {
        if (content == null) {
            throw new NullPointerException("content");
        }
        this.content = content;
    }

    @Override
    public HttpVersion getVersion() {
        return getHeaders().getVersion();
    }

    @Override
    public HttpMessage setVersion(HttpVersion version) {
        getHeaders().setVersion(version);
        return this;
    }

    String getUri() {
        return getHeaders().getUri();
    }

    HttpRequest setUri(String uri) {
        getHeaders().setUri(uri);
        return (HttpRequest) this;
    }

    HttpMethod getMethod() {
        return getHeaders().getMethod();
    }

    HttpRequest setMethod(HttpMethod method) {
        getHeaders().setMethod(method);
        return (HttpRequest) this;
    }

    HttpResponseStatus getStatus() {
        return getHeaders().getStatus();
    }

    HttpResponse setStatus(HttpResponseStatus status) {
        getHeaders().setStatus(status);
        return (HttpResponse) this;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(getHeaders());
        buf.append(StringUtil.NEWLINE);
        buf.append(getContent());
        if (hasTrailingHeaders()) {
            buf.append(StringUtil.NEWLINE);
            buf.append(getTrailingHeaders());
        }
        return buf.toString();
    }
}
