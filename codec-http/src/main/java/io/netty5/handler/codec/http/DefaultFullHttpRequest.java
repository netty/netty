/*
 * Copyright 2013 The Netty Project
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

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.Send;
import io.netty5.util.IllegalReferenceCountException;

import static java.util.Objects.requireNonNull;

/**
 * Default implementation of {@link FullHttpRequest}.
 */
public class DefaultFullHttpRequest extends DefaultHttpRequest implements FullHttpRequest {
    private final Buffer payload;
    private final HttpHeaders trailingHeader;

    /**
     * Used to cache the value of the hash code and avoid {@link IllegalReferenceCountException}.
     */
    private int hash;

    public DefaultFullHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri, Buffer payload) {
        this(httpVersion, method, uri, payload, true);
    }

    public DefaultFullHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri,
                                  Buffer payload, boolean validateHeaders) {
        super(httpVersion, method, uri, validateHeaders);
        this.payload = requireNonNull(payload, "payload");
        trailingHeader = new DefaultHttpHeaders(validateHeaders);
    }

    public DefaultFullHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri,
                                  Buffer payload, HttpHeaders headers, HttpHeaders trailingHeader) {
        super(httpVersion, method, uri, headers);
        this.payload = requireNonNull(payload, "payload");
        this.trailingHeader = requireNonNull(trailingHeader, "trailingHeader");
    }

    @Override
    public void close() {
        payload.close();
    }

    @Override
    public boolean isAccessible() {
        return payload.isAccessible();
    }

    @Override
    public FullHttpRequest touch(Object hint) {
        payload.touch(hint);
        return this;
    }

    @Override
    public Buffer payload() {
        return payload;
    }

    @Override
    public Send<FullHttpRequest> send() {
        return payload.send().map(FullHttpRequest.class,
                payload -> new DefaultFullHttpRequest(
                        protocolVersion(), method(), uri(), payload, headers(), trailingHeader));
    }

    @Override
    public HttpHeaders trailingHeaders() {
        return trailingHeader;
    }

    @Override
    public FullHttpRequest setProtocolVersion(HttpVersion version) {
        super.setProtocolVersion(version);
        return this;
    }

    @Override
    public FullHttpRequest setMethod(HttpMethod method) {
        super.setMethod(method);
        return this;
    }

    @Override
    public FullHttpRequest setUri(String uri) {
        super.setUri(uri);
        return this;
    }

    @Override
    public int hashCode() {
        int hash = this.hash;
        if (hash == 0) {
            final Buffer payload = payload();
            if (payload.isAccessible()) {
                try {
                    hash = 31 + payload.hashCode();
                } catch (IllegalReferenceCountException ignored) {
                    // Handle race condition between checking refCnt() == 0 and using the object.
                    hash = 31;
                }
            } else {
                hash = 31;
            }
            hash = 31 * hash + trailingHeaders().hashCode();
            hash = 31 * hash + super.hashCode();
            this.hash = hash;
        }
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefaultFullHttpRequest)) {
            return false;
        }

        DefaultFullHttpRequest other = (DefaultFullHttpRequest) o;

        return super.equals(other) &&
                payload().equals(other.payload()) &&
                trailingHeaders().equals(other.trailingHeaders());
    }

    @Override
    public String toString() {
        return HttpMessageUtil.appendFullRequest(new StringBuilder(256), this).toString();
    }
}
