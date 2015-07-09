/*
 * Copyright 2015 The Netty Project
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

import io.netty.util.ReferenceCounted;

/**
 * An object that contains a readable region. For simplicity, extends {@link ReferenceCounted}.
 */
public interface Readable extends ReferenceCounted {
    /**
     * Returns current read position in the object.
     */
    long readerPosition();

    /**
     * Returns {@code true} if and only if {@link #readableBytes()} > {@code 0}.
     */
    boolean isReadable();

    /**
     * Returns the number of readable bytes in this object.
     */
    long readableBytes();

    /**
     * Increases the current {@code readerPosition} by the specified
     * {@code length} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@link #readableBytes()}
     */
    Readable skipBytes(long length);

    /**
     * Returns a slice of this object's readable region. This method is
     * identical to {@code r.slice(r.readerPosition(), r.readableBytes())}.
     * This method does not modify {@code readerPosition}.
     * <p>
     * Also be aware that this method will NOT call {@link #retain()} and so the
     * reference count will NOT be increased.
     */
    Readable slice();

    /**
     * Returns a slice of this object's sub-region. This method does not modify
     * {@code readerPosition}.
     * <p>
     * Also be aware that this method will NOT call {@link #retain()} and so the
     * reference count will NOT be increased.
     *
     * @param position the starting position for the slice.
     *
     * @param length the size of the new slice relative to {@code position}.
     *
     * @throws IndexOutOfBoundsException
     *         if any part of the requested region falls outside of the currently readable region.
     */
    Readable slice(long position, long length);

    /**
     * Returns a new slice of this object's sub-region starting at the current
     * {@link #readerPosition()} and increases the {@code readerPosition} by the size
     * of the new slice (= {@code length}).
     * <p>
     * Also be aware that this method will NOT call {@link #retain()} and so the
     * reference count will NOT be increased.
     *
     * @param length the size of the new slice
     *
     * @return the newly created slice
     *
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@link #readableBytes()}
     */
    Readable readSlice(long length);
}
