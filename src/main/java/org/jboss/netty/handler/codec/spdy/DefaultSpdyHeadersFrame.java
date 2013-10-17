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
package org.jboss.netty.handler.codec.spdy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.netty.util.internal.StringUtil;

/**
 * The default {@link SpdyHeadersFrame} implementation.
 */
public class DefaultSpdyHeadersFrame extends DefaultSpdyStreamFrame
        implements SpdyHeadersFrame {

    private boolean invalid;
    private boolean truncated;
    private final SpdyHeaders headers = new DefaultSpdyHeaders();

    /**
     * Creates a new instance.
     *
     * @param streamId the Stream-ID of this frame
     */
    public DefaultSpdyHeadersFrame(int streamId) {
        super(streamId);
    }

    public boolean isInvalid() {
        return invalid;
    }

    public void setInvalid() {
        invalid = true;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated() {
        truncated = true;
    }

    public SpdyHeaders headers() {
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

    @Deprecated
    public void clearHeaders() {
        headers.clear();
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

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(getClass().getSimpleName());
        buf.append("(last: ");
        buf.append(isLast());
        buf.append(')');
        buf.append(StringUtil.NEWLINE);
        buf.append("--> Stream-ID = ");
        buf.append(getStreamId());
        buf.append(StringUtil.NEWLINE);
        buf.append("--> Headers:");
        buf.append(StringUtil.NEWLINE);
        appendHeaders(buf);

        // Remove the last newline.
        buf.setLength(buf.length() - StringUtil.NEWLINE.length());
        return buf.toString();
    }

    protected void appendHeaders(StringBuilder buf) {
        for (Map.Entry<String, String> e: headers()) {
            buf.append("    ");
            buf.append(e.getKey());
            buf.append(": ");
            buf.append(e.getValue());
            buf.append(StringUtil.NEWLINE);
        }
    }
}
