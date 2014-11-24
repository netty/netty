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
package io.netty.util.concurrent;

/**
 * {@link GenericFutureListener} implementation which takes other {@link Future}s
 * and notifies them on completion.
 *
 * @param V the type of value returned by the future
 * @param F the type of future
 */
public class PromiseNotifier<V, F extends Future<V>> implements GenericFutureListener<F> {

    private final Promise<? super V>[] promises;

    /**
     * Create a new instance.
     *
     * @param promises  the {@link Promise}s to notify once this {@link GenericFutureListener} is notified.
     */
    @SafeVarargs
    public PromiseNotifier(Promise<? super V>... promises) {
        if (promises == null) {
            throw new NullPointerException("promises");
        }
        for (Promise<? super V> promise: promises) {
            if (promise == null) {
                throw new IllegalArgumentException("promises contains null Promise");
            }
        }
        this.promises = promises.clone();
    }

    @Override
    public void operationComplete(F future) throws Exception {
        if (future.isSuccess()) {
            V result = future.get();
            for (Promise<? super V> p: promises) {
                p.setSuccess(result);
            }
            return;
        }

        Throwable cause = future.cause();
        for (Promise<? super V> p: promises) {
            p.setFailure(cause);
        }
    }

}
