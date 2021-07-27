/*
 * Copyright 2012 The Netty Project
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
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseNotifier;
import io.netty.util.internal.PromiseNotificationUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * ChannelFutureListener implementation which takes other {@link ChannelPromise}(s) and notifies them on completion.
 *
 * @deprecated use {@link PromiseNotifier}.
 */
@Deprecated
public final class ChannelPromiseNotifier
    extends PromiseNotifier<Void, ChannelFuture>
    implements ChannelFutureListener {

    /**
     * Create a new instance
     *
     * @param promises  the {@link ChannelPromise}s to notify once this {@link ChannelFutureListener} is notified.
     */
    public ChannelPromiseNotifier(ChannelPromise... promises) {
        super(promises);
    }

    /**
     * Create a new instance
     *
     * @param logNotifyFailure {@code true} if logging should be done in case notification fails.
     * @param promises  the {@link ChannelPromise}s to notify once this {@link ChannelFutureListener} is notified.
     */
    public ChannelPromiseNotifier(boolean logNotifyFailure, ChannelPromise... promises) {
        super(logNotifyFailure, promises);
    }

    /**
     * Link the {@link ChannelFuture} and {@link Promise} such that if the {@link ChannelFuture} completes the
     * {@link Promise} will be notified, and completed with the channel in question.
     * Cancellation is propagated both ways such that if the {@link Future} is cancelled the {@link Promise} is
     * cancelled and vise-versa.
     *
     * @param future    the {@link ChannelFuture} which will be used to listen to for notifying the {@link Promise}.
     * @param promise   the {@link Promise} which will be notified
     * @return          the passed in {@link ChannelFuture}
     */
    public static ChannelFuture cascadeChannel(ChannelFuture future, final Promise<? super Channel> promise) {
        return cascadeChannel(true, future, promise);
    }

    /**
     * Link the {@link Future} and {@link Promise} such that if the {@link Future} completes the {@link Promise}
     * will be notified. Cancellation is propagated both ways such that if the {@link Future} is cancelled
     * the {@link Promise} is cancelled and vise-versa.
     *
     * @param logNotifyFailure  {@code true} if logging should be done in case notification fails.
     * @param future            the {@link Future} which will be used to listen to for notifying the {@link Promise}.
     * @param promise           the {@link Promise} which will be notified
     * @return                  the passed in {@link Future}
     */
    public static ChannelFuture cascadeChannel(boolean logNotifyFailure, final ChannelFuture future,
                                                     final Promise<? super Channel> promise) {
        promise.addListener(new FutureListener<Object>() {
            @Override
            public void operationComplete(Future<Object> f) {
                if (f.isCancelled()) {
                    future.cancel(false);
                }
            }
        });
        future.addListener(new GenericFutureListener<ChannelFuture>() {
            @Override
            public void operationComplete(ChannelFuture f) throws Exception {
                if (promise.isCancelled() && f.isCancelled()) {
                    // Just return if we propagate a cancel from the promise to the future and both are notified already
                    return;
                }
                InternalLogger internalLogger = logNotifyFailure ?
                        InternalLoggerFactory.getInstance(PromiseNotifier.class) : null;
                if (f.isSuccess()) {
                    promise.setSuccess(f.channel());
                } else if (f.isCancelled()) {
                    PromiseNotificationUtil.tryCancel(promise, internalLogger);
                } else {
                    Throwable cause = future.cause();
                    promise.setFailure(cause);
                }
            }
        });
        return future;
    }
}
