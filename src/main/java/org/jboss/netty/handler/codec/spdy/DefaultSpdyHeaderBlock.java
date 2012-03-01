/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
 * The default {@link SpdyHeaderBlock} implementation.
 */
public class DefaultSpdyHeaderBlock implements SpdyHeaderBlock {

    private boolean invalid;
    private final SpdyHeaders headers = new SpdyHeaders();

    /**
     * Creates a new instance.
     */
    protected DefaultSpdyHeaderBlock() {
    }

    public boolean isInvalid() {
        return invalid;
    }

    public void setInvalid() {
        this.invalid = true;
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

    public void clearHeaders() {
        headers.clearHeaders();
    }

    public String getHeader(final String name) {
        return headers.getHeader(name);
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

    protected void appendHeaders(StringBuilder buf) {
        for (Map.Entry<String, String> e: getHeaders()) {
            buf.append("    ");
            buf.append(e.getKey());
            buf.append(": ");
            buf.append(e.getValue());
            buf.append(StringUtil.NEWLINE);
        }
    }
}
