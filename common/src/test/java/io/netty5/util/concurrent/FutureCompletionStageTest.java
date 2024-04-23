/*
 * Copyright 2019 The Netty Project
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

import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class FutureCompletionStageTest {
    @AutoClose("shutdownGracefully")
    private static EventExecutorGroup group;
    @AutoClose("shutdownGracefully")
    private static EventExecutorGroup asyncExecutorGroup;
    private static final IllegalStateException EXPECTED_EXCEPTION = new IllegalStateException();
    private static final Integer EXPECTED_INTEGER = 1;
    private static final Boolean INITIAL_BOOLEAN = Boolean.TRUE;
    private static final Boolean EXPECTED_BOOLEAN = Boolean.FALSE;

    @BeforeAll
    public static void setup() {
        group = new MultithreadEventExecutorGroup(1, Executors.defaultThreadFactory());
        asyncExecutorGroup = new MultithreadEventExecutorGroup(1, Executors.defaultThreadFactory());
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
        FutureCompletionStage<Boolean> stage = promise.asFuture().asStage();
        assertSame(executor, stage.executor());
        assertSame(promise, stage.future());
    }

    @Test
    public void testThrowsUnsupportedOperationException() {
        EventExecutor executor = executor();
        Promise<Boolean> promise = executor.newPromise();
        FutureCompletionStage<Boolean> stage = promise.asFuture().asStage();
        assertThrows(UnsupportedOperationException.class, () -> stage.toCompletableFuture());
    }

    @Test
    public void testThenApply() throws Exception {
        testThenApply0(stage -> stage.thenApply(v -> {
            assertSame(INITIAL_BOOLEAN, v);
            assertTrue(stage.executor().inEventLoop());

            return EXPECTED_BOOLEAN;
        }), false);
    }

    @Test
    public void testThenApplyAsync() throws Exception {
        testThenApply0(stage -> stage.thenApplyAsync(v -> {
            assertSame(INITIAL_BOOLEAN, v);
            assertFalse(stage.executor().inEventLoop());

            return EXPECTED_BOOLEAN;
        }), false);
    }

    @Test
    public void testThenApplyAsyncWithExecutor() throws Exception {
        EventExecutor asyncExecutor = asyncExecutor();
        testThenApply0(stage -> stage.thenApplyAsync(v -> {
            assertSame(INITIAL_BOOLEAN, v);
            assertFalse(stage.executor().inEventLoop());

            return EXPECTED_BOOLEAN;
        }, asyncExecutor), false);
    }

    @Test
    public void testThenApplyCallbackNotExecuted() throws Exception {
        testHandle0(newFailedFuture(), stage -> stage.thenApply(v -> {
            fail();
            return null;
        }), true);
    }

    @Test
    public void testThenApplyAsyncCallbackNotExecuted() throws Exception {
        testHandle0(newFailedFuture(), stage -> stage.thenApplyAsync(v -> {
            fail();
            return null;
        }), true);
    }

    @Test
    public void testThenApplyAsyncWithExecutorCallbackNotExecuted() throws Exception {
        EventExecutor asyncExecutor = asyncExecutor();
        testHandle0(newFailedFuture(), stage -> stage.thenApplyAsync(v -> {
            fail();
            return null;
        }, asyncExecutor), true);
    }

    @Test
    public void testThenApplyFunctionThrows() throws Exception {
        testThenApply0(stage -> stage.thenApply(v -> {
            throw EXPECTED_EXCEPTION;
        }), true);
    }

    @Test
    public void testThenApplyAsyncFunctionThrows() throws Exception {
        testThenApply0(stage -> stage.thenApplyAsync(v -> {
            throw EXPECTED_EXCEPTION;
        }), true);
    }

    @Test
    public void testThenApplyAsyncWithExecutorFunctionThrows() throws Exception {
        EventExecutor asyncExecutor = asyncExecutor();
        testThenApply0(stage -> stage.thenApplyAsync(v -> {
            throw EXPECTED_EXCEPTION;
        }, asyncExecutor), true);
    }

    private static void testHandle0(Future<Boolean> future,
                                    Function<FutureCompletionStage<Boolean>, FutureCompletionStage<Boolean>> fn,
                                    boolean exception) throws Exception {
        FutureCompletionStage<Boolean> stage = future.asStage();

        Future<Boolean> f = fn.apply(stage).await().future();
        if (exception) {
            assertSame(EXPECTED_EXCEPTION, f.cause());
        } else {
            assertSame(EXPECTED_BOOLEAN, f.asStage().get());
        }
    }

    private static void testWhenComplete0(Future<Boolean> future,
                                          Function<FutureCompletionStage<Boolean>, FutureCompletionStage<Boolean>> fn,
                                          boolean exception) throws Exception {
        testHandle0(future, stage -> fn.apply(stage).thenApply(v -> {
            assertSame(INITIAL_BOOLEAN, v);
            return EXPECTED_BOOLEAN;
        }), exception);
    }

    private static void testThenApply0(
            Function<FutureCompletionStage<Boolean>, FutureCompletionStage<Boolean>> fn, boolean exception)
            throws Exception {
        testHandle0(newSucceededFuture(), fn, exception);
    }

    @Test
    public void testThenAccept() throws Exception {
        testThenAccept0(stage -> stage.thenAccept(v -> {
            assertSame(INITIAL_BOOLEAN, v);
            assertTrue(stage.executor().inEventLoop());
        }), false);
    }

    @Test
    public void testThenAcceptAsync() throws Exception {
        testThenAccept0(stage -> stage.thenAcceptAsync(v -> {
            assertSame(INITIAL_BOOLEAN, v);

            assertFalse(stage.executor().inEventLoop());
        }), false);
    }

    @Test
    public void testThenAcceptAsyncWithExecutor() throws Exception {
        EventExecutor asyncExecutor = asyncExecutor();
        testThenAccept0(stage -> stage.thenAcceptAsync(v -> {
            assertSame(INITIAL_BOOLEAN, v);

            assertFalse(stage.executor().inEventLoop());
            assertTrue(asyncExecutor.inEventLoop());
        }, asyncExecutor), false);
    }

    @Test
    public void testThenAcceptCallbackNotExecuted() throws Exception {
        testThenAccept0(newFailedFuture(), stage -> stage.thenAccept(v -> {
            fail();
        }), true);
    }

    @Test
    public void testThenAcceptAsyncCallbackNotExecuted() throws Exception {
        testThenAccept0(newFailedFuture(), stage -> stage.thenAcceptAsync(v -> {
            fail();
        }), true);
    }

    @Test
    public void testThenAcceptAsyncWithExecutorCallbackNotExecuted() throws Exception {
        EventExecutor asyncExecutor = asyncExecutor();
        testThenAccept0(newFailedFuture(), stage -> stage.thenAcceptAsync(v -> {
           fail();
        }, asyncExecutor), true);
    }

    @Test
    public void testThenAcceptConsumerThrows() throws Exception {
        testThenAccept0(stage -> stage.thenAccept(v -> {
            throw EXPECTED_EXCEPTION;
        }), true);
    }

    @Test
    public void testThenAcceptAsyncConsumerThrows() throws Exception {
        testThenAccept0(stage -> stage.thenAcceptAsync(v -> {
            throw EXPECTED_EXCEPTION;
        }), true);
    }

    @Test
    public void testThenAcceptAsyncWithExecutorConsumerThrows() throws Exception {
        EventExecutor asyncExecutor = asyncExecutor();
        testThenAccept0(stage -> stage.thenAcceptAsync(v -> {
            throw EXPECTED_EXCEPTION;
        }, asyncExecutor), true);
    }

    private static void testThenAccept0(
            Function<FutureCompletionStage<Boolean>, FutureCompletionStage<Void>> fn, boolean exception)
            throws Exception {
        testThenAccept0(newSucceededFuture(), fn, exception);
    }

    private static void testThenAccept0(Future<Boolean> future,
                                        Function<FutureCompletionStage<Boolean>, FutureCompletionStage<Void>> fn,
                                        boolean exception)
            throws Exception {
        FutureCompletionStage<Void> stage = fn.apply(future.asStage());
        if (exception) {
            assertSame(EXPECTED_EXCEPTION, stage.getCause());
        } else {
            assertNull(stage.get());
        }
    }

    @Test
    public void testThenRun() throws Exception {
        testThenAccept0(stage -> stage.thenRun(() -> {
            assertTrue(stage.executor().inEventLoop());
        }), false);
    }

    @Test
    public void testThenRunAsync() throws Exception {
        testThenAccept0(stage -> stage.thenRunAsync(() -> {
            assertFalse(stage.executor().inEventLoop());
        }), false);
    }

    @Test
    public void testThenRunAsyncWithExecutor() throws Exception {
        EventExecutor asyncExecutor = asyncExecutor();
        testThenAccept0(stage -> stage.thenRunAsync(() -> {
            assertFalse(stage.executor().inEventLoop());
            assertTrue(asyncExecutor.inEventLoop());
        }, asyncExecutor), false);
    }

    @Test
    public void testThenRunCallbackNotExecuted() throws Exception {
        testThenAccept0(newFailedFuture(), stage -> stage.thenRun(() -> {
            fail();
        }), true);
    }

    @Test
    public void testThenRunAsyncCallbackNotExecuted() throws Exception {
        testThenAccept0(newFailedFuture(), stage -> stage.thenRunAsync(() -> {
            fail();
        }), true);
    }

    @Test
    public void testThenRunAsyncWithExecutorCallbackNotExecuted() throws Exception {
        EventExecutor asyncExecutor = asyncExecutor();
        testThenAccept0(newFailedFuture(), stage -> stage.thenRunAsync(() -> {
            fail();
        }, asyncExecutor), true);
    }

    @Test
    public void testThenRunTaskThrows() throws Exception {
        testThenAccept0(stage -> stage.thenRun(() -> {
            throw EXPECTED_EXCEPTION;
        }), true);
    }

    @Test
    public void testThenRunAsyncTaskThrows() throws Exception {
        testThenAccept0(stage -> stage.thenRunAsync(() -> {
            throw EXPECTED_EXCEPTION;
        }), true);
    }

    @Test
    public void testThenRunAsyncWithExecutorTaskThrows() throws Exception {
        EventExecutor asyncExecutor = asyncExecutor();
        testThenAccept0(stage -> stage.thenRunAsync(() -> {
            throw EXPECTED_EXCEPTION;
        }, asyncExecutor), true);
    }

    @Test
    public void testThenCombine() throws Exception {
        testCombination0((stage, other) -> stage.thenCombine(other, (v1, v2) -> {
            assertSame(v1, INITIAL_BOOLEAN);
            assertSame(v2, INITIAL_BOOLEAN);
            assertTrue(stage.executor().inEventLoop());

            return 1;
        }), true, CombinationTestMode.COMPLETE);
    }

    @Test
    public void testThenCombineAsync() throws Exception {
        testCombination0((stage, other) -> stage.thenCombineAsync(other, (v1, v2) -> {
            assertSame(v1, INITIAL_BOOLEAN);
            assertSame(v2, INITIAL_BOOLEAN);
            assertFalse(stage.executor().inEventLoop());

            return 1;
        }), true, CombinationTestMode.COMPLETE);
    }

    @Test
    public void testThenCombineAsyncWithExecutor() throws Exception {
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
    public void testThenCombineThrowable() throws Exception {
        testCombination0((stage, other) -> stage.thenCombine(other, (v1, v2) -> {
            fail();
            return 1;
        }), true, CombinationTestMode.COMPLETE_EXCEPTIONAL);
    }

    @Test
    public void testThenCombineAsyncThrowable() throws Exception {
        testCombination0((stage, other) -> stage.thenCombineAsync(other, (v1, v2) -> {
            fail();
            return 1;
        }), true, CombinationTestMode.COMPLETE_EXCEPTIONAL);
    }

    @Test
    public void testThenCombineAsyncWithExecutorThrowable() throws Exception {
        EventExecutor asyncExecutor = asyncExecutor();

        testCombination0((stage, other) -> stage.thenCombineAsync(other, (v1, v2) -> {
            fail();
            return 1;
        }, asyncExecutor), true, CombinationTestMode.COMPLETE_EXCEPTIONAL);
    }

    @Test
    public void testThenCombineThrows() throws Exception {
        testCombination0((stage, other) -> stage.thenCombine(other, (v1, v2) -> {
            throw EXPECTED_EXCEPTION;
        }), true, CombinationTestMode.THROW);
    }

    @Test
    public void testThenCombineAsyncThrows() throws Exception {
        testCombination0((stage, other) -> stage.thenCombineAsync(other, (v1, v2) -> {
            throw EXPECTED_EXCEPTION;
        }), true, CombinationTestMode.THROW);
    }

    @Test
    public void testThenCombineAsyncWithExecutorThrows() throws Exception {
        EventExecutor asyncExecutor = asyncExecutor();

        testCombination0((stage, other) -> stage.thenCombineAsync(other, (v1, v2) -> {
            throw EXPECTED_EXCEPTION;
        }, asyncExecutor), true, CombinationTestMode.THROW);
    }

    @Test
    public void testThenAcceptBoth() throws Exception {
        testBoth0((stage, other) -> stage.thenAcceptBoth(other, (v1, v2) -> {
            assertSame(v1, INITIAL_BOOLEAN);
            assertSame(v2, INITIAL_BOOLEAN);
            assertTrue(stage.executor().inEventLoop());
        }), CombinationTestMode.COMPLETE);
    }

    @Test
    public void testThenAcceptBothAsync() throws Exception {
        testBoth0((stage, other) -> stage.thenAcceptBothAsync(other, (v1, v2) -> {
            assertSame(v1, INITIAL_BOOLEAN);
            assertSame(v2, INITIAL_BOOLEAN);
            assertFalse(stage.executor().inEventLoop());
        }), CombinationTestMode.COMPLETE);
    }

    @Test
    public void testThenAcceptBothAsyncWithExecutor() throws Exception {
        EventExecutor asyncExecutor = asyncExecutor();

        testBoth0((stage, other) -> stage.thenAcceptBothAsync(other, (v1, v2) -> {
            assertSame(v1, INITIAL_BOOLEAN);
            assertSame(v2, INITIAL_BOOLEAN);
            assertFalse(stage.executor().inEventLoop());
            assertTrue(asyncExecutor.inEventLoop());
        }, asyncExecutor), CombinationTestMode.COMPLETE);
    }

    @Test
    public void testThenAcceptBothThrowable() throws Exception {
        testBoth0((stage, other) -> stage.thenAcceptBoth(other, (v1, v2) -> {
            fail();
        }), CombinationTestMode.COMPLETE_EXCEPTIONAL);
    }

    @Test
    public void testThenAcceptBothAsyncThrowable() throws Exception {
        testBoth0((stage, other) -> stage.thenAcceptBothAsync(other, (v1, v2) -> {
            fail();
        }), CombinationTestMode.COMPLETE_EXCEPTIONAL);
    }

    @Test
    public void testThenAcceptBothAsyncWithExecutorThrowable() throws Exception {
        EventExecutor asyncExecutor = asyncExecutor();

        testBoth0((stage, other) -> stage.thenAcceptBothAsync(other, (v1, v2) -> {
            fail();
        }, asyncExecutor), CombinationTestMode.COMPLETE_EXCEPTIONAL);
    }

    @Test
    public void testThenAcceptBothThrows() throws Exception {
        testBoth0((stage, other) -> stage.thenAcceptBoth(other, (v1, v2) -> {
            throw EXPECTED_EXCEPTION;
        }), CombinationTestMode.THROW);
    }

    @Test
    public void testThenAcceptBothAsyncThrows() throws Exception {
        testBoth0((stage, other) -> stage.thenCombineAsync(other, (v1, v2) -> {
            throw EXPECTED_EXCEPTION;
        }), CombinationTestMode.THROW);
    }

    @Test
    public void testThenAcceptBothAsyncWithExecutorThrows() throws Exception {
        EventExecutor asyncExecutor = asyncExecutor();

        testBoth0((stage, other) -> stage.thenCombineAsync(other, (v1, v2) -> {
            throw EXPECTED_EXCEPTION;
        }, asyncExecutor), CombinationTestMode.THROW);
    }

    @Test
    public void testRunAfterBoth() throws Exception {
        testBoth0((stage, other) -> stage.runAfterBoth(other, () -> {
            assertTrue(stage.executor().inEventLoop());
        }), CombinationTestMode.COMPLETE);
    }

    @Test
    public void testRunAfterBothAsync() throws Exception {
        testBoth0((stage, other) -> stage.runAfterBothAsync(other, () -> {
            assertFalse(stage.executor().inEventLoop());
        }), CombinationTestMode.COMPLETE);
    }

    @Test
    public void testRunAfterBothAsyncWithExecutor() throws Exception {
        EventExecutor asyncExecutor = asyncExecutor();

        testBoth0((stage, other) -> stage.runAfterBothAsync(other, () -> {
            assertFalse(stage.executor().inEventLoop());
            assertTrue(asyncExecutor.inEventLoop());
        }, asyncExecutor), CombinationTestMode.COMPLETE);
    }

    @Test
    public void testRunAfterBothThrowable() throws Exception {
        testBoth0((stage, other) -> stage.runAfterBothAsync(other, () -> {
            fail();
        }), CombinationTestMode.COMPLETE_EXCEPTIONAL);
    }

    @Test
    public void testRunAfterBothAsyncThrowable() throws Exception {
        testBoth0((stage, other) -> stage.runAfterBothAsync(other, () -> {
            fail();
        }), CombinationTestMode.COMPLETE_EXCEPTIONAL);
    }

    @Test
    public void testRunAfterBothAsyncWithExecutorThrowable() throws Exception {
        EventExecutor asyncExecutor = asyncExecutor();

        testBoth0((stage, other) -> stage.runAfterBothAsync(other, () -> {
            fail();
        }, asyncExecutor), CombinationTestMode.COMPLETE_EXCEPTIONAL);
    }

    @Test
    public void testRunAfterBothThrows() throws Exception {
        testBoth0((stage, other) -> stage.runAfterBothAsync(other, () -> {
            throw EXPECTED_EXCEPTION;
        }), CombinationTestMode.THROW);
    }

    @Test
    public void testRunAfterBothAsyncThrows() throws Exception {
        testBoth0((stage, other) -> stage.runAfterBothAsync(other, () -> {
            throw EXPECTED_EXCEPTION;
        }), CombinationTestMode.THROW);
    }

    @Test
    public void testRunAfterBothAsyncWithExecutorThrows() throws Exception {
        EventExecutor asyncExecutor = asyncExecutor();

        testBoth0((stage, other) -> stage.runAfterBothAsync(other, () -> {
            throw EXPECTED_EXCEPTION;
        }, asyncExecutor), CombinationTestMode.THROW);
    }

    @Test
    public void testApplyToEither() throws Exception {
        testCombination0((stage, other) -> stage.applyToEither(other, v1 -> {
            assertSame(v1, INITIAL_BOOLEAN);
            assertTrue(stage.executor().inEventLoop());

            return 1;
        }), false, CombinationTestMode.COMPLETE);
    }

    @Test
    public void testApplyToEitherAsync() throws Exception {
        testCombination0((stage, other) -> stage.applyToEitherAsync(other, v1 -> {
            assertSame(v1, INITIAL_BOOLEAN);
            assertFalse(stage.executor().inEventLoop());

            return 1;
        }), false, CombinationTestMode.COMPLETE);
    }

    @Test
    public void testApplyToEitherAsyncWithExecutor() throws Exception {
        EventExecutor asyncExecutor = asyncExecutor();

        testCombination0((stage, other) -> stage.applyToEitherAsync(other, v1 -> {
            assertSame(v1, INITIAL_BOOLEAN);
            assertFalse(stage.executor().inEventLoop());
            assertTrue(asyncExecutor.inEventLoop());
            return 1;
        }, asyncExecutor), false, CombinationTestMode.COMPLETE);
    }

    @Test
    public void testApplyToEitherThrows() throws Exception {
        testCombination0((stage, other) -> stage.applyToEither(other, v1 -> {
            throw EXPECTED_EXCEPTION;
        }), false, CombinationTestMode.THROW);
    }

    @Test
    public void testApplyToEitherAsyncThrows() throws Exception {
        testCombination0((stage, other) -> stage.applyToEitherAsync(other, v1 -> {
            throw EXPECTED_EXCEPTION;
        }), false, CombinationTestMode.THROW);
    }

    @Test
    public void testApplyToEitherAsyncWithExecutorThrows() throws Exception {
        EventExecutor asyncExecutor = asyncExecutor();

        testCombination0((stage, other) -> stage.applyToEitherAsync(other, v1 -> {
            throw EXPECTED_EXCEPTION;
        }, asyncExecutor), true, CombinationTestMode.THROW);
    }

    @Test
    public void testAcceptEither() throws Exception {
        testEither0((stage, other) -> stage.acceptEither(other, v1 -> {
            assertSame(v1, INITIAL_BOOLEAN);
            assertTrue(stage.executor().inEventLoop());
        }), false, CombinationTestMode.COMPLETE);
    }

    @Test
    public void testAcceptEitherAsync() throws Exception {
        testEither0((stage, other) -> stage.acceptEitherAsync(other, v1 -> {
            assertSame(v1, INITIAL_BOOLEAN);
            assertFalse(stage.executor().inEventLoop());
        }), false, CombinationTestMode.COMPLETE);
    }

    @Test
    public void testAcceptEitherAsyncWithExecutor() throws Exception {
        EventExecutor asyncExecutor = asyncExecutor();

        testEither0((stage, other) -> stage.acceptEitherAsync(other, v1 -> {
            assertSame(v1, INITIAL_BOOLEAN);
            assertFalse(stage.executor().inEventLoop());
            assertTrue(asyncExecutor.inEventLoop());
        }, asyncExecutor), false, CombinationTestMode.COMPLETE);
    }

    @Test
    public void testAcceptEitherThrows() throws Exception {
        testEither0((stage, other) -> stage.acceptEither(other, v1 -> {
            throw EXPECTED_EXCEPTION;
        }), false, CombinationTestMode.THROW);
    }

    @Test
    public void testAcceptEitherAsyncThrows() throws Exception {
        testEither0((stage, other) -> stage.acceptEitherAsync(other, v1 -> {
            throw EXPECTED_EXCEPTION;
        }), false, CombinationTestMode.THROW);
    }

    @Test
    public void testAcceptEitherAsyncWithExecutorThrows() throws Exception {
        EventExecutor asyncExecutor = asyncExecutor();

        testEither0((stage, other) -> stage.acceptEitherAsync(other, v1 -> {
            throw EXPECTED_EXCEPTION;
        }, asyncExecutor), true, CombinationTestMode.THROW);
    }

    @Test
    public void testRunAfterEither() throws Exception {
        testEither0((stage, other) -> stage.runAfterEither(other, () -> {
            assertTrue(stage.executor().inEventLoop());
        }), false, CombinationTestMode.COMPLETE);
    }

    @Test
    public void testRunAfterEitherAsync() throws Exception {
        testEither0((stage, other) -> stage.runAfterEitherAsync(other, () -> {
            assertFalse(stage.executor().inEventLoop());
        }), false, CombinationTestMode.COMPLETE);
    }

    @Test
    public void testRunAfterEitherAsyncWithExecutor() throws Exception {
        EventExecutor asyncExecutor = asyncExecutor();

        testEither0((stage, other) -> stage.runAfterEitherAsync(other, () -> {
            assertFalse(stage.executor().inEventLoop());
            assertTrue(asyncExecutor.inEventLoop());
        }, asyncExecutor), false, CombinationTestMode.COMPLETE);
    }

    @Test
    public void testRunAfterEitherThrows() throws Exception {
        testEither0((stage, other) -> stage.runAfterEither(other, () -> {
            throw EXPECTED_EXCEPTION;
        }), false, CombinationTestMode.THROW);
    }

    @Test
    public void testRunAfterEitherAsyncThrows() throws Exception {
        testEither0((stage, other) -> stage.runAfterEitherAsync(other, () -> {
            throw EXPECTED_EXCEPTION;
        }), false, CombinationTestMode.THROW);
    }

    @Test
    public void testRunAfterEitherAsyncWithExecutorThrows() throws Exception {
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

    private static void testEither0(BiFunction<FutureCompletionStage<Boolean>,
            CompletionStage<Boolean>, FutureCompletionStage<Void>> fn,
                                    boolean notifyAll, CombinationTestMode testMode) throws Exception {
        testCombination0((futureStage, stage) -> fn.apply(futureStage, stage).thenApply(v -> {
            assertNull(v);
            return EXPECTED_INTEGER;
        }), notifyAll, testMode);
    }

    private static void testBoth0(BiFunction<FutureCompletionStage<Boolean>,
            CompletionStage<Boolean>, FutureCompletionStage<Void>> fn, CombinationTestMode testMode) throws Exception {
        testCombination0((futureStage, stage) -> fn.apply(futureStage, stage).thenApply(v -> {
            assertNull(v);
            return EXPECTED_INTEGER;
        }), true, testMode);
    }

    private static void testCombination0(BiFunction<FutureCompletionStage<Boolean>,
            CompletionStage<Boolean>, FutureCompletionStage<Integer>> fn,
                                         boolean notifyAll, CombinationTestMode testMode) throws Exception {
        EventExecutor executor = executor();

        // We run this in a loop as we need to ensure our implementation is thread-safe as the both stages
        // may use different threads.
        for (int i = 0; i < 1000; i++) {
            Promise<Boolean> promise = executor.newPromise();
            FutureCompletionStage<Boolean> stage = promise.asFuture().asStage();
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

            f.asStage().await().future();

            switch (testMode) {
                case COMPLETE_EXCEPTIONAL:
                case THROW:
                    assertSame(EXPECTED_EXCEPTION, f.cause());
                    break;
                case COMPLETE:
                    assertEquals(EXPECTED_INTEGER, f.asStage().get());
                    break;
                default:
                    fail();
            }
        }
    }

    @Test
    public void testThenCompose() throws Exception {
        testHandle0(newSucceededFuture(), stage -> stage.thenCompose(v -> {
            assertSame(INITIAL_BOOLEAN, v);

            assertTrue(stage.executor().inEventLoop());

            return CompletableFuture.completedFuture(EXPECTED_BOOLEAN);
        }), false);
    }

    @Test
    public void testThenComposeAsync() throws Exception {
        testHandle0(newSucceededFuture(), stage -> stage.thenComposeAsync(v -> {
            assertSame(INITIAL_BOOLEAN, v);

            assertFalse(stage.executor().inEventLoop());

            return CompletableFuture.completedFuture(EXPECTED_BOOLEAN);
        }), false);
    }

    @Test
    public void testThenComposeAsyncWithExecutor() throws Exception {
        EventExecutor asyncExecutor = asyncExecutor();

        testHandle0(newSucceededFuture(), stage -> stage.thenComposeAsync(v -> {
            assertSame(INITIAL_BOOLEAN, v);

            assertFalse(stage.executor().inEventLoop());
            assertTrue(asyncExecutor.inEventLoop());

            return CompletableFuture.completedFuture(EXPECTED_BOOLEAN);
        }, asyncExecutor), false);
    }

    @Test
    public void testThenComposeThrows() throws Exception {
        testHandle0(newSucceededFuture(), stage -> stage.thenCompose(v -> {
            throw EXPECTED_EXCEPTION;
        }), true);
    }

    @Test
    public void testThenComposeAsyncThrows() throws Exception {
        testHandle0(newSucceededFuture(), stage -> stage.thenComposeAsync(v -> {
            throw EXPECTED_EXCEPTION;
        }), true);
    }

    @Test
    public void testThenComposeWithExecutorThrows() throws Exception {
        EventExecutor asyncExecutor = asyncExecutor();
        testHandle0(newSucceededFuture(), stage -> stage.thenComposeAsync(v -> {
            throw EXPECTED_EXCEPTION;
        }, asyncExecutor), true);
    }

    @Test
    public void testExceptionally() throws Exception {
        testHandle0(newFailedFuture(), stage -> stage.exceptionally(error -> {
            assertSame(EXPECTED_EXCEPTION, error);
            return EXPECTED_BOOLEAN;
        }), false);
    }

    @Test
    public void testExceptionallyThrows() throws Exception {
        testHandle0(newFailedFuture(), stage -> stage.exceptionally(error -> {
            throw EXPECTED_EXCEPTION;
        }), true);
    }

    @Test
    public void testWhenComplete() throws Exception {
        testWhenComplete0(newSucceededFuture(), stage -> stage.whenComplete((v, cause) -> {
            assertSame(INITIAL_BOOLEAN, v);
            assertNull(cause);
            assertTrue(stage.executor().inEventLoop());
        }), false);
    }

    @Test
    public void testWhenCompleteAsync() throws Exception {
        testWhenComplete0(newSucceededFuture(), stage -> stage.whenCompleteAsync((v, cause) -> {
            assertSame(INITIAL_BOOLEAN, v);
            assertNull(cause);

            assertFalse(stage.executor().inEventLoop());
        }), false);
    }

    @Test
    public void testWhenCompleteAsyncWithExecutor() throws Exception {
        EventExecutor asyncExecutor = asyncExecutor();

        testWhenComplete0(newSucceededFuture(), stage -> stage.whenCompleteAsync((v, cause) -> {
            assertSame(INITIAL_BOOLEAN, v);
            assertNull(cause);

            assertFalse(stage.executor().inEventLoop());
            assertTrue(asyncExecutor.inEventLoop());
        }, asyncExecutor), false);
    }

    @Test
    public void testWhenCompleteThrowable() throws Exception {
        testWhenComplete0(newFailedFuture(), stage -> stage.whenComplete((v, cause) -> {
            assertSame(EXPECTED_EXCEPTION, cause);
            assertNull(v);

            assertTrue(stage.executor().inEventLoop());
        }), true);
    }

    @Test
    public void testWhenCompleteAsyncThrowable() throws Exception {
        testWhenComplete0(newFailedFuture(), stage -> stage.whenCompleteAsync((v, cause) -> {
            assertSame(EXPECTED_EXCEPTION, cause);
            assertNull(v);

            assertFalse(stage.executor().inEventLoop());
        }), true);
    }

    @Test
    public void testWhenCompleteAsyncWithExecutorThrowable() throws Exception {
        EventExecutor asyncExecutor = asyncExecutor();
        testWhenComplete0(newFailedFuture(), stage -> stage.whenCompleteAsync((v, cause) -> {
            assertSame(EXPECTED_EXCEPTION, cause);
            assertNull(v);

            assertFalse(stage.executor().inEventLoop());
            assertTrue(asyncExecutor.inEventLoop());
        }, asyncExecutor), true);
    }

    @Test
    public void testWhenCompleteThrows() throws Exception {
        testWhenComplete0(newSucceededFuture(), stage -> stage.whenComplete((v, cause) -> {
            throw EXPECTED_EXCEPTION;
        }), true);
    }

    @Test
    public void testWhenCompleteAsyncThrows() throws Exception {
        testWhenComplete0(newSucceededFuture(), stage -> stage.whenCompleteAsync((v, cause) -> {
            throw EXPECTED_EXCEPTION;
        }), true);
    }

    @Test
    public void testWhenCompleteAsyncWithExecutorThrows() throws Exception {
        EventExecutor asyncExecutor = asyncExecutor();
        testWhenComplete0(newSucceededFuture(), stage -> stage.whenCompleteAsync((v, cause) -> {
            throw EXPECTED_EXCEPTION;
        }, asyncExecutor), true);
    }

    @Test
    public void testHandle() throws Exception {
        testHandle0(newSucceededFuture(), stage -> stage.handle((v, cause) -> {
            assertSame(INITIAL_BOOLEAN, v);
            assertNull(cause);

            assertTrue(stage.executor().inEventLoop());

            return EXPECTED_BOOLEAN;
        }), false);
    }

    @Test
    public void testHandleAsync() throws Exception {
        testHandle0(newSucceededFuture(), stage -> stage.handleAsync((v, cause) -> {
            assertSame(INITIAL_BOOLEAN, v);
            assertNull(cause);

            assertFalse(stage.executor().inEventLoop());

            return EXPECTED_BOOLEAN;
        }), false);
    }

    @Test
    public void testHandleAsyncWithExecutor() throws Exception {
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
    public void testHandleThrowable() throws Exception {
        testHandle0(newFailedFuture(), stage -> stage.handle((v, cause) -> {
            assertSame(EXPECTED_EXCEPTION, cause);
            assertNull(v);

            assertTrue(stage.executor().inEventLoop());

            return EXPECTED_BOOLEAN;
        }), false);
    }

    @Test
    public void testHandleAsyncThrowable() throws Exception {
        testHandle0(newFailedFuture(), stage -> stage.handleAsync((v, cause) -> {
            assertSame(EXPECTED_EXCEPTION, cause);
            assertNull(v);

            assertFalse(stage.executor().inEventLoop());

            return EXPECTED_BOOLEAN;
        }), false);
    }

    @Test
    public void testHandleAsyncWithExecutorThrowable() throws Exception {
        EventExecutor asyncExecutor = asyncExecutor();
        testHandle0(newFailedFuture(), stage -> stage.handleAsync((v, cause) -> {
            assertSame(EXPECTED_EXCEPTION, cause);
            assertNull(v);

            assertFalse(stage.executor().inEventLoop());
            assertTrue(asyncExecutor.inEventLoop());

            return EXPECTED_BOOLEAN;
        }, asyncExecutor), false);
    }

    @Test
    public void testHandleFunctionThrows() throws Exception {
        testHandle0(newSucceededFuture(), stage -> stage.handle((v, cause) -> {
            throw EXPECTED_EXCEPTION;
        }), true);
    }

    @Test
    public void testHandleAsyncFunctionThrows() throws Exception {
        testHandle0(newSucceededFuture(), stage -> stage.handleAsync((v, cause) -> {
            throw EXPECTED_EXCEPTION;
        }), true);
    }

    @Test
    public void testHandleAsyncWithExecutorFunctionThrows() throws Exception {
        EventExecutor asyncExecutor = asyncExecutor();
        testHandle0(newSucceededFuture(), stage -> stage.handleAsync((v, cause) -> {
            throw EXPECTED_EXCEPTION;
        }, asyncExecutor), true);
    }

    @Test
    public void testCompleteFuture() throws Exception {
        FutureCompletionStage<Boolean> stage = FutureCompletionStage.toFutureCompletionStage(
                CompletableFuture.completedFuture(Boolean.TRUE), ImmediateEventExecutor.INSTANCE);
        assertSame(ImmediateEventExecutor.INSTANCE, stage.executor());
        assertSame(Boolean.TRUE, stage.get());
    }

    @Test
    public void testCompleteFutureFailed() throws Exception {
        IllegalStateException exception = new IllegalStateException();
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        future.completeExceptionally(exception);

        FutureCompletionStage<Boolean> stage = FutureCompletionStage.toFutureCompletionStage(
                future, ImmediateEventExecutor.INSTANCE);
        assertSame(ImmediateEventExecutor.INSTANCE, stage.executor());
        assertSame(exception, stage.getCause());
    }

    @Test
    public void testFutureCompletionStageWithSameExecutor() {
        FutureCompletionStage<Boolean> stage = ImmediateEventExecutor.INSTANCE
                .newSucceededFuture(Boolean.TRUE).asStage();
        assertSame(stage, FutureCompletionStage.toFutureCompletionStage(stage, ImmediateEventExecutor.INSTANCE));
    }

    @Test
    public void testFutureCompletionStageWithDifferentExecutor() throws Exception {
        MultithreadEventExecutorGroup group = new MultithreadEventExecutorGroup(1, Executors.defaultThreadFactory());
        try {
            FutureCompletionStage<Boolean> stage = group.next().newSucceededFuture(Boolean.TRUE).asStage();
            FutureCompletionStage<Boolean> stage2 = FutureCompletionStage.toFutureCompletionStage(
                    stage, ImmediateEventExecutor.INSTANCE);
            assertNotSame(stage, stage2);
            assertSame(stage.get(), stage2.get());
        } finally {
            group.shutdownGracefully();
        }
    }
}
