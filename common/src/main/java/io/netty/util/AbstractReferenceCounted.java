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
package io.netty.util;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import io.netty.util.internal.AtomicReferenceCountUpdater;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.ReferenceCountUpdater;
import io.netty.util.internal.ReferenceCountUpdater.UpdaterType;
import io.netty.util.internal.UnsafeReferenceCountUpdater;
import io.netty.util.internal.VarHandleReferenceCountUpdater;

import static io.netty.util.internal.ReferenceCountUpdater.getUnsafeOffset;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 * Abstract base class for classes wants to implement {@link ReferenceCounted}.
 */
public abstract class AbstractReferenceCounted implements ReferenceCounted {
    private static final long REFCNT_FIELD_OFFSET;
    private static final AtomicIntegerFieldUpdater<AbstractReferenceCounted> AIF_UPDATER;
    private static final Object REFCNT_FIELD_VH;
    private static final ReferenceCountUpdater<AbstractReferenceCounted> updater;

    static {
        UpdaterType updaterType = ReferenceCountUpdater.updaterTypeOf(AbstractReferenceCounted.class, "refCnt");
        switch (updaterType) {
            case Atomic:
                AIF_UPDATER = newUpdater(AbstractReferenceCounted.class, "refCnt");
                REFCNT_FIELD_OFFSET = -1;
                REFCNT_FIELD_VH = null;
                updater = new AtomicReferenceCountUpdater<AbstractReferenceCounted>() {
                    @Override
                    protected AtomicIntegerFieldUpdater<AbstractReferenceCounted> updater() {
                        return AIF_UPDATER;
                    }
                };
                break;
            case Unsafe:
                AIF_UPDATER = null;
                REFCNT_FIELD_OFFSET = getUnsafeOffset(AbstractReferenceCounted.class, "refCnt");
                REFCNT_FIELD_VH = null;
                updater = new UnsafeReferenceCountUpdater<AbstractReferenceCounted>() {
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
                        AbstractReferenceCounted.class, "refCnt");
                updater = new VarHandleReferenceCountUpdater<AbstractReferenceCounted>() {
                    @Override
                    protected VarHandle varHandle() {
                        return (VarHandle) REFCNT_FIELD_VH;
                    }
                };
                break;
            default:
                throw new Error("Unexpected updater type for AbstractReferenceCounted: " + updaterType);
        }
    }

    // Value might not equal "real" reference count, all access should be via the updater
    @SuppressWarnings({"unused", "FieldMayBeFinal"})
    private volatile int refCnt = updater.initialValue();

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

    @Override
    public ReferenceCounted retain() {
        return updater.retain(this);
    }

    @Override
    public ReferenceCounted retain(int increment) {
        return updater.retain(this, increment);
    }

    @Override
    public ReferenceCounted touch() {
        return touch(null);
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
