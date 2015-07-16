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

import java.io.IOException;
import java.nio.channels.WritableByteChannel;

/**
 * An object that contains a readable region. For simplicity, extends {@link ReferenceCounted}.
 */
public interface ReadableObject extends ReferenceCounted {
    /**
     * Returns current read position in the object.
     */
    long readerPosition();

    /**
     * Sets the {@code readerPosition} of this object.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code readerPosition} is
     *            less than {@code 0} or
     *            greater than {@link #readerLimit()}.
     */
    ReadableObject readerPosition(long readerPosition);

    /**
     * Gets the read position limit (exclusive). The value of {@code readerPosition} must always
     * be less than or equal to this limit.
     */
    long readerLimit();

    /**
     * Returns {@code true} if and only if {@link #readableBytes()} > {@code 0}.
     */
    boolean isReadable();

    /**
     * Returns {@code true} if and only if this buffer contains equal to or more than the specified number of elements.
     */
    boolean isReadable(int size);

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
    ReadableObject skipBytes(long length);

    /**
     * Returns a slice of this object's readable region. This method is
     * identical to {@code r.slice(r.readerPosition(), r.readableBytes())}.
     * This method does not modify {@code readerPosition}.
     * <p>
     * Also be aware that this method will NOT call {@link #retain()} and so the
     * reference count will NOT be increased.
     */
    ReadableObject slice();

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
    ReadableObject slice(long position, long length);

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
    ReadableObject readSlice(long length);

    /**
     * Return the underlying {@link ReadableObject} instance if this is a wrapper around another
     * object (e.g. a slice of another object).
     *
     * @return {@code null} if this is not a wrapper
     */
    ReadableObject unwrap();

    /**
     * Discards the bytes between position 0 and {@code readerPosition}.
     * <p>
     * Please refer to the class documentation for more detailed explanation.
     */
    ReadableObject discardReadBytes();

    /**
     * Similar to {@link #discardReadBytes()} except that this method might discard
     * some, all, or none of read bytes depending on its internal implementation to reduce
     * overall memory bandwidth consumption at the cost of potentially additional memory
     * consumption.
     */
    ReadableObject discardSomeReadBytes();

    /**
     * Transfers this object's data to the specified stream starting at the
     * given {@code readerPosition}. Does not change the {@code readerPosition}.
     *
     * @param pos the starting position for the transfer. This must be < {@link #readerLimit()}.
     * @param length the maximum number of bytes to transfer
     *
     * @return the actual number of bytes written out to the specified channel
     *
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.readableBytes}
     * @throws IOException
     *         if the specified channel threw an exception during I/O
     */
    long readTo(WritableByteChannel channel, long pos, long length) throws IOException;

    @Override
    ReadableObject retain();

    @Override
    ReadableObject retain(int increment);

    @Override
    ReadableObject touch(Object hint);

    @Override
    ReadableObject touch();
}
