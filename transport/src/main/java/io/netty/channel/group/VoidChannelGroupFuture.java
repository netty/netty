/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.group;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

// Suppress a warning about returning the same iterator since it always returns an empty iterator
final class VoidChannelGroupFuture implements ChannelGroupFuture {  // lgtm[java/iterable-wraps-iterator]

    private static final Iterator<ChannelFuture> EMPTY = Collections.<ChannelFuture>emptyList().iterator();
    private final ChannelGroup group;

    VoidChannelGroupFuture(ChannelGroup group) {
        this.group = group;
    }

    @Override
    public ChannelGroup group() {
        return group;
    }

    @Override
    public ChannelFuture find(Channel channel) {
        return null;
    }

    @Override
    public boolean isSuccess() {
        return false;
    }

    @Override
    public ChannelGroupException cause() {
        return null;
    }

    @Override
    public boolean isPartialSuccess() {
        return false;
    }

    @Override
    public boolean isPartialFailure() {
        return false;
    }

    @Override
    public ChannelGroupFuture addListener(GenericFutureListener<? extends Future<? super Void>> listener) {
        throw reject();
    }

    @Override
    public ChannelGroupFuture addListeners(GenericFutureListener<? extends Future<? super Void>>... listeners) {
        throw reject();
    }

    @Override
    public ChannelGroupFuture removeListener(GenericFutureListener<? extends Future<? super Void>> listener) {
        throw reject();
    }

    @Override
    public ChannelGroupFuture removeListeners(GenericFutureListener<? extends Future<? super Void>>... listeners) {
        throw reject();
    }

    @Override
    public ChannelGroupFuture await() {
        throw reject();
    }

    @Override
    public ChannelGroupFuture awaitUninterruptibly() {
        throw reject();
    }

    @Override
    public ChannelGroupFuture syncUninterruptibly() {
        throw reject();
    }

    @Override
    public ChannelGroupFuture sync() {
        throw reject();
    }

    @Override
    public Iterator<ChannelFuture> iterator() {
        return EMPTY;
    }

    @Override
    public boolean isCancellable() {
        return false;
    }

    @Override
    public boolean await(long timeout, TimeUnit unit) {
        throw reject();
    }

    @Override
    public boolean await(long timeoutMillis) {
        throw reject();
    }

    @Override
    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        throw reject();
    }

    @Override
    public boolean awaitUninterruptibly(long timeoutMillis) {
        throw reject();
    }

    @Override
    public Void getNow() {
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * @param mayInterruptIfRunning this value has no effect in this implementation.
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public Void get() {
        throw reject();
    }

    @Override
    public Void get(long timeout, TimeUnit unit)  {
        throw reject();
    }

    private static RuntimeException reject() {
        return new IllegalStateException("void future");
    }
}
