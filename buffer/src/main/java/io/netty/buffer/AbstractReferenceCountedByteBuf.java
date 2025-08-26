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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import io.netty.util.internal.AtomicReferenceCountUpdater;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.ReferenceCountUpdater;
import io.netty.util.internal.UnsafeReferenceCountUpdater;
import io.netty.util.internal.VarHandleReferenceCountUpdater;

import static io.netty.util.internal.ReferenceCountUpdater.getUnsafeOffset;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 * Abstract base class for {@link ByteBuf} implementations that count references.
 */
public abstract class AbstractReferenceCountedByteBuf extends AbstractByteBuf {
    private static final long REFCNT_FIELD_OFFSET;
    private static final AtomicIntegerFieldUpdater<AbstractReferenceCountedByteBuf> AIF_UPDATER;
    private static final Object REFCNT_FIELD_VH;
    private static final ReferenceCountUpdater<AbstractReferenceCountedByteBuf> updater;

    static {
        switch (ReferenceCountUpdater.updaterTypeOf(AbstractReferenceCountedByteBuf.class, "refCnt")) {
            case Atomic:
                AIF_UPDATER = newUpdater(AbstractReferenceCountedByteBuf.class, "refCnt");
                REFCNT_FIELD_OFFSET = -1;
                REFCNT_FIELD_VH = null;
                updater = new AtomicReferenceCountUpdater<AbstractReferenceCountedByteBuf>() {
                    @Override
                    protected AtomicIntegerFieldUpdater<AbstractReferenceCountedByteBuf> updater() {
                        return AIF_UPDATER;
                    }
                };
                break;
            case Unsafe:
                AIF_UPDATER = null;
                REFCNT_FIELD_OFFSET = getUnsafeOffset(AbstractReferenceCountedByteBuf.class, "refCnt");
                REFCNT_FIELD_VH = null;
                updater = new UnsafeReferenceCountUpdater<AbstractReferenceCountedByteBuf>() {
                    @Override
                    protected long refCntFieldOffset() {
                        return REFCNT_FIELD_OFFSET;
                    }
                };
                break;
            case VarHandle:
                AIF_UPDATER = null;
                REFCNT_FIELD_OFFSET = -1;
                REFCNT_FIELD_VH = PlatformDependent.findVarHandleOfIntField(MethodHandles.lookup(),
                        AbstractReferenceCountedByteBuf.class, "refCnt");
                updater = new VarHandleReferenceCountUpdater<AbstractReferenceCountedByteBuf>() {
                    @Override
                    protected VarHandle varHandle() {
                        return (VarHandle) REFCNT_FIELD_VH;
                    }
                };
                break;
            default:
                throw new Error("Unknown updater type for AbstractReferenceCountedByteBuf");
        }
    }

    // Value might not equal "real" reference count, all access should be via the updater
    @SuppressWarnings({"unused", "FieldMayBeFinal"})
    private volatile int refCnt;

    protected AbstractReferenceCountedByteBuf(int maxCapacity) {
        super(maxCapacity);
        updater.setInitialValue(this);
    }

    @Override
    boolean isAccessible() {
        // Try to do non-volatile read for performance as the ensureAccessible() is racy anyway and only provide
        // a best-effort guard.
        return updater.isLiveNonVolatile(this);
    }

    @Override
    public int refCnt() {
        return updater.refCnt(this);
    }

    /**
     * An unsafe operation intended for use by a subclass that sets the reference count of the buffer directly
     */
    protected final void setRefCnt(int refCnt) {
        updater.setRefCnt(this, refCnt);
    }

    /**
     * An unsafe operation intended for use by a subclass that resets the reference count of the buffer to 1
     */
    protected final void resetRefCnt() {
        updater.resetRefCnt(this);
    }

    @Override
    public ByteBuf retain() {
        return updater.retain(this);
    }

    @Override
    public ByteBuf retain(int increment) {
        return updater.retain(this, increment);
    }

    @Override
    public ByteBuf touch() {
        return this;
    }

    @Override
    public ByteBuf touch(Object hint) {
        return this;
    }

    @Override
    public boolean release() {
        return handleRelease(updater.release(this));
    }

    @Override
    public boolean release(int decrement) {
        return handleRelease(updater.release(this, decrement));
    }

    private boolean handleRelease(boolean result) {
        if (result) {
            deallocate();
        }
        return result;
    }

    /**
     * Called once {@link #refCnt()} is equals 0.
     */
    protected abstract void deallocate();
}
