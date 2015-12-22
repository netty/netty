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
package io.netty.channel;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;

/**
 * Base class for {@link FileRegion} implementation.
 *
 */
public abstract class AbstractFileRegion implements FileRegion {

    private long transferIndex;

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
    public FileRegion transferIndex(long index) {
        if (index < 0 || index > count()) {
            throw new IndexOutOfBoundsException(String.format(
                    "transferIndex: %d (expected: 0 <= transferIndex <= count(%d))", index, count()));
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
            throw new IllegalArgumentException("position out of range: " + position + " (expected: 0 - "
                    + (this.count() - 1) + ')');
        }
        if (length < 0) {
            throw new IllegalArgumentException("negative length " + length);
        }
        length = Math.min(count() - position, length);
        if (length == 0) {
            return 0L;
        }
        return channel().transferTo(position() + position, length, target);
    }

    @Override
    public FileRegion slice(long index, long length) {
        return new SlicedFileRegion(this, index, length);
    }

    @Override
    public FileRegion transferSlice(long length) {
        FileRegion sliced = slice(transferIndex(), length);
        transferIndex += length;
        return sliced;
    }
}
