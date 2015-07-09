/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.channel;

import io.netty.util.IllegalReferenceCountException;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;

/**
 * A skeletal implementation of a {@link FileRegion}.
 */
public abstract class AbstractFileRegion implements FileRegion {

    protected final long position;

    protected final long count;

    protected long transfered;

    protected AbstractFileRegion(long position, long count) {
        this.position = position;
        this.count = count;
    }

    @Override
    public long position() {
        return position;
    }

    @Override
    public long transfered() {
        return transfered;
    }

    @Override
    public long count() {
        return count;
    }

    @Override
    public long transferTo(WritableByteChannel target, long position) throws IOException {
        long remaining = count - position;
        if (remaining < 0 || position < 0) {
            throw new IllegalArgumentException("position out of range: " + position + " (expected: 0 - " + (count - 1)
                    + ')');
        }
        if (remaining == 0) {
            return 0L;
        }
        if (refCnt() == 0) {
            throw new IllegalReferenceCountException(0);
        }

        long written = channel().transferTo(this.position + position, remaining, target);
        if (written > 0) {
            transfered += written;
        }
        return written;
    }

    @Override
    public SlicedFileRegion readSlice(long length) {
        if (length < 0) {
            throw new IllegalArgumentException("negative length " + length);
        }
        if (length > count - transfered) {
            throw new IllegalArgumentException("length " + length + " too large, max allowed is "
                    + (count - transfered));
        }
        SlicedFileRegion sliced = new SlicedFileRegion(this, position + transfered, length);
        transfered += length;
        return sliced;
    }

}
