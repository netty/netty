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
import io.netty.buffer.Unpooled;
import io.netty.util.internal.StringUtil;


/**
 * Default implementation of a {@link FullHttpResponse}.
 */
public class DefaultFullHttpResponse extends DefaultHttpResponse implements FullHttpResponse {
    private static final int HASH_CODE_PRIME = 31;
    private final ByteBuf content;
    private final HttpHeaders trailingHeaders;
    private final boolean validateHeaders;

    public DefaultFullHttpResponse(HttpVersion version, HttpResponseStatus status) {
        this(version, status, Unpooled.buffer(0));
    }

    public DefaultFullHttpResponse(HttpVersion version, HttpResponseStatus status, ByteBuf content) {
        this(version, status, content, true);
    }

    public DefaultFullHttpResponse(HttpVersion version, HttpResponseStatus status, boolean validateHeaders) {
        this(version, status, Unpooled.buffer(0), validateHeaders);
    }

    public DefaultFullHttpResponse(HttpVersion version, HttpResponseStatus status,
                                   ByteBuf content, boolean validateHeaders) {
        super(version, status, validateHeaders);
        if (content == null) {
            throw new NullPointerException("content");
        }
        this.content = content;
        trailingHeaders = new DefaultHttpHeaders(validateHeaders);
        this.validateHeaders = validateHeaders;
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

    /**
     * Copy this object
     *
     * @param copyContent
     * <ul>
     * <li>{@code true} if this object's {@link #content()} should be used to copy.</li>
     * <li>{@code false} if {@code newContent} should be used instead.</li>
     * </ul>
     * @param newContent
     * <ul>
     * <li>if {@code copyContent} is false then this will be used in the copy's content.</li>
     * <li>if {@code null} then a default buffer of 0 size will be selected</li>
     * </ul>
     * @return A copy of this object
     */
    private FullHttpResponse copy(boolean copyContent, ByteBuf newContent) {
        DefaultFullHttpResponse copy = new DefaultFullHttpResponse(
                protocolVersion(), status(),
                copyContent ? content().copy() :
                    newContent == null ? Unpooled.buffer(0) : newContent);
        copy.headers().set(headers());
        copy.trailingHeaders().set(trailingHeaders());
        return copy;
    }

    @Override
    public FullHttpResponse copy(ByteBuf newContent) {
        return copy(false, newContent);
    }

    @Override
    public FullHttpResponse copy() {
        return copy(true, null);
    }

    @Override
    public FullHttpResponse duplicate() {
        DefaultFullHttpResponse duplicate = new DefaultFullHttpResponse(protocolVersion(), status(),
                content().duplicate(), validateHeaders);
        duplicate.headers().set(headers());
        duplicate.trailingHeaders().set(trailingHeaders());
        return duplicate;
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = HASH_CODE_PRIME * result + content().hashCode();
        result = HASH_CODE_PRIME * result + trailingHeaders().hashCode();
        result = HASH_CODE_PRIME * result + super.hashCode();
        return result;
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
        StringBuilder buf = new StringBuilder();
        appendAll(buf);
        buf.append(StringUtil.NEWLINE);
        appendHeaders(buf, trailingHeaders());

        // Remove the last newline.
        buf.setLength(buf.length() - StringUtil.NEWLINE.length());
        return buf.toString();
    }
}
