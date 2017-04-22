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
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

/**
 * The default {@link HttpResponse} implementation.
 */
public class DefaultHttpResponse extends DefaultHttpMessage implements HttpResponse {

    /**
     * Creates a new instance.
     *
     * @param version the HTTP version of this response
     * @param status  the getStatus of this response
     */
    public DefaultHttpResponse(HttpVersion version, HttpResponseStatus status) {
        super(version, status);
    }

    @Override
    public HttpResponseStatus getStatus() {
        return super.getStatus();
    }

    @Override
    public HttpResponse setStatus(HttpResponseStatus status) {
        return super.setStatus(status);
    }

    @Override
    public HttpResponse setVersion(HttpVersion version) {
        return (HttpResponse) super.setVersion(version);
    }

    @Override
    public HttpResponse setContent(ByteBuf content) {
        return (HttpResponse) super.setContent(content);
    }

    @Override
    public HttpResponse setContent(Stream<ByteBuf> content) {
        return (HttpResponse) super.setContent(content);
    }
}
