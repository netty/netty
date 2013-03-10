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
package io.netty.handler.codec.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Default implementation of {@link HttpMessage}.
 */
public class DefaultHttpMessage extends DefaultLastHttpContent implements HttpMessage {

    private final HttpHeaders headers;
    private final ByteBuf content;
    private final HttpHeaders trailingHeader = new DefaultHttpHeaders(HttpMessageType.TRAILER);

    public DefaultHttpMessage(HttpVersion httpVersion, HttpMethod method, String path) {
        this(httpVersion, method, path, Unpooled.buffer(0));
    }

    public DefaultHttpMessage(HttpVersion httpVersion, HttpMethod method, String path, ByteBuf content) {
        if (content == null) {
            throw new NullPointerException("content");
        }
        headers = new DefaultHttpHeaders(HttpMessageType.REQUEST);
        headers.setVersion(httpVersion);
        headers.setMethod(method);
        headers.setPath(path);
        this.content = content;
    }

    public DefaultHttpMessage(HttpVersion httpVersion, HttpResponseStatus status) {
        this(httpVersion, status, Unpooled.buffer(0));
    }

    public DefaultHttpMessage(HttpVersion httpVersion, HttpResponseStatus status, ByteBuf content) {
        if (content == null) {
            throw new NullPointerException("content");
        }
        headers = new DefaultHttpHeaders(HttpMessageType.RESPONSE);
        headers.setVersion(httpVersion);
        headers.setStatus(status);
        this.content = content;
    }

    public DefaultHttpMessage(HttpMessageType type, ByteBuf content) {
        if (content == null) {
            throw new NullPointerException("content");
        }
        headers = new DefaultHttpHeaders(type);
        this.content = content;
    }

    @Override
    public HttpHeaders headers() {
        return headers;
    }

    @Override
    public HttpHeaders trailingHeaders() {
        return trailingHeader;
    }

    @Override
    public ByteBuf data() {
        return content;
    }

    @Override
    public int refCnt() {
        return content.refCnt();
    }

    @Override
    public HttpMessage retain() {
        content.retain();
        return this;
    }

    @Override
    public HttpMessage retain(int increment) {
        content.retain(increment);
        return this;
    }

    @Override
    public boolean release() {
        return content.release();
    }

    @Override
    public boolean release(int decrement) {
        return content.release(decrement);
    }

    @Override
    public HttpMessage copy() {
        DefaultHttpMessage copy = new DefaultHttpMessage(headers().getType(), data().copy());
        copy.headers().set(headers());
        return copy;
    }
}
