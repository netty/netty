/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.buffer;

/**
 * Abstract base class for {@link ByteBuf} implementations that count references.
 */
public abstract class AbstractReferenceCountedByteBuf extends AbstractByteBuf {
    protected AbstractReferenceCountedByteBuf(int maxCapacity) {
        super(maxCapacity);
    }

    @Override
    boolean isAccessible() {
        // Try to do non-volatile read for performance as the ensureAccessible() is racy anyway and only provide
        // a best-effort guard.
        return updater.isLiveNonVolatile(this);
    }

    /**
     * An unsafe operation intended for use by a subclass that sets the reference count of the buffer directly
     */
    @Override
    protected final void setRefCnt(int refCnt) {
        super.setRefCnt(refCnt);
    }

    /**
     * An unsafe operation intended for use by a subclass that resets the reference count of the buffer to 1
     */
    protected final void resetRefCnt() {
        updater.resetRefCnt(this);
    }

    @Override
    public ByteBuf touch(Object hint) {
        return this;
    }

    /**
     * Called once {@link #refCnt()} is equals 0.
     */
    @Override
    protected abstract void deallocate(); // Re-abstract the empty method in ByteBuf.
}
