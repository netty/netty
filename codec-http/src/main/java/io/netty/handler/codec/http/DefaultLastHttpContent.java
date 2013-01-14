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
package io.netty.handler.codec.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.internal.StringUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The default {@link LastHttpContent} implementation.
 */
public class DefaultLastHttpContent extends DefaultHttpContent implements LastHttpContent {

    private final HttpHeaders headers = new HttpHeaders() {
        @Override
        void validateHeaderName0(String name) {
            super.validateHeaderName0(name);
            if (name.equalsIgnoreCase(HttpHeaders.Names.CONTENT_LENGTH) ||
                name.equalsIgnoreCase(HttpHeaders.Names.TRANSFER_ENCODING) ||
                name.equalsIgnoreCase(HttpHeaders.Names.TRAILER)) {
                throw new IllegalArgumentException(
                        "prohibited trailing header: " + name);
            }
        }
    };

    public DefaultLastHttpContent() {
        this(Unpooled.EMPTY_BUFFER);
    }

    public DefaultLastHttpContent(ByteBuf content) {
        super(content);
    }

    @Override
    public void addHeader(final String name, final Object value) {
        headers.addHeader(name, value);
    }

    @Override
    public void setHeader(final String name, final Object value) {
        headers.setHeader(name, value);
    }

    @Override
    public void setHeader(final String name, final Iterable<?> values) {
        headers.setHeader(name, values);
    }

    @Override
    public void removeHeader(final String name) {
        headers.removeHeader(name);
    }

    @Override
    public void clearHeaders() {
        headers.clearHeaders();
    }

    @Override
    public String getHeader(final String name) {
        return headers.getHeader(name);
    }

    @Override
    public List<String> getHeaders(final String name) {
        return headers.getHeaders(name);
    }

    @Override
    public List<Map.Entry<String, String>> getHeaders() {
        return headers.getHeaders();
    }

    @Override
    public boolean containsHeader(final String name) {
        return headers.containsHeader(name);
    }

    @Override
    public Set<String> getHeaderNames() {
        return headers.getHeaderNames();
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(getClass().getSimpleName());
        buf.append(", size: ");
        buf.append(getContent().readableBytes());
        buf.append(", decodeResult: ");
        buf.append(getDecoderResult());
        buf.append(')');
        buf.append(StringUtil.NEWLINE);
        appendHeaders(buf);

        // Remove the last newline.
        buf.setLength(buf.length() - StringUtil.NEWLINE.length());
        return buf.toString();
    }

    private void appendHeaders(StringBuilder buf) {
        for (Map.Entry<String, String> e: getHeaders()) {
            buf.append(e.getKey());
            buf.append(": ");
            buf.append(e.getValue());
            buf.append(StringUtil.NEWLINE);
        }
    }
}
