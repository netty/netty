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
package io.netty5.buffer.api;

import java.lang.invoke.VarHandle;

/**
 * A mutable reference to a buffer.
 */
public final class BufferRef extends BufferHolder<BufferRef> {
    /**
     * Create a reference to the given {@linkplain Buffer buffer}.
     *
     * @param buf The buffer to reference.
     */
    private BufferRef(Buffer buf) {
        super(buf);
        // BufferRef is meant to be atomic, so we need to add a fence to get the semantics of a volatile store.
        VarHandle.fullFence();
    }

    /**
     * Create a reference that holds the exclusive ownership of the sent buffer.
     *
     * @param send The {@linkplain Send sent} buffer to take ownership of.
     */
    public BufferRef(Send<Buffer> send) {
        super(send);
        // BufferRef is meant to be atomic, so we need to add a fence to get the semantics of a volatile store.
        VarHandle.fullFence();
    }

    @Override
    protected BufferRef receive(Buffer buf) {
        return new BufferRef(buf);
    }

    /**
     * Replace the underlying referenced buffer with the given buffer.
     * <p>
     * <strong>Note:</strong> this method closes the current buffer,
     * and takes exclusive ownership of the received buffer.
     * <p>
     * The buffer assignment is performed using a volatile store.
     *
     * @param send The {@link Send} with the new {@link Buffer} instance that is replacing the currently held buffer.
     */
    public void replace(Send<Buffer> send) {
        replaceBufferVolatile(send);
    }

    /**
     * Access the buffer in this reference.
     *
     * @return The buffer held by the reference.
     */
    public Buffer content() {
        return getBufferVolatile();
    }
}
