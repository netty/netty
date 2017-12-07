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

import io.netty.util.IllegalReferenceCountException;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

/**
 * Base class for {@link FileRegion} implementations which based on a {@link FileChannel}.
 */
public abstract class FileChannelBasedFileRegion implements SliceableFileRegion {

    private long transferIndex;
    private long transferred;

    @Override
    public long transferBytesTo(WritableByteChannel target, long length) throws IOException {
        long written = transferBytesTo(target, transferIndex, length);
        if (written > 0) {
            transferIndex += written;
        }
        return written;
    }

    @Override
    public long transferIndex() {
        return transferIndex;
    }

    @Override
    public FileChannelBasedFileRegion transferIndex(long index) {
        if (index < 0 || index > count()) {
            throw new IndexOutOfBoundsException(
                    String.format("transferIndex: %d (expected: 0 <= transferIndex <= count(%d))",
                            index, count()));
        }
        this.transferIndex = index;
        return this;
    }

    @Override
    public boolean isTransferable() {
        return transferIndex < count();
    }

    @Override
    public long transferableBytes() {
        return count() - transferIndex;
    }

    @Override
    public long transferBytesTo(WritableByteChannel target, long position, long length) throws IOException {
        if (position < 0 || position > count()) {
            throw new IllegalArgumentException(String
                    .format("position out of range: %d (expected: 0 - %d)", position, count() - 1));
        }
        if (length < 0) {
            throw new IllegalArgumentException("negative length " + length);
        }
        length = Math.min(count() - position, length);
        if (length == 0) {
            return 0;
        }
        if (refCnt() == 0) {
            throw new IllegalReferenceCountException(0);
        }
        // Call open to make sure fc is initialized. This is a no-op if we called it before.
        open();
        return channel().transferTo(position() + position, length, target);
    }

    @Override
    public SliceableFileRegion slice(long index, long length) {
        return new FileChannelBasedSlicedFileRegion(this, index, length);
    }

    @Override
    public SliceableFileRegion retainedSlice(long index, long length) {
        SliceableFileRegion sliced = slice(index, length);
        sliced.retain();
        return sliced;
    }

    @Override
    public SliceableFileRegion transferSlice(long length) {
        SliceableFileRegion sliced = slice(transferIndex(), length);
        transferIndex += length;
        return sliced;
    }

    @Override
    public SliceableFileRegion transferRetainedSlice(long length) {
        SliceableFileRegion sliced = transferSlice(length);
        sliced.retain();
        return sliced;
    }

    /**
     * Explicitly open the underlying file-descriptor if not done yet.
     * <p>
     * The implementation should be idempotent.
     */
    public abstract void open() throws IOException;

    /**
     * Returns {@code true} if the {@link FileChannelBasedFileRegion} has a open file-descriptor
     */
    public abstract boolean isOpen();

    /**
     * Return the underlying file channel instance of this file region. Will return {@code null} if
     * the underlying file channel has not been opened yet.
     */
    protected abstract FileChannel channel();

    // The methods below are only used to keep compatible with old APIs.
    @Override
    public long transfered() {
        return transferred;
    }

    @Override
    public long transferred() {
        return transferred;
    }

    @Override
    public long transferTo(WritableByteChannel target, long position) throws IOException {
        // pass Long.MAX_VALUE to indicate that transfer as much as possible.
        long written = transferBytesTo(target, position, Long.MAX_VALUE);
        if (written > 0) {
            transferred += written;
        }
        return written;
    }
}
