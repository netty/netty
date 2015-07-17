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
package io.netty.buffer;

import io.netty.util.IllegalReferenceCountException;
import io.netty.util.ResourceLeak;
import io.netty.util.ResourceLeakDetector;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;

/**
 * A skeletal implementation of a {@link ReadableObject}.
 */
public abstract class AbstractReadableObject implements ReadableObject {
    private static final ResourceLeakDetector<ReadableObject> leakDetector =
            new ResourceLeakDetector<ReadableObject>(ReadableObject.class);

    private final ResourceLeak leak;
    private long readerPosition;

    public AbstractReadableObject(long readerPosition) {
        this.readerPosition = readerPosition;
        leak = leakDetector.open(this);
    }

    @Override
    public final long readerPosition() {
        return readerPosition;
    }

    @Override
    public final long readableBytes() {
        return readerLimit() - readerPosition;
    }

    @Override
    public ReadableObject readerPosition(long readerPosition) {
        if (readerPosition < 0 || readerPosition > readerLimit()) {
            throw new IndexOutOfBoundsException("readerPosition " + readerPosition);
        }
        this.readerPosition = readerPosition;
        return this;
    }

    @Override
    public final boolean isReadable() {
        return readableBytes() > 0;
    }

    @Override
    public final boolean isReadable(long size) {
        return readableBytes() >= size;
    }

    @Override
    public ReadableObject skipBytes(long length) {
        ensureReadable(length);
        readerPosition += length;
        return this;
    }

    @Override
    public final long readTo(WritableByteChannel target, long pos, long length) throws IOException {
        if (refCnt() == 0) {
            throw new IllegalReferenceCountException(0);
        }
        if (pos < 0 || length < 0 || readerLimit() - length < pos) {
            throw new IndexOutOfBoundsException(String.format("pos: %d, length: %d", pos, length));
        }
        if (length == 0) {
            return 0L;
        }

        return readTo0(target, pos, length);
    }

    @Override
    public final ReadableObject slice() {
        return slice(readerPosition, readableBytes());
    }

    @Override
    public ReadableObject slice(long position, long length) {
        if (length < 0 || position < readerPosition || readerLimit() - length < position) {
            throw new IllegalArgumentException("Slice must be within readable region");
        }
        return new SlicedReadableObject(this, position, length);
    }

    @Override
    public final ReadableObject readSlice(long length) {
        ReadableObject slice = slice(readerPosition, length);
        readerPosition += length;
        return slice;
    }

    /**
     * Default implementation, just returns {@code null}.
     */
    @Override
    public ReadableObject unwrap() {
        return null;
    }

    @Override
    public ReadableObject retain() {
        return retain(1);
    }

    @Override
    public ReadableObject touch() {
        return touch(null);
    }

    @Override
    public ReadableObject touch(Object hint) {
        touch0(hint);
        if (leak != null) {
            leak.record(hint);
        }
        return this;
    }

    @Override
    public final boolean release() {
        if (release0(1)) {
            if (leak != null) {
                leak.close();
            }
            return true;
        }
        return false;
    }

    @Override
    public final boolean release(int decrement) {
        if (release0(decrement)) {
            if (leak != null) {
                leak.close();
            }
            return true;
        }
        return false;
    }

    protected abstract long readTo0(WritableByteChannel target, long pos, long length) throws IOException;
    protected abstract boolean release0(int decrement);

    protected ReadableObject touch0(Object hint) {
        return this;
    }

    /**
     * Should be called by every method that tries to access the content to check
     * if the object was released before.
     */
    protected final void ensureAccessible() {
        if (refCnt() == 0) {
            throw new IllegalReferenceCountException(0);
        }
    }

    /**
     * Verifies that this object is accessible and that there are enough readable bytes to
     * satisfy the requested length.
     */
    protected final void ensureReadable(long length) {
        ensureAccessible();
        if (length < 0 || length > readableBytes()) {
            throw new IllegalArgumentException("length must be within readable region: " + length);
        }
    }
}
