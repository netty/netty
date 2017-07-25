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
import java.nio.channels.FileChannel;

/**
 * A derived {@link FileChannelBasedFileRegion} which shares reference counting with its parent.
 */
final class FileChannelBasedSlicedFileRegion extends FileChannelBasedFileRegion {

    private final FileChannelBasedFileRegion parent;
    private final long offset;
    private final long count;

    FileChannelBasedSlicedFileRegion(FileChannelBasedFileRegion parent, long index, long count) {
        if (index < 0 || index > parent.count() - count) {
            throw new IndexOutOfBoundsException(parent + ".slice(" + index + ", " + count + ')');
        }

        if (parent instanceof FileChannelBasedSlicedFileRegion) {
            FileChannelBasedSlicedFileRegion sliced = (FileChannelBasedSlicedFileRegion) parent;
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
    public long count() {
        return count;
    }

    @Override
    public FileChannelBasedSlicedFileRegion retain() {
        parent.retain();
        return this;
    }

    @Override
    public FileChannelBasedSlicedFileRegion retain(int increment) {
        parent.retain(increment);
        return this;
    }

    @Override
    public FileChannelBasedSlicedFileRegion touch() {
        parent.touch();
        return this;
    }

    @Override
    public FileChannelBasedSlicedFileRegion touch(Object hint) {
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
    public FileChannelBasedFileRegion unwrap() {
        return parent;
    }

    @Override
    public boolean isOpen() {
        return parent.isOpen();
    }

    @Override
    public void open() throws IOException {
        parent.open();
    }

    @Override
    protected FileChannel channel() {
        return parent.channel();
    }
}
