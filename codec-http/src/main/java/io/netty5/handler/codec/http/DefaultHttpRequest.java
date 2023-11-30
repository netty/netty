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
 * The default {@link HttpRequest} implementation.
 */
public class DefaultHttpRequest extends DefaultHttpMessage implements HttpRequest {
    private static final int HASH_CODE_PRIME = 31;
    private HttpMethod method;
    private String uri;

    /**
     * Creates a new instance.
     *
     * @param httpVersion the HTTP version of the request
     * @param method      the HTTP method of the request
     * @param uri         the URI or path of the request
     */
    public DefaultHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri) {
        this(httpVersion, method, uri, DefaultHttpHeadersFactory.headersFactory());
    }

    /**
     * Creates a new instance.
     *
     * @param httpVersion       the HTTP version of the request
     * @param method            the HTTP method of the request
     * @param uri               the URI or path of the request
     * @param factory the factory used to create the {@link HttpHeaders}.
     * The recommended default is {@link DefaultHttpHeadersFactory#headersFactory()}.
     */
    public DefaultHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri, HttpHeadersFactory factory) {
        super(httpVersion, factory);
        this.method = requireNonNull(method, "method");
        this.uri = requireNonNull(uri, "uri");
    }

    /**
     * Creates a new instance.
     *
     * @param httpVersion       the HTTP version of the request
     * @param method            the HTTP method of the request
     * @param uri               the URI or path of the request
     * @param headers           the Headers for this Request
     */
    public DefaultHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri, HttpHeaders headers) {
        super(httpVersion, headers);
        this.method = requireNonNull(method, "method");
        this.uri = requireNonNull(uri, "uri");
    }

    @Override
    public HttpMethod method() {
        return method;
    }

    @Override
    public String uri() {
        return uri;
    }

    @Override
    public HttpRequest setMethod(HttpMethod method) {
        requireNonNull(method, "method");
        this.method = method;
        return this;
    }

    @Override
    public HttpRequest setUri(String uri) {
        requireNonNull(uri, "uri");
        this.uri = uri;
        return this;
    }

    @Override
    public HttpRequest setProtocolVersion(HttpVersion version) {
        super.setProtocolVersion(version);
        return this;
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = HASH_CODE_PRIME * result + method.hashCode();
        result = HASH_CODE_PRIME * result + uri.hashCode();
        result = HASH_CODE_PRIME * result + super.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefaultHttpRequest)) {
            return false;
        }

        DefaultHttpRequest other = (DefaultHttpRequest) o;

        return method().equals(other.method()) &&
               uri().equalsIgnoreCase(other.uri()) &&
               super.equals(o);
    }

    @Override
    public String toString() {
        return HttpMessageUtil.appendRequest(new StringBuilder(256), this).toString();
    }
}
