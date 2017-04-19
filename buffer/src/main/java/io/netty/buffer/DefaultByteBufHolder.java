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

import io.netty.util.IllegalReferenceCountException;
import io.netty.util.internal.StringUtil;

/**
 * Default implementation of a {@link ByteBufHolder} that holds it's data in a {@link ByteBuf}.
 *
 */
public class DefaultByteBufHolder implements ByteBufHolder {

    private final ByteBuf data;

    public DefaultByteBufHolder(ByteBuf data) {
        if (data == null) {
            throw new NullPointerException("data");
        }
        this.data = data;
    }

    @Override
    public ByteBuf content() {
        if (data.refCnt() <= 0) {
            throw new IllegalReferenceCountException(data.refCnt());
        }
        return data;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method calls {@code replace(content().copy())} by default.
     */
    @Override
    public ByteBufHolder copy() {
        return replace(data.copy());
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method calls {@code replace(content().duplicate())} by default.
     */
    @Override
    public ByteBufHolder duplicate() {
        return replace(data.duplicate());
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method calls {@code replace(content().retainedDuplicate())} by default.
     */
    @Override
    public ByteBufHolder retainedDuplicate() {
        return replace(data.retainedDuplicate());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Override this method to return a new instance of this object whose content is set to the specified
     * {@code content}. The default implementation of {@link #copy()}, {@link #duplicate()} and
     * {@link #retainedDuplicate()} invokes this method to create a copy.
     */
    @Override
    public ByteBufHolder replace(ByteBuf content) {
        return new DefaultByteBufHolder(content);
    }

    @Override
    public int refCnt() {
        return data.refCnt();
    }

    @Override
    public ByteBufHolder retain() {
        data.retain();
        return this;
    }

    @Override
    public ByteBufHolder retain(int increment) {
        data.retain(increment);
        return this;
    }

    @Override
    public ByteBufHolder touch() {
        data.touch();
        return this;
    }

    @Override
    public ByteBufHolder touch(Object hint) {
        data.touch(hint);
        return this;
    }

    @Override
    public boolean release() {
        return data.release();
    }

    @Override
    public boolean release(int decrement) {
        return data.release(decrement);
    }

    /**
     * Return {@link ByteBuf#toString()} without checking the reference count first. This is useful to implement
     * {@link #toString()}.
     */
    protected final String contentToString() {
        return data.toString();
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + '(' + contentToString() + ')';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof ByteBufHolder) {
            return data.equals(((ByteBufHolder) o).content());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return data.hashCode();
    }
}
