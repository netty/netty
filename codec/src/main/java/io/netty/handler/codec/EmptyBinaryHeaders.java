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

public class EmptyBinaryHeaders extends EmptyHeaders<ByteString> implements BinaryHeaders {
    protected EmptyBinaryHeaders() {
    }

    @Override
    public BinaryHeaders add(ByteString name, ByteString value) {
        super.add(name, value);
        return this;
    }

    @Override
    public BinaryHeaders add(ByteString name, Iterable<? extends ByteString> values) {
        super.add(name, values);
        return this;
    }

    @Override
    public BinaryHeaders add(ByteString name, ByteString... values) {
        super.add(name, values);
        return this;
    }

    @Override
    public BinaryHeaders addObject(ByteString name, Object value) {
        super.addObject(name, value);
        return this;
    }

    @Override
    public BinaryHeaders addObject(ByteString name, Iterable<?> values) {
        super.addObject(name, values);
        return this;
    }

    @Override
    public BinaryHeaders addObject(ByteString name, Object... values) {
        super.addObject(name, values);
        return this;
    }

    @Override
    public BinaryHeaders addBoolean(ByteString name, boolean value) {
        super.addBoolean(name, value);
        return this;
    }

    @Override
    public BinaryHeaders addChar(ByteString name, char value) {
        super.addChar(name, value);
        return this;
    }

    @Override
    public BinaryHeaders addByte(ByteString name, byte value) {
        super.addByte(name, value);
        return this;
    }

    @Override
    public BinaryHeaders addShort(ByteString name, short value) {
        super.addShort(name, value);
        return this;
    }

    @Override
    public BinaryHeaders addInt(ByteString name, int value) {
        super.addInt(name, value);
        return this;
    }

    @Override
    public BinaryHeaders addLong(ByteString name, long value) {
        super.addLong(name, value);
        return this;
    }

    @Override
    public BinaryHeaders addFloat(ByteString name, float value) {
        super.addFloat(name, value);
        return this;
    }

    @Override
    public BinaryHeaders addDouble(ByteString name, double value) {
        super.addDouble(name, value);
        return this;
    }

    @Override
    public BinaryHeaders addTimeMillis(ByteString name, long value) {
        super.addTimeMillis(name, value);
        return this;
    }

    @Override
    public BinaryHeaders add(BinaryHeaders headers) {
        super.add(headers);
        return this;
    }

    @Override
    public BinaryHeaders set(ByteString name, ByteString value) {
        super.set(name, value);
        return this;
    }

    @Override
    public BinaryHeaders set(ByteString name, Iterable<? extends ByteString> values) {
        super.set(name, values);
        return this;
    }

    @Override
    public BinaryHeaders set(ByteString name, ByteString... values) {
        super.set(name, values);
        return this;
    }

    @Override
    public BinaryHeaders setObject(ByteString name, Object value) {
        super.setObject(name, value);
        return this;
    }

    @Override
    public BinaryHeaders setObject(ByteString name, Iterable<?> values) {
        super.setObject(name, values);
        return this;
    }

    @Override
    public BinaryHeaders setObject(ByteString name, Object... values) {
        super.setObject(name, values);
        return this;
    }

    @Override
    public BinaryHeaders setBoolean(ByteString name, boolean value) {
        super.setBoolean(name, value);
        return this;
    }

    @Override
    public BinaryHeaders setChar(ByteString name, char value) {
        super.setChar(name, value);
        return this;
    }

    @Override
    public BinaryHeaders setByte(ByteString name, byte value) {
        super.setByte(name, value);
        return this;
    }

    @Override
    public BinaryHeaders setShort(ByteString name, short value) {
        super.setShort(name, value);
        return this;
    }

    @Override
    public BinaryHeaders setInt(ByteString name, int value) {
        super.setInt(name, value);
        return this;
    }

    @Override
    public BinaryHeaders setLong(ByteString name, long value) {
        super.setLong(name, value);
        return this;
    }

    @Override
    public BinaryHeaders setFloat(ByteString name, float value) {
        super.setFloat(name, value);
        return this;
    }

    @Override
    public BinaryHeaders setDouble(ByteString name, double value) {
        super.setDouble(name, value);
        return this;
    }

    @Override
    public BinaryHeaders setTimeMillis(ByteString name, long value) {
        super.setTimeMillis(name, value);
        return this;
    }

    @Override
    public BinaryHeaders set(BinaryHeaders headers) {
        super.set(headers);
        return this;
    }

    @Override
    public BinaryHeaders setAll(BinaryHeaders headers) {
        super.setAll(headers);
        return this;
    }

    @Override
    public BinaryHeaders clear() {
        super.clear();
        return this;
    }
}
