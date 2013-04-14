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
package io.netty.channel;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

/**
 * Special {@link ChannelPromise} which will be notified once the associated bytes is transferring.
 */
public interface ChannelTransferPromise extends ChannelTransferFuture, ChannelPromise {

    /**
     * Increment the current transferred bytes amount
     * */
    void incrementTransferredBytes(long amount);

    @Override
    ChannelTransferPromise addTransferFutureListener(TransferFutureListener listener);

    @Override
    ChannelTransferPromise addTransferFutureListeners(TransferFutureListener... listeners);

    @Override
    ChannelTransferPromise removeTransferFutureListener(TransferFutureListener listener);

    @Override
    ChannelTransferPromise removeTransferFutureListeners(TransferFutureListener... listeners);

    @Override
    ChannelTransferPromise addListener(GenericFutureListener<? extends Future<Void>> listener);

    @Override
    ChannelTransferPromise addListeners(GenericFutureListener<? extends Future<Void>>... listeners);

    @Override
    ChannelTransferPromise removeListener(GenericFutureListener<? extends Future<Void>> listener);

    @Override
    ChannelTransferPromise removeListeners(GenericFutureListener<? extends Future<Void>>... listeners);

    @Override
    ChannelTransferPromise sync() throws InterruptedException;

    @Override
    ChannelTransferPromise syncUninterruptibly();

    @Override
    ChannelTransferPromise await() throws InterruptedException;

    @Override
    ChannelTransferPromise awaitUninterruptibly();
}
