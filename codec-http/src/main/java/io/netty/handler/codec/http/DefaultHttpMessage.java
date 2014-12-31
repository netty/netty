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

    protected DefaultHttpMessage(final HttpVersion version, boolean validate) {
        if (version == null) {
            throw new NullPointerException("version");
        }
        this.version = version;
        headers = new DefaultHttpHeaders(validate);
    }

    @Override
    public HttpHeaders headers() {
        return headers;
    }

    @Override
    public HttpVersion getProtocolVersion() {
        return version;
    }

    @Override
    public HttpMessage setProtocolVersion(HttpVersion version) {
        if (version == null) {
            throw new NullPointerException("version");
        }
        this.version = version;
        return this;
    }
}
