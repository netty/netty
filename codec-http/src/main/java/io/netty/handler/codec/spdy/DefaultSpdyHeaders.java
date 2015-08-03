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

import io.netty.handler.codec.DefaultTextHeaders;
import io.netty.handler.codec.TextHeaders;

import java.util.LinkedHashMap;

public class DefaultSpdyHeaders extends DefaultTextHeaders implements SpdyHeaders {

    public DefaultSpdyHeaders() {
        super(new LinkedHashMap<CharSequence, Object>(),
              HeaderNameValidator.INSTANCE,
              HeaderValueConverterAndValidator.INSTANCE,
              false);
    }

    @Override
    public SpdyHeaders add(CharSequence name, CharSequence value) {
        super.add(name, value);
        return this;
    }

    @Override
    public SpdyHeaders add(CharSequence name, Iterable<? extends CharSequence> values) {
        super.add(name, values);
        return this;
    }

    @Override
    public SpdyHeaders add(CharSequence name, CharSequence... values) {
        super.add(name, values);
        return this;
    }

    @Override
    public SpdyHeaders addObject(CharSequence name, Object value) {
        super.addObject(name, value);
        return this;
    }

    @Override
    public SpdyHeaders addObject(CharSequence name, Iterable<?> values) {
        super.addObject(name, values);
        return this;
    }

    @Override
    public SpdyHeaders addObject(CharSequence name, Object... values) {
        super.addObject(name, values);
        return this;
    }

    @Override
    public SpdyHeaders addBoolean(CharSequence name, boolean value) {
        super.addBoolean(name, value);
        return this;
    }

    @Override
    public SpdyHeaders addChar(CharSequence name, char value) {
        super.addChar(name, value);
        return this;
    }

    @Override
    public SpdyHeaders addByte(CharSequence name, byte value) {
        super.addByte(name, value);
        return this;
    }

    @Override
    public SpdyHeaders addShort(CharSequence name, short value) {
        super.addShort(name, value);
        return this;
    }

    @Override
    public SpdyHeaders addInt(CharSequence name, int value) {
        super.addInt(name, value);
        return this;
    }

    @Override
    public SpdyHeaders addLong(CharSequence name, long value) {
        super.addLong(name, value);
        return this;
    }

    @Override
    public SpdyHeaders addFloat(CharSequence name, float value) {
        super.addFloat(name, value);
        return this;
    }

    @Override
    public SpdyHeaders addDouble(CharSequence name, double value) {
        super.addDouble(name, value);
        return this;
    }

    @Override
    public SpdyHeaders addTimeMillis(CharSequence name, long value) {
        super.addTimeMillis(name, value);
        return this;
    }

    @Override
    public SpdyHeaders add(TextHeaders headers) {
        super.add(headers);
        return this;
    }

    @Override
    public SpdyHeaders set(CharSequence name, CharSequence value) {
        super.set(name, value);
        return this;
    }

    @Override
    public SpdyHeaders set(CharSequence name, Iterable<? extends CharSequence> values) {
        super.set(name, values);
        return this;
    }

    @Override
    public SpdyHeaders set(CharSequence name, CharSequence... values) {
        super.set(name, values);
        return this;
    }

    @Override
    public SpdyHeaders setObject(CharSequence name, Object value) {
        super.setObject(name, value);
        return this;
    }

    @Override
    public SpdyHeaders setObject(CharSequence name, Iterable<?> values) {
        super.setObject(name, values);
        return this;
    }

    @Override
    public SpdyHeaders setObject(CharSequence name, Object... values) {
        super.setObject(name, values);
        return this;
    }

    @Override
    public SpdyHeaders setBoolean(CharSequence name, boolean value) {
        super.setBoolean(name, value);
        return this;
    }

    @Override
    public SpdyHeaders setChar(CharSequence name, char value) {
        super.setChar(name, value);
        return this;
    }

    @Override
    public SpdyHeaders setByte(CharSequence name, byte value) {
        super.setByte(name, value);
        return this;
    }

    @Override
    public SpdyHeaders setShort(CharSequence name, short value) {
        super.setShort(name, value);
        return this;
    }

    @Override
    public SpdyHeaders setInt(CharSequence name, int value) {
        super.setInt(name, value);
        return this;
    }

    @Override
    public SpdyHeaders setLong(CharSequence name, long value) {
        super.setLong(name, value);
        return this;
    }

    @Override
    public SpdyHeaders setFloat(CharSequence name, float value) {
        super.setFloat(name, value);
        return this;
    }

    @Override
    public SpdyHeaders setDouble(CharSequence name, double value) {
        super.setDouble(name, value);
        return this;
    }

    @Override
    public SpdyHeaders setTimeMillis(CharSequence name, long value) {
        super.setTimeMillis(name, value);
        return this;
    }

    @Override
    public SpdyHeaders set(TextHeaders headers) {
        super.set(headers);
        return this;
    }

    @Override
    public SpdyHeaders setAll(TextHeaders headers) {
        super.setAll(headers);
        return this;
    }

    @Override
    public SpdyHeaders clear() {
        super.clear();
        return this;
    }

    private static class HeaderNameValidator implements NameValidator<CharSequence> {

        public static final HeaderNameValidator INSTANCE = new HeaderNameValidator();

        @Override
        public void validate(CharSequence name) {
            SpdyCodecUtil.validateHeaderName(name);
        }
    }

    private static class HeaderValueConverterAndValidator extends CharSequenceConverter {

        public static final HeaderValueConverterAndValidator INSTANCE = new HeaderValueConverterAndValidator();

        @Override
        public CharSequence convertObject(Object value) {
            CharSequence seq;
            if (value instanceof CharSequence) {
                seq = (CharSequence) value;
            } else {
                seq = value.toString();
            }

            SpdyCodecUtil.validateHeaderValue(seq);
            return seq;
        }
    }
}
