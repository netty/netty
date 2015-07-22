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

import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import io.netty.handler.codec.Headers;

/**
 * Provides the constants for the standard HTTP header names and values and
 * commonly used utility methods that accesses an {@link HttpMessage}.
 */
public interface HttpHeaders extends Headers<CharSequence> {
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
    HttpHeaders add(Headers<? extends CharSequence> headers);

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
    HttpHeaders set(Headers<? extends CharSequence> headers);

    @Override
    HttpHeaders setAll(Headers<? extends CharSequence> headers);

    @Override
    HttpHeaders clear();

    /**
     * {@link Headers#get(Object)} and convert the result to a {@link String}.
     * @param name the name of the header to retrieve
     * @return the first header value if the header is found. {@code null} if there's no such header.
     */
    String getAsString(CharSequence name);

    /**
     * {@link Headers#getAll(Object)} and convert each element of {@link List} to a {@link String}.
     * @param name the name of the header to retrieve
     * @return a {@link List} of header values or an empty {@link List} if no values are found.
     */
    List<String> getAllAsString(CharSequence name);

    /**
     * {@link Iterator} that converts each {@link Entry}'s key and value to a {@link String}.
     */
    Iterator<Entry<String, String>> iteratorAsString();

    /**
     * Returns {@code true} if a header with the {@code name} and {@code value} exists, {@code false} otherwise.
     * <p>
     * If {@code ignoreCase} is {@code true} then a case insensitive compare is done on the value.
     * @param name the name of the header to find
     * @param value the value of the header to find
     * @param ignoreCase {@code true} then a case insensitive compare is run to compare values.
     * otherwise a case sensitive compare is run to compare values.
     */
    boolean contains(CharSequence name, CharSequence value, boolean ignoreCase);
}
