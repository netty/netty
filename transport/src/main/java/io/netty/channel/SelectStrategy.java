/*
 * Copyright 2016 The Netty Project
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

import io.netty.util.IntSupplier;

/**
 * Select strategy interface.
 * 选择策略界面。
 *
 * Provides the ability to control the behavior of the select loop. For example a blocking select
 * operation can be delayed or skipped entirely if there are events to process immediately.
 * 提供控制选择循环行为的能力。例如，如果有要立即处理的事件，则可以延迟或完全跳过阻塞选择操作。
 */
public interface SelectStrategy {

    /**
     * Indicates a blocking select should follow.
     * 指示应遵循阻止选择。
     */
    int SELECT = -1;
    /**
     * Indicates the IO loop should be retried, no blocking select to follow directly.
     * 指示应重试IO循环，无阻塞选择直接跟随。
     */
    int CONTINUE = -2;
    /**
     * Indicates the IO loop to poll for new events without blocking.
     * 指示在不阻塞的情况下轮询新事件的IO循环。
     */
    int BUSY_WAIT = -3;

    /**
     * The {@link SelectStrategy} can be used to steer the outcome of a potential select
     * call.
     * {@link SelectStrategy} 可用于引导潜在select调用的结果。
     *
     * @param selectSupplier The supplier with the result of a select result.  具有选择结果的供应商。
     * @param hasTasks true if tasks are waiting to be processed.              如果任务正在等待处理，则为true。
     * @return {@link #SELECT} if the next step should be blocking select {@link #CONTINUE} if
     *         the next step should be to not select but rather jump back to the IO loop and try
     *         again. Any value >= 0 is treated as an indicator that work needs to be done.
     *         {@link #SELECT} 如果下一步应该阻止，请选择 {@link #CONTINUE} 如果下一步应该不是选择，而是跳回IO循环，然后重试。任何> = 0的值都被视为需要完成工作的指标。
     */
    int calculateStrategy(IntSupplier selectSupplier, boolean hasTasks) throws Exception;
}
