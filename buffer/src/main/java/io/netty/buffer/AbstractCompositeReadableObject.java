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

import static java.lang.Math.min;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Base class for any composite {@link ReadableObject}.
 */
public abstract class AbstractCompositeReadableObject extends AbstractReadableObject {

    protected final Deque<Component> components = new ArrayDeque<Component>(4);
    protected long readerLimit;

    public AbstractCompositeReadableObject() {
        super(0);
    }

    @Override
    public final long readerLimit() {
        return readerLimit;
    }

    /**
     * Discards all read bytes. Calls the {@link #removeComponent0()} to perform the removal of
     * any fully read component.
     */
    @Override
    @SuppressWarnings("unchecked")
    public ReadableObject discardReadBytes() {
        // Discard all fully read components and save the offset of the reader position.
        long offset = readerPosition();
        for (;;) {
            Component c = components.peek();
            if (c == null) {
                break;
            }

            if (readerPosition() < c.endPos()) {
                // Found the new first component.
                if (offset > 0) {
                    // Shift the readable region so that it begins at the previous readerPosition.
                    c.shiftReadableRegion(offset);
                }
                break;
            }

            offset -= c.length();
            removeComponent0();
        }

        // Shift the start position for all remaining components.
        updateComponentOffsets(readerPosition());
        return this;
    }

    /**
     * Delegates to {@link #discardReadBytes()}.
     */
    @Override
    @SuppressWarnings("unchecked")
    public ReadableObject discardSomeReadBytes() {
        return discardReadBytes();
    }

    @Override
    protected long readTo0(WritableByteChannel target, long pos, long length) throws IOException {
        final long startPos = pos;
        final long endPos = pos + length;
        for (Component c : components) {
            if (pos < endPos) {
                break;
            }

            if (readerPosition() < c.offset) {
                // Should never happen.
                throw new IllegalStateException("Composite is corrupted.");
            }

            if (readerPosition() > c.endPos()) {
                // We've already read past this comopnent. Go to the next one.
                continue;
            }

            // Read the bytes to the channel.
            long bytesToTransfer = min(length, c.endPos() - readerPosition());
            long bytesTransferred = c.readableObject.readTo(target, pos - c.offset, bytesToTransfer);
            pos += bytesTransferred;

            if (bytesTransferred < bytesToTransfer) {
                // Stop at the first component that fails to transfer all of the bytes.
                break;
            }
        }

        return pos - startPos;
    }

    /**
     * Updates the offset for all components by the given delta.
     */
    protected void updateComponentOffsets(long delta) {
        if (delta > 0) {
            for (Component c : components) {
                c.offset -= delta;
            }
            readerLimit -= delta;
            readerPosition(readerPosition() - delta);
        }
    }

    /**
     * Performs the removal operation on the component at the head of the queue.
     */
    private Component removeComponent0()  {
        Component c = components.poll();
        if (c != null) {
            c.release();
        }
        return c;
    }

    /**
     * A wrapper around a component {@link ReadableObject} that maintains an offset of the
     * start of its readable region relative to the start of the composite.
     */
    protected static final class Component {
        protected ReadableObject readableObject;
        protected long offset;
        private boolean own;

        protected Component(long offset, ReadableObject readableObject) {
            this(offset, readableObject, true);
        }

        protected Component(long offset, ReadableObject readableObject, boolean own) {
            this.offset = offset;
            this.readableObject = readableObject;
            this.own = own;
        }

        protected long length() {
            return readableObject.readableBytes();
        }

        protected long endPos() {
            return offset + length();
        }

        protected Component shiftReadableRegion(long pos) {
            readableObject = readableObject.slice(pos, readableObject.readableBytes() - pos);
            return this;
        }

        protected void retain() {
            retain(1);
        }

        /**
         * Retains the internal {@link ReadableObject} and assumes ownership of it.
         */
        protected void retain(int increment) {
            readableObject.retain(increment);
            own = true;
        }

        protected void release() {
            release(1);
        }

        /**
         * Releases the {@link ReadableObject} if this component owns it.
         */
        protected boolean release(int decrement) {
            return !own || readableObject.release(decrement);
        }
    }
}
