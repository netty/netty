/*
 * Copyright 2016 The Netty Project
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

public final class PromiseCombiner {
    private int expectedCount;
    private int doneCount;
    private boolean doneAdding;
    private Promise<Void> aggregatePromise;
    private Throwable cause;
    private final GenericFutureListener<Future<?>> listener = new GenericFutureListener<Future<?>>() {
        @Override
        public void operationComplete(Future<?> future) throws Exception {
            ++doneCount;
            if (!future.isSuccess() && cause == null) {
                cause = future.cause();
            }
            if (doneCount == expectedCount && doneAdding) {
                tryPromise();
            }
        }
    };

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void add(Promise promise) {
        checkAddAllowed();
        ++expectedCount;
        promise.addListener(listener);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addAll(Promise... promises) {
        checkAddAllowed();
        expectedCount += promises.length;
        for (Promise promise : promises) {
            promise.addListener(listener);
        }
    }

    public void finish(Promise<Void> aggregatePromise) {
        if (doneAdding) {
            throw new IllegalStateException("Already finished");
        }
        doneAdding = true;
        this.aggregatePromise = ObjectUtil.checkNotNull(aggregatePromise, "aggregatePromise");
        if (doneCount == expectedCount) {
            tryPromise();
        }
    }

    private boolean tryPromise() {
        return (cause == null) ? aggregatePromise.trySuccess(null) : aggregatePromise.tryFailure(cause);
    }

    private void checkAddAllowed() {
        if (doneAdding) {
            throw new IllegalStateException("Adding promises is not allowed after finished adding");
        }
    }
}
