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

package io.netty.util.concurrent;

import io.netty.util.internal.ObjectUtil;

import java.util.concurrent.TimeUnit;

/**
 * A skeletal {@link Future} implementation which represents a {@link Future} which has been completed already.
 */
public abstract class CompleteFuture<V> extends AbstractFuture<V> {

    private final EventExecutor executor;

    /**
     * Creates a new instance.
     *
     * @param executor the {@link EventExecutor} associated with this future
     */
    protected CompleteFuture(EventExecutor executor) {
        this.executor = executor;
    }

    /**
     * Return the {@link EventExecutor} which is used by this {@link CompleteFuture}.
     */
    protected EventExecutor executor() {
        return executor;
    }

    @Override
    public Future<V> addListener(GenericFutureListener<? extends Future<? super V>> listener) {
        DefaultPromise.notifyListener(executor(), this, ObjectUtil.checkNotNull(listener, "listener"));
        return this;
    }

    @Override
    public Future<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners) {
        for (GenericFutureListener<? extends Future<? super V>> l:
                ObjectUtil.checkNotNull(listeners, "listeners")) {

            if (l == null) {
                break;
            }
            DefaultPromise.notifyListener(executor(), this, l);
        }
        return this;
    }

    @Override
    public Future<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener) {
        // NOOP
        return this;
    }

    @Override
    public Future<V> removeListeners(GenericFutureListener<? extends Future<? super V>>... listeners) {
        // NOOP
        return this;
    }

    @Override
    public Future<V> await() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        return this;
    }

    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        return true;
    }

    @Override
    public Future<V> sync() throws InterruptedException {
        return this;
    }

    @Override
    public Future<V> syncUninterruptibly() {
        return this;
    }

    @Override
    public boolean await(long timeoutMillis) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        return true;
    }

    @Override
    public Future<V> awaitUninterruptibly() {
        return this;
    }

    @Override
    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        return true;
    }

    @Override
    public boolean awaitUninterruptibly(long timeoutMillis) {
        return true;
    }

    @Override
    public boolean isDone() {
        return true;
    }

    @Override
    public boolean isCancellable() {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * @param mayInterruptIfRunning this value has no effect in this implementation.
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }
}
