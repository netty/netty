/*
 * Copyright 2013 The Netty Project
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
package io.netty.channel;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.ProgressiveFuture;

/**
 * An special {@link ChannelFuture} which is used to indicate the {@link FileRegion} transfer progress
 */
public interface ChannelProgressiveFuture extends ChannelFuture, ProgressiveFuture<Void> {
    @Override
    ChannelProgressiveFuture addListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelProgressiveFuture addListeners(GenericFutureListener<? extends Future<? super Void>>... listeners);

    @Override
    ChannelProgressiveFuture removeListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelProgressiveFuture removeListeners(GenericFutureListener<? extends Future<? super Void>>... listeners);

    @Override
    ChannelProgressiveFuture sync() throws InterruptedException;

    @Override
    ChannelProgressiveFuture syncUninterruptibly();

    @Override
    ChannelProgressiveFuture await() throws InterruptedException;

    @Override
    ChannelProgressiveFuture awaitUninterruptibly();
}
