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
import static java.lang.Math.max;

import io.netty.util.AbstractReferenceCounted;
import io.netty.util.ReferenceCounted;

/**
 * A composite wrapper around a number of {@link ReadableObject}s.
 */
public class CompositeReadableObject extends AbstractCompositeReadableObject {

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

    /**
     * Adds a component to the end of this composite.
     */
    public void add(ReadableObject readableObject) {
        checkNotNull(readableObject, "readableObject");

        Component c;
        if (components.isEmpty()) {
            c = new Component(0, readableObject);
        } else {
            c = new Component(components.getLast().endPos(), readableObject);
        }
        components.add(c);
        readerLimit += readableObject.readableBytes();
    }

    /**
     * Returns the number of components are contained within this composite.
     */
    public int size() {
        return components.size();
    }

    /**
     * Returns {@code true} if there are no components.
     */
    public boolean isEmpty() {
        return components.isEmpty();
    }

    /**
     * Returns but does not remove the first component, or {@code null} if empty.
     */
    public ReadableObject peek() {
        Component c = components.peek();
        return c == null ? null : c.readableObject;
    }

    /**
     * Removes and releases the first component in this composite and updates the
     * {@code readerPosition} to be relative to the next component.
     */
    public CompositeReadableObject remove() {
        Component c = removeComponent();
        updateComponentOffsets(c.offset);
        return this;
    }

    /**
     * Removes all components.
     */
    public void clear() {
        while (components.isEmpty()) {
            removeComponent();
        }
        readerLimit = 0;
        readerPosition(0);
    }

    @Override
    public CompositeReadableObject discardSomeReadBytes() {
        super.discardSomeReadBytes();
        return this;
    }

    @Override
    public CompositeReadableObject discardReadBytes() {
        super.discardReadBytes();
        return this;
    }

    @Override
    public CompositeReadableObject readerPosition(long readerPosition) {
        super.readerPosition(readerPosition);
        return this;
    }

    @Override
    public CompositeReadableObject skipBytes(long length) {
        super.skipBytes(length);
        return this;
    }

    @Override
    public ReadableObject slice(long position, long length) {
        return new CompositeSlice(position, length);
    }

    @Override
    public CompositeReadableObject retain(int increment) {
        refCnt.retain(increment);
        return this;
    }

    @Override
    public CompositeReadableObject retain() {
        refCnt.retain();
        return this;
    }

    @Override
    public CompositeReadableObject touch() {
        refCnt.touch();
        return this;
    }

    @Override
    public CompositeReadableObject touch(Object hint) {
        refCnt.touch(hint);
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

    /**
     * Removes and releases the component.
     */
    protected Component removeComponent() {
        Component c = components.poll();
        if (c != null) {
            c.release();
        }
        return c;
    }

    /**
     * A slice of a {@link CompositeReadableObject} that maintains slices of all individual
     * {@link ReadableObject}s contained within the sliced region of the parent. All reference
     * counting is delegated to the parent.
     */
    private final class CompositeSlice extends AbstractCompositeReadableObject {
        private long offset;

        public CompositeSlice(long offset, long length) {
            if (length < 0 || offset < 0 || Long.MAX_VALUE - length < offset ||
                    offset > CompositeReadableObject.this.readerLimit() - length) {
                throw new IndexOutOfBoundsException(
                        "compositeSlice(" + offset + ", " + length + ')');
            }

            this.offset = offset;
            readerLimit = this.offset + length;

            // Copy over the components for the requested range.
            copyComponents(offset, length);
        }

        @Override
        public ReadableObject slice(long position, long length) {
            return new CompositeSlice(offset + position, length);
        }

        @Override
        public CompositeReadableObject unwrap() {
            return CompositeReadableObject.this;
        }

        @Override
        public int refCnt() {
            return CompositeReadableObject.this.refCnt();
        }

        @Override
        public CompositeSlice retain() {
            return retain(1);
        }

        /**
         * Retains the parent and each component.
         */
        @Override
        public CompositeSlice retain(int increment) {
            CompositeReadableObject.this.retain(increment);

            // Also retain each of the components.
            for (Component c : components) {
                c.retain(increment);
            }
            return this;
        }

        @Override
        public boolean release() {
            return release(1);
        }

        /**
         * Releases the parent and each component.
         */
        @Override
        public boolean release(int decrement) {
            if (CompositeReadableObject.this.release(decrement)) {
                // Also retain each of the components.
                for (Component c : components) {
                    if (!c.release(decrement)) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }

        @Override
        public CompositeSlice touch() {
            CompositeReadableObject.this.touch();
            return this;
        }

        @Override
        public CompositeSlice touch(Object hint) {
            CompositeReadableObject.this.touch(hint);
            return this;
        }

        /**
         * Copies the components for the given readable range to the given deque.
         */
        private void copyComponents(long start, long length) {
            CompositeReadableObject parent = CompositeReadableObject.this;
            components.clear();
            readerLimit = 0L;
            for (Component c : parent.components) {
                if (c.endPos() < start) {
                    // Not in range yet.
                    continue;
                }

                long end = start + length;
                if (c.offset >= end) {
                    // done.
                    break;
                }

                // Create an appropriate slice of the readable object.
                ReadableObject obj = c.readableObject;
                long sliceStart = max(0, start - c.offset);
                long sliceLength = obj.readableBytes() - (sliceStart + max(0, c.endPos() - end));
                obj = obj.slice(sliceStart, sliceLength);

                components.add(new Component(readerLimit, obj, false));
                readerLimit += obj.readableBytes();
            }
        }
    }
}
