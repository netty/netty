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
package io.netty.util.internal;

import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.ImmediateExecutor;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static org.junit.jupiter.api.Assertions.assertSame;

public class ThreadExecutorMapTest {

    @Test
    public void testDecorateExecutor() {
        Executor executor = ThreadExecutorMap.apply(ImmediateExecutor.INSTANCE, ImmediateEventExecutor.INSTANCE);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                assertSame(ImmediateEventExecutor.INSTANCE, ThreadExecutorMap.currentExecutor());
            }
        });
    }

    @Test
    public void testDecorateRunnable() {
        ThreadExecutorMap.apply(new Runnable() {
            @Override
            public void run() {
                assertSame(ImmediateEventExecutor.INSTANCE,
                        ThreadExecutorMap.currentExecutor());
            }
        }, ImmediateEventExecutor.INSTANCE).run();
    }

    @Test
    public void testDecorateThreadFactory() throws InterruptedException {
        ThreadFactory threadFactory =
                ThreadExecutorMap.apply(Executors.defaultThreadFactory(), ImmediateEventExecutor.INSTANCE);
        Thread thread = threadFactory.newThread(new Runnable() {
            @Override
            public void run() {
                assertSame(ImmediateEventExecutor.INSTANCE, ThreadExecutorMap.currentExecutor());
            }
        });
        thread.start();
        thread.join();
    }
}
