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
 * Default implementation of a {@link FullHttpResponse}.
 */
public class DefaultFullHttpResponse extends DefaultHttpResponse implements FullHttpResponse {

    private final Buffer payload;
    private final HttpHeaders trailingHeaders;

    /**
     * Used to cache the value of the hash code and avoid {@link IllegalReferenceCountException}.
     */
    private int hash;

    public DefaultFullHttpResponse(HttpVersion version, HttpResponseStatus status, Buffer payload) {
        this(version, status, payload, true);
    }

    public DefaultFullHttpResponse(HttpVersion version, HttpResponseStatus status,
                                   Buffer payload, boolean validateHeaders) {
        this(version, status, payload, validateHeaders, false);
    }

    public DefaultFullHttpResponse(HttpVersion version, HttpResponseStatus status,
                                   Buffer payload, boolean validateHeaders, boolean singleFieldHeaders) {
        super(version, status, validateHeaders, singleFieldHeaders);
        this.payload = requireNonNull(payload, "payload");
        this.trailingHeaders = singleFieldHeaders ? new CombinedHttpHeaders(validateHeaders)
                                                  : new DefaultHttpHeaders(validateHeaders);
    }

    public DefaultFullHttpResponse(HttpVersion version, HttpResponseStatus status,
                                   Buffer payload, HttpHeaders headers, HttpHeaders trailingHeaders) {
        super(version, status, headers);
        this.payload = requireNonNull(payload, "payload");
        this.trailingHeaders = requireNonNull(trailingHeaders, "trailingHeaders");
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
    public FullHttpResponse touch(Object hint) {
        payload.touch(hint);
        return this;
    }

    @Override
    public Buffer payload() {
        return payload;
    }

    @Override
    public Send<FullHttpResponse> send() {
        return payload.send().map(FullHttpResponse.class,
                payload -> new DefaultFullHttpResponse(protocolVersion(), status(), payload, headers(),
                        trailingHeaders));
    }

    @Override
    public HttpHeaders trailingHeaders() {
        return trailingHeaders;
    }

    @Override
    public FullHttpResponse setProtocolVersion(HttpVersion version) {
        super.setProtocolVersion(version);
        return this;
    }

    @Override
    public FullHttpResponse setStatus(HttpResponseStatus status) {
        super.setStatus(status);
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
        if (!(o instanceof DefaultFullHttpResponse)) {
            return false;
        }

        DefaultFullHttpResponse other = (DefaultFullHttpResponse) o;

        return super.equals(other) &&
               payload().equals(other.payload()) &&
               trailingHeaders().equals(other.trailingHeaders());
    }

    @Override
    public String toString() {
        return HttpMessageUtil.appendFullResponse(new StringBuilder(256), this).toString();
    }
}
