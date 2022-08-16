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
 * A view onto the buffer component being processed in a given iteration of {@link Buffer#forEachComponent()}.
 */
public interface BufferComponent {

    /**
     * Check if this component is backed by a cached byte array that can be accessed cheaply.
     * <p>
     * <strong>Note</strong> that regardless of what this method returns, the array should not be used to modify the
     * contents of this buffer component.
     *
     * @return {@code true} if {@link #readableArray()} is a cheap operation, otherwise {@code false}.
     */
    boolean hasReadableArray();

    /**
     * Check if this component is backed by a cached byte array that can be accessed cheaply.
     *
     * @return {@code true} if {@link #writableArray()} is a cheap operation, otherwise {@code false}.
     */
    boolean hasWritableArray();

    /**
     * Get a byte array of the contents of this component.
     * <p>
     * <strong>Note</strong> that the array is meant to be read-only. It may either be a direct reference to the
     * concrete array instance that is backing this component, or it is a fresh copy. Writing to the array may produce
     * undefined behaviour.
     *
     * @return A byte array of the contents of this component.
     * @throws UnsupportedOperationException if {@link #hasReadableArray()} returns {@code false}.
     * @see #readableArrayOffset()
     * @see #readableArrayLength()
     */
    byte[] readableArray();

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
     * An offset into the {@link #readableArray()} where this component starts.
     *
     * @return An offset into {@link #readableArray()}.
     * @throws UnsupportedOperationException if {@link #hasReadableArray()} returns {@code false}.
     */
    int readableArrayOffset();

    /**
     * An offset into the {@link #writableArray()} where this component starts.
     *
     * @return An offset into {@link #writableArray()}.
     * @throws UnsupportedOperationException if {@link #hasWritableArray()} returns {@code false}.
     */
    int writableArrayOffset();

    /**
     * The number of bytes in the {@link #readableArray()} that belong to this component.
     *
     * @return The number of bytes, from the {@link #readableArrayOffset()} into the {@link #readableArray()},
     * that belong to this component.
     * @throws UnsupportedOperationException if {@link #hasReadableArray()} returns {@code false}.
     */
    int readableArrayLength();

    /**
     * The number of bytes in the {@link #writableArray()} that belong to this component.
     *
     * @return The number of bytes, from the {@link #writableArrayOffset()} into the {@link #writableArray()},
     * that belong to this component.
     * @throws UnsupportedOperationException if {@link #hasWritableArray()} returns {@code false}.
     */
    int writableArrayLength();

    /**
     * Give the base native memory address backing this buffer, or return 0 if this buffer has no native memory address.
     * <p>
     * The <em>base</em> native address, is the address that the buffer components internal read- and write-offsets are
     * relative to, and which is used to compute the {@link #readableNativeAddress()} and the
     * {@link #writableNativeAddress()}.
     * <p>
     * <strong>Note</strong> that the address should not be used for reading or writing. It should only be used for
     * offset calculations.
     *
     * @return The base native memory address, if any, otherwise 0.
     */
    long baseNativeAddress();

    /**
     * Give the native memory address backing this buffer, or return 0 if this buffer has no native memory address.
     * <p>
     * <strong>Note</strong> that the address should not be used for writing to the buffer memory, and doing so may
     * produce undefined behaviour.
     *
     * @return The readable native memory address, if any, otherwise 0.
     */
    long readableNativeAddress();

    /**
     * Give the native memory address backing this buffer, or return 0 if this buffer has no native memory address.
     *
     * @return The writable native memory address, if any, otherwise 0.
     */
    long writableNativeAddress();

    /**
     * Get a {@link ByteBuffer} instance for this memory component.
     * <p>
     * <strong>Note</strong> that the {@link ByteBuffer} is read-only, to prevent write accesses to the memory,
     * when the buffer component is obtained through {@link Buffer#forEachComponent()}.
     *
     * @return A new {@link ByteBuffer}, with its own position and limit, for this memory component.
     */
    ByteBuffer readableBuffer();

    /**
     * Get a {@link ByteBuffer} instance for this memory component, which can be used for modifying the buffer
     * contents.
     *
     * @return A new {@link ByteBuffer}, with its own position and limit, for this memory component.
     */
    ByteBuffer writableBuffer();

    /**
     * Get the number of readable bytes from this component.
     *
     * @return The number of bytes that can be read from this readable component.
     */
    int readableBytes();

    /**
     * Get the space available to be written to this component, as a number of bytes.
     *
     * @return The maximum number of bytes that can be written to this component.
     */
    int writableBytes();

    /**
     * Open a cursor to iterate the readable bytes of this component.
     * Any offsets internal to the component are not modified by the cursor.
     * <p>
     * Care should be taken to ensure that the buffers lifetime extends beyond the cursor and the iteration, and that
     * the internal offsets of the component (such as {@link Buffer#readerOffset()} and {@link Buffer#writerOffset()})
     * are not modified while the iteration takes place. Otherwise, unpredictable behaviour might result.
     *
     * @return A {@link ByteCursor} for iterating the readable bytes of this buffer.
     * @see Buffer#openCursor()
     */
    ByteCursor openCursor();

    /**
     * Move the read-offset to indicate that the given number of bytes were read from this component.
     *
     * @param byteCount The number of bytes read from this component.
     * @return itself.
     * @see Buffer#skipReadableBytes(int)
     */
    BufferComponent skipReadableBytes(int byteCount);

    /**
     * Move the write-offset to indicate that the given number of bytes were written to this component.
     *
     * @param byteCount The number of bytes written to this component.
     * @return itself.
     * @see Buffer#skipWritableBytes(int)
     */
    BufferComponent skipWritableBytes(int byteCount);
}
