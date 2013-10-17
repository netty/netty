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
package org.jboss.netty.handler.codec.http;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.util.internal.StringUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The default {@link HttpMessage} implementation.
 */
public class DefaultHttpMessage implements HttpMessage {

    private final HttpHeaders headers = new DefaultHttpHeaders(true);
    private HttpVersion version;
    private ChannelBuffer content = ChannelBuffers.EMPTY_BUFFER;
    private boolean chunked;

    /**
     * Creates a new instance.
     */
    protected DefaultHttpMessage(final HttpVersion version) {
        setProtocolVersion(version);
    }

    public HttpHeaders headers() {
        return headers;
    }

    @Deprecated
    public void addHeader(final String name, final Object value) {
        headers.add(name, value);
    }

    @Deprecated
    public void setHeader(final String name, final Object value) {
        headers.set(name, value);
    }

    @Deprecated
    public void setHeader(final String name, final Iterable<?> values) {
        headers.set(name, values);
    }

    @Deprecated
    public void removeHeader(final String name) {
        headers.remove(name);
    }

    public boolean isChunked() {
        if (chunked) {
            return true;
        } else {
            return HttpCodecUtil.isTransferEncodingChunked(this);
        }
    }

    public void setChunked(boolean chunked) {
        this.chunked = chunked;
        if (chunked) {
            setContent(ChannelBuffers.EMPTY_BUFFER);
        }
    }

    @Deprecated
    public void clearHeaders() {
        headers.clear();
    }

    public void setContent(ChannelBuffer content) {
        if (content == null) {
            content = ChannelBuffers.EMPTY_BUFFER;
        }
        if (content.readable() && isChunked()) {
            throw new IllegalArgumentException(
                    "non-empty content disallowed if this.chunked == true");
        }
        this.content = content;
    }

    @Deprecated
    public String getHeader(final String name) {
        return headers.get(name);
    }

    @Deprecated
    public List<String> getHeaders(final String name) {
        return headers.getAll(name);
    }

    @Deprecated
    public List<Map.Entry<String, String>> getHeaders() {
        return headers.entries();
    }

    @Deprecated
    public boolean containsHeader(final String name) {
        return headers.contains(name);
    }

    @Deprecated
    public Set<String> getHeaderNames() {
        return headers.names();
    }

    public HttpVersion getProtocolVersion() {
        return version;
    }

    public void setProtocolVersion(HttpVersion version) {
        if (version == null) {
            throw new NullPointerException("version");
        }
        this.version = version;
    }

    public ChannelBuffer getContent() {
        return content;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(getClass().getSimpleName());
        buf.append("(version: ");
        buf.append(getProtocolVersion().getText());
        buf.append(", keepAlive: ");
        buf.append(HttpHeaders.isKeepAlive(this));
        buf.append(", chunked: ");
        buf.append(isChunked());
        buf.append(')');
        buf.append(StringUtil.NEWLINE);
        appendHeaders(buf);

        // Remove the last newline.
        buf.setLength(buf.length() - StringUtil.NEWLINE.length());
        return buf.toString();
    }

    void appendHeaders(StringBuilder buf) {
        for (Map.Entry<String, String> e: headers()) {
            buf.append(e.getKey());
            buf.append(": ");
            buf.append(e.getValue());
            buf.append(StringUtil.NEWLINE);
        }
    }
}
