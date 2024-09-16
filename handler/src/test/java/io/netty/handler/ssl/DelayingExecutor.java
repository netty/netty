/*
 * Copyright 2024 The Netty Project
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
package io.netty.handler.ssl;

import io.netty.util.internal.PlatformDependent;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

final class DelayingExecutor implements Executor {
    private static final int CORE_POOL_SIZE = 10;
    private final ScheduledExecutorService service;

    DelayingExecutor() {
        this.service = Executors.newScheduledThreadPool(CORE_POOL_SIZE);
    }

    DelayingExecutor(ThreadFactory threadFactory) {
        this.service = Executors.newScheduledThreadPool(CORE_POOL_SIZE, threadFactory);
    }

    @Override
    public void execute(Runnable command) {
        // Let's add some jitter in terms of when the task is actual run
        service.schedule(command,
                PlatformDependent.threadLocalRandom().nextInt(100), TimeUnit.MILLISECONDS);
    }

    void shutdown() {
        service.shutdown();
    }
}
