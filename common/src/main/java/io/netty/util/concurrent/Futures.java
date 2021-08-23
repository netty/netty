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
import java.util.function.Function;

import static io.netty.util.internal.PromiseNotificationUtil.tryCancel;
import static io.netty.util.internal.PromiseNotificationUtil.tryFailure;

/**
 * Combinator operations on {@linkplain Future futures}.
 * Used for implementing {@link Future#map(Function)} and {@link Future#flatMap(Function)}.
 *
 * @implNote The operations themselves are implemented as static inner classes instead of lambdas to aid debugging.
 */
final class Futures {
    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(Futures.class);
    private static final PassThrough<?> PASS_THROUGH_CONSTANT = new PassThrough<Object>();

    /**
     * @see Future#map(Function)
     */
    static <V, R> Future<R> map(Future<V> future, Function<V, R> mapper) {
        Objects.requireNonNull(future, "The future cannot be null.");
        Objects.requireNonNull(mapper, "The mapper function cannot be null.");
        Promise<R> promise = future.executor().newPromise();
        future.addListener(new Mapper<>(promise, mapper));
        promise.addListener(future, PromiseNotifier::propagateCancel);
        return promise;
    }

    /**
     * @see Future#flatMap(Function)
     */
    static <V, R> Future<R> flatMap(Future<V> future, Function<V, Future<R>> mapper) {
        Objects.requireNonNull(future, "The future cannot be null.");
        Objects.requireNonNull(mapper, "The mapper function cannot be null.");
        Promise<R> promise = future.executor().newPromise();
        future.addListener(new FlatMapper<>(promise, mapper));
        promise.addListener(future, PromiseNotifier::propagateCancel);
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
            tryCancel(recipient, LOGGER);
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
                    if (recipient.setUncancellable()) {
                        R mapped = mapper.apply(result);
                        recipient.setSuccess(mapped);
                    }
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
                    if (recipient.setUncancellable()) {
                        Future<R> future = mapper.apply(result);
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
