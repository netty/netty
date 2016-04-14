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
package io.netty.buffer;

import java.nio.ByteOrder;

/**
 * A {@link ByteBuf} implementation that wraps another buffer to prevent a user from increasing or decreasing the
 * wrapped buffer's reference count.
 */
final class UnreleasableByteBuf extends WrappedByteBuf {

    private SwappedByteBuf swappedBuf;

    UnreleasableByteBuf(ByteBuf buf) {
        super(buf);
    }

    @Override
    public ByteBuf order(ByteOrder endianness) {
        if (endianness == null) {
            throw new NullPointerException("endianness");
        }
        if (endianness == order()) {
            return this;
        }

        SwappedByteBuf swappedBuf = this.swappedBuf;
        if (swappedBuf == null) {
            this.swappedBuf = swappedBuf = new SwappedByteBuf(this);
        }
        return swappedBuf;
    }

    @Override
    public ByteBuf asReadOnly() {
        return new UnreleasableByteBuf(buf.asReadOnly());
    }

    @Override
    public ByteBuf readSlice(int length) {
        return new UnreleasableByteBuf(buf.readSlice(length));
    }

    @Override
    public ByteBuf readRetainedSlice(int length) {
        return new UnreleasableByteBuf(buf.readRetainedSlice(length));
    }

    @Override
    public ByteBuf slice() {
        return new UnreleasableByteBuf(buf.slice());
    }

    @Override
    public ByteBuf retainedSlice() {
        return new UnreleasableByteBuf(buf.retainedSlice());
    }

    @Override
    public ByteBuf slice(int index, int length) {
        return new UnreleasableByteBuf(buf.slice(index, length));
    }

    @Override
    public ByteBuf retainedSlice(int index, int length) {
        return new UnreleasableByteBuf(buf.retainedSlice(index, length));
    }

    @Override
    public ByteBuf duplicate() {
        return new UnreleasableByteBuf(buf.duplicate());
    }

    @Override
    public ByteBuf retainedDuplicate() {
        return new UnreleasableByteBuf(buf.retainedDuplicate());
    }

    @Override
    public ByteBuf retain(int increment) {
        return this;
    }

    @Override
    public ByteBuf retain() {
        return this;
    }

    @Override
    public ByteBuf touch() {
        return this;
    }

    @Override
    public ByteBuf touch(Object hint) {
        return this;
    }

    @Override
    public boolean release() {
        return false;
    }

    @Override
    public boolean release(int decrement) {
        return false;
    }
}
