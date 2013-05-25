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

import io.netty.util.concurrent.AbstractFuture;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.concurrent.TimeUnit;

final class VoidChannelPromise extends AbstractFuture<Void> implements ChannelPromise {

    private final Channel channel;
    private final boolean fireException;

    /**
     * Creates a new instance.
     *
     * @param channel the {@link Channel} associated with this future
     */
    public VoidChannelPromise(Channel channel, boolean fireException) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        this.channel = channel;
        this.fireException = fireException;
    }

    @Override
    public VoidChannelPromise addListener(GenericFutureListener<? extends Future<Void>> listener) {
        fail();
        return this;
    }

    @Override
    public VoidChannelPromise addListeners(GenericFutureListener<? extends Future<Void>>... listeners) {
        fail();
        return this;
    }

    @Override
    public VoidChannelPromise removeListener(GenericFutureListener<? extends Future<Void>> listener) {
        // NOOP
        return this;
    }

    @Override
    public VoidChannelPromise removeListeners(GenericFutureListener<? extends Future<Void>>... listeners) {
        // NOOP
        return this;
    }

    @Override
    public VoidChannelPromise await() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        return this;
    }

    @Override
    public boolean await(long timeout, TimeUnit unit) {
        fail();
        return false;
    }

    @Override
    public boolean await(long timeoutMillis) {
        fail();
        return false;
    }

    @Override
    public VoidChannelPromise awaitUninterruptibly() {
        fail();
        return this;
    }

    @Override
    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        fail();
        return false;
    }

    @Override
    public boolean awaitUninterruptibly(long timeoutMillis) {
        fail();
        return false;
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean isSuccess() {
        return false;
    }

    @Override
    public Throwable cause() {
        return null;
    }

    @Override
    public VoidChannelPromise sync() {
        fail();
        return this;
    }

    @Override
    public VoidChannelPromise syncUninterruptibly() {
        fail();
        return this;
    }
    @Override
    public VoidChannelPromise setFailure(Throwable cause) {
        if (fireException) {
            channel.pipeline().fireExceptionCaught(cause);
        }
        return this;
    }

    @Override
    public VoidChannelPromise setSuccess() {
        return this;
    }

    @Override
    public boolean tryFailure(Throwable cause) {
        if (fireException) {
            channel.pipeline().fireExceptionCaught(cause);
        }
        return false;
    }

    @Override
    public boolean trySuccess() {
        return false;
    }

    private static void fail() {
        throw new IllegalStateException("void future");
    }

    @Override
    public VoidChannelPromise setSuccess(Void result) {
        return this;
    }

    @Override
    public boolean trySuccess(Void result) {
        return false;
    }

    @Override
    public Void getNow() {
        return null;
    }
}
