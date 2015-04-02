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

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * The default {@link RejectedTaskHandler} that just logs the rejection of a rejected task.
 */
public final class DefaultRejectedTaskHandler implements RejectedTaskHandler {

    /**
     * The singleton.
     */
    public static final RejectedTaskHandler INSTANCE = new DefaultRejectedTaskHandler();

    private static final InternalLogger rejectedExecutionLogger =
            InternalLoggerFactory.getInstance(DefaultRejectedTaskHandler.class);

    private DefaultRejectedTaskHandler() { }

    @Override
    public void taskRejected(EventExecutor executor, Runnable task, Throwable cause) {
        rejectedExecutionLogger.error(
                "Failed to submit a task ({}) to an executor ({}). Event loop shut down?", task, executor, cause);
    }
}
