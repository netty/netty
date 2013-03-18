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

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.RunnableFuture;

class PromiseTask<V> extends DefaultPromise<V> implements RunnableFuture<V> {
    protected final Callable<V> task;

    PromiseTask(EventExecutor executor, Runnable runnable, V result) {
        this(executor, Executors.callable(runnable, result));
    }

    PromiseTask(EventExecutor executor, Callable<V> callable) {
        super(executor);
        task = callable;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public void run() {
        try {
            V result = task.call();
            setSuccessInternal(result);
        } catch (Throwable e) {
            setFailureInternal(e);
        }
    }

    @Override
    public Promise<V> setFailure(Throwable cause) {
        throw new IllegalStateException();
    }

    protected final Promise<V> setFailureInternal(Throwable cause) {
        super.setFailure(cause);
        return this;
    }

    @Override
    public boolean tryFailure(Throwable cause) {
        return false;
    }

    protected final boolean tryFailureInternal(Throwable cause) {
        return super.tryFailure(cause);
    }

    @Override
    public Promise<V> setSuccess(V result) {
        throw new IllegalStateException();
    }

    protected final Promise<V> setSuccessInternal(V result) {
        super.setSuccess(result);
        return this;
    }

    @Override
    public boolean trySuccess(V result) {
        return false;
    }

    protected final boolean trySuccessInternal(V result) {
        return super.trySuccess(result);
    }
}
