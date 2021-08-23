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
 * Internal operators interface used for implementing {@link Future#map(Function)} and {@link
 * Future#flatMap(Function)}.
 * <p>
 * <em>Note:</em> The operations themselves are implemented as static inner classes instead of lambdas to aid
 * debugging.
 * <p>
 * This library reduces object allocation compared to what could otherwise be accomplished, by relying on the {@link
 * DefaultPromise} itself being a {@link FutureContextListener} that take the operator instance as a context. This way,
 * only the operator instance itself necessarily needs to be allocated, and in the case of {@link #passThrough()} even
 * that is avoided.
 */
interface PromiseOperator<R> extends FutureContextListener<Promise<R>, Object> {
    InternalLogger LOGGER = InternalLoggerFactory.getInstance(PromiseOperator.class);
    PassThrough<?> PASS_THROUGH_CONSTANT = new PassThrough<>();

    @SuppressWarnings("unchecked")
    static <R> PromiseOperator<R> passThrough() {
        return (PromiseOperator<R>) PASS_THROUGH_CONSTANT;
    }

    static <T, R> PromiseOperator<R> map(Function<T, R> mapper) {
        Objects.requireNonNull(mapper, "The mapper function cannot be null.");
        return new Mapper<>(mapper);
    }

    static <T, R> PromiseOperator<R> flatMap(Function<T, Future<R>> mapper) {
        return new FlatMapper<>(mapper);
    }

    static <A, B> void propagateUncommonCompletion(Future<? extends A> completed, Promise<B> recipient) {
        if (completed.isCancelled()) {
            tryCancel(recipient, LOGGER);
        } else {
            Throwable cause = completed.cause();
            tryFailure(recipient, cause, LOGGER);
        }
    }

    class PassThrough<R> implements PromiseOperator<R> {
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

    class Mapper<R, T> implements PromiseOperator<R> {
        private final Function<T, R> mapper;

        Mapper(Function<T, R> mapper) {
            this.mapper = mapper;
        }

        @Override
        public void operationComplete(Promise<R> recipient, Future<?> completed) throws Exception {
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

    class FlatMapper<R, T> implements PromiseOperator<R> {
        private final Function<T, Future<R>> mapper;

        FlatMapper(Function<T, Future<R>> mapper) {
            this.mapper = mapper;
        }

        @Override
        public void operationComplete(Promise<R> recipient, Future<?> completed) throws Exception {
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
