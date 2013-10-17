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
 * The default {@link HttpChunkTrailer} implementation.
 */
public class DefaultHttpChunkTrailer implements HttpChunkTrailer {

    private final HttpHeaders trailingHeaders = new TrailingHeaders(true);

    public boolean isLast() {
        return true;
    }

    @Deprecated
    public void addHeader(final String name, final Object value) {
        trailingHeaders.add(name, value);
    }

    @Deprecated
    public void setHeader(final String name, final Object value) {
        trailingHeaders.set(name, value);
    }

    @Deprecated
    public void setHeader(final String name, final Iterable<?> values) {
        trailingHeaders.set(name, values);
    }

    @Deprecated
    public void removeHeader(final String name) {
        trailingHeaders.remove(name);
    }

    @Deprecated
    public void clearHeaders() {
        trailingHeaders.clear();
    }

    @Deprecated
    public String getHeader(final String name) {
        return trailingHeaders.get(name);
    }

    @Deprecated
    public List<String> getHeaders(final String name) {
        return trailingHeaders.getAll(name);
    }

    @Deprecated
    public List<Map.Entry<String, String>> getHeaders() {
        return trailingHeaders.entries();
    }

    @Deprecated
    public boolean containsHeader(final String name) {
        return trailingHeaders.contains(name);
    }

    @Deprecated
    public Set<String> getHeaderNames() {
        return trailingHeaders.names();
    }

    public ChannelBuffer getContent() {
        return ChannelBuffers.EMPTY_BUFFER;
    }

    public void setContent(ChannelBuffer content) {
        throw new IllegalStateException("read-only");
    }

    public HttpHeaders trailingHeaders() {
        return trailingHeaders;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(super.toString());
        buf.append(StringUtil.NEWLINE);
        appendHeaders(buf);

        // Remove the last newline.
        buf.setLength(buf.length() - StringUtil.NEWLINE.length());
        return buf.toString();
    }

    private void appendHeaders(StringBuilder buf) {
        for (Map.Entry<String, String> e: trailingHeaders()) {
            buf.append(e.getKey());
            buf.append(": ");
            buf.append(e.getValue());
            buf.append(StringUtil.NEWLINE);
        }
    }

    private static final class TrailingHeaders extends DefaultHttpHeaders {

        TrailingHeaders(boolean validateHeaders) {
            super(validateHeaders);
        }

        @Override
        public HttpHeaders add(String name, Object value) {
            if (validate) {
                validateName(name);
            }
            return super.add(name, value);
        }

        @Override
        public HttpHeaders add(String name, Iterable<?> values) {
            if (validate) {
                validateName(name);
            }
            return super.add(name, values);
        }

        @Override
        public HttpHeaders set(String name, Iterable<?> values) {
            if (validate) {
                validateName(name);
            }
            return super.set(name, values);
        }

        @Override
        public HttpHeaders set(String name, Object value) {
            if (validate) {
                validateName(name);
            }
            return super.set(name, value);
        }

        private static void validateName(String name) {
            if (name.equalsIgnoreCase(HttpHeaders.Names.CONTENT_LENGTH) ||
                    name.equalsIgnoreCase(HttpHeaders.Names.TRANSFER_ENCODING) ||
                    name.equalsIgnoreCase(HttpHeaders.Names.TRAILER)) {
                throw new IllegalArgumentException(
                        "prohibited trailing header: " + name);
            }
        }
    }
}
