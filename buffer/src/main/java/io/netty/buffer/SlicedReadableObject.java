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
package io.netty.buffer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.GatheringByteChannel;

/**
 * A derived {@link ReadableObject} which shares reference counting with its parent.
 */
public class SlicedReadableObject extends AbstractReadableObject {

    private final ReadableObject parent;

    private final long readerLimit;

    private long offset;

    public SlicedReadableObject(ReadableObject parent, long offset, long length) {
        super(0);
        if (length < 0 || offset < 0 || parent.objectReaderLimit() - length < offset) {
            throw new IndexOutOfBoundsException("slice(" + offset + ", " + length + ')');
        }
        if (parent instanceof SlicedReadableObject) {
            this.parent = parent.unwrapObject();
            this.offset = ((SlicedReadableObject) parent).offset + offset;
        } else {
            this.parent = parent;
            this.offset = offset;
        }
        this.readerLimit = this.offset + length;
    }

    @Override
    public long objectReaderLimit() {
        return readerLimit;
    }

    @Override
    public ReadableObject unwrapObject() {
        return parent;
    }

    @Override
    protected long getObjectBytes0(GatheringByteChannel out, long pos, long length) throws IOException {
        return parent.getObjectBytes(out, offset + pos, length);
    }

    @Override
    protected SlicedReadableObject getObjectBytes0(OutputStream out, long pos, long length) throws IOException {
        parent.getObjectBytes(out, offset + pos, length);
        return this;
    }

    @Override
    protected long getObjectBytes0(SingleReadableObjectWriter out, long pos, long length) throws IOException {
        return parent.getObjectBytes(out, offset + pos, length);
    }

    @Override
    public int refCnt() {
        return parent.refCnt();
    }

    @Override
    public SlicedReadableObject retain() {
        parent.retain();
        return this;
    }

    @Override
    public SlicedReadableObject retain(int increment) {
        parent.retain(increment);
        return this;
    }

    @Override
    public SlicedReadableObject touch(Object hint) {
        parent.touch(hint);
        return this;
    }

    @Override
    public SlicedReadableObject touch() {
        parent.touch();
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

}
