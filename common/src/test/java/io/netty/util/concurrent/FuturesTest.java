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

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty.util.concurrent.DefaultPromise.newSuccessfulPromise;
import static io.netty.util.concurrent.Futures.flatMap;
import static io.netty.util.concurrent.Futures.map;
import static io.netty.util.concurrent.ImmediateEventExecutor.INSTANCE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FuturesTest {

    @Test
    public void mapMustApplyMapperFunctionWhenFutureSucceeds() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        Future<String> strFut = map(promise, i -> i.toString());
        promise.setSuccess(42);
        assertThat(strFut.getNow()).isEqualTo("42");
    }

    @Test
    public void mapMustApplyMapperFunctionOnSuccededFuture() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        promise.setSuccess(42);
        assertThat(map(promise, i -> i.toString()).getNow()).isEqualTo("42");
    }

    @Test
    public void mapOnFailedFutureMustProduceFailedFuture() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        Exception cause = new Exception("boom");
        promise.setFailure(cause);
        assertThat(map(promise, i -> i.toString()).cause()).isSameAs(cause);
    }

    @Test
    public void mapOnFailedFutureMustNotApplyMapperFunction() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        Exception cause = new Exception("boom");
        promise.setFailure(cause);
        AtomicInteger counter = new AtomicInteger();
        assertThat(map(promise, i -> {
            counter.getAndIncrement();
            return i.toString();
        }).cause()).isSameAs(cause);
        assertThat(counter.get()).isZero();
    }

    @Test
    public void mapMustFailReturnedFutureWhenMapperFunctionThrows() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        RuntimeException cause = new RuntimeException("boom");
        Future<Object> future = map(promise, i -> {
            throw cause;
        });
        promise.setSuccess(42);
        assertThat(future.cause()).isSameAs(cause);
    }

    @Test
    public void mapMustNotFailOriginalFutureWhenMapperFunctionThrows() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        map(promise, i -> {
            throw new RuntimeException("boom");
        });
        promise.setSuccess(42);
        assertThat(promise.getNow()).isEqualTo(42);
    }

    @Test
    public void cancelOnFutureFromMapMustCancelOriginalFuture() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        Future<String> strFut = map(promise, i -> i.toString());
        strFut.cancel(false);
        assertTrue(promise.isCancelled());
        assertTrue(strFut.isCancelled());
    }

    @Test
    public void cancelOnOriginalFutureMustCancelFutureFromMap() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        Future<String> strFut = map(promise, i -> i.toString());
        promise.cancel(false);
        assertTrue(promise.isCancelled());
        assertTrue(strFut.isCancelled());
    }

    @Test
    public void flatMapMustApplyMapperFunctionWhenFutureSucceeds() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        Future<String> strFut = flatMap(promise, i -> newSuccessfulPromise(INSTANCE, i.toString()));
        promise.setSuccess(42);
        assertThat(strFut.getNow()).isEqualTo("42");
    }

    @Test
    public void flatMapMustApplyMapperFunctionOnSuccededFuture() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        promise.setSuccess(42);
        assertThat(flatMap(promise, i -> newSuccessfulPromise(INSTANCE, i.toString())).getNow()).isEqualTo("42");
    }

    @Test
    public void flatMapOnFailedFutureMustProduceFailedFuture() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        Exception cause = new Exception("boom");
        promise.setFailure(cause);
        assertThat(flatMap(promise, i -> newSuccessfulPromise(INSTANCE, i.toString())).cause()).isSameAs(cause);
    }

    @Test
    public void flatMapOnFailedFutureMustNotApplyMapperFunction() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        Exception cause = new Exception("boom");
        promise.setFailure(cause);
        AtomicInteger counter = new AtomicInteger();
        assertThat(flatMap(promise, i -> {
            counter.getAndIncrement();
            return newSuccessfulPromise(INSTANCE, i.toString());
        }).cause()).isSameAs(cause);
        assertThat(counter.get()).isZero();
    }

    @Test
    public void flatMapMustFailReturnedFutureWhenMapperFunctionThrows() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        RuntimeException cause = new RuntimeException("boom");
        Future<Object> future = flatMap(promise, i -> {
            throw cause;
        });
        promise.setSuccess(42);
        assertThat(future.cause()).isSameAs(cause);
    }

    @Test
    public void flatMapMustNotFailOriginalFutureWhenMapperFunctionThrows() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        flatMap(promise, i -> {
            throw new RuntimeException("boom");
        });
        promise.setSuccess(42);
        assertThat(promise.getNow()).isEqualTo(42);
    }

    @Test
    public void cancelOnFutureFromFlatMapMustCancelOriginalFuture() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        Future<String> strFut = flatMap(promise, i -> newSuccessfulPromise(INSTANCE, i.toString()));
        strFut.cancel(false);
        assertTrue(promise.isCancelled());
        assertTrue(strFut.isCancelled());
    }

    @Test
    public void cancelOnOriginalFutureMustCancelFutureFromFlatMap() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        Future<String> strFut = flatMap(promise, i -> newSuccessfulPromise(INSTANCE, i.toString()));
        promise.cancel(false);
        assertTrue(promise.isCancelled());
        assertTrue(strFut.isCancelled());
    }

    @Test
    public void cancelOnFutureFromFlatMapMapperMustCancelReturnedFuture() throws Exception {
        DefaultPromise<Integer> original = new DefaultPromise<>(INSTANCE);
        Future<String> strFut = flatMap(original, i -> {
            Future<String> future = new DefaultPromise<>(INSTANCE);
            future.cancel(false);
            return future;
        });

        original.setSuccess(42);
        assertTrue(strFut.await(5, SECONDS));
        assertTrue(strFut.isCancelled());
    }

    @Test
    public void futureFromFlatMapMustNotCompleteUntilMappedFutureCompletes() throws Exception {
        TestEventExecutor executor = new TestEventExecutor();
        DefaultPromise<Integer> original = new DefaultPromise<>(executor);
        CountDownLatch mappingLatchEnter = new CountDownLatch(1);
        CountDownLatch mappingLatchExit = new CountDownLatch(1);
        Future<String> strFut = flatMap(original, i -> {
            return executor.submit(() -> {
                mappingLatchEnter.countDown();
                mappingLatchExit.await();
                return i.toString();
            });
        });

        executor.submit(() -> original.setSuccess(42));
        mappingLatchEnter.await();
        assertFalse(strFut.await(100));
        mappingLatchExit.countDown();
        assertThat(strFut.get(5, SECONDS)).isEqualTo("42");
    }
}
