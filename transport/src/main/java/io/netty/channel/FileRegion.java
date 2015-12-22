/*
 * Copyright 2012 The Netty Project
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

import io.netty.util.ReferenceCounted;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

/**
 * A region of a file that is sent via a {@link Channel} which supports
 * <a href="http://en.wikipedia.org/wiki/Zero-copy">zero-copy file transfer</a>.
 *
 * <h3>Upgrade your JDK / JRE</h3>
 *
 * {@link FileChannel#transferTo(long, long, WritableByteChannel)} has at least
 * four known bugs in the old versions of Sun JDK and perhaps its derived ones.
 * Please upgrade your JDK to 1.6.0_18 or later version if you are going to use
 * zero-copy file transfer.
 * <ul>
 * <li><a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5103988">5103988</a>
 *   - FileChannel.transferTo() should return -1 for EAGAIN instead throws IOException</li>
 * <li><a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6253145">6253145</a>
 *   - FileChannel.transferTo() on Linux fails when going beyond 2GB boundary</li>
 * <li><a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6427312">6427312</a>
 *   - FileChannel.transferTo() throws IOException "system call interrupted"</li>
 * <li><a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6524172">6470086</a>
 *   - FileChannel.transferTo(2147483647, 1, channel) causes "Value too large" exception</li>
 * </ul>
 *
 * <h3>Check your operating system and JDK / JRE</h3>
 *
 * If your operating system (or JDK / JRE) does not support zero-copy file
 * transfer, sending a file with {@link FileRegion} might fail or yield worse
 * performance.  For example, sending a large file doesn't work well in Windows.
 *
 * <h3>Not all transports support it</h3>
 */
public interface FileRegion extends ReferenceCounted {

    /**
     * Returns the offset in the file where the transfer began. This value is relative to the return value of
     * {@link #channel()}.
     */
    long position();

    /**
     * Return the bytes which was transfered already
     *
     * @deprecated use {@link #transferIndex()} based API.
     */
    @Deprecated
    long transfered();

    /**
     * Returns the number of bytes to transfer.
     */
    long count();

    /**
     * Transfers the content of this file region to the specified channel.
     *
     * @param target    the destination of the transfer
     * @param position  the relative offset of the file where the transfer
     *                  begins from.  For example, <tt>0</tt> will make the
     *                  transfer start from {@link #position()}th byte and
     *                  <tt>{@link #count()} - 1</tt> will make the last
     *                  byte of the region transferred.
     * @deprecated      use {@link #transferIndex()} based API.
     */
    @Deprecated
    long transferTo(WritableByteChannel target, long position) throws IOException;

    /**
     * Transfers the content of this file region to the specified channel.<p>
     * This method does not modify {@link #transferIndex()}.
     *
     * @param target    the destination of the transfer
     * @param position  the relative offset of the file where the transfer
     *                  begins from.  For example, <tt>0</tt> will make the
     *                  transfer start from {@link #position()}th byte and
     *                  <tt>{@link #count()} - 1</tt> will make the last
     *                  byte of the region transferred.
     * @param length    the maximum number of bytes to transfer
     */
    long transferBytesTo(WritableByteChannel target, long position, long length) throws IOException;

    /**
     * Transfers the content of this file region to the specified channel.
     *
     * @param target    the destination of the transfer
     * @param length    the maximum number of bytes to transfer
     */
    long transferBytesTo(WritableByteChannel target, long length) throws IOException;

    /**
     * Returns the {@code transferIndex} of this file region.
     */
    long transferIndex();

    /**
     * Sets the {@code transferIndex} of file region.
     *
     * @throws IndexOutOfBoundsException
     *             if the specified {@code transferIndex} is less than {@code 0} or greater than {@link #count()}
     */
    FileRegion transferIndex(long index);

    /**
     * Returns {@code true} if and only if {@link #transferableBytes()} is greater than {@code 0}.
     */
    boolean isTransferable();

    /**
     * Returns the number of readable bytes which is equal to {@code (count - transferIndex)}.
     */
    long transferableBytes();

    /**
     * Returns a slice of this file region's sub-region. This method does not modify {@code transferIndex} of this
     * buffer.
     * <p>
     * Also be aware that this method will NOT call {@link #retain()} and so the reference count will NOT be increased.
     */
    FileRegion slice(long index, long length);

    /**
     * Returns a new slice of this file region's sub-region starting at the current {@code transferIndex} and increases
     * the {@code transferIndex} by the size of the new slice (= {@code length}).
     * <p>
     * Also be aware that this method will NOT call {@link #retain()} and so the reference count will NOT be increased.
     *
     * @param length    the size of the new slice
     */
    FileRegion transferSlice(long length);

    /**
     * Return the underlying file region instance if this file region is a wrapper of another file region.
     *
     * @return {@code null} if this file region is not a wrapper
     */
    FileRegion unwrap();

    /**
     * Return the underlying file channel instance of this file region.
     */
    FileChannel channel() throws IOException;

    @Override
    FileRegion retain();

    @Override
    FileRegion retain(int increment);

    @Override
    FileRegion touch();

    @Override
    FileRegion touch(Object hint);
}
