/*
 * Copyright 2012 The Netty Project
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


public abstract class CompletePromise<V> extends CompleteFuture<V> implements Promise<V> {

    protected CompletePromise(EventExecutor executor) {
        super(executor);
    }

    @Override
    public Promise<V> setFailure(Throwable cause) {
        throw new IllegalStateException();
    }

    @Override
    public boolean tryFailure(Throwable cause) {
        return false;
    }

    @Override
    public Promise<V> setSuccess(V result) {
        throw new IllegalStateException();
    }

    @Override
    public boolean trySuccess(V result) {
        return false;
    }

    @Override
    public Promise<V> await() throws InterruptedException {
        return this;
    }

    @Override
    public Promise<V> awaitUninterruptibly() {
        return this;
    }

    @Override
    public Promise<V> syncUninterruptibly() {
        return this;
    }

    @Override
    public Promise<V> sync() throws InterruptedException {
        return this;
    }

    @Override
    public Promise<V> addListener(GenericFutureListener<? extends Future<? super V>> listener) {
        return (Promise<V>) super.addListener(listener);
    }

    @Override
    public Promise<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners) {
        return (Promise<V>) super.addListeners(listeners);
    }

    @Override
    public Promise<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener) {
        return (Promise<V>) super.removeListener(listener);
    }

    @Override
    public Promise<V> removeListeners(GenericFutureListener<? extends Future<? super V>>... listeners) {
        return (Promise<V>) super.removeListeners(listeners);
    }
}
