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
        this(version, status, true, false);
    }

    /**
     * Creates a new instance.
     *
     * @param version           the HTTP version of this response
     * @param status            the status of this response
     * @param validateHeaders   validate the header names and values when adding them to the {@link HttpHeaders}
     */
    public DefaultHttpResponse(HttpVersion version, HttpResponseStatus status, boolean validateHeaders) {
        this(version, status, validateHeaders, false);
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
     */
    public DefaultHttpResponse(HttpVersion version, HttpResponseStatus status, boolean validateHeaders,
                               boolean singleFieldHeaders) {
        super(version, validateHeaders, singleFieldHeaders);
        this.status = checkNotNull(status, "status");
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
    public String toString() {
        return HttpMessageUtil.appendResponse(new StringBuilder(256), this).toString();
    }
}
