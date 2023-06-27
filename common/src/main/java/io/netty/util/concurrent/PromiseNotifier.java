/*
 * Copyright 2014 The Netty Project
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
package io.netty.util.concurrent;

import io.netty.util.internal.PromiseNotificationUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static io.netty.util.internal.ObjectUtil.checkNotNullWithIAE;

/**
 * {@link GenericFutureListener} implementation which takes other {@link Promise}s
 * and notifies them on completion.
 *
 * @param <V> the type of value returned by the future
 * @param <F> the type of future
 */
public class PromiseNotifier<V, F extends Future<V>> implements GenericFutureListener<F> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PromiseNotifier.class);
    private final Promise<? super V>[] promises;
    private final boolean logNotifyFailure;

    /**
     * Create a new instance.
     *
     * @param promises  the {@link Promise}s to notify once this {@link GenericFutureListener} is notified.
     */
    @SafeVarargs
    public PromiseNotifier(Promise<? super V>... promises) {
        this(true, promises);
    }

    /**
     * Create a new instance.
     *
     * @param logNotifyFailure {@code true} if logging should be done in case notification fails.
     * @param promises  the {@link Promise}s to notify once this {@link GenericFutureListener} is notified.
     */
    @SafeVarargs
    public PromiseNotifier(boolean logNotifyFailure, Promise<? super V>... promises) {
        checkNotNull(promises, "promises");
        for (Promise<? super V> promise: promises) {
            checkNotNullWithIAE(promise, "promise");
        }
        this.promises = promises.clone();
        this.logNotifyFailure = logNotifyFailure;
    }

    /**
     * Link the {@link Future} and {@link Promise} such that if the {@link Future} completes the {@link Promise}
     * will be notified. Cancellation is propagated both ways such that if the {@link Future} is cancelled
     * the {@link Promise} is cancelled and vise-versa.
     *
     * @param future    the {@link Future} which will be used to listen to for notifying the {@link Promise}.
     * @param promise   the {@link Promise} which will be notified
     * @param <V>       the type of the value.
     * @param <F>       the type of the {@link Future}
     * @return          the passed in {@link Future}
     */
    public static <V, F extends Future<V>> F cascade(final F future, final Promise<? super V> promise) {
        return cascade(true, future, promise);
    }

    /**
     * Link the {@link Future} and {@link Promise} such that if the {@link Future} completes the {@link Promise}
     * will be notified. Cancellation is propagated both ways such that if the {@link Future} is cancelled
     * the {@link Promise} is cancelled and vise-versa.
     *
     * @param logNotifyFailure  {@code true} if logging should be done in case notification fails.
     * @param future            the {@link Future} which will be used to listen to for notifying the {@link Promise}.
     * @param promise           the {@link Promise} which will be notified
     * @param <V>               the type of the value.
     * @param <F>               the type of the {@link Future}
     * @return                  the passed in {@link Future}
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <V, F extends Future<V>> F cascade(boolean logNotifyFailure, final F future,
                                                     final Promise<? super V> promise) {
        promise.addListener(new FutureListener() {
            @Override
            public void operationComplete(Future f) {
                if (f.isCancelled()) {
                    future.cancel(false);
                }
            }
        });
        future.addListener(new PromiseNotifier(logNotifyFailure, promise) {
            @Override
            public void operationComplete(Future f) throws Exception {
                if (promise.isCancelled() && f.isCancelled()) {
                    // Just return if we propagate a cancel from the promise to the future and both are notified already
                    return;
                }
                super.operationComplete(future);
            }
        });
        return future;
    }

    @Override
    public void operationComplete(F future) throws Exception {
        InternalLogger internalLogger = logNotifyFailure ? logger : null;
        if (future.isSuccess()) {
            V result = future.get();
            for (Promise<? super V> p: promises) {
                PromiseNotificationUtil.trySuccess(p, result, internalLogger);
            }
        } else if (future.isCancelled()) {
            for (Promise<? super V> p: promises) {
                PromiseNotificationUtil.tryCancel(p, internalLogger);
            }
        } else {
            Throwable cause = future.cause();
            for (Promise<? super V> p: promises) {
                PromiseNotificationUtil.tryFailure(p, cause, internalLogger);
            }
        }
    }
}
