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
 * Default implementation of a {@link HttpResponse}.
 */
public class DefaultHttpResponse extends DefaultHttpResponseHeader implements HttpResponse {
    private ByteBuf content = Unpooled.EMPTY_BUFFER;

    public DefaultHttpResponse(HttpVersion version, HttpResponseStatus status) {
        this(version, status, Unpooled.EMPTY_BUFFER);
    }

    public DefaultHttpResponse(HttpVersion version, HttpResponseStatus status, ByteBuf content) {
        super(version, status);
        setContent(content);
    }

    @Override
    public ByteBuf getContent() {
        return content;
    }

    @Override
    public void setContent(ByteBuf content) {
        if (content == null) {
            throw new NullPointerException("content");
        }
        this.content = content;
    }
}
