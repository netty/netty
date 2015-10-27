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
package io.netty.handler.codec.spdy;

import io.netty.handler.codec.CharSequenceValueConverter;
import io.netty.handler.codec.DefaultHeaders;
import io.netty.handler.codec.HeadersUtils;

import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import static io.netty.util.AsciiString.CASE_INSENSITIVE_HASHER;
import static io.netty.util.AsciiString.CASE_SENSITIVE_HASHER;

public class DefaultSpdyHeaders extends DefaultHeaders<CharSequence, CharSequence, SpdyHeaders> implements SpdyHeaders {
    private static final NameValidator<CharSequence> SpydNameValidator = new NameValidator<CharSequence>() {
        @Override
        public void validateName(CharSequence name) {
            SpdyCodecUtil.validateHeaderName(name);
        }
    };

    public DefaultSpdyHeaders() {
        this(true);
    }

    @SuppressWarnings("unchecked")
    public DefaultSpdyHeaders(boolean validate) {
        super(CASE_INSENSITIVE_HASHER,
                validate ? HeaderValueConverterAndValidator.INSTANCE : CharSequenceValueConverter.INSTANCE,
                validate ? SpydNameValidator : NameValidator.NOT_NULL);
    }

    @Override
    public String getAsString(CharSequence name) {
        return HeadersUtils.getAsString(this, name);
    }

    @Override
    public List<String> getAllAsString(CharSequence name) {
        return HeadersUtils.getAllAsString(this, name);
    }

    @Override
    public Iterator<Entry<String, String>> iteratorAsString() {
        return HeadersUtils.iteratorAsString(this);
    }

    @Override
    public boolean contains(CharSequence name, CharSequence value) {
        return contains(name, value, false);
    }

    @Override
    public boolean contains(CharSequence name, CharSequence value, boolean ignoreCase) {
        return contains(name, value,
                ignoreCase ? CASE_INSENSITIVE_HASHER : CASE_SENSITIVE_HASHER);
    }

    private static final class HeaderValueConverterAndValidator extends CharSequenceValueConverter {
        public static final HeaderValueConverterAndValidator INSTANCE = new HeaderValueConverterAndValidator();

        @Override
        public CharSequence convertObject(Object value) {
            final CharSequence seq = super.convertObject(value);
            SpdyCodecUtil.validateHeaderValue(seq);
            return seq;
        }
    }
}
