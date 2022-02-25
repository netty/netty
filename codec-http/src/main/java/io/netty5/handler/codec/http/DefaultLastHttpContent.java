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
package io.netty5.handler.codec.http;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.Send;
import io.netty5.handler.codec.DefaultHeaders.NameValidator;
import io.netty5.util.internal.StringUtil;

import java.util.Map.Entry;

import static java.util.Objects.requireNonNull;

/**
 * The default {@link LastHttpContent} implementation.
 */
public class DefaultLastHttpContent extends DefaultHttpObject implements LastHttpContent<DefaultLastHttpContent> {
    private final HttpHeaders trailingHeaders;
    private final Buffer payload;

    public DefaultLastHttpContent(Buffer payload) {
        this(payload, true);
    }

    public DefaultLastHttpContent(Buffer payload, boolean validateHeaders) {
        this(payload, new TrailingHttpHeaders(validateHeaders));
    }

    public DefaultLastHttpContent(Buffer payload, HttpHeaders trailingHeaders) {
        this.payload = requireNonNull(payload, "payload");
        this.trailingHeaders = requireNonNull(trailingHeaders, "trailingHeaders");
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

    @Override
    public Send<DefaultLastHttpContent> send() {
        return payload.send().map(DefaultLastHttpContent.class,
                payload -> new DefaultLastHttpContent(payload, trailingHeaders));
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
    public DefaultLastHttpContent touch(Object hint) {
        payload.touch(hint);
        return this;
    }

    @Override
    public Buffer payload() {
        return payload;
    }

    private static final class TrailingHttpHeaders extends DefaultHttpHeaders {
        private static final NameValidator<CharSequence> TrailerNameValidator = name -> {
            DefaultHttpHeaders.HttpNameValidator.validateName(name);
            if (HttpHeaderNames.CONTENT_LENGTH.contentEqualsIgnoreCase(name)
                    || HttpHeaderNames.TRANSFER_ENCODING.contentEqualsIgnoreCase(name)
                    || HttpHeaderNames.TRAILER.contentEqualsIgnoreCase(name)) {
                throw new IllegalArgumentException("prohibited trailing header: " + name);
            }
        };

        @SuppressWarnings({ "unchecked" })
        TrailingHttpHeaders(boolean validate) {
            super(validate, validate ? TrailerNameValidator : NameValidator.NOT_NULL);
        }
    }
}
