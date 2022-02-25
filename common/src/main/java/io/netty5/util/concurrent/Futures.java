/*
 * Copyright 2021 The Netty Project
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
package io.netty5.util.concurrent;

import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.util.concurrent.Callable;
import java.util.function.Function;

import static io.netty5.util.internal.PromiseNotificationUtil.tryFailure;
import static java.util.Objects.requireNonNull;

/**
 * Combinator operations on {@linkplain Future futures}.
 * <p>
 * Used for implementing {@link Future#map(Function)} and {@link Future#flatMap(Function)}
 *
 * @implNote The operations themselves are implemented as static inner classes instead of lambdas to aid debugging.
 */
final class Futures {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Futures.class);
    private static final PassThrough<?> PASS_THROUGH = new PassThrough<>();
    private static final PropagateCancel PROPAGATE_CANCEL = new PropagateCancel();

    /**
     * Creates a new {@link Future} that will complete with the result of the given {@link Future} mapped through the
     * given mapper function.
     * <p>
     * If the given future fails, then the returned future will fail as well, with the same exception. Cancellation of
     * either future will cancel the other. If the mapper function throws, the returned future will fail, but the given
     * future will be unaffected.
     *
     * @param future The future whose result will flow to the returned future, through the mapping function.
     * @param mapper The function that will convert the result of the given future into the result of the returned
     *               future.
     * @param <R>    The result type of the mapper function, and of the returned future.
     * @return A new future instance that will complete with the mapped result of the given future.
     */
    public static <V, R> Future<R> map(Future<V> future, Function<V, R> mapper) {
        requireNonNull(future, "future");
        requireNonNull(mapper, "mapper");
        if (future.isFailed()) {
            @SuppressWarnings("unchecked") // Cast is safe because the result type is not used in failed futures.
            Future<R> failed = (Future<R>) future;
            return failed;
        }
        if (future.isSuccess()) {
            return future.executor().submit(new CallableMapper<>(future, mapper));
        }
        Promise<R> promise = future.executor().newPromise();
        future.addListener(new Mapper<>(promise, mapper));
        Future<R> mappedFuture = promise.asFuture();
        mappedFuture.addListener(future, propagateCancel());
        return mappedFuture;
    }

    /**
     * Creates a new {@link Future} that will complete with the result of the given {@link Future} flat-mapped through
     * the given mapper function.
     * <p>
     * The "flat" in "flat-map" means the given mapper function produces a result that itself is a future-of-R, yet this
     * method also returns a future-of-R, rather than a future-of-future-of-R. In other words, if the same mapper
     * function was used with the {@link #map(Future, Function)} method, you would get back a {@code Future<Future<R>>}.
     * These nested futures are "flattened" into a {@code Future<R>} by this method.
     * <p>
     * Effectively, this method behaves similar to this serial code, except asynchronously and with proper exception and
     * cancellation handling:
     * <pre>{@code
     * V x = future.sync().getNow();
     * Future<R> y = mapper.apply(x);
     * R result = y.sync().getNow();
     * }</pre>
     * <p>
     * If the given future fails, then the returned future will fail as well, with the same exception. Cancellation of
     * either future will cancel the other. If the mapper function throws, the returned future will fail, but the given
     * future will be unaffected.
     *
     * @param mapper The function that will convert the result of the given future into the result of the returned
     *               future.
     * @param <R>    The result type of the mapper function, and of the returned future.
     * @return A new future instance that will complete with the mapped result of the given future.
     */
    public static <V, R> Future<R> flatMap(Future<V> future, Function<V, Future<R>> mapper) {
        requireNonNull(future, "future");
        requireNonNull(mapper, "mapper");
        Promise<R> promise = future.executor().newPromise();
        future.addListener(new FlatMapper<>(promise, mapper));
        Future<R> mappedFuture = promise.asFuture();
        if (!future.isSuccess()) {
            // Propagate cancellation if future is either incomplete or failed.
            // Failed means it could be cancelled, so that needs to be propagated.
            mappedFuture.addListener(future, propagateCancel());
        }
        return mappedFuture;
    }

    @SuppressWarnings("unchecked")
    static FutureContextListener<Future<?>, Object> propagateCancel() {
        return (FutureContextListener<Future<?>, Object>) (FutureContextListener<?, ?>) PROPAGATE_CANCEL;
    }

    @SuppressWarnings("unchecked")
    static <R> FutureContextListener<Promise<R>, Object> passThrough() {
        return (FutureContextListener<Promise<R>, Object>) (FutureContextListener<?, ?>) PASS_THROUGH;
    }

    static <A, B> void propagateUncommonCompletion(Future<? extends A> completed, Promise<B> recipient) {
        if (completed.isCancelled()) {
            // Don't check or log if cancellation propagation fails.
            // Propagation goes both ways, which means at least one future will already be cancelled here.
            recipient.cancel();
        } else {
            Throwable cause = completed.cause();
            recipient.tryFailure(cause);
        }
    }

    private Futures() {
    }

    private static final class PropagateCancel implements FutureContextListener<Future<Object>, Object> {
        @Override
        public void operationComplete(Future<Object> context, Future<?> future) throws Exception {
            if (future.isCancelled()) {
                context.cancel();
            }
        }
    }

    private static final class PassThrough<R> implements FutureContextListener<Promise<R>, Object> {
        @Override
        public void operationComplete(Promise<R> recipient, Future<?> completed) throws Exception {
            if (completed.isSuccess()) {
                try {
                    @SuppressWarnings("unchecked")
                    R result = (R) completed.getNow();
                    recipient.trySuccess(result);
                } catch (Throwable e) {
                    tryFailure(recipient, e, logger);
                }
            } else {
                propagateUncommonCompletion(completed, recipient);
            }
        }
    }

    private static final class CallableMapper<R, T> implements Callable<R> {
        private final Future<T> future;
        private final Function<T, R> mapper;

        CallableMapper(Future<T> future, Function<T, R> mapper) {
            this.future = future;
            this.mapper = mapper;
        }

        @Override
        public R call() throws Exception {
            return mapper.apply(future.getNow());
        }
    }

    private static final class Mapper<R, T> implements FutureListener<Object> {
        private final Promise<R> recipient;
        private final Function<T, R> mapper;

        Mapper(Promise<R> recipient, Function<T, R> mapper) {
            this.recipient = recipient;
            this.mapper = mapper;
        }

        @Override
        public void operationComplete(Future<?> completed) throws Exception {
            if (completed.isSuccess()) {
                try {
                    @SuppressWarnings("unchecked")
                    T result = (T) completed.getNow();
                    R mapped = mapper.apply(result);
                    recipient.trySuccess(mapped);
                } catch (Throwable e) {
                    tryFailure(recipient, e, logger);
                }
            } else {
                propagateUncommonCompletion(completed, recipient);
            }
        }
    }

    private static final class FlatMapper<R, T> implements FutureListener<Object> {
        private final Promise<R> recipient;
        private final Function<T, Future<R>> mapper;

        FlatMapper(Promise<R> recipient, Function<T, Future<R>> mapper) {
            this.recipient = recipient;
            this.mapper = mapper;
        }

        @Override
        public void operationComplete(Future<?> completed) throws Exception {
            if (completed.isSuccess()) {
                try {
                    @SuppressWarnings("unchecked")
                    T result = (T) completed.getNow();
                    Future<R> future = mapper.apply(result);
                    if (future.isSuccess()) {
                        recipient.trySuccess(future.getNow());
                    } else if (future.isFailed()) {
                        propagateUncommonCompletion(future, recipient);
                    } else {
                        future.addListener(recipient, passThrough());
                        recipient.asFuture().addListener(future, propagateCancel());
                    }
                } catch (Throwable e) {
                    tryFailure(recipient, e, logger);
                }
            } else {
                propagateUncommonCompletion(completed, recipient);
            }
        }
    }

    /**
     * Link the {@link Future} and {@link Promise} such that if the {@link Future} completes the {@link Promise}
     * will be notified. Cancellation is propagated both ways such that if the {@link Future} is cancelled
     * the {@link Promise} is cancelled and vice-versa.
     *
     * @param future            the {@link Future} which will be used to listen to for notifying the {@link Promise}.
     * @param promise           the {@link Promise} which will be notified
     * @param <V>               the type of the value.
     */
    static <V> void cascade(final Future<V> future, final Promise<? super V> promise) {
        requireNonNull(future, "future");
        requireNonNull(promise, "promise");

        if (!future.isSuccess()) {
            // Propagate cancellation if future is either incomplete or failed.
            // Failed means it could be cancelled, so that needs to be propagated.
            promise.asFuture().addListener(future, propagateCancel());
        }
        future.addListener(promise, passThrough());
    }
}
