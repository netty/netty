/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DefaultHeaders;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.internal.StringUtil;

import java.util.Map;

/**
 * {@link DefaultHttp2TranslatedHttpContent} contains {@code streamId}
 * from last (endOfStream) {@link Http2DataFrame}.
 */
public class DefaultHttp2TranslatedLastHttpContent extends DefaultHttp2TranslatedHttpContent
        implements LastHttpContent {

    private final HttpHeaders trailingHeaders;
    private final boolean validateHeaders;

    /***
     * Creates a new instance with the specified chunk content.
     * @param streamId Stream ID of HTTP/2 Data Frame
     */
    public DefaultHttp2TranslatedLastHttpContent(ByteBuf content, int streamId, boolean validateHeaders) {
        super(content, streamId);
        this.validateHeaders = validateHeaders;
        trailingHeaders = new TrailingHttpHeaders(validateHeaders);
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
        DefaultHttp2TranslatedLastHttpContent lastHttpContent = new DefaultHttp2TranslatedLastHttpContent(content,
                streamId(), validateHeaders);
        lastHttpContent.trailingHeaders().set(trailingHeaders());
        return lastHttpContent;
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
        for (Map.Entry<String, String> e : trailingHeaders()) {
            buf.append(e.getKey());
            buf.append(": ");
            buf.append(e.getValue());
            buf.append(StringUtil.NEWLINE);
        }
    }

    private static final class TrailingHttpHeaders extends DefaultHttpHeaders {
        private static final DefaultHeaders.NameValidator<CharSequence> TrailerNameValidator =
                new DefaultHeaders.NameValidator<CharSequence>() {
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

        @SuppressWarnings("unchecked")
        TrailingHttpHeaders(boolean validate) {
            super(validate, validate ? TrailerNameValidator : DefaultHeaders.NameValidator.NOT_NULL);
        }
    }
}
