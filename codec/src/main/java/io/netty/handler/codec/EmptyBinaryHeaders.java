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

public class EmptyBinaryHeaders extends EmptyHeaders<AsciiString> implements BinaryHeaders {
    protected EmptyBinaryHeaders() {
    }

    @Override
    public BinaryHeaders add(AsciiString name, AsciiString value) {
        super.add(name, value);
        return this;
    }

    @Override
    public BinaryHeaders add(AsciiString name, Iterable<? extends AsciiString> values) {
        super.add(name, values);
        return this;
    }

    @Override
    public BinaryHeaders add(AsciiString name, AsciiString... values) {
        super.add(name, values);
        return this;
    }

    @Override
    public BinaryHeaders addObject(AsciiString name, Object value) {
        super.addObject(name, value);
        return this;
    }

    @Override
    public BinaryHeaders addObject(AsciiString name, Iterable<?> values) {
        super.addObject(name, values);
        return this;
    }

    @Override
    public BinaryHeaders addObject(AsciiString name, Object... values) {
        super.addObject(name, values);
        return this;
    }

    @Override
    public BinaryHeaders addBoolean(AsciiString name, boolean value) {
        super.addBoolean(name, value);
        return this;
    }

    @Override
    public BinaryHeaders addChar(AsciiString name, char value) {
        super.addChar(name, value);
        return this;
    }

    @Override
    public BinaryHeaders addByte(AsciiString name, byte value) {
        super.addByte(name, value);
        return this;
    }

    @Override
    public BinaryHeaders addShort(AsciiString name, short value) {
        super.addShort(name, value);
        return this;
    }

    @Override
    public BinaryHeaders addInt(AsciiString name, int value) {
        super.addInt(name, value);
        return this;
    }

    @Override
    public BinaryHeaders addLong(AsciiString name, long value) {
        super.addLong(name, value);
        return this;
    }

    @Override
    public BinaryHeaders addFloat(AsciiString name, float value) {
        super.addFloat(name, value);
        return this;
    }

    @Override
    public BinaryHeaders addDouble(AsciiString name, double value) {
        super.addDouble(name, value);
        return this;
    }

    @Override
    public BinaryHeaders addTimeMillis(AsciiString name, long value) {
        super.addTimeMillis(name, value);
        return this;
    }

    @Override
    public BinaryHeaders add(BinaryHeaders headers) {
        super.add(headers);
        return this;
    }

    @Override
    public BinaryHeaders set(AsciiString name, AsciiString value) {
        super.set(name, value);
        return this;
    }

    @Override
    public BinaryHeaders set(AsciiString name, Iterable<? extends AsciiString> values) {
        super.set(name, values);
        return this;
    }

    @Override
    public BinaryHeaders set(AsciiString name, AsciiString... values) {
        super.set(name, values);
        return this;
    }

    @Override
    public BinaryHeaders setObject(AsciiString name, Object value) {
        super.setObject(name, value);
        return this;
    }

    @Override
    public BinaryHeaders setObject(AsciiString name, Iterable<?> values) {
        super.setObject(name, values);
        return this;
    }

    @Override
    public BinaryHeaders setObject(AsciiString name, Object... values) {
        super.setObject(name, values);
        return this;
    }

    @Override
    public BinaryHeaders setBoolean(AsciiString name, boolean value) {
        super.setBoolean(name, value);
        return this;
    }

    @Override
    public BinaryHeaders setChar(AsciiString name, char value) {
        super.setChar(name, value);
        return this;
    }

    @Override
    public BinaryHeaders setByte(AsciiString name, byte value) {
        super.setByte(name, value);
        return this;
    }

    @Override
    public BinaryHeaders setShort(AsciiString name, short value) {
        super.setShort(name, value);
        return this;
    }

    @Override
    public BinaryHeaders setInt(AsciiString name, int value) {
        super.setInt(name, value);
        return this;
    }

    @Override
    public BinaryHeaders setLong(AsciiString name, long value) {
        super.setLong(name, value);
        return this;
    }

    @Override
    public BinaryHeaders setFloat(AsciiString name, float value) {
        super.setFloat(name, value);
        return this;
    }

    @Override
    public BinaryHeaders setDouble(AsciiString name, double value) {
        super.setDouble(name, value);
        return this;
    }

    @Override
    public BinaryHeaders setTimeMillis(AsciiString name, long value) {
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
