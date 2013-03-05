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

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.concurrent.TimeUnit;

final class VoidChannelPromise implements ChannelFuture.Unsafe, ChannelPromise {

    private final Channel channel;

    /**
     * Creates a new instance.
     *
     * @param channel the {@link Channel} associated with this future
     */
    public VoidChannelPromise(Channel channel) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        this.channel = channel;
    }

    @Override
    public ChannelPromise addListener(GenericFutureListener<? extends Future> listener) {
        fail();
        return this;
    }

    @Override
    public ChannelPromise addListeners(GenericFutureListener<? extends Future>... listeners) {
        fail();
        return this;
    }

    @Override
    public ChannelPromise removeListener(GenericFutureListener<? extends Future> listener) {
        // NOOP
        return this;
    }

    @Override
    public ChannelPromise removeListeners(GenericFutureListener<? extends Future>... listeners) {
        // NOOP
        return this;
    }

    @Override
    public ChannelPromise await() throws InterruptedException {
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
    public ChannelPromise awaitUninterruptibly() {
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
    public ChannelPromise sync() {
        fail();
        return this;
    }

    @Override
    public ChannelPromise syncUninterruptibly() {
        fail();
        return this;
    }
    @Override
    public ChannelPromise setFailure(Throwable cause) {
        return this;
    }

    @Override
    public ChannelPromise setSuccess() {
        return this;
    }

    @Override
    public boolean tryFailure(Throwable cause) {
        return false;
    }

    @Override
    public boolean trySuccess() {
        return false;
    }

    private static void fail() {
        throw new IllegalStateException("void future");
    }
}
