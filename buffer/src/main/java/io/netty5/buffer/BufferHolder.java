/*
 * Copyright 2021 The Netty Project
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
package io.netty5.buffer;

import io.netty5.buffer.internal.ResourceSupport;
import io.netty5.buffer.internal.InternalBufferUtils;
import io.netty5.util.Resource;

import java.lang.invoke.VarHandle;
import java.util.Objects;

import static java.lang.invoke.MethodHandles.lookup;

/**
 * The {@link BufferHolder} is an abstract class that simplifies the implementation of objects that themselves contain
 * a {@link Buffer} instance.
 * <p>
 * The {@link BufferHolder} can only hold on to a single buffer, so objects and classes that need to hold on to multiple
 * buffers will have to do their implementation from scratch, though they can use the code of the {@link BufferHolder}
 * as inspiration. Alternatively, multiple buffers can be
 * {@linkplain BufferAllocator#compose(Iterable) composed} into a single buffer, which can then be put
 * in a buffer holder.
 * <p>
 * If you just want an object that is a reference to a buffer, then the {@link BufferRef} can be used for that purpose.
 * If you have an advanced use case where you wish to implement {@link Resource}, and tightly control lifetimes, then
 * {@link ResourceSupport} can be of help.
 *
 * @param <T> The concrete {@link BufferHolder} type.
 */
public abstract class BufferHolder<T extends Resource<T>> implements Resource<T> {
    private static final VarHandle BUF = InternalBufferUtils.findVarHandle(
            lookup(), BufferHolder.class, "buf", Buffer.class);
    private Buffer buf;

    /**
     * Create a new {@link BufferHolder} to hold the given {@linkplain Buffer buffer}.
     * <p>
     * The given buffer is {@linkplain Resource#moveAndClose() moved} into the holder.
     *
     * @param buf The {@linkplain Buffer buffer} to be held by this holder.
     */
    protected BufferHolder(Buffer buf) {
        this.buf = Objects.requireNonNull(buf, "The buffer cannot be null.").moveAndClose();
    }

    @Override
    public void close() {
        buf.close();
    }

    /**
     * Replace the underlying referenced buffer with the given buffer.
     * <p>
     * This method is protected to permit advanced use cases of {@link BufferHolder} sub-class implementations.
     * <p>
     * <strong>Note:</strong> This method closes the current buffer,
     * and takes exclusive ownership of the received buffer.
     * <p>
     * The buffer assignment is performed using a plain store.
     *
     * @param buffer The new {@link Buffer} instance that is replacing the currently held buffer.
     */
    protected final void replaceBuffer(Buffer buffer) {
        Buffer received = buffer.moveAndClose();
        buf.close();
        buf = received;
    }

    /**
     * Replace the underlying referenced buffer with the given buffer.
     * <p>
     * This method is protected to permit advanced use cases of {@link BufferHolder} sub-class implementations.
     * <p>
     * <strong>Note:</strong> this method closes the current buffer,
     * and takes exclusive ownership of the received buffer.
     * <p>
     * The buffer assignment is performed using a volatile store.
     *
     * @param buffer The new {@link Buffer} instance that is replacing the currently held buffer.
     */
    protected final void replaceBufferVolatile(Buffer buffer) {
        var prev = (Buffer) BUF.getAndSet(this, buffer.moveAndClose());
        prev.close();
    }

    /**
     * Access the held {@link Buffer} instance.
     * <p>
     * The access is performed using a plain load.
     *
     * @return The {@link Buffer} instance being held by this {@linkplain T buffer holder}.
     */
    protected final Buffer getBuffer() {
        return buf;
    }

    /**
     * Access the held {@link Buffer} instance.
     * <p>
     * The access is performed using a volatile load.
     *
     * @return The {@link Buffer} instance being held by this {@linkplain T buffer holder}.
     */
    protected final Buffer getBufferVolatile() {
        return (Buffer) BUF.getVolatile(this);
    }

    @Override
    public boolean isAccessible() {
        return buf.isAccessible();
    }

    @SuppressWarnings("unchecked")
    @Override
    public T touch(Object hint) {
        buf.touch(hint);
        return (T) this;
    }

    /**
     * This implementation of the {@code equals} operation is restricted to work only with instances of the same class.
     * The reason for that is that Netty library already has a number of classes that extend {@link BufferHolder} and
     * override {@code equals} method with an additional comparison logic, and we need the symmetric property of the
     * {@code equals} operation to be preserved.
     *
     * @param other The reference object with which to compare.
     * @return {@code true} if this object is the same as the obj argument; {@code false} otherwise.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other != null && getClass() == other.getClass()) {
            return buf.equals(((BufferHolder<?>) other).buf);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = getClass().hashCode();
        result *= 31 + buf.hashCode();
        return result;
    }
}
