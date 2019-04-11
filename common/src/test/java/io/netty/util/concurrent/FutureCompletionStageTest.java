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

import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class FutureCompletionStageTest {

    @Test
    public void testCompleteFuture() {
        FutureCompletionStage stage = FutureCompletionStage.toFutureCompletionStage(
                CompletableFuture.completedFuture(Boolean.TRUE), ImmediateEventExecutor.INSTANCE);
        assertSame(ImmediateEventExecutor.INSTANCE, stage.executor());
        assertSame(Boolean.TRUE, stage.future().syncUninterruptibly().getNow());
    }

    @Test
    public void testCompleteFutureFailed() {
        IllegalStateException exception = new IllegalStateException();
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        future.completeExceptionally(exception);

        FutureCompletionStage stage = FutureCompletionStage.toFutureCompletionStage(
                future, ImmediateEventExecutor.INSTANCE);
        assertSame(ImmediateEventExecutor.INSTANCE, stage.executor());
        assertSame(exception, stage.future().awaitUninterruptibly().cause());
    }

    @Test
    public void testFutureCompletionStageWithSameExecutor() {
        FutureCompletionStage<Boolean> stage = ImmediateEventExecutor.INSTANCE
                .newSucceededFuture(Boolean.TRUE).asStage();
        assertSame(stage, FutureCompletionStage.toFutureCompletionStage(stage, ImmediateEventExecutor.INSTANCE));
    }

    @Test
    public void testFutureCompletionStageWithDifferentExecutor() {
        MultithreadEventExecutorGroup group = new MultithreadEventExecutorGroup(1, Executors.defaultThreadFactory());
        try {
            FutureCompletionStage<Boolean> stage = group.next().newSucceededFuture(Boolean.TRUE).asStage();
            FutureCompletionStage<Boolean> stage2 = FutureCompletionStage.toFutureCompletionStage(
                    stage, ImmediateEventExecutor.INSTANCE);
            assertNotSame(stage, stage2);
            assertSame(stage.future().syncUninterruptibly().getNow(), stage2.future().syncUninterruptibly().getNow());
        } finally {
            group.shutdownGracefully();
        }
    }
}
