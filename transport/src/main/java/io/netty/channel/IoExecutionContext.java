/*
 * Copyright 2018 The Netty Project
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

/**
 * The execution context for an {@link IoHandler}.
 * All method must be called from the {@link EventLoop} thread.
 */
public interface IoExecutionContext {
    /**
     * Returns {@code true} if blocking for IO is allowed or if we should try to do a non-blocking request for IO to be
     * ready.
     */
    boolean canBlock();

    /**
     * Returns the amount of time left until the scheduled task with the closest dead line should run..
     */
    long delayNanos(long currentTimeNanos);

    /**
     * Returns the absolute point in time (relative to {@link SingleThreadEventLoop#nanoTime()}) at which the the next
     * closest scheduled task should run.
     */
    long deadlineNanos();
}
