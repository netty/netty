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

/**
 * The default {@link HttpResponse} implementation.
 */
public class DefaultHttpResponse extends DefaultHttpMessage implements HttpResponse {
    private static final int HASH_CODE_PRIME = 31;
    private HttpResponseStatus status;

    /**
     * Creates a new instance.
     *
     * @param version the HTTP version of this response
     * @param status  the getStatus of this response
     */
    public DefaultHttpResponse(HttpVersion version, HttpResponseStatus status) {
        this(version, status, true);
    }

    /**
     * Creates a new instance.
     *
     * @param version           the HTTP version of this response
     * @param status            the getStatus of this response
     * @param validateHeaders   validate the header names and values when adding them to the {@link HttpHeaders}
     */
    public DefaultHttpResponse(HttpVersion version, HttpResponseStatus status, boolean validateHeaders) {
        super(version, validateHeaders);
        if (status == null) {
            throw new NullPointerException("status");
        }
        this.status = status;
    }

    @Override
    public HttpResponseStatus status() {
        return status;
    }

    @Override
    public HttpResponse setStatus(HttpResponseStatus status) {
        if (status == null) {
            throw new NullPointerException("status");
        }
        this.status = status;
        return this;
    }

    @Override
    public HttpResponse setProtocolVersion(HttpVersion version) {
        super.setProtocolVersion(version);
        return this;
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = HASH_CODE_PRIME * result + status.hashCode();
        result = HASH_CODE_PRIME * result + super.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefaultHttpResponse)) {
            return false;
        }

        DefaultHttpResponse other = (DefaultHttpResponse) o;

        return status().equals(other.status()) && super.equals(o);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        appendAll(buf);

        // Remove the last newline.
        buf.setLength(buf.length() - StringUtil.NEWLINE.length());
        return buf.toString();
    }

    void appendAll(StringBuilder buf) {
        buf.append(StringUtil.simpleClassName(this));
        buf.append("(decodeResult: ");
        buf.append(decoderResult());
        buf.append(')');
        buf.append(StringUtil.NEWLINE);
        buf.append(protocolVersion().text());
        buf.append(' ');
        buf.append(status());
        buf.append(StringUtil.NEWLINE);
        appendHeaders(buf);
    }
}
