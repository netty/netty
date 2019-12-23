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

import io.netty.util.internal.ObjectUtil;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

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
        this(httpVersion, method, uri, true);
    }

    /**
     * Creates a new instance.
     *
     * @param httpVersion       the HTTP version of the request
     * @param method            the HTTP method of the request
     * @param uri               the URI or path of the request
     * @param validateHeaders   validate the header names and values when adding them to the {@link HttpHeaders}
     */
    public DefaultHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri, boolean validateHeaders) {
        super(httpVersion, validateHeaders, false);
        this.method = checkNotNull(method, "method");
        this.uri = checkNotNull(uri, "uri");
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
        this.method = checkNotNull(method, "method");
        this.uri = checkNotNull(uri, "uri");
    }

    @Override
    @Deprecated
    public HttpMethod getMethod() {
        return method();
    }

    @Override
    public HttpMethod method() {
        return method;
    }

    @Override
    @Deprecated
    public String getUri() {
        return uri();
    }

    @Override
    public String uri() {
        return uri;
    }

    @Override
    public HttpRequest setMethod(HttpMethod method) {
        this.method = ObjectUtil.checkNotNull(method, "method");
        return this;
    }

    @Override
    public HttpRequest setUri(String uri) {
        this.uri = ObjectUtil.checkNotNull(uri, "uri");
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
