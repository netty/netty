/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.http;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpHeaders.Values;

/**
 * The default {@link HttpMessage} implementation.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (trustin@gmail.com)
 * @version $Rev$, $Date$
 */
public class DefaultHttpMessage implements HttpMessage {

    private final HttpHeaders headers = new HttpHeaders();
    private HttpVersion version;
    private ChannelBuffer content = ChannelBuffers.EMPTY_BUFFER;
    private boolean chunked;

    /**
     * Creates a new instance.
     */
    protected DefaultHttpMessage(final HttpVersion version) {
        setProtocolVersion(version);
    }

    public void addHeader(final String name, final Object value) {
        headers.addHeader(name, value);
    }

    public void setHeader(final String name, final Object value) {
        headers.setHeader(name, value);
    }

    public void setHeader(final String name, final Iterable<?> values) {
        headers.setHeader(name, values);
    }

    public void removeHeader(final String name) {
        headers.removeHeader(name);
    }

    @Deprecated
    public long getContentLength() {
        return HttpHeaders.getContentLength(this);
    }

    @Deprecated
    public long getContentLength(long defaultValue) {
        return HttpHeaders.getContentLength(this, defaultValue);
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

    public boolean isKeepAlive() {
        HttpVersion version = getProtocolVersion();
        if (!version.getProtocolName().equals("HTTP")) {
            return false;
        }

        String connection = getHeader(Names.CONNECTION);
        if (HttpHeaders.Values.CLOSE.equalsIgnoreCase(connection)) {
            return false;
        }

        if (version.equals(HttpVersion.HTTP_1_0) &&
            !HttpHeaders.Values.KEEP_ALIVE.equalsIgnoreCase(connection)) {
            return false;
        }
        return true;
    }

    public void setKeepAlive(boolean keepAlive) {
        HttpVersion version = getProtocolVersion();
        if (!version.getProtocolName().equals("HTTP")) {
            return;
        }

        if (version.equals(HttpVersion.HTTP_1_0)) {
            if (keepAlive) {
                setHeader(Names.CONNECTION, Values.KEEP_ALIVE);
            } else {
                removeHeader(Names.CONNECTION);
            }
        } else {
            if (keepAlive) {
                removeHeader(Names.CONNECTION);
            } else {
                setHeader(Names.CONNECTION, Values.CLOSE);
            }
        }
    }

    public void clearHeaders() {
        headers.clearHeaders();
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

    public String getHeader(final String name) {
        List<String> values = getHeaders(name);
        return values.size() > 0 ? values.get(0) : null;
    }

    public List<String> getHeaders(final String name) {
        return headers.getHeaders(name);
    }

    public List<Map.Entry<String, String>> getHeaders() {
        return headers.getHeaders();
    }

    public boolean containsHeader(final String name) {
        return headers.containsHeader(name);
    }

    public Set<String> getHeaderNames() {
        return headers.getHeaderNames();
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
}
