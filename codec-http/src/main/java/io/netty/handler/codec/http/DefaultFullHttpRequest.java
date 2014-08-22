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
 * Default implementation of {@link FullHttpRequest}.
 */
public class DefaultFullHttpRequest extends DefaultHttpRequest implements FullHttpRequest {
    private static final int HASH_CODE_PRIME = 31;
    private final ByteBuf content;
    private final HttpHeaders trailingHeader;
    private final boolean validateHeaders;

    public DefaultFullHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri) {
        this(httpVersion, method, uri, Unpooled.buffer(0));
    }

    public DefaultFullHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri, ByteBuf content) {
        this(httpVersion, method, uri, content, true);
    }

    public DefaultFullHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri, boolean validateHeaders) {
        this(httpVersion, method, uri, Unpooled.buffer(0), true);
    }

    public DefaultFullHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri,
                                  ByteBuf content, boolean validateHeaders) {
        super(httpVersion, method, uri, validateHeaders);
        if (content == null) {
            throw new NullPointerException("content");
        }
        this.content = content;
        trailingHeader = new DefaultHttpHeaders(validateHeaders);
        this.validateHeaders = validateHeaders;
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
    private FullHttpRequest copy(boolean copyContent, ByteBuf newContent) {
        DefaultFullHttpRequest copy = new DefaultFullHttpRequest(
                protocolVersion(), method(), uri(),
                copyContent ? content().copy() :
                    newContent == null ? Unpooled.buffer(0) : newContent);
        copy.headers().set(headers());
        copy.trailingHeaders().set(trailingHeaders());
        return copy;
    }

    @Override
    public FullHttpRequest copy(ByteBuf newContent) {
        return copy(false, newContent);
    }

    @Override
    public FullHttpRequest copy() {
        return copy(true, null);
    }

    @Override
    public FullHttpRequest duplicate() {
        DefaultFullHttpRequest duplicate = new DefaultFullHttpRequest(
                protocolVersion(), method(), uri(), content().duplicate(), validateHeaders);
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
        StringBuilder buf = new StringBuilder();
        appendAll(buf);
        buf.append(StringUtil.NEWLINE);
        appendHeaders(buf, trailingHeaders());

        // Remove the last newline.
        buf.setLength(buf.length() - StringUtil.NEWLINE.length());
        return buf.toString();
    }
}
