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
package io.netty.channel;

import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

public class DefaultEventLoop extends SingleThreadEventLoop {

    public DefaultEventLoop() {
        this((EventLoopGroup) null);
    }

    public DefaultEventLoop(ThreadFactory threadFactory) {
        this(null, threadFactory);
    }

    public DefaultEventLoop(Executor executor) {
        this(null, executor);
    }

    public DefaultEventLoop(EventLoopGroup parent) {
        this(parent, new DefaultThreadFactory(DefaultEventLoop.class));
    }

    public DefaultEventLoop(EventLoopGroup parent, ThreadFactory threadFactory) {
        super(parent, threadFactory, true);
    }

    public DefaultEventLoop(EventLoopGroup parent, Executor executor) {
        super(parent, executor, true);
    }

    @Override
    protected void run() {
        for (;;) {
            Runnable task = takeTask();
            if (task != null) {
                runTask(task);
                updateLastExecutionTime();
            }

            if (confirmShutdown()) {
                break;
            }
        }
    }
}
