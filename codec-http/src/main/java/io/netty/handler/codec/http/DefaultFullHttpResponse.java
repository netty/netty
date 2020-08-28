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
 * Default implementation of a {@link FullHttpResponse}.
 */
public class DefaultFullHttpResponse extends DefaultHttpResponse implements FullHttpResponse {

    private final ByteBuf content;
    private final HttpHeaders trailingHeaders;

    /**
     * Used to cache the value of the hash code and avoid {@link IllegalReferenceCountException}.
     */
    private int hash;

    public DefaultFullHttpResponse(HttpVersion version, HttpResponseStatus status) {
        this(version, status, Unpooled.buffer(0));
    }

    public DefaultFullHttpResponse(HttpVersion version, HttpResponseStatus status, ByteBuf content) {
        this(version, status, content, true);
    }

    public DefaultFullHttpResponse(HttpVersion version, HttpResponseStatus status, boolean validateHeaders) {
        this(version, status, Unpooled.buffer(0), validateHeaders, false);
    }

    public DefaultFullHttpResponse(HttpVersion version, HttpResponseStatus status, boolean validateHeaders,
                                   boolean singleFieldHeaders) {
        this(version, status, Unpooled.buffer(0), validateHeaders, singleFieldHeaders);
    }

    public DefaultFullHttpResponse(HttpVersion version, HttpResponseStatus status,
                                   ByteBuf content, boolean validateHeaders) {
        this(version, status, content, validateHeaders, false);
    }

    public DefaultFullHttpResponse(HttpVersion version, HttpResponseStatus status,
                                   ByteBuf content, boolean validateHeaders, boolean singleFieldHeaders) {
        super(version, status, validateHeaders, singleFieldHeaders);
        this.content = checkNotNull(content, "content");
        this.trailingHeaders = singleFieldHeaders ? new CombinedHttpHeaders(validateHeaders)
                                                  : new DefaultHttpHeaders(validateHeaders);
    }

    public DefaultFullHttpResponse(HttpVersion version, HttpResponseStatus status,
            ByteBuf content, HttpHeaders headers, HttpHeaders trailingHeaders) {
        super(version, status, headers);
        this.content = checkNotNull(content, "content");
        this.trailingHeaders = checkNotNull(trailingHeaders, "trailingHeaders");
    }

    @Override
    public HttpHeaders trailingHeaders() {
        return trailingHeaders;
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
    public FullHttpResponse retain() {
        content.retain();
        return this;
    }

    @Override
    public FullHttpResponse retain(int increment) {
        content.retain(increment);
        return this;
    }

    @Override
    public FullHttpResponse touch() {
        content.touch();
        return this;
    }

    @Override
    public FullHttpResponse touch(Object hint) {
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
    public FullHttpResponse copy() {
        return replace(content().copy());
    }

    @Override
    public FullHttpResponse duplicate() {
        return replace(content().duplicate());
    }

    @Override
    public FullHttpResponse retainedDuplicate() {
        return replace(content().retainedDuplicate());
    }

    @Override
    public FullHttpResponse replace(ByteBuf content) {
        FullHttpResponse response = new DefaultFullHttpResponse(protocolVersion(), status(), content,
                headers().copy(), trailingHeaders().copy());
        response.setDecoderResult(decoderResult());
        return response;
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
        if (!(o instanceof DefaultFullHttpResponse)) {
            return false;
        }

        DefaultFullHttpResponse other = (DefaultFullHttpResponse) o;

        return super.equals(other) &&
               content().equals(other.content()) &&
               trailingHeaders().equals(other.trailingHeaders());
    }

    @Override
    public String toString() {
        return HttpMessageUtil.appendFullResponse(new StringBuilder(256), this).toString();
    }
}
