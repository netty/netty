/*
 * Copyright 2015 The Netty Project
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

/**
 * A handler that is notified when a task is rejected by an {@link EventExecutor}.
 */
public interface RejectedTaskHandler {
    /**
     * Invoked when a task has been rejected for execution.
     *
     * @param executor the {@link EventExecutor} which was asked for execution
     * @param task the rejected task
     * @param cause the cause of rejection
     */
    void taskRejected(EventExecutor executor, Runnable task, Throwable cause);
}
