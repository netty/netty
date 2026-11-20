/*
 * Copyright 2016 The Netty Project
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PromiseCombinerTest {
    private Promise<Void> p1;
    private Promise<Void> p2;
    private Promise<Void> p3;
    private PromiseCombiner combiner;

    @BeforeEach
    public void setup() {
        p1 = ImmediateEventExecutor.INSTANCE.newPromise();
        p2 = ImmediateEventExecutor.INSTANCE.newPromise();
        p3 = ImmediateEventExecutor.INSTANCE.newPromise();
        combiner = new PromiseCombiner(ImmediateEventExecutor.INSTANCE);
    }

    @Test
    public void testNullArgument() {
        assertThrows(NullPointerException.class, () -> combiner.finish(null));
        combiner.finish(p1);
        verifySuccess(p1);
    }

    @Test
    public void testNullAggregatePromise() {
        combiner.finish(p1);
        verifySuccess(p1);
    }

    @Test
    public void testAddNullPromise() {
        assertThrows(NullPointerException.class, () -> combiner.add(null));
    }

    @Test
    public void testAddAllNullPromise() {
        assertThrows(NullPointerException.class, () -> combiner.addAll(null));
    }

    @Test
    public void testAddAfterFinish() {
        combiner.finish(p1);
        assertThrows(IllegalStateException.class, () -> combiner.add(p2));
    }

    @Test
    public void testAddAllAfterFinish() {
        combiner.finish(p1);
        assertThrows(IllegalStateException.class, () -> combiner.addAll(p2));
    }

    @Test
    public void testFinishCalledTwiceThrows() {
        combiner.finish(p1);
        assertThrows(IllegalStateException.class, () -> combiner.finish(p1));
    }

    @Test
    public void testAddAllSuccess() {
        combiner.addAll(p1, p2);
        combiner.finish(p3);
        p1.setSuccess(null);
        verifyNotCompleted(p3);
        p2.setSuccess(null);
        verifySuccess(p3);
    }

    @Test
    public void testAddSuccess() {
        combiner.add(p1);
        p1.setSuccess(null);
        combiner.add(p2);
        p2.setSuccess(null);
        verifyNotCompleted(p3);
        combiner.finish(p3);
        verifySuccess(p3);
    }

    @Test
    public void testAddAllFail() {
        RuntimeException e1 = new RuntimeException("fake exception 1");
        RuntimeException e2 = new RuntimeException("fake exception 2");
        combiner.addAll(p1, p2);
        combiner.finish(p3);
        p1.setFailure(e1);
        verifyNotCompleted(p3);
        p2.setFailure(e2);
        verifyFail(p3, e1);
    }

    @Test
    public void testAddFail() {
        RuntimeException e1 = new RuntimeException("fake exception 1");
        RuntimeException e2 = new RuntimeException("fake exception 2");
        combiner.add(p1);
        p1.setFailure(e1);
        combiner.add(p2);
        p2.setFailure(e2);
        verifyNotCompleted(p3);
        combiner.finish(p3);
        verifyFail(p3, e1);
    }

    @Test
    public void testEventExecutor() {
        // The test thread is never the GlobalEventExecutor thread, so inEventLoop() returns false.
        EventExecutor executor = GlobalEventExecutor.INSTANCE;
        assertThat(executor.inEventLoop()).isFalse();
        combiner = new PromiseCombiner(executor);

        Future<?> future = ImmediateEventExecutor.INSTANCE.newPromise();

        assertThrows(IllegalStateException.class, () -> combiner.add(future));

        assertThrows(IllegalStateException.class, () -> combiner.addAll(future));

        Promise<Void> promise = ImmediateEventExecutor.INSTANCE.newPromise();
        assertThrows(IllegalStateException.class, () -> combiner.finish(promise));
        assertThat(promise.isDone()).isFalse();
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
