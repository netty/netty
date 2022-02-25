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

import java.nio.ByteBuffer;

/**
 * A view onto the buffer component being processed in a given iteration of
 * {@link Buffer#forEachWritable(int, WritableComponentProcessor)}.
 */
public interface WritableComponent {

    /**
     * Check if this component is backed by a cached byte array that can be accessed cheaply.
     *
     * @return {@code true} if {@link #writableArray()} is a cheap operation, otherwise {@code false}.
     */
    boolean hasWritableArray();

    /**
     * Get a byte array of the contents of this component.
     *
     * @return A byte array of the contents of this component.
     * @throws UnsupportedOperationException if {@link #hasWritableArray()} returns {@code false}.
     * @see #writableArrayOffset()
     * @see #writableArrayLength()
     */
    byte[] writableArray();

    /**
     * An offset into the {@link #writableArray()} where this component starts.
     *
     * @return An offset into {@link #writableArray()}.
     * @throws UnsupportedOperationException if {@link #hasWritableArray()} returns {@code false}.
     */
    int writableArrayOffset();

    /**
     * The number of bytes in the {@link #writableArray()} that belong to this component.
     *
     * @return The number of bytes, from the {@link #writableArrayOffset()} into the {@link #writableArray()},
     * that belong to this component.
     * @throws UnsupportedOperationException if {@link #hasWritableArray()} returns {@code false}.
     */
    int writableArrayLength();

    /**
     * Give the native memory address backing this buffer, or return 0 if this buffer has no native memory address.
     *
     * @return The native memory address, if any, otherwise 0.
     */
    long writableNativeAddress();

    /**
     * Get the space available to be written to this component, as a number of bytes.
     *
     * @return The maximum number of bytes that can be written to this component.
     */
    int writableBytes();

    /**
     * Get a {@link ByteBuffer} instance for this memory component, which can be used for modifying the buffer
     * contents.
     *
     * @return A new {@link ByteBuffer}, with its own position and limit, for this memory component.
     */
    ByteBuffer writableBuffer();

    /**
     * Move the write-offset to indicate that the given number of bytes were written to this component.
     *
     * @param byteCount The number of bytes written to this component.
     * @see Buffer#skipWritable(int)
     */
    void skipWritable(int byteCount);
}
