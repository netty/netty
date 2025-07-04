/*
 * Copyright 2025 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.buffer;

/**
 * Specialized {@link ReadOnlyByteBuf} sub-type which allows fast access to parent
 * without extra bound-checks.
 */
final class ReadOnlyAbstractByteBuf extends ReadOnlyByteBuf {

    ReadOnlyAbstractByteBuf(AbstractByteBuf buffer) {
        super(buffer);
    }

    @Override
    public AbstractByteBuf unwrap() {
        return (AbstractByteBuf) super.unwrap();
    }

    @Override
    protected byte _getByte(int index) {
        return unwrap()._getByte(index);
    }

    @Override
    protected short _getShort(int index) {
        return unwrap()._getShort(index);
    }

    @Override
    protected short _getShortLE(int index) {
        return unwrap()._getShortLE(index);
    }

    @Override
    protected int _getUnsignedMedium(int index) {
        return unwrap()._getUnsignedMedium(index);
    }

    @Override
    protected int _getUnsignedMediumLE(int index) {
        return unwrap()._getUnsignedMediumLE(index);
    }

    @Override
    protected int _getInt(int index) {
        return unwrap()._getInt(index);
    }

    @Override
    protected int _getIntLE(int index) {
        return unwrap()._getIntLE(index);
    }

    @Override
    protected long _getLong(int index) {
        return unwrap()._getLong(index);
    }

    @Override
    protected long _getLongLE(int index) {
        return unwrap()._getLongLE(index);
    }
}
