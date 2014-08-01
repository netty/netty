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

import io.netty.util.internal.StringUtil;

import java.util.Map;

/**
 * The default {@link HttpMessage} implementation.
 */
public abstract class DefaultHttpMessage extends DefaultHttpObject implements HttpMessage {

    private HttpVersion version;
    private final HttpHeaders headers;

    /**
     * Creates a new instance.
     */
    protected DefaultHttpMessage(final HttpVersion version) {
        this(version, true);
    }

    /**
     * Creates a new instance.
     */
    protected DefaultHttpMessage(final HttpVersion version, boolean validateHeaders) {
        if (version == null) {
            throw new NullPointerException("version");
        }
        this.version = version;
        headers = new DefaultHttpHeaders(validateHeaders);
    }

    @Override
    public HttpHeaders headers() {
        return headers;
    }

    @Override
    public HttpVersion protocolVersion() {
        return version;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + headers.hashCode();
        result = prime * result + version.hashCode();
        result = prime * result + super.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefaultHttpMessage)) {
            return false;
        }

        DefaultHttpMessage other = (DefaultHttpMessage) o;

        return headers().equals(other.headers()) &&
               protocolVersion().equals(other.protocolVersion()) &&
               super.equals(o);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(StringUtil.simpleClassName(this));
        buf.append("(version: ");
        buf.append(protocolVersion().text());
        buf.append(", keepAlive: ");
        buf.append(HttpHeaderUtil.isKeepAlive(this));
        buf.append(')');
        buf.append(StringUtil.NEWLINE);
        appendHeaders(buf);

        // Remove the last newline.
        buf.setLength(buf.length() - StringUtil.NEWLINE.length());
        return buf.toString();
    }

    @Override
    public HttpMessage setProtocolVersion(HttpVersion version) {
        if (version == null) {
            throw new NullPointerException("version");
        }
        this.version = version;
        return this;
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
