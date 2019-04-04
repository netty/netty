/*
 * Copyright 2018 The Netty Project
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

import static java.util.Objects.requireNonNull;

import io.netty.util.internal.StringUtil;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class RunnableFutureAdapter<V> implements RunnableFuture<V> {

    private final Promise<V> promise;
    private final Callable<V> task;

    RunnableFutureAdapter(Promise<V> promise, Callable<V> task) {
        this.promise = requireNonNull(promise, "promise");
        this.task = requireNonNull(task, "task");
    }

    @Override
    public EventExecutor executor() {
        return promise.executor();
    }

    @Override
    public boolean isSuccess() {
        return promise.isSuccess();
    }

    @Override
    public boolean isCancellable() {
        return promise.isCancellable();
    }

    @Override
    public Throwable cause() {
        return promise.cause();
    }

    @Override
    public RunnableFuture<V> addListener(GenericFutureListener<? extends Future<? super V>> listener) {
        promise.addListener(listener);
        return this;
    }

    @Override
    public RunnableFuture<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners) {
        promise.addListeners(listeners);
        return this;
    }

    @Override
    public RunnableFuture<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener) {
        promise.removeListener(listener);
        return this;
    }

    @Override
    public RunnableFuture<V> removeListeners(GenericFutureListener<? extends Future<? super V>>... listeners) {
        promise.removeListeners(listeners);
        return this;
    }

    @Override
    public RunnableFuture<V> sync() throws InterruptedException {
        promise.sync();
        return this;
    }

    @Override
    public RunnableFuture<V> syncUninterruptibly() {
        promise.syncUninterruptibly();
        return this;
    }

    @Override
    public RunnableFuture<V> await() throws InterruptedException {
        promise.await();
        return this;
    }

    @Override
    public RunnableFuture<V> awaitUninterruptibly() {
        promise.awaitUninterruptibly();
        return this;
    }

    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return promise.await(timeout, unit);
    }

    @Override
    public boolean await(long timeoutMillis) throws InterruptedException {
        return promise.await(timeoutMillis);
    }

    @Override
    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        return promise.awaitUninterruptibly(timeout, unit);
    }

    @Override
    public boolean awaitUninterruptibly(long timeoutMillis) {
        return promise.awaitUninterruptibly(timeoutMillis);
    }

    @Override
    public V getNow() {
        return promise.getNow();
    }

    @Override
    public void run() {
        try {
            if (promise.setUncancellable()) {
                V result = task.call();
                promise.setSuccess(result);
            }
        } catch (Throwable e) {
            promise.setFailure(e);
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return promise.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return promise.isCancelled();
    }

    @Override
    public boolean isDone() {
        return promise.isDone();
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        return promise.get();
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return promise.get(timeout, unit);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(64)
                .append(StringUtil.simpleClassName(this))
                .append('@')
                .append(Integer.toHexString(hashCode()));

        if (!isDone()) {
            buf.append("(incomplete)");
        } else {
            Throwable cause = cause();
            if (cause != null) {
                buf.append("(failure: ")
                        .append(cause)
                        .append(')');
            } else {
                Object result = getNow();
                if (result == null) {
                    buf.append("(success)");
                } else {
                    buf.append("(success: ")
                            .append(result)
                            .append(')');
                }
            }
        }

        return buf.append(" task: ")
                .append(task)
                .append(')').toString();
    }
}
