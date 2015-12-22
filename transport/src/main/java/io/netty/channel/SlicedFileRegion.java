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
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

/**
 * A derived {@link FileRegion} which shares reference counting with its parent.
 */
public class SlicedFileRegion extends AbstractFileRegion {

    private final FileRegion parent;

    private final long offset;

    private final long count;

    public SlicedFileRegion(FileRegion parent, long index, long count) {
        if (index < 0 || index > parent.count() - count) {
            throw new IndexOutOfBoundsException(parent + ".slice(" + index + ", " + count + ')');
        }

        if (parent instanceof SlicedFileRegion) {
            SlicedFileRegion sliced = (SlicedFileRegion) parent;
            this.parent = sliced.parent;
            this.offset = sliced.offset + index;
        } else {
            this.parent = parent;
            this.offset = index;
        }
        this.count = count;
    }

    @Override
    public long position() {
        return parent.position() + offset;
    }

    @Override
    public long transfered() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long count() {
        return count;
    }

    @Override
    public long transferTo(WritableByteChannel target, long position) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public SlicedFileRegion retain() {
        parent.retain();
        return this;
    }

    @Override
    public SlicedFileRegion retain(int increment) {
        parent.retain(increment);
        return this;
    }

    @Override
    public SlicedFileRegion touch() {
        parent.touch();
        return this;
    }

    @Override
    public SlicedFileRegion touch(Object hint) {
        parent.touch(hint);
        return this;
    }

    @Override
    public int refCnt() {
        return parent.refCnt();
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
    public FileRegion unwrap() {
        return parent;
    }

    @Override
    public FileChannel channel() throws IOException {
        return parent.channel();
    }
}
