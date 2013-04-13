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
 * Special {@link ChannelPromise} which can be notify the transfered bytes amount.
 */
public interface ChannelTransferPromise extends ChannelPromise, ChannelTransferSensor {
    /**
     * Set the current transfered bytes amount
     * */
    void setTransferedAmount(long amount);

    @Override
    ChannelTransferPromise removeListeners(GenericFutureListener<? extends Future<Void>>... listeners);

    @Override
    ChannelTransferPromise addListener(GenericFutureListener<? extends Future<Void>> listener);

    @Override
    ChannelTransferPromise addListeners(GenericFutureListener<? extends Future<Void>>... listeners);

    @Override
    ChannelTransferPromise removeListener(GenericFutureListener<? extends Future<Void>> listener);

    @Override
    ChannelTransferPromise sync() throws InterruptedException;

    @Override
    ChannelTransferPromise syncUninterruptibly();

    @Override
    ChannelPromise await() throws InterruptedException;

    @Override
    ChannelPromise awaitUninterruptibly();
}
