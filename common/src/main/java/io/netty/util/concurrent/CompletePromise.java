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


public abstract class CompletePromise extends CompleteFuture implements Promise {

    protected CompletePromise(EventExecutor executor) {
        super(executor);
    }

    @Override
    public Promise setFailure(Throwable cause) {
        throw new IllegalStateException();
    }

    @Override
    public boolean tryFailure(Throwable cause) {
        return false;
    }

    @Override
    public Promise setSuccess() {
        throw new IllegalStateException();
    }

    @Override
    public boolean trySuccess() {
        return false;
    }

    @Override
    public Promise await() throws InterruptedException {
        return this;
    }

    @Override
    public Promise awaitUninterruptibly() {
        return this;
    }

    @Override
    public Promise syncUninterruptibly() {
        return this;
    }

    @Override
    public Promise sync() throws InterruptedException {
        return this;
    }

    @Override
    public Promise addListener(GenericFutureListener<? extends Future> listener) {
        return (Promise) super.addListener(listener);
    }

    @Override
    public Promise addListeners(GenericFutureListener<? extends Future>... listeners) {
        return (Promise) super.addListeners(listeners);
    }

    @Override
    public Promise removeListener(GenericFutureListener<? extends Future> listener) {
        return (Promise) super.removeListener(listener);
    }

    @Override
    public Promise removeListeners(GenericFutureListener<? extends Future>... listeners) {
        return (Promise) super.removeListeners(listeners);
    }
}
