/*
 * Copyright 2012 The Netty Project
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

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

/**
 * Default {@link SingleThreadEventExecutor} implementation which just execute all submitted task in a
 * serial fashion.
 */
public final class DefaultEventExecutor extends SingleThreadEventExecutor {

    public DefaultEventExecutor() {
        this((EventExecutorGroup) null);
    }

    public DefaultEventExecutor(ThreadFactory threadFactory) {
        this(null, threadFactory);
    }

    public DefaultEventExecutor(Executor executor) {
        this(null, executor);
    }

    public DefaultEventExecutor(EventExecutorGroup parent) {
        this(parent, new DefaultThreadFactory(DefaultEventExecutor.class));
    }

    public DefaultEventExecutor(EventExecutorGroup parent, ThreadFactory threadFactory) {
        super(parent, threadFactory, true);
    }

    public DefaultEventExecutor(EventExecutorGroup parent, Executor executor) {
        super(parent, executor, true);
    }

    public DefaultEventExecutor(EventExecutorGroup parent, ThreadFactory threadFactory, int maxPendingTasks,
                                RejectedExecutionHandler rejectedExecutionHandler) {
        super(parent, threadFactory, true, maxPendingTasks, rejectedExecutionHandler);
    }

    public DefaultEventExecutor(EventExecutorGroup parent, Executor executor, int maxPendingTasks,
                                RejectedExecutionHandler rejectedExecutionHandler) {
        super(parent, executor, true, maxPendingTasks, rejectedExecutionHandler);
    }

    @Override
    protected void run() {
        for (;;) {
            Runnable task = takeTask();
            if (task != null) {
                task.run();
                updateLastExecutionTime();
            }

            if (confirmShutdown()) {
                break;
            }
        }
    }
}
