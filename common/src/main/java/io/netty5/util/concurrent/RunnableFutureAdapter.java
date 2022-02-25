/*
 * Copyright 2018 The Netty Project
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
package io.netty5.util.concurrent;

import io.netty5.util.internal.StringUtil;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.util.Objects.requireNonNull;

final class RunnableFutureAdapter<V> implements RunnableFuture<V> {

    private final Promise<V> promise;
    private final Future<V> future;
    private final Callable<V> task;

    RunnableFutureAdapter(Promise<V> promise, Callable<V> task) {
        this.promise = requireNonNull(promise, "promise");
        this.task = requireNonNull(task, "task");
        future = promise.asFuture();
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
    public boolean isFailed() {
        return promise.isFailed();
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
    public RunnableFuture<V> addListener(FutureListener<? super V> listener) {
        future.addListener(listener);
        return this;
    }

    @Override
    public <C> RunnableFuture<V> addListener(C context, FutureContextListener<? super C, ? super V> listener) {
        future.addListener(context, listener);
        return this;
    }

    @Override
    public RunnableFuture<V> sync() throws InterruptedException {
        future.sync();
        return this;
    }

    @Override
    public RunnableFuture<V> syncUninterruptibly() {
        future.syncUninterruptibly();
        return this;
    }

    @Override
    public RunnableFuture<V> await() throws InterruptedException {
        future.await();
        return this;
    }

    @Override
    public RunnableFuture<V> awaitUninterruptibly() {
        future.awaitUninterruptibly();
        return this;
    }

    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return future.await(timeout, unit);
    }

    @Override
    public boolean await(long timeoutMillis) throws InterruptedException {
        return future.await(timeoutMillis);
    }

    @Override
    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        return future.awaitUninterruptibly(timeout, unit);
    }

    @Override
    public boolean awaitUninterruptibly(long timeoutMillis) {
        return future.awaitUninterruptibly(timeoutMillis);
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
    public boolean cancel() {
        return future.cancel();
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
        return future.get();
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(timeout, unit);
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
