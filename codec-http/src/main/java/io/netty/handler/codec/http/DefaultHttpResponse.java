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
package io.netty.handler.codec.http;

import io.netty.util.internal.ObjectUtil;

import static io.netty.handler.codec.http.DefaultHttpHeadersFactory.headersFactory;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

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
        this(version, status, headersFactory());
    }

    /**
     * Creates a new instance.
     *
     * @param version           the HTTP version of this response
     * @param status            the status of this response
     * @param validateHeaders   validate the header names and values when adding them to the {@link HttpHeaders}
     * @deprecated Use the {@link #DefaultHttpResponse(HttpVersion, HttpResponseStatus, HttpHeadersFactory)} constructor
     * instead.
     */
    @Deprecated
    public DefaultHttpResponse(HttpVersion version, HttpResponseStatus status, boolean validateHeaders) {
        this(version, status, headersFactory().withValidation(validateHeaders));
    }

    /**
     * Creates a new instance.
     *
     * @param version           the HTTP version of this response
     * @param status            the status of this response
     * @param validateHeaders   validate the header names and values when adding them to the {@link HttpHeaders}
     * @param singleFieldHeaders {@code true} to check and enforce that headers with the same name are appended
     * to the same entry and comma separated.
     * See <a href="https://tools.ietf.org/html/rfc7230#section-3.2.2">RFC 7230, 3.2.2</a>.
     * {@code false} to allow multiple header entries with the same name to
     * coexist.
     * @deprecated Use the {@link #DefaultHttpResponse(HttpVersion, HttpResponseStatus, HttpHeadersFactory)} constructor
     * instead.
     */
    @Deprecated
    public DefaultHttpResponse(HttpVersion version, HttpResponseStatus status, boolean validateHeaders,
                               boolean singleFieldHeaders) {
        this(version, status, headersFactory().withValidation(validateHeaders)
                .withCombiningHeaders(singleFieldHeaders));
    }

    /**
     * Creates a new instance.
     *
     * @param version           the HTTP version of this response
     * @param status            the status of this response
     * @param headersFactory    the {@link HttpHeadersFactory} used to create the headers for this HTTP Response.
     * The recommended default is {@link DefaultHttpHeadersFactory#headersFactory()}.
     */
    public DefaultHttpResponse(HttpVersion version, HttpResponseStatus status, HttpHeadersFactory headersFactory) {
        this(version, status, headersFactory.newHeaders());
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
        this.status = checkNotNull(status, "status");
    }

    @Override
    @Deprecated
    public HttpResponseStatus getStatus() {
        return status();
    }

    @Override
    public HttpResponseStatus status() {
        return status;
    }

    @Override
    public HttpResponse setStatus(HttpResponseStatus status) {
        this.status = ObjectUtil.checkNotNull(status, "status");
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
