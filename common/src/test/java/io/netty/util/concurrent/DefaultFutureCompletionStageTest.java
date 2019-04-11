/*
 * Copyright 2019 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util.concurrent;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DefaultFutureCompletionStageTest {

    private static EventExecutorGroup group;
    private static EventExecutorGroup asyncExecutorGroup;
    private static final IllegalStateException EXPECTED_EXCEPTION = new IllegalStateException();
    private static final Integer EXPECTED_INTEGER = 1;
    private static final Boolean INITIAL_BOOLEAN = Boolean.TRUE;
    private static final Boolean EXPECTED_BOOLEAN = Boolean.FALSE;

    @BeforeClass
    public static void setup() {
        group = new MultithreadEventExecutorGroup(1, Executors.defaultThreadFactory());
        asyncExecutorGroup = new MultithreadEventExecutorGroup(1, Executors.defaultThreadFactory());
    }

    @AfterClass
    public static void destroy() {
        group.shutdownGracefully();
        asyncExecutorGroup.shutdownGracefully();
    }

    private static EventExecutor executor() {
        return group.next();
    }

    private static EventExecutor asyncExecutor() {
        return asyncExecutorGroup.next();
    }

    private static Future<Boolean> newSucceededFuture() {
        return executor().newSucceededFuture(INITIAL_BOOLEAN);
    }

    private static <T> Future<T> newFailedFuture() {
        return executor().newFailedFuture(EXPECTED_EXCEPTION);
    }

    @Test
    public void testSameExecutorAndFuture() {
        EventExecutor executor = executor();
        Promise<Boolean> promise = executor.newPromise();
        FutureCompletionStage<Boolean> stage = new DefaultFutureCompletionStage<>(promise);
        assertSame(executor, stage.executor());
        assertSame(promise, stage.future());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testThrowsUnsupportedOperationException() {
        EventExecutor executor = executor();
        Promise<Boolean> promise = executor.newPromise();
        FutureCompletionStage<Boolean> stage = new DefaultFutureCompletionStage<>(promise);
        stage.toCompletableFuture();
    }

    @Test
    public void testThenApply() {
        testThenApply0(stage -> stage.thenApply(v -> {
            assertSame(INITIAL_BOOLEAN, v);
            assertTrue(stage.executor().inEventLoop());

            return EXPECTED_BOOLEAN;
        }), false);
    }

    @Test
    public void testThenApplyAsync() {
        testThenApply0(stage -> stage.thenApplyAsync(v -> {
            assertSame(INITIAL_BOOLEAN, v);
            assertFalse(stage.executor().inEventLoop());

            return EXPECTED_BOOLEAN;
        }), false);
    }

    @Test
    public void testThenApplyAsyncWithExecutor() {
        EventExecutor asyncExecutor = asyncExecutor();
        testThenApply0(stage -> stage.thenApplyAsync(v -> {
            assertSame(INITIAL_BOOLEAN, v);
            assertFalse(stage.executor().inEventLoop());

            return EXPECTED_BOOLEAN;
        }, asyncExecutor), false);
    }

    @Test
    public void testThenApplyCallbackNotExecuted() {
        testHandle0(newFailedFuture(), stage -> stage.thenApply(v -> {
            fail();
            return null;
        }), true);
    }

    @Test
    public void testThenApplyAsyncCallbackNotExecuted() {
        testHandle0(newFailedFuture(), stage -> stage.thenApplyAsync(v -> {
            fail();
            return null;
        }), true);
    }

    @Test
    public void testThenApplyAsyncWithExecutorCallbackNotExecuted() {
        EventExecutor asyncExecutor = asyncExecutor();
        testHandle0(newFailedFuture(), stage -> stage.thenApplyAsync(v -> {
            fail();
            return null;
        }, asyncExecutor), true);
    }

    @Test
    public void testThenApplyFunctionThrows() {
        testThenApply0(stage -> stage.thenApply(v -> {
            throw EXPECTED_EXCEPTION;
        }), true);
    }

    @Test
    public void testThenApplyAsyncFunctionThrows() {
        testThenApply0(stage -> stage.thenApplyAsync(v -> {
            throw EXPECTED_EXCEPTION;
        }), true);
    }

    @Test
    public void testThenApplyAsyncWithExecutorFunctionThrows() {
        EventExecutor asyncExecutor = asyncExecutor();
        testThenApply0(stage -> stage.thenApplyAsync(v -> {
            throw EXPECTED_EXCEPTION;
        }, asyncExecutor), true);
    }

    private void testHandle0(Future<Boolean> future,
                             Function<FutureCompletionStage<Boolean>, FutureCompletionStage<Boolean>> fn,
                             boolean exception) {
        FutureCompletionStage<Boolean> stage = new DefaultFutureCompletionStage<>(future);

        Future<Boolean> f = fn.apply(stage).future().awaitUninterruptibly();
        if (exception) {
            assertSame(EXPECTED_EXCEPTION, f.cause());
        } else {
            assertSame(EXPECTED_BOOLEAN, f.syncUninterruptibly().getNow());
        }
    }

    private void testWhenComplete0(Future<Boolean> future,
                             Function<FutureCompletionStage<Boolean>, FutureCompletionStage<Boolean>> fn,
                             boolean exception) {
        testHandle0(future, stage -> fn.apply(stage).thenApply(v -> {
            assertSame(INITIAL_BOOLEAN, v);
            return EXPECTED_BOOLEAN;
        }), exception);
    }

    private void testThenApply0(
            Function<FutureCompletionStage<Boolean>, FutureCompletionStage<Boolean>> fn, boolean exception) {
        testHandle0(newSucceededFuture(), fn, exception);
    }

    @Test
    public void testThenAccept() {
        testThenAccept0(stage -> stage.thenAccept(v -> {
            assertSame(INITIAL_BOOLEAN, v);
            assertTrue(stage.executor().inEventLoop());
        }), false);
    }

    @Test
    public void testThenAcceptAsync() {
        testThenAccept0(stage -> stage.thenAcceptAsync(v -> {
            assertSame(INITIAL_BOOLEAN, v);

            assertFalse(stage.executor().inEventLoop());
        }), false);
    }

    @Test
    public void testThenAcceptAsyncWithExecutor() {
        EventExecutor asyncExecutor = asyncExecutor();
        testThenAccept0(stage -> stage.thenAcceptAsync(v -> {
            assertSame(INITIAL_BOOLEAN, v);

            assertFalse(stage.executor().inEventLoop());
            assertTrue(asyncExecutor.inEventLoop());
        }, asyncExecutor), false);
    }

    @Test
    public void testThenAcceptCallbackNotExecuted() {
        testThenAccept0(newFailedFuture(), stage -> stage.thenAccept(v -> {
            fail();
        }), true);
    }

    @Test
    public void testThenAcceptAsyncCallbackNotExecuted() {
        testThenAccept0(newFailedFuture(), stage -> stage.thenAcceptAsync(v -> {
            fail();
        }), true);
    }

    @Test
    public void testThenAcceptAsyncWithExecutorCallbackNotExecuted() {
        EventExecutor asyncExecutor = asyncExecutor();
        testThenAccept0(newFailedFuture(), stage -> stage.thenAcceptAsync(v -> {
           fail();
        }, asyncExecutor), true);
    }

    @Test
    public void testThenAcceptConsumerThrows() {
        testThenAccept0(stage -> stage.thenAccept(v -> {
            throw EXPECTED_EXCEPTION;
        }), true);
    }

    @Test
    public void testThenAcceptAsyncConsumerThrows() {
        testThenAccept0(stage -> stage.thenAcceptAsync(v -> {
            throw EXPECTED_EXCEPTION;
        }), true);
    }

    @Test
    public void testThenAcceptAsyncWithExecutorConsumerThrows() {
        EventExecutor asyncExecutor = asyncExecutor();
        testThenAccept0(stage -> stage.thenAcceptAsync(v -> {
            throw EXPECTED_EXCEPTION;
        }, asyncExecutor), true);
    }

    private void testThenAccept0(
            Function<FutureCompletionStage<Boolean>, FutureCompletionStage<Void>> fn, boolean exception) {
        testThenAccept0(newSucceededFuture(), fn, exception);
    }

    private void testThenAccept0(Future<Boolean> future,
            Function<FutureCompletionStage<Boolean>, FutureCompletionStage<Void>> fn, boolean exception) {
        FutureCompletionStage<Boolean> stage = new DefaultFutureCompletionStage<>(future);
        Future<Void> f = fn.apply(stage).future().awaitUninterruptibly();
        if (exception) {
            assertSame(EXPECTED_EXCEPTION, f.cause());
        } else {
            assertNull(f.syncUninterruptibly().getNow());
        }
    }

    @Test
    public void testThenRun() {
        testThenAccept0(stage -> stage.thenRun(() -> {
            assertTrue(stage.executor().inEventLoop());
        }), false);
    }

    @Test
    public void testThenRunAsync() {
        testThenAccept0(stage -> stage.thenRunAsync(() -> {
            assertFalse(stage.executor().inEventLoop());
        }), false);
    }

    @Test
    public void testThenRunAsyncWithExecutor() {
        EventExecutor asyncExecutor = asyncExecutor();
        testThenAccept0(stage -> stage.thenRunAsync(() -> {
            assertFalse(stage.executor().inEventLoop());
            assertTrue(asyncExecutor.inEventLoop());
        }, asyncExecutor), false);
    }

    @Test
    public void testThenRunCallbackNotExecuted() {
        testThenAccept0(newFailedFuture(), stage -> stage.thenRun(() -> {
            fail();
        }), true);
    }

    @Test
    public void testThenRunAsyncCallbackNotExecuted() {
        testThenAccept0(newFailedFuture(), stage -> stage.thenRunAsync(() -> {
            fail();
        }), true);
    }

    @Test
    public void testThenRunAsyncWithExecutorCallbackNotExecuted() {
        EventExecutor asyncExecutor = asyncExecutor();
        testThenAccept0(newFailedFuture(), stage -> stage.thenRunAsync(() -> {
            fail();
        }, asyncExecutor), true);
    }

    @Test
    public void testThenRunTaskThrows() {
        testThenAccept0(stage -> stage.thenRun(() -> {
            throw EXPECTED_EXCEPTION;
        }), true);
    }

    @Test
    public void testThenRunAsyncTaskThrows() {
        testThenAccept0(stage -> stage.thenRunAsync(() -> {
            throw EXPECTED_EXCEPTION;
        }), true);
    }

    @Test
    public void testThenRunAsyncWithExecutorTaskThrows() {
        EventExecutor asyncExecutor = asyncExecutor();
        testThenAccept0(stage -> stage.thenRunAsync(() -> {
            throw EXPECTED_EXCEPTION;
        }, asyncExecutor), true);
    }

    @Test
    public void testThenCombine() {
        testCombination0((stage, other) -> stage.thenCombine(other, (v1, v2) -> {
            assertSame(v1, INITIAL_BOOLEAN);
            assertSame(v2, INITIAL_BOOLEAN);
            assertTrue(stage.executor().inEventLoop());

            return 1;
        }), true, CombinationTestMode.COMPLETE);
    }

    @Test
    public void testThenCombineAsync() {
        testCombination0((stage, other) -> stage.thenCombineAsync(other, (v1, v2) -> {
            assertSame(v1, INITIAL_BOOLEAN);
            assertSame(v2, INITIAL_BOOLEAN);
            assertFalse(stage.executor().inEventLoop());

            return 1;
        }), true, CombinationTestMode.COMPLETE);
    }

    @Test
    public void testThenCombineAsyncWithExecutor() {
        EventExecutor asyncExecutor = asyncExecutor();

        testCombination0((stage, other) -> stage.thenCombineAsync(other, (v1, v2) -> {
            assertSame(v1, INITIAL_BOOLEAN);
            assertSame(v2, INITIAL_BOOLEAN);
            assertFalse(stage.executor().inEventLoop());
            assertTrue(asyncExecutor.inEventLoop());
            return 1;
        }, asyncExecutor), true, CombinationTestMode.COMPLETE);
    }

    @Test
    public void testThenCombineThrowable() {
        testCombination0((stage, other) -> stage.thenCombine(other, (v1, v2) -> {
            fail();
            return 1;
        }), true, CombinationTestMode.COMPLETE_EXCEPTIONAL);
    }

    @Test
    public void testThenCombineAsyncThrowable() {
        testCombination0((stage, other) -> stage.thenCombineAsync(other, (v1, v2) -> {
            fail();
            return 1;
        }), true, CombinationTestMode.COMPLETE_EXCEPTIONAL);
    }

    @Test
    public void testThenCombineAsyncWithExecutorThrowable() {
        EventExecutor asyncExecutor = asyncExecutor();

        testCombination0((stage, other) -> stage.thenCombineAsync(other, (v1, v2) -> {
            fail();
            return 1;
        }, asyncExecutor), true, CombinationTestMode.COMPLETE_EXCEPTIONAL);
    }

    @Test
    public void testThenCombineThrows() {
        testCombination0((stage, other) -> stage.thenCombine(other, (v1, v2) -> {
            throw EXPECTED_EXCEPTION;
        }), true, CombinationTestMode.THROW);
    }

    @Test
    public void testThenCombineAsyncThrows() {
        testCombination0((stage, other) -> stage.thenCombineAsync(other, (v1, v2) -> {
            throw EXPECTED_EXCEPTION;
        }), true, CombinationTestMode.THROW);
    }

    @Test
    public void testThenCombineAsyncWithExecutorThrows() {
        EventExecutor asyncExecutor = asyncExecutor();

        testCombination0((stage, other) -> stage.thenCombineAsync(other, (v1, v2) -> {
            throw EXPECTED_EXCEPTION;
        }, asyncExecutor), true, CombinationTestMode.THROW);
    }

    @Test
    public void testThenAcceptBoth() {
        testBoth0((stage, other) -> stage.thenAcceptBoth(other, (v1, v2) -> {
            assertSame(v1, INITIAL_BOOLEAN);
            assertSame(v2, INITIAL_BOOLEAN);
            assertTrue(stage.executor().inEventLoop());
        }), CombinationTestMode.COMPLETE);
    }

    @Test
    public void testThenAcceptBothAsync() {
        testBoth0((stage, other) -> stage.thenAcceptBothAsync(other, (v1, v2) -> {
            assertSame(v1, INITIAL_BOOLEAN);
            assertSame(v2, INITIAL_BOOLEAN);
            assertFalse(stage.executor().inEventLoop());
        }), CombinationTestMode.COMPLETE);
    }

    @Test
    public void testThenAcceptBothAsyncWithExecutor() {
        EventExecutor asyncExecutor = asyncExecutor();

        testBoth0((stage, other) -> stage.thenAcceptBothAsync(other, (v1, v2) -> {
            assertSame(v1, INITIAL_BOOLEAN);
            assertSame(v2, INITIAL_BOOLEAN);
            assertFalse(stage.executor().inEventLoop());
            assertTrue(asyncExecutor.inEventLoop());
        }, asyncExecutor), CombinationTestMode.COMPLETE);
    }

    @Test
    public void testThenAcceptBothThrowable() {
        testBoth0((stage, other) -> stage.thenAcceptBoth(other, (v1, v2) -> {
            fail();
        }), CombinationTestMode.COMPLETE_EXCEPTIONAL);
    }

    @Test
    public void testThenAcceptBothAsyncThrowable() {
        testBoth0((stage, other) -> stage.thenAcceptBothAsync(other, (v1, v2) -> {
            fail();
        }), CombinationTestMode.COMPLETE_EXCEPTIONAL);
    }

    @Test
    public void testThenAcceptBothAsyncWithExecutorThrowable() {
        EventExecutor asyncExecutor = asyncExecutor();

        testBoth0((stage, other) -> stage.thenAcceptBothAsync(other, (v1, v2) -> {
            fail();
        }, asyncExecutor), CombinationTestMode.COMPLETE_EXCEPTIONAL);
    }

    @Test
    public void testThenAcceptBothThrows() {
        testBoth0((stage, other) -> stage.thenAcceptBoth(other, (v1, v2) -> {
            throw EXPECTED_EXCEPTION;
        }), CombinationTestMode.THROW);
    }

    @Test
    public void testThenAcceptBothAsyncThrows() {
        testBoth0((stage, other) -> stage.thenCombineAsync(other, (v1, v2) -> {
            throw EXPECTED_EXCEPTION;
        }), CombinationTestMode.THROW);
    }

    @Test
    public void testThenAcceptBothAsyncWithExecutorThrows() {
        EventExecutor asyncExecutor = asyncExecutor();

        testBoth0((stage, other) -> stage.thenCombineAsync(other, (v1, v2) -> {
            throw EXPECTED_EXCEPTION;
        }, asyncExecutor), CombinationTestMode.THROW);
    }

    @Test
    public void testRunAfterBoth() {
        testBoth0((stage, other) -> stage.runAfterBoth(other, () -> {
            assertTrue(stage.executor().inEventLoop());
        }), CombinationTestMode.COMPLETE);
    }

    @Test
    public void testRunAfterBothAsync() {
        testBoth0((stage, other) -> stage.runAfterBothAsync(other, () -> {
            assertFalse(stage.executor().inEventLoop());
        }), CombinationTestMode.COMPLETE);
    }

    @Test
    public void testRunAfterBothAsyncWithExecutor() {
        EventExecutor asyncExecutor = asyncExecutor();

        testBoth0((stage, other) -> stage.runAfterBothAsync(other, () -> {
            assertFalse(stage.executor().inEventLoop());
            assertTrue(asyncExecutor.inEventLoop());
        }, asyncExecutor), CombinationTestMode.COMPLETE);
    }

    @Test
    public void testRunAfterBothThrowable() {
        testBoth0((stage, other) -> stage.runAfterBothAsync(other, () -> {
            fail();
        }), CombinationTestMode.COMPLETE_EXCEPTIONAL);
    }

    @Test
    public void testRunAfterBothAsyncThrowable() {
        testBoth0((stage, other) -> stage.runAfterBothAsync(other, () -> {
            fail();
        }), CombinationTestMode.COMPLETE_EXCEPTIONAL);
    }

    @Test
    public void testRunAfterBothAsyncWithExecutorThrowable() {
        EventExecutor asyncExecutor = asyncExecutor();

        testBoth0((stage, other) -> stage.runAfterBothAsync(other, () -> {
            fail();
        }, asyncExecutor), CombinationTestMode.COMPLETE_EXCEPTIONAL);
    }

    @Test
    public void testRunAfterBothThrows() {
        testBoth0((stage, other) -> stage.runAfterBothAsync(other, () -> {
            throw EXPECTED_EXCEPTION;
        }), CombinationTestMode.THROW);
    }

    @Test
    public void testRunAfterBothAsyncThrows() {
        testBoth0((stage, other) -> stage.runAfterBothAsync(other, () -> {
            throw EXPECTED_EXCEPTION;
        }), CombinationTestMode.THROW);
    }

    @Test
    public void testRunAfterBothAsyncWithExecutorThrows() {
        EventExecutor asyncExecutor = asyncExecutor();

        testBoth0((stage, other) -> stage.runAfterBothAsync(other, () -> {
            throw EXPECTED_EXCEPTION;
        }, asyncExecutor), CombinationTestMode.THROW);
    }

    @Test
    public void testApplyToEither() {
        testCombination0((stage, other) -> stage.applyToEither(other, v1 -> {
            assertSame(v1, INITIAL_BOOLEAN);
            assertTrue(stage.executor().inEventLoop());

            return 1;
        }), false, CombinationTestMode.COMPLETE);
    }

    @Test
    public void testApplyToEitherAsync() {
        testCombination0((stage, other) -> stage.applyToEitherAsync(other, v1 -> {
            assertSame(v1, INITIAL_BOOLEAN);
            assertFalse(stage.executor().inEventLoop());

            return 1;
        }), false, CombinationTestMode.COMPLETE);
    }

    @Test
    public void testApplyToEitherAsyncWithExecutor() {
        EventExecutor asyncExecutor = asyncExecutor();

        testCombination0((stage, other) -> stage.applyToEitherAsync(other, v1 -> {
            assertSame(v1, INITIAL_BOOLEAN);
            assertFalse(stage.executor().inEventLoop());
            assertTrue(asyncExecutor.inEventLoop());
            return 1;
        }, asyncExecutor), false, CombinationTestMode.COMPLETE);
    }

    @Test
    public void testApplyToEitherThrows() {
        testCombination0((stage, other) -> stage.applyToEither(other, v1 -> {
            throw EXPECTED_EXCEPTION;
        }), false, CombinationTestMode.THROW);
    }

    @Test
    public void testApplyToEitherAsyncThrows() {
        testCombination0((stage, other) -> stage.applyToEitherAsync(other, v1 -> {
            throw EXPECTED_EXCEPTION;
        }), false, CombinationTestMode.THROW);
    }

    @Test
    public void testApplyToEitherAsyncWithExecutorThrows() {
        EventExecutor asyncExecutor = asyncExecutor();

        testCombination0((stage, other) -> stage.applyToEitherAsync(other, v1 -> {
            throw EXPECTED_EXCEPTION;
        }, asyncExecutor), true, CombinationTestMode.THROW);
    }

    @Test
    public void testAcceptEither() {
        testEither0((stage, other) -> stage.acceptEither(other, v1 -> {
            assertSame(v1, INITIAL_BOOLEAN);
            assertTrue(stage.executor().inEventLoop());
        }), false, CombinationTestMode.COMPLETE);
    }

    @Test
    public void testAcceptEitherAsync() {
        testEither0((stage, other) -> stage.acceptEitherAsync(other, v1 -> {
            assertSame(v1, INITIAL_BOOLEAN);
            assertFalse(stage.executor().inEventLoop());
        }), false, CombinationTestMode.COMPLETE);
    }

    @Test
    public void testAcceptEitherAsyncWithExecutor() {
        EventExecutor asyncExecutor = asyncExecutor();

        testEither0((stage, other) -> stage.acceptEitherAsync(other, v1 -> {
            assertSame(v1, INITIAL_BOOLEAN);
            assertFalse(stage.executor().inEventLoop());
            assertTrue(asyncExecutor.inEventLoop());
        }, asyncExecutor), false, CombinationTestMode.COMPLETE);
    }

    @Test
    public void testAcceptEitherThrows() {
        testEither0((stage, other) -> stage.acceptEither(other, v1 -> {
            throw EXPECTED_EXCEPTION;
        }), false, CombinationTestMode.THROW);
    }

    @Test
    public void testAcceptEitherAsyncThrows() {
        testEither0((stage, other) -> stage.acceptEitherAsync(other, v1 -> {
            throw EXPECTED_EXCEPTION;
        }), false, CombinationTestMode.THROW);
    }

    @Test
    public void testAcceptEitherAsyncWithExecutorThrows() {
        EventExecutor asyncExecutor = asyncExecutor();

        testEither0((stage, other) -> stage.acceptEitherAsync(other, v1 -> {
            throw EXPECTED_EXCEPTION;
        }, asyncExecutor), true, CombinationTestMode.THROW);
    }

    @Test
    public void testRunAfterEither() {
        testEither0((stage, other) -> stage.runAfterEither(other, () -> {
            assertTrue(stage.executor().inEventLoop());
        }), false, CombinationTestMode.COMPLETE);
    }

    @Test
    public void testRunAfterEitherAsync() {
        testEither0((stage, other) -> stage.runAfterEitherAsync(other, () -> {
            assertFalse(stage.executor().inEventLoop());
        }), false, CombinationTestMode.COMPLETE);
    }

    @Test
    public void testRunAfterEitherAsyncWithExecutor() {
        EventExecutor asyncExecutor = asyncExecutor();

        testEither0((stage, other) -> stage.runAfterEitherAsync(other, () -> {
            assertFalse(stage.executor().inEventLoop());
            assertTrue(asyncExecutor.inEventLoop());
        }, asyncExecutor), false, CombinationTestMode.COMPLETE);
    }

    @Test
    public void testRunAfterEitherThrows() {
        testEither0((stage, other) -> stage.runAfterEither(other, () -> {
            throw EXPECTED_EXCEPTION;
        }), false, CombinationTestMode.THROW);
    }

    @Test
    public void testRunAfterEitherAsyncThrows() {
        testEither0((stage, other) -> stage.runAfterEitherAsync(other, () -> {
            throw EXPECTED_EXCEPTION;
        }), false, CombinationTestMode.THROW);
    }

    @Test
    public void testRunAfterEitherAsyncWithExecutorThrows() {
        EventExecutor asyncExecutor = asyncExecutor();

        testEither0((stage, other) -> stage.runAfterEitherAsync(other, () -> {
            throw EXPECTED_EXCEPTION;
        }, asyncExecutor), true, CombinationTestMode.THROW);
    }

    private enum CombinationTestMode {
        COMPLETE,
        COMPLETE_EXCEPTIONAL,
        THROW
    }

    private void testEither0(BiFunction<FutureCompletionStage<Boolean>,
            CompletionStage<Boolean>, FutureCompletionStage<Void>> fn,
                             boolean notifyAll, CombinationTestMode testMode) {
        testCombination0((futureStage, stage) -> fn.apply(futureStage, stage).thenApply(v -> {
            assertNull(v);
            return EXPECTED_INTEGER;
        }), notifyAll, testMode);
    }

    private void testBoth0(BiFunction<FutureCompletionStage<Boolean>,
            CompletionStage<Boolean>, FutureCompletionStage<Void>> fn, CombinationTestMode testMode) {
        testCombination0((futureStage, stage) -> fn.apply(futureStage, stage).thenApply(v -> {
            assertNull(v);
            return EXPECTED_INTEGER;
        }), true, testMode);
    }

    private void testCombination0(BiFunction<FutureCompletionStage<Boolean>,
            CompletionStage<Boolean>, FutureCompletionStage<Integer>> fn,
                           boolean notifyAll, CombinationTestMode testMode) {
        EventExecutor executor = executor();

        // We run this in a loop as we we need to ensure our implementation is thread-safe as the both stages
        // may use different threads.
        for (int i = 0; i < 1000; i++) {
            Promise<Boolean> promise = executor.newPromise();
            FutureCompletionStage<Boolean> stage = new DefaultFutureCompletionStage<>(promise);
            CompletableFuture<Boolean> completableFuture = new CompletableFuture<>();

            Future<Integer> f = fn.apply(stage, completableFuture).future();

            List<Runnable> runnables = new ArrayList<>(2);
            switch (testMode) {
                case THROW:
                    Collections.addAll(runnables, () -> completableFuture.completeExceptionally(EXPECTED_EXCEPTION),
                            () -> promise.setFailure(EXPECTED_EXCEPTION));
                    break;
                case COMPLETE_EXCEPTIONAL:
                    // Let's randomly notify either one or both of the promise / future with an exception.
                    int random = ThreadLocalRandom.current().nextInt(0, 3);
                    if (random == 0) {
                        Collections.addAll(runnables, () -> completableFuture.complete(INITIAL_BOOLEAN),
                                () -> promise.setFailure(EXPECTED_EXCEPTION));
                    } else if (random == 1) {
                        Collections.addAll(runnables, () -> completableFuture.completeExceptionally(EXPECTED_EXCEPTION),
                                () -> promise.setSuccess(INITIAL_BOOLEAN));
                    } else {
                        Collections.addAll(runnables, () -> completableFuture.completeExceptionally(EXPECTED_EXCEPTION),
                                () -> promise.setFailure(EXPECTED_EXCEPTION));
                    }
                    break;
                case COMPLETE:
                    Collections.addAll(runnables, () -> completableFuture.complete(INITIAL_BOOLEAN),
                            () -> promise.setSuccess(INITIAL_BOOLEAN));
                    break;
                default:
                    fail();
            }

            Collections.shuffle(runnables);
            for (Runnable task : runnables) {
                ForkJoinPool.commonPool().execute(task);
                if (!notifyAll) {
                    break;
                }
            }

            f.awaitUninterruptibly();

            switch (testMode) {
                case COMPLETE_EXCEPTIONAL:
                case THROW:
                    assertSame(EXPECTED_EXCEPTION, f.cause());
                    break;
                case COMPLETE:
                    assertEquals(EXPECTED_INTEGER, f.syncUninterruptibly().getNow());
                    break;
                default:
                    fail();
            }
        }
    }

    @Test
    public void testThenCompose() {
        testHandle0(newSucceededFuture(), stage -> stage.thenCompose(v -> {
            assertSame(INITIAL_BOOLEAN, v);

            assertTrue(stage.executor().inEventLoop());

            return CompletableFuture.completedFuture(EXPECTED_BOOLEAN);
        }), false);
    }

    @Test
    public void testThenComposeAsync() {
        testHandle0(newSucceededFuture(), stage -> stage.thenComposeAsync(v -> {
            assertSame(INITIAL_BOOLEAN, v);

            assertFalse(stage.executor().inEventLoop());

            return CompletableFuture.completedFuture(EXPECTED_BOOLEAN);
        }), false);
    }

    @Test
    public void testThenComposeAsyncWithExecutor() {
        EventExecutor asyncExecutor = asyncExecutor();

        testHandle0(newSucceededFuture(), stage -> stage.thenComposeAsync(v -> {
            assertSame(INITIAL_BOOLEAN, v);

            assertFalse(stage.executor().inEventLoop());
            assertTrue(asyncExecutor.inEventLoop());

            return CompletableFuture.completedFuture(EXPECTED_BOOLEAN);
        }, asyncExecutor), false);
    }

    @Test
    public void testThenComposeThrows() {
        testHandle0(newSucceededFuture(), stage -> stage.thenCompose(v -> {
            throw EXPECTED_EXCEPTION;
        }), true);
    }

    @Test
    public void testThenComposeAsyncThrows() {
        testHandle0(newSucceededFuture(), stage -> stage.thenComposeAsync(v -> {
            throw EXPECTED_EXCEPTION;
        }), true);
    }

    @Test
    public void testThenComposeWithExecutorThrows() {
        EventExecutor asyncExecutor = asyncExecutor();
        testHandle0(newSucceededFuture(), stage -> stage.thenComposeAsync(v -> {
            throw EXPECTED_EXCEPTION;
        }, asyncExecutor), true);
    }

    @Test
    public void testExceptionally() {
        testHandle0(newFailedFuture(), stage -> stage.exceptionally(error -> {
            assertSame(EXPECTED_EXCEPTION, error);
            return EXPECTED_BOOLEAN;
        }), false);
    }

    @Test
    public void testExceptionallyThrows() {
        testHandle0(newFailedFuture(), stage -> stage.exceptionally(error -> {
            throw EXPECTED_EXCEPTION;
        }), true);
    }

    @Test
    public void testWhenComplete() {
        testWhenComplete0(newSucceededFuture(), stage -> stage.whenComplete((v, cause) -> {
            assertSame(INITIAL_BOOLEAN, v);
            assertNull(cause);
            assertTrue(stage.executor().inEventLoop());
        }), false);
    }

    @Test
    public void testWhenCompleteAsync() {
        testWhenComplete0(newSucceededFuture(), stage -> stage.whenCompleteAsync((v, cause) -> {
            assertSame(INITIAL_BOOLEAN, v);
            assertNull(cause);

            assertFalse(stage.executor().inEventLoop());
        }), false);
    }

    @Test
    public void testWhenCompleteAsyncWithExecutor() {
        EventExecutor asyncExecutor = asyncExecutor();

        testWhenComplete0(newSucceededFuture(), stage -> stage.whenCompleteAsync((v, cause) -> {
            assertSame(INITIAL_BOOLEAN, v);
            assertNull(cause);

            assertFalse(stage.executor().inEventLoop());
            assertTrue(asyncExecutor.inEventLoop());
        }, asyncExecutor), false);
    }

    @Test
    public void testWhenCompleteThrowable() {
        testWhenComplete0(newFailedFuture(), stage -> stage.whenComplete((v, cause) -> {
            assertSame(EXPECTED_EXCEPTION, cause);
            assertNull(v);

            assertTrue(stage.future().executor().inEventLoop());
        }), true);
    }

    @Test
    public void testWhenCompleteAsyncThrowable() {
        testWhenComplete0(newFailedFuture(), stage -> stage.whenCompleteAsync((v, cause) -> {
            assertSame(EXPECTED_EXCEPTION, cause);
            assertNull(v);

            assertFalse(stage.future().executor().inEventLoop());
        }), true);
    }

    @Test
    public void testWhenCompleteAsyncWithExecutorThrowable() {
        EventExecutor asyncExecutor = asyncExecutor();
        testWhenComplete0(newFailedFuture(), stage -> stage.whenCompleteAsync((v, cause) -> {
            assertSame(EXPECTED_EXCEPTION, cause);
            assertNull(v);

            assertFalse(stage.future().executor().inEventLoop());
            assertTrue(asyncExecutor.inEventLoop());
        }, asyncExecutor), true);
    }

    @Test
    public void testWhenCompleteThrows() {
        testWhenComplete0(newSucceededFuture(), stage -> stage.whenComplete((v, cause) -> {
            throw EXPECTED_EXCEPTION;
        }), true);
    }

    @Test
    public void testWhenCompleteAsyncThrows() {
        testWhenComplete0(newSucceededFuture(), stage -> stage.whenCompleteAsync((v, cause) -> {
            throw EXPECTED_EXCEPTION;
        }), true);
    }

    @Test
    public void testWhenCompleteAsyncWithExecutorThrows() {
        EventExecutor asyncExecutor = asyncExecutor();
        testWhenComplete0(newSucceededFuture(), stage -> stage.whenCompleteAsync((v, cause) -> {
            throw EXPECTED_EXCEPTION;
        }, asyncExecutor), true);
    }

    @Test
    public void testHandle() {
        testHandle0(newSucceededFuture(), stage -> stage.handle((v, cause) -> {
            assertSame(INITIAL_BOOLEAN, v);
            assertNull(cause);

            assertTrue(stage.executor().inEventLoop());

            return EXPECTED_BOOLEAN;
        }), false);
    }

    @Test
    public void testHandleAsync() {
        testHandle0(newSucceededFuture(), stage -> stage.handleAsync((v, cause) -> {
            assertSame(INITIAL_BOOLEAN, v);
            assertNull(cause);

            assertFalse(stage.executor().inEventLoop());

            return EXPECTED_BOOLEAN;
        }), false);
    }

    @Test
    public void testHandleAsyncWithExecutor() {
        EventExecutor asyncExecutor = asyncExecutor();

        testHandle0(newSucceededFuture(), stage -> stage.handleAsync((v, cause) -> {
            assertSame(INITIAL_BOOLEAN, v);
            assertNull(cause);

            assertFalse(stage.executor().inEventLoop());
            assertTrue(asyncExecutor.inEventLoop());

            return EXPECTED_BOOLEAN;
        }, asyncExecutor), false);
    }

    @Test
    public void testHandleThrowable() {
        testHandle0(newFailedFuture(), stage -> stage.handle((v, cause) -> {
            assertSame(EXPECTED_EXCEPTION, cause);
            assertNull(v);

            assertTrue(stage.future().executor().inEventLoop());

            return EXPECTED_BOOLEAN;
        }), false);
    }

    @Test
    public void testHandleAsyncThrowable() {
        testHandle0(newFailedFuture(), stage -> stage.handleAsync((v, cause) -> {
            assertSame(EXPECTED_EXCEPTION, cause);
            assertNull(v);

            assertFalse(stage.future().executor().inEventLoop());

            return EXPECTED_BOOLEAN;
        }), false);
    }

    @Test
    public void testHandleAsyncWithExecutorThrowable() {
        EventExecutor asyncExecutor = asyncExecutor();
        testHandle0(newFailedFuture(), stage -> stage.handleAsync((v, cause) -> {
            assertSame(EXPECTED_EXCEPTION, cause);
            assertNull(v);

            assertFalse(stage.future().executor().inEventLoop());
            assertTrue(asyncExecutor.inEventLoop());

            return EXPECTED_BOOLEAN;
        }, asyncExecutor), false);
    }

    @Test
    public void testHandleFunctionThrows() {
        testHandle0(newSucceededFuture(), stage -> stage.handle((v, cause) -> {
            throw EXPECTED_EXCEPTION;
        }), true);
    }

    @Test
    public void testHandleAsyncFunctionThrows() {
        testHandle0(newSucceededFuture(), stage -> stage.handleAsync((v, cause) -> {
            throw EXPECTED_EXCEPTION;
        }), true);
    }

    @Test
    public void testHandleAsyncWithExecutorFunctionThrows() {
        EventExecutor asyncExecutor = asyncExecutor();
        testHandle0(newSucceededFuture(), stage -> stage.handleAsync((v, cause) -> {
            throw EXPECTED_EXCEPTION;
        }, asyncExecutor), true);
    }
}
