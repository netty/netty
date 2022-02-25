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
package io.netty5.util.concurrent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.netty5.util.concurrent.ImmediateEventExecutor.INSTANCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class PromiseCombinerTest {
    private Promise<Void> p1;
    private Future<Void> f1;
    private Promise<Void> p2;
    private Future<Void> f2;
    private Promise<Void> p3;
    private PromiseCombiner combiner;

    @BeforeEach
    public void setup() {
        p1 = INSTANCE.newPromise();
        p2 = INSTANCE.newPromise();
        p3 = INSTANCE.newPromise();
        f1 = p1.asFuture();
        f2 = p2.asFuture();
        combiner = new PromiseCombiner(INSTANCE);
    }

    @Test
    public void testNullArgument() {
        try {
            combiner.finish(null);
            fail();
        } catch (NullPointerException expected) {
            // expected
        }
        combiner.finish(p1);
        assertTrue(p1.isSuccess());
        assertThat(p1.getNow()).isNull();
    }

    @Test
    public void testNullAggregatePromise() {
        combiner.finish(p1);
        assertTrue(p1.isSuccess());
        assertThat(p1.getNow()).isNull();
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
        assertThrows(IllegalStateException.class, () -> combiner.add(f2));
    }

    @Test
    public void testAddAllAfterFinish() {
        combiner.finish(p1);
        assertThrows(IllegalStateException.class, () -> combiner.addAll(f2));
    }

    @Test
    public void testFinishCalledTwiceThrows() {
        combiner.finish(p1);
        assertThrows(IllegalStateException.class, () -> combiner.finish(p1));
    }

    @Test
    public void testAddAllSuccess() throws Exception {
        p1.setSuccess(null);
        combiner.addAll(f1, f2);
        combiner.finish(p3);
        assertFalse(p3.isDone());
        p2.setSuccess(null);
        assertTrue(p3.isDone());
        assertTrue(p3.isSuccess());
    }

    @Test
    public void testAddSuccess() throws Exception {
        p1.setSuccess(null);
        p2.setSuccess(null);
        combiner.add(f1);
        combiner.add(f2);
        assertFalse(p3.isDone());
        combiner.finish(p3);
        assertTrue(p3.isSuccess());
    }

    @Test
    public void testAddAllFail() throws Exception {
        RuntimeException e1 = new RuntimeException("fake exception 1");
        RuntimeException e2 = new RuntimeException("fake exception 2");
        combiner.addAll(f1, f2);
        combiner.finish(p3);
        p1.setFailure(e1);
        assertFalse(p3.isDone());
        p2.setFailure(e2);
        assertTrue(p3.isFailed());
        assertThat(p3.cause()).isSameAs(e1);
    }

    @Test
    public void testAddFail() throws Exception {
        RuntimeException e1 = new RuntimeException("fake exception 1");
        RuntimeException e2 = new RuntimeException("fake exception 2");
        combiner.add(f1);
        p1.setFailure(e1);
        combiner.add(f2);
        p2.setFailure(e2);
        assertFalse(p3.isDone());
        combiner.finish(p3);
        assertTrue(p3.isFailed());
        assertThat(p3.cause()).isSameAs(e1);
    }

    @Test
    public void testEventExecutor() {
        EventExecutor executor = new SingleThreadEventExecutor();
        combiner = new PromiseCombiner(executor);

        Future<?> future = executor.newPromise().asFuture();

        try {
            combiner.add(future);
            fail();
        } catch (IllegalStateException expected) {
            // expected
        }

        try {
            combiner.addAll(future);
            fail();
        } catch (IllegalStateException expected) {
            // expected
        }

        Promise<Void> promise = executor.newPromise();
        try {
            combiner.finish(promise);
            fail();
        } catch (IllegalStateException expected) {
            // expected
        }
    }
}
