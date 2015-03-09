/*
 * Copyright 2014 The Netty Project
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

import java.util.concurrent.RejectedExecutionException;

/**
 * Implement this interface if you need your {@link EventExecutor} implementation to be able
 * to reject new work.
 */
public interface PausableEventExecutor extends EventExecutor, WrappedEventExecutor {

    /**
     * After a call to this method the {@link EventExecutor} may throw a {@link RejectedExecutionException} when
     * attempting to assign new work to it (i.e. through a call to {@link EventExecutor#execute(Runnable)}).
     */
    void rejectNewTasks();

    /**
     * With a call to this method the {@link EventExecutor} signals that it is now accepting new work.
     */
    void acceptNewTasks();

    /**
     * Returns {@code true} if and only if this {@link EventExecutor} is accepting a new task.
     */
    boolean isAcceptingNewTasks();
}
