/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.handler.codec.http;

import io.netty5.handler.codec.http.headers.DefaultHttpHeadersFactory;
import io.netty5.handler.codec.http.headers.HttpHeaders;
import io.netty5.handler.codec.http.headers.HttpHeadersFactory;

import static java.util.Objects.requireNonNull;

/**
 * The default {@link HttpResponse} implementation.
 */
public class DefaultHttpResponse extends DefaultHttpMessage implements HttpResponse {

    private HttpResponseStatus status;

    /**
     * Creates a new instance.
     *
     * @param version the HTTP version of this response
     * @param status  the status of this response
     */
    public DefaultHttpResponse(HttpVersion version, HttpResponseStatus status) {
        this(version, status, DefaultHttpHeadersFactory.headersFactory());
    }

    /**
     * Creates a new instance.
     *
     * @param version           the HTTP version of this response
     * @param status            the status of this response
     * @param factory the factory used to create the {@link HttpHeaders}.
     * The recommended default is {@link DefaultHttpHeadersFactory#headersFactory()}.
     */
    public DefaultHttpResponse(HttpVersion version, HttpResponseStatus status, HttpHeadersFactory factory) {
        super(version, factory);
        this.status = requireNonNull(status, "status");
    }

    /**
     * Creates a new instance.
     *
     * @param version           the HTTP version of this response
     * @param status            the status of this response
     * @param headers           the headers for this HTTP Response
     */
    public DefaultHttpResponse(HttpVersion version, HttpResponseStatus status, HttpHeaders headers) {
        super(version, headers);
        this.status = requireNonNull(status, "status");
    }

    @Override
    public HttpResponseStatus status() {
        return status;
    }

    @Override
    public HttpResponse setStatus(HttpResponseStatus status) {
        requireNonNull(status, "status");
        this.status = status;
        return this;
    }

    @Override
    public HttpResponse setProtocolVersion(HttpVersion version) {
        super.setProtocolVersion(version);
        return this;
    }

    @Override
    public String toString() {
        return HttpMessageUtil.appendResponse(new StringBuilder(256), this).toString();
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + status.hashCode();
        result = 31 * result + super.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefaultHttpResponse)) {
            return false;
        }

        DefaultHttpResponse other = (DefaultHttpResponse) o;

        return status.equals(other.status()) && super.equals(o);
    }
}
