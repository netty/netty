/*
 * Copyright 2025 The Netty Project
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
package io.netty.util.internal;

import io.netty.util.IllegalReferenceCountException;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.netty.util.internal.ObjectUtil.checkPositive;

/**
 * Concrete class for reference counting implementations.
 * Provides a factory method to create instances using the most efficient available atomic updater.
 */
public class RefCnt {

    private static final int UNSAFE = 0;
    private static final int VAR_HANDLE = 1;
    private static final int ATOMIC_UPDATER = 2;
    private static final int REF_CNT_IMPL;

    static {
        if (PlatformDependent.hasUnsafe()) {
            REF_CNT_IMPL = UNSAFE;
        } else if (PlatformDependent.hasVarHandle()) {
            REF_CNT_IMPL = VAR_HANDLE;
        } else {
            REF_CNT_IMPL = ATOMIC_UPDATER;
        }
    }

    /*
     * Implementation notes:
     *
     * For the updated int field:
     *   Even => "real" refcount is (refCnt >>> 1)
     *   Odd  => "real" refcount is 0
     */
    volatile int value;

    public RefCnt() {
        switch (REF_CNT_IMPL) {
        case UNSAFE:
            UnsafeRefCnt.init(this);
            break;
        case VAR_HANDLE:
            VarHandleRefCnt.init(this);
            break;
        case ATOMIC_UPDATER:
        default:
            AtomicRefCnt.init(this);
            break;
        }
    }

    private static long getUnsafeOffset(Class<?> clz, String fieldName) {
        try {
            if (PlatformDependent.hasUnsafe()) {
                return PlatformDependent.objectFieldOffset(clz.getDeclaredField(fieldName));
            }
        } catch (Throwable ignore) {
            // fall-back
        }
        return -1;
    }

    /**
     * Returns the current reference count of the given {@code RefCnt} instance with a load acquire semantic.
     *
     * @param ref the target RefCnt instance
     * @return the reference count
     */
    public static int refCnt(RefCnt ref) {
        switch (REF_CNT_IMPL) {
        case UNSAFE:
            return UnsafeRefCnt.refCnt(ref);
        case VAR_HANDLE:
            return VarHandleRefCnt.refCnt(ref);
        case ATOMIC_UPDATER:
        default:
            return AtomicRefCnt.refCnt(ref);
        }
    }

    /**
     * Increases the reference count of the given {@code RefCnt} instance by 1.
     *
     * @param ref the target RefCnt instance
     */
    public static void retain(RefCnt ref) {
        switch (REF_CNT_IMPL) {
        case UNSAFE:
            UnsafeRefCnt.retain(ref);
            break;
        case VAR_HANDLE:
            VarHandleRefCnt.retain(ref);
            break;
        case ATOMIC_UPDATER:
        default:
            AtomicRefCnt.retain(ref);
            break;
        }
    }

    /**
     * Increases the reference count of the given {@code RefCnt} instance by the specified increment.
     *
     * @param ref       the target RefCnt instance
     * @param increment the amount to increase the reference count by
     * @throws IllegalArgumentException if increment is not positive
     */
    public static void retain(RefCnt ref, int increment) {
        switch (REF_CNT_IMPL) {
        case UNSAFE:
            UnsafeRefCnt.retain(ref, increment);
            break;
        case VAR_HANDLE:
            VarHandleRefCnt.retain(ref, increment);
            break;
        case ATOMIC_UPDATER:
        default:
            AtomicRefCnt.retain(ref, increment);
            break;
        }
    }

    /**
     * Decreases the reference count of the given {@code RefCnt} instance by 1.
     *
     * @param ref the target RefCnt instance
     * @return true if the reference count became 0 and the object should be deallocated
     */
    public static boolean release(RefCnt ref) {
        switch (REF_CNT_IMPL) {
        case UNSAFE:
            return UnsafeRefCnt.release(ref);
        case VAR_HANDLE:
            return VarHandleRefCnt.release(ref);
        case ATOMIC_UPDATER:
        default:
            return AtomicRefCnt.release(ref);
        }
    }

    /**
     * Decreases the reference count of the given {@code RefCnt} instance by the specified decrement.
     *
     * @param ref       the target RefCnt instance
     * @param decrement the amount to decrease the reference count by
     * @return true if the reference count became 0 and the object should be deallocated
     * @throws IllegalArgumentException if decrement is not positive
     */
    public static boolean release(RefCnt ref, int decrement) {
        switch (REF_CNT_IMPL) {
        case UNSAFE:
            return UnsafeRefCnt.release(ref, decrement);
        case VAR_HANDLE:
            return VarHandleRefCnt.release(ref, decrement);
        case ATOMIC_UPDATER:
        default:
            return AtomicRefCnt.release(ref, decrement);
        }
    }

    /**
     * Returns {@code true} if and only if the given reference counter is alive.
     * This method is useful to check if the object is alive without incurring the cost of a volatile read.
     *
     * @param ref the target RefCnt instance
     * @return {@code true} if alive
     */
    public static boolean isLiveNonVolatile(RefCnt ref) {
        switch (REF_CNT_IMPL) {
        case UNSAFE:
            return UnsafeRefCnt.isLiveNonVolatile(ref);
        case VAR_HANDLE:
            return VarHandleRefCnt.isLiveNonVolatile(ref);
        case ATOMIC_UPDATER:
        default:
            return AtomicRefCnt.isLiveNonVolatile(ref);
        }
    }

    /**
     * <strong>WARNING:</strong>
     * An unsafe operation that sets the reference count of the given {@code RefCnt} instance directly.
     *
     * @param ref    the target RefCnt instance
     * @param refCnt new reference count
     */
    public static void setRefCnt(RefCnt ref, int refCnt) {
        switch (REF_CNT_IMPL) {
        case UNSAFE:
            UnsafeRefCnt.setRefCnt(ref, refCnt);
            break;
        case VAR_HANDLE:
            VarHandleRefCnt.setRefCnt(ref, refCnt);
            break;
        case ATOMIC_UPDATER:
        default:
            AtomicRefCnt.setRefCnt(ref, refCnt);
            break;
        }
    }

    /**
     * Resets the reference count of the given {@code RefCnt} instance to 1.
     * <p>
     * <strong>Warning:</strong> This method uses release memory semantics, meaning the change may not be
     * immediately visible to other threads. It should only be used in quiescent states where no other
     * threads are accessing the reference count.
     *
     * @param ref the target RefCnt instance
     */
    public static void resetRefCnt(RefCnt ref) {
        switch (REF_CNT_IMPL) {
        case UNSAFE:
            UnsafeRefCnt.resetRefCnt(ref);
            break;
        case VAR_HANDLE:
            VarHandleRefCnt.resetRefCnt(ref);
            break;
        case ATOMIC_UPDATER:
        default:
            AtomicRefCnt.resetRefCnt(ref);
            break;
        }
    }

    static void throwIllegalRefCountOnRelease(int decrement, int curr) {
        throw new IllegalReferenceCountException(curr >>> 1, -(decrement >>> 1));
    }

    private static final class AtomicRefCnt {
        private static final AtomicIntegerFieldUpdater<RefCnt> UPDATER =
                AtomicIntegerFieldUpdater.newUpdater(RefCnt.class, "value");

        static void init(RefCnt instance) {
            UPDATER.set(instance, 2);
        }

        static int refCnt(RefCnt instance) {
            return UPDATER.get(instance) >>> 1;
        }

        static void retain(RefCnt instance) {
            retain0(instance, 2);
        }

        static void retain(RefCnt instance, int increment) {
            retain0(instance, checkPositive(increment, "increment") << 1);
        }

        private static void retain0(RefCnt instance, int increment) {
            int oldRef = UPDATER.getAndAdd(instance, increment);
            if ((oldRef & 0x80000001) != 0 || oldRef > Integer.MAX_VALUE - increment) {
                UPDATER.getAndAdd(instance, -increment);
                throw new IllegalReferenceCountException(0, increment >>> 1);
            }
        }

        static boolean release(RefCnt instance) {
            return release0(instance, 2);
        }

        static boolean release(RefCnt instance, int decrement) {
            return release0(instance, checkPositive(decrement, "decrement") << 1);
        }

        private static boolean release0(RefCnt instance, int decrement) {
            int curr, next;
            do {
                curr = UPDATER.get(instance);
                if (curr == decrement) {
                    next = 1;
                } else {
                    if (curr < decrement || (curr & 1) == 1) {
                        throwIllegalRefCountOnRelease(decrement, curr);
                    }
                    next = curr - decrement;
                }
            } while (!UPDATER.compareAndSet(instance, curr, next));
            return (next & 1) == 1;
        }

        static void setRefCnt(RefCnt instance, int refCnt) {
            int rawRefCnt = refCnt > 0? refCnt << 1 : 1;
            UPDATER.lazySet(instance, rawRefCnt);
        }

        static void resetRefCnt(RefCnt instance) {
            UPDATER.lazySet(instance, 2);
        }

        static boolean isLiveNonVolatile(RefCnt instance) {
            final int rawCnt = instance.value;
            if (rawCnt == 2) {
                return true;
            }
            return (rawCnt & 1) == 0;
        }
    }

    private static final class VarHandleRefCnt {

        private static final VarHandle VH;

        static {
            VH = PlatformDependent.findVarHandleOfIntField(MethodHandles.lookup(), RefCnt.class, "value");
        }

        static void init(RefCnt instance) {
            VH.set(instance, 2);
            VarHandle.storeStoreFence();
        }

        static int refCnt(RefCnt instance) {
            return (int) VH.getAcquire(instance) >>> 1;
        }

        static void retain(RefCnt instance) {
            retain0(instance, 2);
        }

        static void retain(RefCnt instance, int increment) {
            retain0(instance, checkPositive(increment, "increment") << 1);
        }

        private static void retain0(RefCnt instance, int increment) {
            int oldRef = (int) VH.getAndAdd(instance, increment);
            if ((oldRef & 0x80000001) != 0 || oldRef > Integer.MAX_VALUE - increment) {
                VH.getAndAdd(instance, -increment);
                throw new IllegalReferenceCountException(0, increment >>> 1);
            }
        }

        static boolean release(RefCnt instance) {
            return release0(instance, 2);
        }

        static boolean release(RefCnt instance, int decrement) {
            return release0(instance, checkPositive(decrement, "decrement") << 1);
        }

        private static boolean release0(RefCnt instance, int decrement) {
            int curr, next;
            do {
                curr = (int) VH.get(instance);
                if (curr == decrement) {
                    next = 1;
                } else {
                    if (curr < decrement || (curr & 1) == 1) {
                        throwIllegalRefCountOnRelease(decrement, curr);
                    }
                    next = curr - decrement;
                }
            } while (!(boolean) VH.compareAndSet(instance, curr, next));
            return (next & 1) == 1;
        }

        static void setRefCnt(RefCnt instance, int refCnt) {
            int rawRefCnt = refCnt > 0? refCnt << 1 : 1;
            VH.setRelease(instance, rawRefCnt);
        }

        static void resetRefCnt(RefCnt instance) {
            VH.setRelease(instance, 2);
        }

        static boolean isLiveNonVolatile(RefCnt instance) {
            final int rawCnt = (int) VH.get(instance);
            if (rawCnt == 2) {
                return true;
            }
            return (rawCnt & 1) == 0;
        }
    }

    private static final class UnsafeRefCnt {

        private static final long VALUE_OFFSET = getUnsafeOffset(RefCnt.class, "value");

        static void init(RefCnt instance) {
            PlatformDependent.safeConstructPutInt(instance, VALUE_OFFSET, 2);
        }

        static int refCnt(RefCnt instance) {
            return PlatformDependent.getVolatileInt(instance, VALUE_OFFSET) >>> 1;
        }

        static void retain(RefCnt instance) {
            retain0(instance, 2);
        }

        static void retain(RefCnt instance, int increment) {
            retain0(instance, checkPositive(increment, "increment") << 1);
        }

        private static void retain0(RefCnt instance, int increment) {
            int oldRef = PlatformDependent.getAndAddInt(instance, VALUE_OFFSET, increment);
            if ((oldRef & 0x80000001) != 0 || oldRef > Integer.MAX_VALUE - increment) {
                PlatformDependent.getAndAddInt(instance, VALUE_OFFSET, -increment);
                throw new IllegalReferenceCountException(0, increment >>> 1);
            }
        }

        static boolean release(RefCnt instance) {
            return release0(instance, 2);
        }

        static boolean release(RefCnt instance, int decrement) {
            return release0(instance, checkPositive(decrement, "decrement") << 1);
        }

        private static boolean release0(RefCnt instance, int decrement) {
            int curr, next;
            do {
                curr = PlatformDependent.getInt(instance, VALUE_OFFSET);
                if (curr == decrement) {
                    next = 1;
                } else {
                    if (curr < decrement || (curr & 1) == 1) {
                        throwIllegalRefCountOnRelease(decrement, curr);
                    }
                    next = curr - decrement;
                }
            } while (!PlatformDependent.compareAndSwapInt(instance, VALUE_OFFSET, curr, next));
            return (next & 1) == 1;
        }

        static void setRefCnt(RefCnt instance, int refCnt) {
            int rawRefCnt = refCnt > 0? refCnt << 1 : 1;
            PlatformDependent.putOrderedInt(instance, VALUE_OFFSET, rawRefCnt);
        }

        static void resetRefCnt(RefCnt instance) {
            PlatformDependent.putOrderedInt(instance, VALUE_OFFSET, 2);
        }

        static boolean isLiveNonVolatile(RefCnt instance) {
            final int rawCnt = PlatformDependent.getInt(instance, VALUE_OFFSET);
            if (rawCnt == 2) {
                return true;
            }
            return (rawCnt & 1) == 0;
        }
    }
}
