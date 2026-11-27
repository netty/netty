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

public class PromiseNotifierTest {

    @Test
    public void testNullPromisesArray() {
        assertThrows(NullPointerException.class,
                () -> new PromiseNotifier<>((Promise<Void>[]) null));
    }

    @Test
    public void testNullPromiseInArray() {
        assertThrows(IllegalArgumentException.class,
                () -> new PromiseNotifier<>((Promise<Void>) null));
    }

    @Test
    public void testListenerSuccess() throws Exception {
        Promise<Void> p1 = ImmediateEventExecutor.INSTANCE.newPromise();
        Promise<Void> p2 = ImmediateEventExecutor.INSTANCE.newPromise();

        PromiseNotifier<Void, Future<Void>> notifier =
            new PromiseNotifier<>(p1, p2);

        Future<Void> future = ImmediateEventExecutor.INSTANCE.newSucceededFuture(null);

        notifier.operationComplete(future);
        verifySuccess(p1);
        verifySuccess(p2);
    }

    @Test
    public void testListenerFailure() throws Exception {
        Promise<Void> p1 = ImmediateEventExecutor.INSTANCE.newPromise();
        Promise<Void> p2 = ImmediateEventExecutor.INSTANCE.newPromise();

        PromiseNotifier<Void, Future<Void>> notifier =
            new PromiseNotifier<>(p1, p2);

        Throwable t = new RuntimeException("fake exception");
        Future<Void> future = ImmediateEventExecutor.INSTANCE.newFailedFuture(t);

        notifier.operationComplete(future);
        verifyFail(p1, t);
        verifyFail(p2, t);
    }

    @Test
    public void testCancelPropagationWhenFusedFromFuture() {
        Promise<Void> p1 = ImmediateEventExecutor.INSTANCE.newPromise();
        Promise<Void> p2 = ImmediateEventExecutor.INSTANCE.newPromise();

        Promise<Void> returned = PromiseNotifier.cascade(p1, p2);
        assertThat(returned).isSameAs(p1);

        assertThat(returned.cancel(false)).isTrue();
        assertThat(returned.isCancelled()).isTrue();
        assertThat(p2.isCancelled()).isTrue();
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
}
