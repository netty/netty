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

package io.netty.handler.codec.httpx;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

public class DefaultHttpHeaders extends HashedHttpHeaders {

    private HttpVersion version;
    private HttpMethod method;
    private HttpResponseStatus status;
    private String uri;

    public DefaultHttpHeaders(HttpMessageType type) {
        super(type);
    }

    @Override
    public HttpVersion getVersion() {
        return version;
    }

    @Override
    public HttpHeaders setVersion(HttpVersion version) {
        if (version == null) {
            throw new NullPointerException("version");
        }
        this.version = version;
        return this;
    }

    @Override
    public HttpMethod getMethod() {
        return method;
    }

    @Override
    public HttpHeaders setMethod(HttpMethod method) {
        if (method == null) {
            throw new NullPointerException("method");
        }
        this.method = method;
        return this;
    }

    @Override
    public HttpResponseStatus getStatus() {
        return status;
    }

    @Override
    public HttpHeaders setStatus(HttpResponseStatus status) {
        if (status == null) {
            throw new NullPointerException("status");
        }
        this.status = status;
        return this;
    }

    @Override
    public String getUri() {
        return uri;
    }

    @Override
    public HttpHeaders setUri(String uri) {
        if (uri == null) {
            throw new NullPointerException("uri");
        }
        this.uri = uri;
        return this;
    }
}
