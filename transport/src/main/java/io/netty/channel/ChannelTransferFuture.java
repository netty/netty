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
 * This interface defined the methods which an {@link ChannelTransferPromise} should use
 */
public interface ChannelTransferFuture extends ChannelFuture {

    ChannelTransferFuture addTransferFutureListener(TransferFutureListener listener);
    ChannelTransferFuture addTransferFutureListeners(TransferFutureListener ... listeners);
    ChannelTransferFuture removeTransferFutureListener(TransferFutureListener listener);
    ChannelTransferFuture removeTransferFutureListeners(TransferFutureListener ... listeners);

    @Override
    ChannelTransferFuture addListener(GenericFutureListener<? extends Future<Void>> listener);

    @Override
    ChannelTransferFuture addListeners(GenericFutureListener<? extends Future<Void>>... listeners);

    @Override
    ChannelTransferFuture removeListener(GenericFutureListener<? extends Future<Void>> listener);

    @Override
    ChannelTransferFuture removeListeners(GenericFutureListener<? extends Future<Void>>... listeners);

    @Override
    ChannelTransferFuture sync() throws InterruptedException;

    @Override
    ChannelTransferFuture syncUninterruptibly();

    @Override
    ChannelTransferFuture await() throws InterruptedException;

    @Override
    ChannelTransferFuture awaitUninterruptibly();
}
