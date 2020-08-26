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
package io.netty.handler.codec.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.util.IllegalReferenceCountException;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Default implementation of {@link FullHttpRequest}.
 */
public class DefaultFullHttpRequest extends DefaultHttpRequest implements FullHttpRequest {
    private final ByteBuf content;
    private final HttpHeaders trailingHeader;

    /**
     * Used to cache the value of the hash code and avoid {@link IllegalReferenceCountException}.
     */
    private int hash;

    public DefaultFullHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri) {
        this(httpVersion, method, uri, Unpooled.buffer(0));
    }

    public DefaultFullHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri, ByteBuf content) {
        this(httpVersion, method, uri, content, true);
    }

    public DefaultFullHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri, boolean validateHeaders) {
        this(httpVersion, method, uri, Unpooled.buffer(0), validateHeaders);
    }

    public DefaultFullHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri,
                                  ByteBuf content, boolean validateHeaders) {
        super(httpVersion, method, uri, validateHeaders);
        this.content = checkNotNull(content, "content");
        trailingHeader = new DefaultHttpHeaders(validateHeaders);
    }

    public DefaultFullHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri,
            ByteBuf content, HttpHeaders headers, HttpHeaders trailingHeader) {
        super(httpVersion, method, uri, headers);
        this.content = checkNotNull(content, "content");
        this.trailingHeader = checkNotNull(trailingHeader, "trailingHeader");
    }

    @Override
    public HttpHeaders trailingHeaders() {
        return trailingHeader;
    }

    @Override
    public ByteBuf content() {
        return content;
    }

    @Override
    public int refCnt() {
        return content.refCnt();
    }

    @Override
    public FullHttpRequest retain() {
        content.retain();
        return this;
    }

    @Override
    public FullHttpRequest retain(int increment) {
        content.retain(increment);
        return this;
    }

    @Override
    public FullHttpRequest touch() {
        content.touch();
        return this;
    }

    @Override
    public FullHttpRequest touch(Object hint) {
        content.touch(hint);
        return this;
    }

    @Override
    public boolean release() {
        return content.release();
    }

    @Override
    public boolean release(int decrement) {
        return content.release(decrement);
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
    public FullHttpRequest copy() {
        return replace(content().copy());
    }

    @Override
    public FullHttpRequest duplicate() {
        return replace(content().duplicate());
    }

    @Override
    public FullHttpRequest retainedDuplicate() {
        return replace(content().retainedDuplicate());
    }

    @Override
    public FullHttpRequest replace(ByteBuf content) {
        FullHttpRequest request = new DefaultFullHttpRequest(protocolVersion(), method(), uri(), content,
                headers().copy(), trailingHeaders().copy());
        request.setDecoderResult(decoderResult());
        return request;
    }

    @Override
    public int hashCode() {
        int hash = this.hash;
        if (hash == 0) {
            if (ByteBufUtil.isAccessible(content())) {
                try {
                    hash = 31 + content().hashCode();
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
               content().equals(other.content()) &&
               trailingHeaders().equals(other.trailingHeaders());
    }

    @Override
    public String toString() {
        return HttpMessageUtil.appendFullRequest(new StringBuilder(256), this).toString();
    }
}
