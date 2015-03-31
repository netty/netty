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

import io.netty.util.ByteString;

/**
 * A typical {@code ByteString} multimap used by protocols that use binary headers (such as HTTP/2) for the
 * representation of arbitrary key-value data. {@link ByteString} is just a wrapper around a byte array but provides
 * some additional utility when handling text data.
 */
public interface BinaryHeaders extends Headers<ByteString> {
    /**
     * Provides an abstraction to iterate over elements maintained in the {@link Headers} collection.
     */
    interface EntryVisitor extends Headers.EntryVisitor<ByteString> {
    }

    /**
     * Provides an abstraction to iterate over elements maintained in the {@link Headers} collection.
     */
    interface NameVisitor extends Headers.NameVisitor<ByteString> {
    }

    @Override
    BinaryHeaders add(ByteString name, ByteString value);

    @Override
    BinaryHeaders add(ByteString name, Iterable<? extends ByteString> values);

    @Override
    BinaryHeaders add(ByteString name, ByteString... values);

    @Override
    BinaryHeaders addObject(ByteString name, Object value);

    @Override
    BinaryHeaders addObject(ByteString name, Iterable<?> values);

    @Override
    BinaryHeaders addObject(ByteString name, Object... values);

    @Override
    BinaryHeaders addBoolean(ByteString name, boolean value);

    @Override
    BinaryHeaders addByte(ByteString name, byte value);

    @Override
    BinaryHeaders addChar(ByteString name, char value);

    @Override
    BinaryHeaders addShort(ByteString name, short value);

    @Override
    BinaryHeaders addInt(ByteString name, int value);

    @Override
    BinaryHeaders addLong(ByteString name, long value);

    @Override
    BinaryHeaders addFloat(ByteString name, float value);

    @Override
    BinaryHeaders addDouble(ByteString name, double value);

    @Override
    BinaryHeaders addTimeMillis(ByteString name, long value);

    /**
     * See {@link Headers#add(Headers)}
     */
    BinaryHeaders add(BinaryHeaders headers);

    @Override
    BinaryHeaders set(ByteString name, ByteString value);

    @Override
    BinaryHeaders set(ByteString name, Iterable<? extends ByteString> values);

    @Override
    BinaryHeaders set(ByteString name, ByteString... values);

    @Override
    BinaryHeaders setObject(ByteString name, Object value);

    @Override
    BinaryHeaders setObject(ByteString name, Iterable<?> values);

    @Override
    BinaryHeaders setObject(ByteString name, Object... values);

    @Override
    BinaryHeaders setBoolean(ByteString name, boolean value);

    @Override
    BinaryHeaders setByte(ByteString name, byte value);

    @Override
    BinaryHeaders setChar(ByteString name, char value);

    @Override
    BinaryHeaders setShort(ByteString name, short value);

    @Override
    BinaryHeaders setInt(ByteString name, int value);

    @Override
    BinaryHeaders setLong(ByteString name, long value);

    @Override
    BinaryHeaders setFloat(ByteString name, float value);

    @Override
    BinaryHeaders setDouble(ByteString name, double value);

    @Override
    BinaryHeaders setTimeMillis(ByteString name, long value);

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
