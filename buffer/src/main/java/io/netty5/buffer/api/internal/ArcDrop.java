/*
 * Copyright 2021 The Netty Project
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
package io.netty5.buffer.api.internal;

import io.netty5.buffer.api.Drop;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public final class ArcDrop<T> implements Drop<T> {
    private static final VarHandle COUNT;
    static {
        try {
            COUNT = MethodHandles.lookup().findVarHandle(ArcDrop.class, "count", int.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final Drop<T> delegate;
    @SuppressWarnings("FieldMayBeFinal")
    private volatile int count;

    public ArcDrop(Drop<T> delegate) {
        this.delegate = delegate;
        count = 1;
    }

    public static <X> Drop<X> wrap(Drop<X> drop) {
        if (drop.getClass() == ArcDrop.class) {
            return drop;
        }
        return new ArcDrop<>(drop);
    }

    public static <X> Drop<X> acquire(Drop<X> drop) {
        if (drop.getClass() == ArcDrop.class) {
            ((ArcDrop<X>) drop).increment();
            return drop;
        }
        return new ArcDrop<>(drop);
    }

    public ArcDrop<T> increment() {
        int c;
        do {
            c = count;
            checkValidState(c);
        } while (!COUNT.compareAndSet(this, c, c + 1));
        return this;
    }

    @Override
    public void drop(T obj) {
        int c;
        int n;
        do {
            c = count;
            n = c - 1;
            checkValidState(c);
        } while (!COUNT.compareAndSet(this, c, n));
        if (n == 0) {
            delegate.drop(obj);
        }
    }

    @Override
    public Drop<T> fork() {
        return increment();
    }

    @Override
    public void attach(T obj) {
        delegate.attach(obj);
    }

    public Drop<T> unwrap() {
        return delegate;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder()
                .append("ArcDrop@")
                .append(Integer.toHexString(System.identityHashCode(this)))
                .append('(').append(count).append(", ");
        Drop<T> drop = this;
        while ((drop = ((ArcDrop<T>) drop).unwrap()) instanceof ArcDrop) {
            builder.append(((ArcDrop<T>) drop).count).append(", ");
        }
        return builder.append(drop).append(')').toString();
    }

    private static void checkValidState(int count) {
        if (count == 0) {
            throw new IllegalStateException("Underlying resources have already been freed.");
        }
    }
}
