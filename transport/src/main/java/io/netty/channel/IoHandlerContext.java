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
package io.netty.channel;

import io.netty.util.concurrent.MultithreadEventExecutorGroup;
import io.netty.util.concurrent.ThreadAwareExecutor;

/**
 * The context for an {@link IoHandler} that is run by an {@link ThreadAwareExecutor}.
 * All methods  <strong>MUST</strong> be executed on the {@link ThreadAwareExecutor} thread
 * (which means {@link ThreadAwareExecutor#isExecutorThread(Thread)} (Thread)} must return {@code true}).
 */
public interface IoHandlerContext {
    /**
     * Returns {@code true} if blocking for IO is allowed or if we should try to do a non-blocking request for IO to be
     * ready.
     *
     * @return {@code true} if allowed, {@code false} otherwise.
     */
    boolean canBlock();

    /**
     * Returns the amount of time left until the scheduled task with the closest deadline should run.
     *
     * @param currentTimeNanos  the current nanos.
     * @return                  nanos
     */
    long delayNanos(long currentTimeNanos);

    /**
     * Returns the absolute point in time at which the next
     * closest scheduled task should run or {@code -1} if nothing is scheduled to run.
     *
     * @return deadline.
     */
    long deadlineNanos();

    /**
     * Reports the amount of time in nanoseconds that was spent actively processing I/O events.
     * <p>
     * This metric is needed for the dynamic, utilization-based auto-scaling feature
     * in {@link MultithreadEventExecutorGroup}. The reported time
     * allows the auto-scaler to accurately measure the I/O workload of an event loop.
     * <p>
     * {@code IoHandler} implementations should measure the time spent in their event processing
     * logic and report the duration via this method. This should only include time spent
     * actively handling ready I/O events and should <strong>not</strong> include time spent blocking or
     * waiting for I/O (e.g., in an {@code epoll_wait}) call.
     * <p>
     * The default implementation of this method is a no-op. Failing to override it in an
     * {@link IoHandlerContext} that supports auto-scaling will result in the I/O utilization
     * being perceived as zero.
     *
     * @param activeNanos The duration in nanoseconds of active, non-blocking I/O work.
     */
    default void reportActiveIoTime(long activeNanos) {
        // no-op
    }
}
