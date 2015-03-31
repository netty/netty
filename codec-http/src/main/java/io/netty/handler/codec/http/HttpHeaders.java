/*
 * Copyright 2012 The Netty Project
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

import io.netty.handler.codec.TextHeaders;

/**
 * Provides the constants for the standard HTTP header names and values and
 * commonly used utility methods that accesses an {@link HttpMessage}.
 */
public interface HttpHeaders extends TextHeaders {
    @Override
    HttpHeaders add(CharSequence name, CharSequence value);

    @Override
    HttpHeaders add(CharSequence name, Iterable<? extends CharSequence> values);

    @Override
    HttpHeaders add(CharSequence name, CharSequence... values);

    @Override
    HttpHeaders addObject(CharSequence name, Object value);

    @Override
    HttpHeaders addObject(CharSequence name, Iterable<?> values);

    @Override
    HttpHeaders addObject(CharSequence name, Object... values);

    @Override
    HttpHeaders addBoolean(CharSequence name, boolean value);

    @Override
    HttpHeaders addByte(CharSequence name, byte value);

    @Override
    HttpHeaders addChar(CharSequence name, char value);

    @Override
    HttpHeaders addShort(CharSequence name, short value);

    @Override
    HttpHeaders addInt(CharSequence name, int value);

    @Override
    HttpHeaders addLong(CharSequence name, long value);

    @Override
    HttpHeaders addFloat(CharSequence name, float value);

    @Override
    HttpHeaders addDouble(CharSequence name, double value);

    @Override
    HttpHeaders addTimeMillis(CharSequence name, long value);

    @Override
    HttpHeaders add(TextHeaders headers);

    @Override
    HttpHeaders set(CharSequence name, CharSequence value);

    @Override
    HttpHeaders set(CharSequence name, Iterable<? extends CharSequence> values);

    @Override
    HttpHeaders set(CharSequence name, CharSequence... values);

    @Override
    HttpHeaders setObject(CharSequence name, Object value);

    @Override
    HttpHeaders setObject(CharSequence name, Iterable<?> values);

    @Override
    HttpHeaders setObject(CharSequence name, Object... values);

    @Override
    HttpHeaders setBoolean(CharSequence name, boolean value);

    @Override
    HttpHeaders setByte(CharSequence name, byte value);

    @Override
    HttpHeaders setChar(CharSequence name, char value);

    @Override
    HttpHeaders setShort(CharSequence name, short value);

    @Override
    HttpHeaders setInt(CharSequence name, int value);

    @Override
    HttpHeaders setLong(CharSequence name, long value);

    @Override
    HttpHeaders setFloat(CharSequence name, float value);

    @Override
    HttpHeaders setDouble(CharSequence name, double value);

    @Override
    HttpHeaders setTimeMillis(CharSequence name, long value);

    @Override
    HttpHeaders set(TextHeaders headers);

    @Override
    HttpHeaders setAll(TextHeaders headers);

    @Override
    HttpHeaders clear();
}
