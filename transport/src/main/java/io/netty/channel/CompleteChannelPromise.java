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
package io.netty.channel;


import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

abstract class CompleteChannelPromise extends CompleteChannelFuture implements ChannelPromise {

    protected CompleteChannelPromise(Channel channel, EventExecutor executor) {
        super(channel, executor);
    }

    @Override
    public ChannelPromise setFailure(Throwable cause) {
        throw new IllegalStateException();
    }

    @Override
    public boolean tryFailure(Throwable cause) {
        return false;
    }

    @Override
    public ChannelPromise setSuccess() {
        throw new IllegalStateException();
    }

    @Override
    public boolean trySuccess() {
        return false;
    }

    @Override
    public boolean trySuccess(Void result) {
        return false;
    }

    @Override
    public ChannelPromise setSuccess(Void result) {
        throw new IllegalStateException();
    }

    @Override
    public ChannelPromise await() throws InterruptedException {
        return (ChannelPromise) super.await();
    }

    @Override
    public ChannelPromise awaitUninterruptibly() {
        return (ChannelPromise) super.awaitUninterruptibly();
    }

    @Override
    public ChannelPromise addListener(GenericFutureListener<? extends Future<? super Void>> listener) {
        return (ChannelPromise) super.addListener(listener);
    }

    @Override
    public ChannelPromise addListeners(GenericFutureListener<? extends Future<? super Void>>... listeners) {
        return (ChannelPromise) super.addListeners(listeners);
    }

    @Override
    public ChannelPromise removeListener(GenericFutureListener<? extends Future<? super Void>> listener) {
        return (ChannelPromise) super.removeListener(listener);
    }

    @Override
    public ChannelPromise removeListeners(GenericFutureListener<? extends Future<? super Void>>... listeners) {
        return (ChannelPromise) super.removeListeners(listeners);
    }

    @Override
    public ChannelPromise sync() throws InterruptedException {
        return this;
    }

    @Override
    public ChannelPromise syncUninterruptibly() {
        return this;
    }
}
