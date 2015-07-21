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

import static io.netty.util.internal.ObjectUtil.checkNotNull;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;

/**
 * A derived {@link ReadableObject} which shares reference counting with its parent.
 */
public class SlicedReadableObject extends AbstractReadableObject {

    private final ReadableObject parent;
    private final long readerLimit;
    private long offset;

    public SlicedReadableObject(ReadableObject parent, long offset, long length) {
        super(0);

        this.parent = checkNotNull(parent, "parent");
        if (parent.unwrap() != null) {
            throw new IllegalArgumentException("Cannot slice wrapped objects");
        }

        if (length < 0 || offset < 0 || parent.readerLimit() - length < offset) {
            throw new IndexOutOfBoundsException("slice(" + offset + ", " + length + ')');
        }

        this.offset = offset;
        readerLimit = this.offset + length;
    }

    public long offset() {
        return offset;
    }

    @Override
    public ReadableObject slice(long position, long length) {
        if (position < readerPosition() || readerLimit() - length < position) {
            throw new IllegalArgumentException("Slice must be within readable region");
        }

        // Call slice on the parent, applying the offset.
        return parent.slice(position + offset, length);
    }

    @Override
    public ReadableObject retain() {
        parent.retain();
        return this;
    }

    @Override
    public ReadableObject retain(int increment) {
        parent.retain(increment);
        return this;
    }

    @Override
    public ReadableObject unwrap() {
        return parent;
    }

    @Override
    public int refCnt() {
        return parent.refCnt();
    }

    @Override
    public long readerLimit() {
        return readerLimit - offset;
    }

    @Override
    public ReadableObject touch() {
        parent.touch();
        return this;
    }

    @Override
    public ReadableObject touch(Object hint) {
        parent.touch(hint);
        return this;
    }

    @Override
    public boolean release() {
        return parent.release();
    }

    @Override
    public boolean release(int decrement) {
        return parent.release(decrement);
    }

    @Override
    public ReadableObject discardReadBytes() {
        offset += readerPosition();
        readerPosition(0);
        return this;
    }

    @Override
    public ReadableObject discardSomeReadBytes() {
        return discardReadBytes();
    }

    @Override
    protected long readTo0(WritableByteChannel target, long pos, long length) throws IOException {
        return parent.readTo(target, pos + offset, length);
    }
}
