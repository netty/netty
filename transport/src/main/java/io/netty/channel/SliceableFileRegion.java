/*
 * Copyright 2017 The Netty Project
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
package io.netty.channel;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;

/**
 * A {@link FileRegion} which supports slice.
 * <p>
 * We introduce this interface because we can not add new methods to an interface until next
 * minor release. All the methods here will be moved to FileRegion when we release netty 4.2.
 */
public interface SliceableFileRegion extends FileRegion {

    /**
     * Returns the {@code transferIndex} of this file region.
     */
    long transferIndex();

    /**
     * Sets the {@code transferIndex} of file region.
     *
     * @throws IndexOutOfBoundsException
     *             if the specified {@code transferIndex} is less than {@code 0} or greater than
     *             {@link #count()}
     */
    SliceableFileRegion transferIndex(long index);

    /**
     * Returns {@code true} if and only if {@code (this.count - this.transferIndex)} is greater than
     * {@code 0}.
     */
    boolean isTransferable();

    /**
     * Returns the number of readable bytes which is equal to {@code (count - transferIndex)}.
     */
    long transferableBytes();

    /**
     * Transfers the content of this file region to the specified channel.
     *
     * @param target
     *            the destination of the transfer
     * @param length
     *            the maximum number of bytes to transfer
     */
    long transferBytesTo(WritableByteChannel target, long length) throws IOException;

    /**
     * Transfers the content of this file region to the specified channel.
     * <p>
     * This method does not modify {@link #transferIndex()}.
     *
     * @param target
     *            the destination of the transfer
     * @param position
     *            the relative offset of the file where the transfer begins from. For example,
     *            <tt>0</tt> will make the transfer start from {@link #position()}th byte and
     *            <tt>{@link #count()} - 1</tt> will make the last byte of the region transferred.
     * @param length
     *            the maximum number of bytes to transfer
     */
    long transferBytesTo(WritableByteChannel target, long position, long length) throws IOException;

    /**
     * Returns a slice of this file region's sub-region. This method does not modify
     * {@code transferIndex} of this buffer.
     * <p>
     * Also be aware that this method will NOT call {@link #retain()} and so the reference count
     * will NOT be increased.
     */
    SliceableFileRegion slice(long index, long length);

    /**
     * Returns a slice of this file region's sub-region. This method does not modify
     * {@code transferIndex} of this buffer.
     * <p>
     * Note that this method returns a {@linkplain #retain() retained} file region unlike
     * {@link #slice(long, long)}.
     */
    SliceableFileRegion retainedSlice(long index, long length);

    /**
     * Returns a new slice of this file region's sub-region starting at the current
     * {@code transferIndex} and increases the {@code transferIndex} by the size of the new slice (=
     * {@code length}).
     * <p>
     * Also be aware that this method will NOT call {@link #retain()} and so the reference count
     * will NOT be increased.
     *
     * @param length
     *            the size of the new slice
     */
    SliceableFileRegion transferSlice(long length);

    /**
     * Returns a new slice of this file region's sub-region starting at the current
     * {@code transferIndex} and increases the {@code transferIndex} by the size of the new slice (=
     * {@code length}).
     * <p>
     * Note that this method returns a {@linkplain #retain() retained} file region unlike
     * {@link #transferSlice(long)}.
     *
     * @param length
     *            the size of the new slice
     */
    SliceableFileRegion transferRetainedSlice(long length);

    /**
     * Return the underlying file region instance if this file region is a wrapper of another file
     * region.
     *
     * @return {@code null} if this file region is not a wrapper
     */
    SliceableFileRegion unwrap();
}
