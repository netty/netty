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
 * The default {@link HttpMessage} implementation.
 */
public class DefaultHttpMessage implements HttpMessage {

    private final HttpHeaders headers = new HttpHeaders();
    private HttpVersion version;
    private ByteBuf content = Unpooled.EMPTY_BUFFER;
    private HttpTransferEncoding te = HttpTransferEncoding.SINGLE;

    /**
     * Creates a new instance.
     */
    protected DefaultHttpMessage(final HttpVersion version) {
        setProtocolVersion(version);
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
    public HttpTransferEncoding getTransferEncoding() {
        return te;
    }

    @Override
    public void setTransferEncoding(HttpTransferEncoding te) {
        if (te == null) {
            throw new NullPointerException("te (transferEncoding)");
        }
        this.te = te;
        switch (te) {
        case SINGLE:
            HttpCodecUtil.removeTransferEncodingChunked(this);
            break;
        case STREAMED:
            HttpCodecUtil.removeTransferEncodingChunked(this);
            setContent(Unpooled.EMPTY_BUFFER);
            break;
        case CHUNKED:
            if (!HttpCodecUtil.isTransferEncodingChunked(this)) {
                addHeader(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
            }
            removeHeader(HttpHeaders.Names.CONTENT_LENGTH);
            setContent(Unpooled.EMPTY_BUFFER);
            break;
        }
    }

    @Override
    public void clearHeaders() {
        headers.clearHeaders();
    }

    @Override
    public void setContent(ByteBuf content) {
        if (content == null) {
            content = Unpooled.EMPTY_BUFFER;
        }

        if (!getTransferEncoding().isSingle() && content.readable()) {
            throw new IllegalArgumentException(
                    "non-empty content disallowed if this.transferEncoding != SINGLE");
        }

        this.content = content;
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
    public HttpVersion getProtocolVersion() {
        return version;
    }

    @Override
    public void setProtocolVersion(HttpVersion version) {
        if (version == null) {
            throw new NullPointerException("version");
        }
        this.version = version;
    }

    @Override
    public ByteBuf getContent() {
        if (getTransferEncoding() == HttpTransferEncoding.SINGLE) {
            return content;
        } else {
            return Unpooled.EMPTY_BUFFER;
        }
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(getClass().getSimpleName());
        buf.append("(version: ");
        buf.append(getProtocolVersion().getText());
        buf.append(", keepAlive: ");
        buf.append(HttpHeaders.isKeepAlive(this));
        buf.append(", transferEncoding: ");
        buf.append(getTransferEncoding());
        buf.append(')');
        buf.append(StringUtil.NEWLINE);
        appendHeaders(buf);

        // Remove the last newline.
        buf.setLength(buf.length() - StringUtil.NEWLINE.length());
        return buf.toString();
    }

    void appendHeaders(StringBuilder buf) {
        for (Map.Entry<String, String> e: getHeaders()) {
            buf.append(e.getKey());
            buf.append(": ");
            buf.append(e.getValue());
            buf.append(StringUtil.NEWLINE);
        }
    }
}
