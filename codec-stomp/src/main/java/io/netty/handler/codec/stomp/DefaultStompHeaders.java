/*
 * Copyright 2014 The Netty Project
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

package io.netty.handler.codec.stomp;

import io.netty.handler.codec.CharSequenceValueConverter;
import io.netty.handler.codec.DefaultHeaders;
import io.netty.handler.codec.Headers;
import io.netty.handler.codec.HeadersUtils;

import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import static io.netty.util.AsciiString.CASE_INSENSITIVE_HASHER;
import static io.netty.util.AsciiString.CASE_SENSITIVE_HASHER;

public class DefaultStompHeaders extends DefaultHeaders<CharSequence> implements StompHeaders {
    public DefaultStompHeaders() {
        super(CharSequenceValueConverter.INSTANCE);
    }

    @Override
    public StompHeaders add(CharSequence name, CharSequence value) {
        super.add(name, value);
        return this;
    }

    @Override
    public StompHeaders add(CharSequence name, Iterable<? extends CharSequence> values) {
        super.add(name, values);
        return this;
    }

    @Override
    public StompHeaders add(CharSequence name, CharSequence... values) {
        super.add(name, values);
        return this;
    }

    @Override
    public StompHeaders addObject(CharSequence name, Object value) {
        super.addObject(name, value);
        return this;
    }

    @Override
    public StompHeaders addObject(CharSequence name, Iterable<?> values) {
        super.addObject(name, values);
        return this;
    }

    @Override
    public StompHeaders addObject(CharSequence name, Object... values) {
        super.addObject(name, values);
        return this;
    }

    @Override
    public StompHeaders addBoolean(CharSequence name, boolean value) {
        super.addBoolean(name, value);
        return this;
    }

    @Override
    public StompHeaders addChar(CharSequence name, char value) {
        super.addChar(name, value);
        return this;
    }

    @Override
    public StompHeaders addByte(CharSequence name, byte value) {
        super.addByte(name, value);
        return this;
    }

    @Override
    public StompHeaders addShort(CharSequence name, short value) {
        super.addShort(name, value);
        return this;
    }

    @Override
    public StompHeaders addInt(CharSequence name, int value) {
        super.addInt(name, value);
        return this;
    }

    @Override
    public StompHeaders addLong(CharSequence name, long value) {
        super.addLong(name, value);
        return this;
    }

    @Override
    public StompHeaders addFloat(CharSequence name, float value) {
        super.addFloat(name, value);
        return this;
    }

    @Override
    public StompHeaders addDouble(CharSequence name, double value) {
        super.addDouble(name, value);
        return this;
    }

    @Override
    public StompHeaders addTimeMillis(CharSequence name, long value) {
        super.addTimeMillis(name, value);
        return this;
    }

    @Override
    public StompHeaders add(Headers<? extends CharSequence> headers) {
        super.add(headers);
        return this;
    }

    @Override
    public StompHeaders set(CharSequence name, CharSequence value) {
        super.set(name, value);
        return this;
    }

    @Override
    public StompHeaders set(CharSequence name, Iterable<? extends CharSequence> values) {
        super.set(name, values);
        return this;
    }

    @Override
    public StompHeaders set(CharSequence name, CharSequence... values) {
        super.set(name, values);
        return this;
    }

    @Override
    public StompHeaders setObject(CharSequence name, Object value) {
        super.setObject(name, value);
        return this;
    }

    @Override
    public StompHeaders setObject(CharSequence name, Iterable<?> values) {
        super.setObject(name, values);
        return this;
    }

    @Override
    public StompHeaders setObject(CharSequence name, Object... values) {
        super.setObject(name, values);
        return this;
    }

    @Override
    public StompHeaders setBoolean(CharSequence name, boolean value) {
        super.setBoolean(name, value);
        return this;
    }

    @Override
    public StompHeaders setChar(CharSequence name, char value) {
        super.setChar(name, value);
        return this;
    }

    @Override
    public StompHeaders setByte(CharSequence name, byte value) {
        super.setByte(name, value);
        return this;
    }

    @Override
    public StompHeaders setShort(CharSequence name, short value) {
        super.setShort(name, value);
        return this;
    }

    @Override
    public StompHeaders setInt(CharSequence name, int value) {
        super.setInt(name, value);
        return this;
    }

    @Override
    public StompHeaders setLong(CharSequence name, long value) {
        super.setLong(name, value);
        return this;
    }

    @Override
    public StompHeaders setFloat(CharSequence name, float value) {
        super.setFloat(name, value);
        return this;
    }

    @Override
    public StompHeaders setDouble(CharSequence name, double value) {
        super.setDouble(name, value);
        return this;
    }

    @Override
    public StompHeaders setTimeMillis(CharSequence name, long value) {
        super.setTimeMillis(name, value);
        return this;
    }

    @Override
    public StompHeaders set(Headers<? extends CharSequence> headers) {
        super.set(headers);
        return this;
    }

    @Override
    public StompHeaders setAll(Headers<? extends CharSequence> headers) {
        super.setAll(headers);
        return this;
    }

    @Override
    public StompHeaders clear() {
        super.clear();
        return this;
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
}
