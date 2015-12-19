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

import io.netty.util.AbstractReferenceCounted;
import io.netty.util.ReferenceCounted;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.GatheringByteChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * A composite wrapper around a number of {@link ReadableObject}s.
 */
public class CompositeReadableObject extends AbstractReadableObject {

    private final AbstractReferenceCounted refCnt = new AbstractReferenceCounted() {

        @Override
        protected void deallocate() {
            clear();
        }

        @Override
        public ReferenceCounted touch(Object hint) {
            return CompositeReadableObject.this;
        }
    };

    private static final class Component {
        final ReadableObject obj;
        final long length;
        final long offset;
        final long endOffset;

        Component(ReadableObject obj, long offset) {
            this.obj = obj;
            this.length = obj.objectReadableBytes();
            this.offset = offset;
            this.endOffset = offset + length;
        }
    }

    private final List<Component> components = new ArrayList<Component>();
    private long readerLimit;

    public CompositeReadableObject() {
        super(0);
    }

    public CompositeReadableObject addComponents(ReadableObject... objs) {
        for (ReadableObject obj : objs) {
            Component c = new Component(obj, readerLimit);
            components.add(c);
            readerLimit = c.endOffset;
        }
        return this;
    }

    @Override
    public long objectReaderLimit() {
        return readerLimit;
    }

    @Override
    public ReadableObject unwrapObject() {
        return null;
    }

    private Component getComponent(long pos) {
        for (int low = 0, high = components.size(); low <= high;) {
            int mid = low + high >>> 1;
            Component c = components.get(mid);
            if (pos >= c.endOffset) {
                low = mid + 1;
            } else if (pos < c.offset) {
                high = mid - 1;
            } else {
                return components.get(mid);
            }
        }
        throw new Error("should not reach here");
    }

    @Override
    protected long getObjectBytes0(GatheringByteChannel out, long pos, long length) throws IOException {
        Component c = getComponent(pos);
        return c.obj.getObjectBytes(out, pos - c.offset, Math.min(length, c.length));
    }

    @Override
    protected ReadableObject getObjectBytes0(OutputStream out, long pos, long length) throws IOException {
        Component c = getComponent(pos);
        c.obj.getObjectBytes(out, pos - c.offset, Math.min(length, c.length));
        return this;
    }

    @Override
    protected long getObjectBytes0(SingleReadableObjectWriter out, long pos, long length) throws IOException {
        Component c = getComponent(pos);
        return c.obj.getObjectBytes(out, pos - c.offset, Math.min(length, c.length));
    }

    public void clear() {
        for (Component c : components) {
            c.obj.release();
        }
        components.clear();
        readerLimit = 0;
        objectReaderPosition(0);
    }

    @Override
    public CompositeReadableObject retain() {
        refCnt.retain();
        return this;
    }

    @Override
    public CompositeReadableObject retain(int increment) {
        refCnt.retain(increment);
        return this;
    }

    @Override
    public CompositeReadableObject touch(Object hint) {
        refCnt.touch(hint);
        return this;
    }

    @Override
    public CompositeReadableObject touch() {
        refCnt.touch();
        return this;
    }

    @Override
    public int refCnt() {
        return refCnt.refCnt();
    }

    @Override
    public boolean release() {
        return refCnt.release();
    }

    @Override
    public boolean release(int decrement) {
        return refCnt.release(decrement);
    }

}
