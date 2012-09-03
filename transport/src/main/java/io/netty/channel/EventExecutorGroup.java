/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The {@link EventExecutorGroup} is responsible to provide {@link EventExecutor}'s to use via its
 * {@link #next()} method. Beside this it also is responsible to handle their live-cycle and allows
 * to shut them down in a global fashion.
 *
 */
public interface EventExecutorGroup {

    /**
     * Returns one of the {@link EventExecutor}s that belong to this group.
     */
    EventExecutor next();

    /**
     * Shuts down all {@link EventExecutor}s managed by this group.
     *
     * @see ExecutorService#shutdown()
     */
    void shutdown();

    /**
     * Returns {@code true} if and only if {@link #shutdown()} has been called.
     *
     * @see ExecutorService#isShutdown()
     */
    boolean isShutdown();

    /**
     * Returns {@code true} if and only if {@link #shutdown()} has been called and all
     * {@link EventExecutor}s managed by this group has been terminated completely.
     *
     * @see ExecutorService#isTerminated()
     */
    boolean isTerminated();

    /**
     * Waits until {@link #isTerminated()} returns {@code true} or the specified amount of time
     * passes.
     *
     * @see ExecutorService#awaitTermination(long, TimeUnit)
     */
    boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException;
}
