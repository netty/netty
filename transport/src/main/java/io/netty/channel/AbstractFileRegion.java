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

    private final long endPosition;
    private long readPosition;

    protected AbstractFileRegion(long readPosition, long length) {
        if (readPosition < 0) {
            throw new IllegalArgumentException("readPosition must be >= 0 but was " + readPosition);
        }
        if (length < 0) {
            throw new IllegalArgumentException("length must be >= 0 but was " + length);
        }
        if (Long.MAX_VALUE - length < readPosition) {
            throw new IllegalArgumentException("Overflow calculating end position");
        }

        this.readPosition = readPosition;
        endPosition = readPosition + length;
    }

    @Override
    public long readPosition() {
        return readPosition;
    }

    @Override
    public long readableBytes() {
        return endPosition - readPosition;
    }

    @Override
    public boolean isReadable() {
        return readableBytes() > 0;
    }

    @Override
    public FileRegion skipBytes(long length) {
        verifyReadable(length);
        readPosition += length;
        return this;
    }

    @Override
    public long readTo(WritableByteChannel target, long length) throws IOException {
        verifyReadable(length);
        if (length == 0) {
            return 0L;
        }
        if (refCnt() == 0) {
            throw new IllegalReferenceCountException(0);
        }

        long written = channel().transferTo(this.readPosition, length, target);
        if (written > 0) {
            readPosition += written;
        }
        return written;
    }

    @Override
    public FileRegion slice() {
        return slice(readPosition(), readableBytes());
    }

    @Override
    public FileRegion slice(long position, long length) {
        verifyReadable(length);
        if (position < readPosition || endPosition - length < position) {
            throw new IllegalArgumentException("Slice must be within readable region");
        }
        return new SlicedFileRegion(this, position, length);
    }

    @Override
    public FileRegion readSlice(long length) {
        FileRegion slice = slice(readPosition, length);
        readPosition += length;
        return slice;
    }

    private void verifyReadable(long length) {
        if (length < 0 || length > readableBytes()) {
            throw new IllegalArgumentException("length must be within readable region: " + length);
        }
    }
}
