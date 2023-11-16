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

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * The default {@link HttpMessage} implementation.
 */
public abstract class DefaultHttpMessage extends DefaultHttpObject implements HttpMessage {
    private static final int HASH_CODE_PRIME = 31;
    private HttpVersion version;
    private final HttpHeaders headers;

    /**
     * Creates a new instance.
     */
    protected DefaultHttpMessage(final HttpVersion version) {
        this(version, DefaultHttpHeadersFactory.headersFactory());
    }

    /**
     * Creates a new instance.
     * <p>
     * @deprecated Use the {@link #DefaultHttpMessage(HttpVersion, HttpHeadersFactory)} constructor instead,
     * ideally using the {@link DefaultHttpHeadersFactory#headersFactory()},
     * or a factory that otherwise has validation enabled.
     */
    @Deprecated
    protected DefaultHttpMessage(final HttpVersion version, boolean validateHeaders, boolean singleFieldHeaders) {
        this(version, DefaultHttpHeadersFactory.headersFactory()
                .withValidation(validateHeaders)
                .withCombiningHeaders(singleFieldHeaders));
    }

    /**
     * Creates a new instance.
     */
    protected DefaultHttpMessage(HttpVersion version, HttpHeadersFactory headersFactory) {
        this(version, headersFactory.newHeaders());
    }

    /**
     * Creates a new instance.
     */
    protected DefaultHttpMessage(final HttpVersion version, HttpHeaders headers) {
        this.version = checkNotNull(version, "version");
        this.headers = checkNotNull(headers, "headers");
    }

    @Override
    public HttpHeaders headers() {
        return headers;
    }

    @Override
    @Deprecated
    public HttpVersion getProtocolVersion() {
        return protocolVersion();
    }

    @Override
    public HttpVersion protocolVersion() {
        return version;
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = HASH_CODE_PRIME * result + headers.hashCode();
        result = HASH_CODE_PRIME * result + version.hashCode();
        result = HASH_CODE_PRIME * result + super.hashCode();
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
    public HttpMessage setProtocolVersion(HttpVersion version) {
        this.version = checkNotNull(version, "version");
        return this;
    }
}
