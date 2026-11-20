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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("deprecation")
public class PromiseAggregatorTest {

    @Test
    public void testNullAggregatePromise() {
        assertThrows(NullPointerException.class, () -> new PromiseAggregator<Void, Future<Void>>(null));
    }

    @Test
    public void testAddNullFuture() {
        Promise<Void> p = ImmediateEventExecutor.INSTANCE.newPromise();
        PromiseAggregator<Void, Future<Void>> a = new PromiseAggregator<>(p);
        assertThrows(NullPointerException.class, () -> a.add((Promise<Void>[]) null));
    }

    @Test
    public void testSuccessfulNoPending() throws Exception {
        Promise<Void> p = ImmediateEventExecutor.INSTANCE.newPromise();
        PromiseAggregator<Void, Future<Void>> a = new PromiseAggregator<>(p);

        Future<Void> future = ImmediateEventExecutor.INSTANCE.newSucceededFuture(null);

        a.add();
        a.operationComplete(future);
        verifySuccess(p);
    }

    @Test
    public void testSuccessfulPending() {
        Promise<Void> p = ImmediateEventExecutor.INSTANCE.newPromise();
        PromiseAggregator<Void, Future<Void>> a = new PromiseAggregator<>(p);
        Promise<Void> p1 = ImmediateEventExecutor.INSTANCE.newPromise();
        Promise<Void> p2 = ImmediateEventExecutor.INSTANCE.newPromise();

        assertThat(a.add(p1, null, p2)).isSameAs(a);

        p1.setSuccess(null);
        verifyNotCompleted(p);
        p2.setSuccess(null);
        verifySuccess(p);
    }

    @Test
    public void testFailedFutureFailPending() {
        Promise<Void> p = ImmediateEventExecutor.INSTANCE.newPromise();
        PromiseAggregator<Void, Future<Void>> a = new PromiseAggregator<>(p);
        Promise<Void> p1 = ImmediateEventExecutor.INSTANCE.newPromise();
        Promise<Void> p2 = ImmediateEventExecutor.INSTANCE.newPromise();
        Throwable t = new RuntimeException("fake exception");

        a.add(p1, p2);
        p1.setFailure(t);

        verifyFail(p, t);
        verifyFail(p2, t);
    }

    @Test
    public void testFailedFutureNoFailPending() {
        Promise<Void> p = ImmediateEventExecutor.INSTANCE.newPromise();
        PromiseAggregator<Void, Future<Void>> a = new PromiseAggregator<>(p, false);
        Promise<Void> p1 = ImmediateEventExecutor.INSTANCE.newPromise();
        Promise<Void> p2 = ImmediateEventExecutor.INSTANCE.newPromise();
        Throwable t = new RuntimeException("fake exception");

        a.add(p1, p2);
        p1.setFailure(t);

        verifyFail(p, t);
        verifyNotCompleted(p2);
    }

    private static void verifyFail(Promise<Void> p, Throwable cause) {
        assertThat(p.isDone()).isTrue();
        assertThat(p.isSuccess()).isFalse();
        assertThat(p.cause()).isSameAs(cause);
    }

    private static void verifySuccess(Promise<Void> p) {
        assertThat(p.isDone()).isTrue();
        assertThat(p.isSuccess()).isTrue();
        assertThat(p.getNow()).isNull();
    }

    private static void verifyNotCompleted(Promise<Void> p) {
        assertThat(p.isDone()).isFalse();
    }
}
