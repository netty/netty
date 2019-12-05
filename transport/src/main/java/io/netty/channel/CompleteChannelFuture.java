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

import io.netty.util.concurrent.CompleteFuture;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.internal.ObjectUtil;

/**
 * A skeletal {@link ChannelFuture} implementation which represents a
 * {@link ChannelFuture} which has been completed already.
 */
abstract class CompleteChannelFuture extends CompleteFuture<Void> implements ChannelFuture {

    private final Channel channel;

    /**
     * Creates a new instance.
     *
     * @param channel the {@link Channel} associated with this future
     */
    protected CompleteChannelFuture(Channel channel, EventExecutor executor) {
        super(executor);
        this.channel = ObjectUtil.checkNotNull(channel, "channel");
    }

    @Override
    protected EventExecutor executor() {
        EventExecutor e = super.executor();
        if (e == null) {
            return channel().eventLoop();
        } else {
            return e;
        }
    }

    @Override
    public ChannelFuture addListener(GenericFutureListener<? extends Future<? super Void>> listener) {
        super.addListener(listener);
        return this;
    }

    @Override
    public ChannelFuture addListeners(GenericFutureListener<? extends Future<? super Void>>... listeners) {
        super.addListeners(listeners);
        return this;
    }

    @Override
    public ChannelFuture removeListener(GenericFutureListener<? extends Future<? super Void>> listener) {
        super.removeListener(listener);
        return this;
    }

    @Override
    public ChannelFuture removeListeners(GenericFutureListener<? extends Future<? super Void>>... listeners) {
        super.removeListeners(listeners);
        return this;
    }

    @Override
    public ChannelFuture syncUninterruptibly() {
        return this;
    }

    @Override
    public ChannelFuture sync() throws InterruptedException {
        return this;
    }

    @Override
    public ChannelFuture await() throws InterruptedException {
        return this;
    }

    @Override
    public ChannelFuture awaitUninterruptibly() {
        return this;
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public Void getNow() {
        return null;
    }

    @Override
    public boolean isVoid() {
        return false;
    }
}
