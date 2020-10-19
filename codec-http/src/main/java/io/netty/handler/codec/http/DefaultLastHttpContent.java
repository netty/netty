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
import io.netty.handler.codec.DefaultHeaders.NameValidator;
import io.netty.util.internal.StringUtil;

import java.util.Map.Entry;

/**
 * The default {@link LastHttpContent} implementation.
 */
public class DefaultLastHttpContent extends DefaultHttpContent implements LastHttpContent {
    private final HttpHeaders trailingHeaders;
    private final boolean validateHeaders;

    public DefaultLastHttpContent() {
        this(Unpooled.buffer(0));
    }

    public DefaultLastHttpContent(ByteBuf content) {
        this(content, true);
    }

    public DefaultLastHttpContent(ByteBuf content, boolean validateHeaders) {
        super(content);
        trailingHeaders = new TrailingHttpHeaders(validateHeaders);
        this.validateHeaders = validateHeaders;
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
        final DefaultLastHttpContent dup = new DefaultLastHttpContent(content, validateHeaders);
        dup.trailingHeaders().set(trailingHeaders());
        return dup;
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

    private static final class TrailingHttpHeaders extends DefaultHttpHeaders {
        private static final NameValidator<CharSequence> TrailerNameValidator = new NameValidator<CharSequence>() {
            @Override
            public void validateName(CharSequence name) {
                DefaultHttpHeaders.HttpNameValidator.validateName(name);
                if (HttpHeaderNames.CONTENT_LENGTH.contentEqualsIgnoreCase(name)
                        || HttpHeaderNames.TRANSFER_ENCODING.contentEqualsIgnoreCase(name)
                        || HttpHeaderNames.TRAILER.contentEqualsIgnoreCase(name)) {
                    throw new IllegalArgumentException("prohibited trailing header: " + name);
                }
            }
        };

        @SuppressWarnings({ "unchecked" })
        TrailingHttpHeaders(boolean validate) {
            super(validate, validate ? TrailerNameValidator : NameValidator.NOT_NULL);
        }
    }
}
