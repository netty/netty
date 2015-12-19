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
import java.io.OutputStream;
import java.nio.channels.GatheringByteChannel;

/**
 * An object that contains a readable region. For simplicity, extends {@link ReferenceCounted}.
 */
public interface ReadableObject extends ReferenceCounted {

    /**
     * Returns current read position in the object.
     */
    long objectReaderPosition();

    /**
     * Sets the {@code readerPosition} of this object.
     *
     * @throws IndexOutOfBoundsException
     *             if the specified position is less than {@code 0} or greater than {@link #readerLimit()}.
     */
    ReadableObject objectReaderPosition(long readerPosition);

    /**
     * Gets the read position limit (exclusive). The value of {@link #readerPosition()} must always be less than or
     * equal to this limit.
     */
    long objectReaderLimit();

    /**
     * Returns {@code true} if and only if {@link #readableBytes()} {@code >} {@code 0}.
     */
    boolean isObjectReadable();

    /**
     * Returns {@code true} if and only if this buffer contains equal to or more than the specified number of elements.
     */
    boolean isObjectReadable(long size);

    /**
     * Returns the number of readable bytes in this object.
     */
    long objectReadableBytes();

    /**
     * Increases the current {@link #readerPosition()} by the specified {@code length} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *             if {@code length} is greater than {@link #readableBytes()}
     */
    ReadableObject skipObjectBytes(long length);

    /**
     * Returns a slice of this object's readable region. This method is identical to
     * {@code r.slice(r.readerPosition(), r.readableBytes())}. This method does not modify {@link #readerPosition()}.
     * <p>
     * Also be aware that this method will NOT call {@link #retain()} and so the reference count will NOT be increased.
     */
    ReadableObject sliceObject();

    /**
     * Returns a slice of this object's sub-region. This method does not modify {@link #readerPosition()}.
     * <p>
     * Also be aware that this method will NOT call {@link #retain()} and so the reference count will NOT be increased.
     *
     * @param position
     *            the starting position for the slice.
     *
     * @param length
     *            the size of the new slice relative to {@link #readerPosition()}.
     *
     * @throws IndexOutOfBoundsException
     *             if any part of the requested region falls outside of the currently readable region.
     */
    ReadableObject sliceObject(long position, long length);

    /**
     * Returns a new slice of this object's sub-region starting at the current {@link #readerPosition()} and increases
     * the {@link #readerPosition()} by the size of the new slice (= {@code length}).
     * <p>
     * Also be aware that this method will NOT call {@link #retain()} and so the reference count will NOT be increased.
     *
     * @param length
     *            the size of the new slice
     *
     * @return the newly created slice
     *
     * @throws IndexOutOfBoundsException
     *             if {@code length} is greater than {@link #readableBytes()}
     */
    ReadableObject readObjectSlice(long length);

    /**
     * Return the underlying {@link ReadableObject} instance if this is a wrapper around another object (e.g. a slice of
     * another object) or {@code null} if this is not a wrapper.
     */
    ReadableObject unwrapObject();

    /**
     * Transfers this object's data to the specified channel starting at the given {@code pos}. Does not change the
     * {@link #objectReaderPosition()}.
     *
     * @param pos
     *            the starting position for the transfer. This must be {@code <} {@link #readerLimit()}.
     * @param length
     *            the maximum number of bytes to transfer
     * @return the actual number of bytes written out to the specified writer
     * @throws IndexOutOfBoundsException
     *             if {@code pos < 0} or {@code pos + length >} {@link #readerLimit()}
     * @throws IOException
     *             if the specified writer threw an exception during I/O
     */
    long getObjectBytes(GatheringByteChannel out, long pos, long length) throws IOException;

    /**
     * Transfers this object's data to the specified writer starting at the current {@link #objectReaderPosition()}.
     *
     * @param length
     *            the maximum number of bytes to transfer
     * @return the actual number of bytes written out to the specified writer
     * @throws IndexOutOfBoundsException
     *             if {@link #readerPosition()}{@code + length >} {@link #readerLimit()}
     * @throws IOException
     *             if the specified writer threw an exception during I/O
     */
    long readObjectBytes(GatheringByteChannel out, long length) throws IOException;

    /**
     * Transfers this object's data to the specified channel starting at the given {@code pos}. Does not change the
     * {@link #objectReaderPosition()}.
     *
     * @param pos
     *            the starting position for the transfer. This must be {@code <} {@link #readerLimit()}.
     * @param length
     *            the number of bytes to transfer
     * @throws IndexOutOfBoundsException
     *             if {@code pos < 0} or {@code pos + length >} {@link #readerLimit()}
     * @throws IOException
     *             if the specified writer threw an exception during I/O
     */
    ReadableObject getObjectBytes(OutputStream out, long pos, long length) throws IOException;

    /**
     * Transfers this object's data to the specified writer starting at the current {@link #objectReaderPosition()}.
     *
     * @param length
     *            the number of bytes to transfer
     * @throws IndexOutOfBoundsException
     *             if {@link #readerPosition()}{@code + length >} {@link #readerLimit()}
     * @throws IOException
     *             if the specified writer threw an exception during I/O
     */
    ReadableObject readObjectBytes(OutputStream out, long length) throws IOException;

    public interface SingleReadableObjectWriter {

        /**
         * Write the specified {@code obj} out.
         * <p>
         * Does not modify the {@code readerPosition} of the given {@code obj}.
         *
         * @param obj
         *            must not be {@link SlicedReadableObject} nor {@link CompositeReadableObject}(But could be
         *            {@link SlicedByteBuf} or {@link CompositeByteBuf}).
         * @param pos
         *            the starting position for the transfer. This must be {@code <} {@link #readerLimit()}.
         * @param length
         *            the maximum number of bytes to transfer
         * @return the actual number of bytes written out
         * @throws IOException
         *             if an I/O error occurs.
         */
        long write(ReadableObject obj, long pos, long length) throws IOException;
    }

    /**
     * Transfers this object's data to the specified writer starting at the given {@code pos}. Does not change the
     * {@link #objectReaderPosition()}.
     *
     * @param pos
     *            the starting position for the transfer. This must be {@code <} {@link #readerLimit()}.
     * @param length
     *            the maximum number of bytes to transfer
     * @return the actual number of bytes written out to the specified writer
     * @throws IndexOutOfBoundsException
     *             if {@code pos < 0} or {@code pos + length >} {@link #readerLimit()}
     * @throws IOException
     *             if the specified writer threw an exception during I/O
     */
    long getObjectBytes(SingleReadableObjectWriter out, long pos, long length) throws IOException;

    /**
     * Transfers this object's data to the specified writer starting at the current {@link #objectReaderPosition()}.
     *
     * @param length
     *            the maximum number of bytes to transfer
     * @return the actual number of bytes written out to the specified writer
     * @throws IndexOutOfBoundsException
     *             if {@link #readerPosition()}{@code + length >} {@link #readerLimit()}
     * @throws IOException
     *             if the specified writer threw an exception during I/O
     */
    long readObjectBytes(SingleReadableObjectWriter out, long length) throws IOException;

    @Override
    ReadableObject retain();

    @Override
    ReadableObject retain(int increment);

    @Override
    ReadableObject touch(Object hint);

    @Override
    ReadableObject touch();
}
