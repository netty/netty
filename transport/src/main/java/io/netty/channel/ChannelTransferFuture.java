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
 * An special {@link ChannelFuture} which is used to indicate the {@link FileRegion} transfer progress
 */
public interface ChannelTransferFuture extends ChannelFuture {

    /**
     * Adds the specified listener to this future.  The
     * specified listener is notified when this bytes associated with this if being transferred.
     * If this future is already completed, the specified listener is notified immediately.
     */
    ChannelTransferFuture addTransferListener(TransferFutureListener listener);

    /**
     * Adds the specified listeners to this future.  The
     * specified listeners is notified when this bytes associated with this if being transferred.
     * If this future is already completed, the specified listeners is notified immediately.
     */
    ChannelTransferFuture addTransferListeners(TransferFutureListener ... listeners);

    /**
     * Removes the specified listener from this future.
     * The specified listener is no longer notified when this
     * future is {@linkplain #isDone() done}.  If the specified
     * listener is not associated with this future, this method
     * does nothing and returns silently.
     */
    ChannelTransferFuture removeTransferListener(TransferFutureListener listener);

    /**
     * Removes the specified listener from this future.
     * The specified listener is no longer notified when this
     * future is {@linkplain #isDone() done}.  If the specified
     * listener is not associated with this future, this method
     * does nothing and returns silently.
     */
    ChannelTransferFuture removeTransferListeners(TransferFutureListener ... listeners);

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
