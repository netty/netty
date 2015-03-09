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
 * A typical {@code AsciiString} multimap used by protocols that use binary headers (such as HTTP/2) for the
 * representation of arbitrary key-value data. {@link AsciiString} is just a wrapper around a byte array but provides
 * some additional utility when handling text data.
 */
public interface BinaryHeaders extends Headers<AsciiString> {
    /**
     * A visitor that helps reduce GC pressure while iterating over a collection of {@link Headers}.
     */
    interface EntryVisitor extends Headers.EntryVisitor<AsciiString> {
    }

    /**
     * A visitor that helps reduce GC pressure while iterating over a collection of {@link Headers}.
     */
    interface NameVisitor extends Headers.NameVisitor<AsciiString> {
    }

    @Override
    BinaryHeaders add(AsciiString name, AsciiString value);

    @Override
    BinaryHeaders add(AsciiString name, Iterable<? extends AsciiString> values);

    @Override
    BinaryHeaders add(AsciiString name, AsciiString... values);

    @Override
    BinaryHeaders addObject(AsciiString name, Object value);

    @Override
    BinaryHeaders addObject(AsciiString name, Iterable<?> values);

    @Override
    BinaryHeaders addObject(AsciiString name, Object... values);

    @Override
    BinaryHeaders addBoolean(AsciiString name, boolean value);

    @Override
    BinaryHeaders addByte(AsciiString name, byte value);

    @Override
    BinaryHeaders addChar(AsciiString name, char value);

    @Override
    BinaryHeaders addShort(AsciiString name, short value);

    @Override
    BinaryHeaders addInt(AsciiString name, int value);

    @Override
    BinaryHeaders addLong(AsciiString name, long value);

    @Override
    BinaryHeaders addFloat(AsciiString name, float value);

    @Override
    BinaryHeaders addDouble(AsciiString name, double value);

    @Override
    BinaryHeaders addTimeMillis(AsciiString name, long value);

    /**
     * See {@link Headers#add(Headers)}
     */
    BinaryHeaders add(BinaryHeaders headers);

    @Override
    BinaryHeaders set(AsciiString name, AsciiString value);

    @Override
    BinaryHeaders set(AsciiString name, Iterable<? extends AsciiString> values);

    @Override
    BinaryHeaders set(AsciiString name, AsciiString... values);

    @Override
    BinaryHeaders setObject(AsciiString name, Object value);

    @Override
    BinaryHeaders setObject(AsciiString name, Iterable<?> values);

    @Override
    BinaryHeaders setObject(AsciiString name, Object... values);

    @Override
    BinaryHeaders setBoolean(AsciiString name, boolean value);

    @Override
    BinaryHeaders setByte(AsciiString name, byte value);

    @Override
    BinaryHeaders setChar(AsciiString name, char value);

    @Override
    BinaryHeaders setShort(AsciiString name, short value);

    @Override
    BinaryHeaders setInt(AsciiString name, int value);

    @Override
    BinaryHeaders setLong(AsciiString name, long value);

    @Override
    BinaryHeaders setFloat(AsciiString name, float value);

    @Override
    BinaryHeaders setDouble(AsciiString name, double value);

    @Override
    BinaryHeaders setTimeMillis(AsciiString name, long value);

    /**
     * See {@link Headers#set(Headers)}
     */
    BinaryHeaders set(BinaryHeaders headers);

    /**
     * See {@link Headers#setAll(Headers)}
     */
    BinaryHeaders setAll(BinaryHeaders headers);

    @Override
    BinaryHeaders clear();
}
