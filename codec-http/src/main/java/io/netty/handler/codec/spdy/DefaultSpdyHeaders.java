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

import io.netty.handler.codec.AsciiString;
import io.netty.handler.codec.DefaultTextHeaders;
import io.netty.handler.codec.TextHeaderProcessor;
import io.netty.handler.codec.TextHeaders;

import java.util.Locale;


public class DefaultSpdyHeaders extends DefaultTextHeaders implements SpdyHeaders {
    @Override
    protected CharSequence convertName(CharSequence name) {
        name = super.convertName(name);
        if (name instanceof AsciiString) {
            name = ((AsciiString) name).toLowerCase();
        } else {
            name = name.toString().toLowerCase(Locale.US);
        }
        SpdyCodecUtil.validateHeaderName(name);
        return name;
    }

    @Override
    protected CharSequence convertValue(Object value) {
        if (value == null) {
            throw new NullPointerException("value");
        }

        CharSequence seq;
        if (value instanceof CharSequence) {
            seq = (CharSequence) value;
        } else {
            seq = value.toString();
        }

        SpdyCodecUtil.validateHeaderValue(seq);
        return seq;
    }

    @Override
    public SpdyHeaders add(CharSequence name, Object value) {
        super.add(name, value);
        return this;
    }

    @Override
    public SpdyHeaders add(CharSequence name, Iterable<?> values) {
        super.add(name, values);
        return this;
    }

    @Override
    public SpdyHeaders add(CharSequence name, Object... values) {
        super.add(name, values);
        return this;
    }

    @Override
    public SpdyHeaders add(TextHeaders headers) {
        super.add(headers);
        return this;
    }

    @Override
    public SpdyHeaders set(CharSequence name, Object value) {
        super.set(name, value);
        return this;
    }

    @Override
    public SpdyHeaders set(CharSequence name, Object... values) {
        super.set(name, values);
        return this;
    }

    @Override
    public SpdyHeaders set(CharSequence name, Iterable<?> values) {
        super.set(name, values);
        return this;
    }

    @Override
    public SpdyHeaders set(TextHeaders headers) {
        super.set(headers);
        return this;
    }

    @Override
    public SpdyHeaders clear() {
        super.clear();
        return this;
    }

    @Override
    public SpdyHeaders forEachEntry(TextHeaderProcessor processor) {
        super.forEachEntry(processor);
        return this;
    }
}
