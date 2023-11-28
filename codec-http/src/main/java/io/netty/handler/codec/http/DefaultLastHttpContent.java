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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.internal.StringUtil;

import java.util.Map.Entry;

import static io.netty.handler.codec.http.DefaultHttpHeadersFactory.trailersFactory;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * The default {@link LastHttpContent} implementation.
 */
public class DefaultLastHttpContent extends DefaultHttpContent implements LastHttpContent {
    private final HttpHeaders trailingHeaders;

    /**
     * Create a new empty, last HTTP content message.
     */
    public DefaultLastHttpContent() {
        this(Unpooled.buffer(0));
    }

    /**
     * Create a new last HTTP content message with the given contents.
     */
    public DefaultLastHttpContent(ByteBuf content) {
        this(content, trailersFactory());
    }

    /**
     * Create a new last HTTP content message with the given contents, and optional trailing header validation.
     * <p>
     * <b>Warning!</b> Setting {@code validateHeaders} to {@code false} will mean that Netty won't
     * validate & protect against user-supplied header values that are malicious.
     * This can leave your server implementation vulnerable to
     * <a href="https://cwe.mitre.org/data/definitions/113.html">
     *     CWE-113: Improper Neutralization of CRLF Sequences in HTTP Headers ('HTTP Response Splitting')
     * </a>.
     * When disabling this validation, it is the responsibility of the caller to ensure that the values supplied
     * do not contain a non-url-escaped carriage return (CR) and/or line feed (LF) characters.
     *
     * @deprecated Prefer the {@link #DefaultLastHttpContent(ByteBuf)} constructor instead, to always have header
     * validation enabled.
     */
    @Deprecated
    public DefaultLastHttpContent(ByteBuf content, boolean validateHeaders) {
        this(content, trailersFactory().withValidation(validateHeaders));
    }

    /**
     * Create a new last HTTP content message with the given contents, and trailing headers from the given factory.
     */
    public DefaultLastHttpContent(ByteBuf content, HttpHeadersFactory trailersFactory) {
        super(content);
        trailingHeaders = trailersFactory.newHeaders();
    }

    /**
     * Create a new last HTTP content message with the given contents, and trailing headers.
     */
    public DefaultLastHttpContent(ByteBuf content, HttpHeaders trailingHeaders) {
        super(content);
        this.trailingHeaders = checkNotNull(trailingHeaders, "trailingHeaders");
    }

    @Override
    public LastHttpContent copy() {
        return replace(content().copy());
    }

    @Override
    public LastHttpContent duplicate() {
        return replace(content().duplicate());
    }

    @Override
    public LastHttpContent retainedDuplicate() {
        return replace(content().retainedDuplicate());
    }

    @Override
    public LastHttpContent replace(ByteBuf content) {
        return new DefaultLastHttpContent(content, trailingHeaders().copy());
    }

    @Override
    public LastHttpContent retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public LastHttpContent retain() {
        super.retain();
        return this;
    }

    @Override
    public LastHttpContent touch() {
        super.touch();
        return this;
    }

    @Override
    public LastHttpContent touch(Object hint) {
        super.touch(hint);
        return this;
    }

    @Override
    public HttpHeaders trailingHeaders() {
        return trailingHeaders;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(super.toString());
        buf.append(StringUtil.NEWLINE);
        appendHeaders(buf);

        // Remove the last newline.
        buf.setLength(buf.length() - StringUtil.NEWLINE.length());
        return buf.toString();
    }

    private void appendHeaders(StringBuilder buf) {
        for (Entry<String, String> e : trailingHeaders()) {
            buf.append(e.getKey());
            buf.append(": ");
            buf.append(e.getValue());
            buf.append(StringUtil.NEWLINE);
        }
    }
}
