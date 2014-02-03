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

/**
 * Special {@link ProgressiveFuture} which is writable.
 */
public interface ProgressivePromise<V> extends Promise<V>, ProgressiveFuture<V> {

    /**
     * Sets the current progress of the operation and notifies the listeners that implement
     * {@link GenericProgressiveFutureListener}.
     */
    ProgressivePromise<V> setProgress(long progress, long total);

    /**
     * Tries to set the current progress of the operation and notifies the listeners that implement
     * {@link GenericProgressiveFutureListener}.  If the operation is already complete or the progress is out of range,
     * this method does nothing but returning {@code false}.
     */
    boolean tryProgress(long progress, long total);

    @Override
    ProgressivePromise<V> setSuccess(V result);

    @Override
    ProgressivePromise<V> setFailure(Throwable cause);

    @Override
    ProgressivePromise<V> addListener(GenericFutureListener<? extends Future<? super V>> listener);

    @Override
    ProgressivePromise<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    @Override
    ProgressivePromise<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener);

    @Override
    ProgressivePromise<V> removeListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    @Override
    ProgressivePromise<V> await() throws InterruptedException;

    @Override
    ProgressivePromise<V> awaitUninterruptibly();

    @Override
    ProgressivePromise<V> sync() throws InterruptedException;

    @Override
    ProgressivePromise<V> syncUninterruptibly();
}
