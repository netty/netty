/*
 * Copyright 2014 The Netty Project
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
package io.netty.util.internal;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

final class UnsafeAtomicLongFieldUpdater<T> extends AtomicLongFieldUpdater<T> {
    private final long offset;
    private final Unsafe unsafe;

    UnsafeAtomicLongFieldUpdater(Unsafe unsafe, Class<? super T> tClass, String fieldName)
            throws NoSuchFieldException {
        Field field = tClass.getDeclaredField(fieldName);
        if (!Modifier.isVolatile(field.getModifiers())) {
            throw new IllegalArgumentException("Must be volatile");
        }
        this.unsafe = unsafe;
        offset = unsafe.objectFieldOffset(field);
    }

    @Override
    public boolean compareAndSet(T obj, long expect, long update) {
        return unsafe.compareAndSwapLong(obj, offset, expect, update);
    }

    @Override
    public boolean weakCompareAndSet(T obj, long expect, long update) {
        return unsafe.compareAndSwapLong(obj, offset, expect, update);
    }

    @Override
    public void set(T obj, long newValue) {
        unsafe.putLongVolatile(obj, offset, newValue);
    }

    @Override
    public void lazySet(T obj, long newValue) {
        unsafe.putOrderedLong(obj, offset, newValue);
    }

    @Override
    public long get(T obj) {
        return unsafe.getLongVolatile(obj, offset);
    }
}
