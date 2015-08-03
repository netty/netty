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

package io.netty.handler.codec;

/**
 * A typical string multimap used by text protocols such as HTTP for the representation of arbitrary key-value data. One
 * thing to note is that it uses {@link CharSequence} as its primary key and value type rather than {@link String}. When
 * you invoke the operations that produce {@link String}s such as {@link #get(Object)}, a {@link CharSequence} is
 * implicitly converted to a {@link String}. This is particularly useful for speed optimization because this multimap
 * can hold a special {@link CharSequence} implementation that a codec can treat specially, such as {@link CharSequence}
 * .
 */
public interface TextHeaders extends ConvertibleHeaders<CharSequence, String> {

    /**
     * Returns {@code true} if a header with the name and value exists.
     * @param name the header name
     * @param value the header value
     * @return {@code true} if it contains it {@code false} otherwise
     */
    boolean contains(CharSequence name, CharSequence value, boolean ignoreCase);

    @Override
    TextHeaders add(CharSequence name, CharSequence value);

    @Override
    TextHeaders add(CharSequence name, Iterable<? extends CharSequence> values);

    @Override
    TextHeaders add(CharSequence name, CharSequence... values);

    @Override
    TextHeaders addObject(CharSequence name, Object value);

    @Override
    TextHeaders addObject(CharSequence name, Iterable<?> values);

    @Override
    TextHeaders addObject(CharSequence name, Object... values);

    @Override
    TextHeaders addBoolean(CharSequence name, boolean value);

    @Override
    TextHeaders addByte(CharSequence name, byte value);

    @Override
    TextHeaders addChar(CharSequence name, char value);

    @Override
    TextHeaders addShort(CharSequence name, short value);

    @Override
    TextHeaders addInt(CharSequence name, int value);

    @Override
    TextHeaders addLong(CharSequence name, long value);

    @Override
    TextHeaders addFloat(CharSequence name, float value);

    @Override
    TextHeaders addDouble(CharSequence name, double value);

    @Override
    TextHeaders addTimeMillis(CharSequence name, long value);

    /**
     * See {@link Headers#add(Headers)}
     */
    TextHeaders add(TextHeaders headers);

    @Override
    TextHeaders set(CharSequence name, CharSequence value);

    @Override
    TextHeaders set(CharSequence name, Iterable<? extends CharSequence> values);

    @Override
    TextHeaders set(CharSequence name, CharSequence... values);

    @Override
    TextHeaders setObject(CharSequence name, Object value);

    @Override
    TextHeaders setObject(CharSequence name, Iterable<?> values);

    @Override
    TextHeaders setObject(CharSequence name, Object... values);

    @Override
    TextHeaders setBoolean(CharSequence name, boolean value);

    @Override
    TextHeaders setByte(CharSequence name, byte value);

    @Override
    TextHeaders setChar(CharSequence name, char value);

    @Override
    TextHeaders setShort(CharSequence name, short value);

    @Override
    TextHeaders setInt(CharSequence name, int value);

    @Override
    TextHeaders setLong(CharSequence name, long value);

    @Override
    TextHeaders setFloat(CharSequence name, float value);

    @Override
    TextHeaders setDouble(CharSequence name, double value);

    @Override
    TextHeaders setTimeMillis(CharSequence name, long value);

    /**
     * See {@link Headers#set(Headers)}
     */
    TextHeaders set(TextHeaders headers);

    /**
     * See {@link Headers#setAll(Headers)}
     */
    TextHeaders setAll(TextHeaders headers);

    @Override
    TextHeaders clear();
}
