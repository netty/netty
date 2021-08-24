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
package io.netty.util.concurrent;

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;

import static io.netty.util.internal.PromiseNotificationUtil.tryFailure;

/**
 * Combinator operations on {@linkplain Future futures}.
 *
 * @implNote The operations themselves are implemented as static inner classes instead of lambdas to aid debugging.
 */
public final class Futures {
    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(Futures.class);
    private static final PassThrough<?> PASS_THROUGH_CONSTANT = new PassThrough<Object>();

    /**
     * Creates a new {@link Future} that will complete with the result of the given {@link Future} mapped through the
     * given mapper function.
     * <p>
     * If the given future fails, then the returned future will fail as well, with the same exception.
     * Cancellation of either future will cancel the other.
     * If the mapper function throws, the returned future will fail, but the given future will be unaffected.
     *
     * @param future The future whose result will flow to the returned future, through the mapping function.
     * @param mapper The function that will convert the result of this future into the result of the returned future.
     * @param <R>    The result type of the mapper function, and of the returned future.
     * @return A new future instance that will complete with the mapped result of this future.
     */
    public static <V, R> Future<R> map(Future<V> future, Function<V, R> mapper) {
        Objects.requireNonNull(future, "The future cannot be null.");
        Objects.requireNonNull(mapper, "The mapper function cannot be null.");
        if (future.isSuccess()) {
            return future.executor().submit(new CallableMapper<>(future, mapper));
        }
        Promise<R> promise = future.executor().newPromise();
        future.addListener(new Mapper<>(promise, mapper));
        promise.addListener(future, PromiseNotifier::propagateCancel);
        return promise;
    }

    /**
     * Creates a new {@link Future} that will complete with the result of the given {@link Future} flat-mapped through
     * the given mapper function.
     * <p>
     * The "flat" in "flat-map" means the given mapper function produces a result that itself is a future-of-R, yet this
     * method also returns a future-of-R, rather than a future-of-future-of-R. In other words, if the same mapper
     * function was used with the {@link #map(Future, Function)} method, you would get back a {@code Future<Future<R>>}.
     * These nested futures are "flattened" into a {@code Future<R>} by this method. Note that the future returned by
     * this method is not the same instance as the one the mapper function returns. The reason is that this method needs
     * to return immediately, but the mapper function cannot be applied before this future has completed.
     * <p>
     * Effectively, this method behaves similar to this serial code, except asynchronously and with proper exception
     * and cancellation handling:
     * <pre>{@code
     * V x = future.sync().getNow();
     * Future<R> y = mapper.apply(x);
     * R result = y.sync().getNow();
     * }</pre>
     * <p>
     * If the given future fails, then the returned future will fail as well, with the same exception.
     * Cancellation of either future will cancel the other.
     * If the mapper function throws, the returned future will fail, but the given future will be unaffected.
     *
     * @param mapper The function that will convert the result of this future into the result of the returned future.
     * @param <R>    The result type of the mapper function, and of the returned future.
     * @return A new future instance that will complete with the mapped result of this future.
     */
    public static <V, R> Future<R> flatMap(Future<V> future, Function<V, Future<R>> mapper) {
        Objects.requireNonNull(future, "The future cannot be null.");
        Objects.requireNonNull(mapper, "The mapper function cannot be null.");
        Promise<R> promise = future.executor().newPromise();
        future.addListener(new FlatMapper<>(promise, mapper));
        if (!future.isSuccess()) {
            // Propagate cancellation if future is either incomplete or failed.
            // Failed means it could be cancelled, so that needs to be propagated.
            promise.addListener(future, PromiseNotifier::propagateCancel);
        }
        return promise;
    }

    private Futures() {
    }

    @SuppressWarnings("unchecked")
    static <R> FutureContextListener<Promise<R>, Object> passThrough() {
        return (FutureContextListener<Promise<R>, Object>) (FutureContextListener<?, ?>) PASS_THROUGH_CONSTANT;
    }

    static <A, B> void propagateUncommonCompletion(Future<? extends A> completed, Promise<B> recipient) {
        if (completed.isCancelled()) {
            // Don't check or log if cancellation propagation fails.
            // Propagation goes both ways, which means at least one future will already be cancelled here.
            recipient.cancel(false);
        } else {
            Throwable cause = completed.cause();
            tryFailure(recipient, cause, LOGGER);
        }
    }

    private static class PassThrough<R> implements FutureContextListener<Promise<R>, Object> {
        @Override
        public void operationComplete(Promise<R> recipient, Future<?> completed) throws Exception {
            if (completed.isSuccess()) {
                try {
                    @SuppressWarnings("unchecked")
                    R result = (R) completed.getNow();
                    recipient.trySuccess(result);
                } catch (RuntimeException e) {
                    tryFailure(recipient, e, LOGGER);
                }
            } else {
                propagateUncommonCompletion(completed, recipient);
            }
        }
    }

    private static class CallableMapper<R, T> implements Callable<R> {
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

    private static class Mapper<R, T> implements FutureListener<Object> {
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
                } catch (RuntimeException e) {
                    tryFailure(recipient, e, LOGGER);
                }
            } else {
                propagateUncommonCompletion(completed, recipient);
            }
        }
    }

    private static class FlatMapper<R, T> implements FutureListener<Object> {
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
                    } else {
                        future.addListener(recipient, passThrough());
                        recipient.addListener(future, PromiseNotifier::propagateCancel);
                    }
                } catch (Exception e) {
                    tryFailure(recipient, e, LOGGER);
                }
            } else {
                propagateUncommonCompletion(completed, recipient);
            }
        }
    }
}
