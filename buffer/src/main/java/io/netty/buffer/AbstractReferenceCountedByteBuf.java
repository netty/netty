/*
 * Copyright 2013 The Netty Project
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

import io.netty.util.IllegalReferenceCountException;
import io.netty.util.internal.PlatformDependent;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.netty.util.internal.ObjectUtil.checkPositive;

/**
 * Abstract base class for {@link ByteBuf} implementations that count references.
 * 从类的名称看该类主要对引用进行计数，类似于JVM内存回收的对象引用计数器，用于跟踪对象的分配和销毁，做自动内存回收。
 */
public abstract class AbstractReferenceCountedByteBuf extends AbstractByteBuf {

    // 用于标识refCnt字段在AtomicIntegerFieldUpdater中的内存地址，该内存地址的获取是JDK强关联的；
    private static final long REFCNT_FIELD_OFFSET;//
    //AtomicIntegerFieldUpdater 通过原子的方式对成员变量进行更新等操作，以实现线程安全，消除锁；
    private static final AtomicIntegerFieldUpdater<AbstractReferenceCountedByteBuf> refCntUpdater =
            AtomicIntegerFieldUpdater.newUpdater(AbstractReferenceCountedByteBuf.class, "refCnt");

    // 跟踪对象的 引用次数
    private volatile int refCnt = 1;

    // 获得内存地址；
    static {
        long refCntFieldOffset = -1;
        try {
            if (PlatformDependent.hasUnsafe()) {
                refCntFieldOffset = PlatformDependent.objectFieldOffset(
                        AbstractReferenceCountedByteBuf.class.getDeclaredField("refCnt"));
            }
        } catch (Throwable ignore) {
            refCntFieldOffset = -1;
        }

        REFCNT_FIELD_OFFSET = refCntFieldOffset;//抵消，补偿；平版印刷；支管
    }

    protected AbstractReferenceCountedByteBuf(int maxCapacity) {
        super(maxCapacity);
    }

    @Override
    int internalRefCnt() {
        // Try to do non-volatile挥发性的；不稳定的；爆炸性的；反复无常的 read for performance 性能；绩效；表演；执行；表现 as the ensureAccessible() is racy生动的；保持原味的；适于赛跑的；猥亵的 anyway and only provide
        // a best-effort guard.
        //
        // TODO: Once we compile against later versions of Java we can replace the Unsafe usage here by varhandles.
        return REFCNT_FIELD_OFFSET != -1 ? PlatformDependent.getInt(this, REFCNT_FIELD_OFFSET) : refCnt();
    }

    @Override
    public int refCnt() {
        return refCnt;
    }

    /**
     * An unsafe operation intended for use by a subclass that sets the reference count of the buffer directly
     */
    protected final void setRefCnt(int refCnt) {
        refCntUpdater.set(this, refCnt);
    }

    //
    @Override
    public ByteBuf retain() {
        return retain0(1);
    }

    @Override
    public ByteBuf retain(int increment) {
        return retain0(checkPositive(increment, "increment"));
    }

    //
    private ByteBuf retain0(final int increment) {

        int oldRef = refCntUpdater.getAndAdd(this, increment);
        if (oldRef <= 0 || oldRef + increment < oldRef) {
            // Ensure we don't resurrect（使复活；复兴；挖出） (which means the refCnt was 0) and also that we encountered an overflow.
            refCntUpdater.getAndAdd(this, -increment);
            throw new IllegalReferenceCountException(oldRef, increment);
        }
        return this;
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
        return release0(1);
    }

    @Override
    public boolean release(int decrement) {
        return release0(checkPositive(decrement, "decrement"));
    }

    private boolean release0(int decrement) {
        int oldRef = refCntUpdater.getAndAdd(this, -decrement);
        if (oldRef == decrement) {
            deallocate();
            return true;
        }
        if (oldRef < decrement || oldRef - decrement > oldRef) {
            // Ensure we don't over-release, and avoid underflow.
            refCntUpdater.getAndAdd(this, decrement);
            throw new IllegalReferenceCountException(oldRef, -decrement);
        }
        return false;
    }
    /**
     * Called once {@link #refCnt()} is equals 0.
     */
    protected abstract void deallocate();
}
