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

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty5.util.concurrent.ImmediateEventExecutor.INSTANCE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FuturesTest {
    @Test
    public void mapMustApplyMapperFunctionWhenFutureSucceeds() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        Future<String> strFut = promise.map(i -> i.toString());
        promise.setSuccess(42);
        assertThat(strFut.getNow()).isEqualTo("42");
    }

    @Test
    public void mapMustApplyMapperFunctionOnSucceededFuture() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        promise.setSuccess(42);
        assertThat(promise.map(i -> i.toString()).getNow()).isEqualTo("42");
    }

    @Test
    public void mapOnFailedFutureMustProduceFailedFuture() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        Exception cause = new Exception("boom");
        promise.setFailure(cause);
        assertThat(promise.map(i -> i.toString()).cause()).isSameAs(cause);
    }

    @Test
    public void mapOnFailedFutureMustNotApplyMapperFunction() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        Exception cause = new Exception("boom");
        promise.setFailure(cause);
        AtomicInteger counter = new AtomicInteger();
        assertThat(promise.map(i -> {
            counter.getAndIncrement();
            return i.toString();
        }).cause()).isSameAs(cause);
        assertThat(counter.get()).isZero();
    }

    @Test
    public void mapMustFailReturnedFutureWhenMapperFunctionThrows() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        RuntimeException cause = new RuntimeException("boom");
        Future<Object> future = promise.map(i -> {
            throw cause;
        });
        promise.setSuccess(42);
        assertThat(future.cause()).isSameAs(cause);
    }

    @Test
    public void mapMustNotFailOriginalFutureWhenMapperFunctionThrows() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        promise.map(i -> {
            throw new RuntimeException("boom");
        });
        promise.setSuccess(42);
        assertThat(promise.getNow()).isEqualTo(42);
    }

    @Test
    public void cancelOnFutureFromMapMustCancelOriginalFuture() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        Future<String> strFut = promise.map(i -> i.toString());
        strFut.cancel();
        assertTrue(promise.isCancelled());
        assertTrue(strFut.isCancelled());
    }

    @Test
    public void cancelOnOriginalFutureMustCancelFutureFromMap() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        Future<String> strFut = promise.map(i -> i.toString());
        promise.cancel();
        assertTrue(promise.isCancelled());
        assertTrue(strFut.isCancelled());
    }

    @Test
    public void flatMapMustApplyMapperFunctionWhenFutureSucceeds() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        Future<String> strFut = promise.flatMap(i -> INSTANCE.newSucceededFuture(i.toString()));
        promise.setSuccess(42);
        assertThat(strFut.getNow()).isEqualTo("42");
    }

    @Test
    public void flatMapMustApplyMapperFunctionOnSucceededFuture() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        promise.setSuccess(42);
        assertThat(promise.flatMap(i -> INSTANCE.newSucceededFuture(i.toString())).getNow()).isEqualTo("42");
    }

    @Test
    public void flatMapOnFailedFutureMustProduceFailedFuture() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        Exception cause = new Exception("boom");
        promise.setFailure(cause);
        assertThat(promise.flatMap(i -> INSTANCE.newSucceededFuture(i.toString())).cause()).isSameAs(cause);
    }

    @Test
    public void flatMapOnFailedFutureMustNotApplyMapperFunction() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        Exception cause = new Exception("boom");
        promise.setFailure(cause);
        AtomicInteger counter = new AtomicInteger();
        assertThat(promise.flatMap(i -> {
            counter.getAndIncrement();
            return INSTANCE.newSucceededFuture(i.toString());
        }).cause()).isSameAs(cause);
        assertThat(counter.get()).isZero();
    }

    @Test
    public void flatMapMustFailReturnedFutureWhenMapperFunctionThrows() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        RuntimeException cause = new RuntimeException("boom");
        Future<Object> future = promise.flatMap(i -> {
            throw cause;
        });
        promise.setSuccess(42);
        assertThat(future.cause()).isSameAs(cause);
    }

    @Test
    public void flatMapMustNotFailOriginalFutureWhenMapperFunctionThrows() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        promise.flatMap(i -> {
            throw new RuntimeException("boom");
        });
        promise.setSuccess(42);
        assertThat(promise.getNow()).isEqualTo(42);
    }

    @Test
    public void cancelOnFutureFromFlatMapMustCancelOriginalFuture() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        Future<String> strFut = promise.flatMap(i -> INSTANCE.newSucceededFuture(i.toString()));
        strFut.cancel();
        assertTrue(promise.isCancelled());
        assertTrue(strFut.isCancelled());
    }

    @Test
    public void cancelOnOriginalFutureMustCancelFutureFromFlatMap() {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        Future<String> strFut = promise.flatMap(i -> INSTANCE.newSucceededFuture(i.toString()));
        promise.cancel();
        assertTrue(promise.isCancelled());
        assertTrue(strFut.isCancelled());
    }

    @Test
    public void cancelOnFutureFromFlatMapMapperMustCancelReturnedFuture() throws Exception {
        DefaultPromise<Integer> promise = new DefaultPromise<>(INSTANCE);
        Future<String> strFut = promise.flatMap(i -> {
            Future<String> future = new DefaultPromise<>(INSTANCE);
            future.cancel();
            return future;
        });

        promise.setSuccess(42);
        assertTrue(strFut.await(5, SECONDS));
        assertTrue(strFut.isCancelled());
    }

    @Test
    public void futureFromFlatMapMustNotCompleteUntilMappedFutureCompletes() throws Exception {
        TestEventExecutor executor = new TestEventExecutor();
        DefaultPromise<Integer> promise = new DefaultPromise<>(executor);
        CountDownLatch mappingLatchEnter = new CountDownLatch(1);
        CountDownLatch mappingLatchExit = new CountDownLatch(1);
        Future<String> strFut = promise.flatMap(i -> {
            return executor.submit(() -> {
                mappingLatchEnter.countDown();
                mappingLatchExit.await();
                return i.toString();
            });
        });

        executor.submit(() -> promise.setSuccess(42));
        mappingLatchEnter.await();
        assertFalse(strFut.await(100));
        mappingLatchExit.countDown();
        assertThat(strFut.get(5, SECONDS)).isEqualTo("42");
    }

    @Test
    public void cascadeToNullPromise() {
        TestEventExecutor executor = new TestEventExecutor();
        DefaultPromise<Void> promise = new DefaultPromise<>(executor);
        assertThrows(NullPointerException.class, () -> promise.cascadeTo(null));
    }

    @Test
    public void cascadeToSuccess()  throws Exception {
        TestEventExecutor executor = new TestEventExecutor();
        DefaultPromise<Integer> promise = new DefaultPromise<>(executor);
        DefaultPromise<Integer> promise2 = new DefaultPromise<>(executor);
        promise.cascadeTo(promise2);
        promise.setSuccess(1);
        assertTrue(promise.isSuccess());
        assertThat(promise2.get(1, SECONDS)).isEqualTo(1);
    }

    @Test
    public void cascadeToFailure() throws Exception {
        TestEventExecutor executor = new TestEventExecutor();
        DefaultPromise<Integer> promise = new DefaultPromise<>(executor);
        DefaultPromise<Integer> promise2 = new DefaultPromise<>(executor);
        promise.cascadeTo(promise2);

        Exception ex = new Exception();
        promise.setFailure(ex);
        assertTrue(promise.isFailed());
        assertTrue(promise2.await(1, SECONDS));
        assertTrue(promise2.isFailed());
        assertSame(promise.cause(), promise2.cause());
    }

    @Test
    public void cascadeToCancel() throws Exception {
        TestEventExecutor executor = new TestEventExecutor();
        DefaultPromise<Integer> promise = new DefaultPromise<>(executor);
        DefaultPromise<Integer> promise2 = new DefaultPromise<>(executor);
        promise.cascadeTo(promise2);

        assertTrue(promise.cancel());
        assertTrue(promise.isCancelled());
        assertTrue(promise2.await(1, SECONDS));
        assertTrue(promise2.isCancelled());
    }

    @Test
    public void cascadeToCancelSecond() throws Exception {
        TestEventExecutor executor = new TestEventExecutor();
        DefaultPromise<Integer> promise = new DefaultPromise<>(executor);
        DefaultPromise<Integer> promise2 = new DefaultPromise<>(executor);
        promise.cascadeTo(promise2);

        assertTrue(promise2.cancel());
        assertTrue(promise2.isCancelled());

        //
        assertTrue(promise.await(1, SECONDS));
        assertTrue(promise2.isCancelled());
    }
}
