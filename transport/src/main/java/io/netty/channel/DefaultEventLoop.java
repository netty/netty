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

import io.netty.util.concurrent.DefaultExecutorFactory;

import java.util.concurrent.Executor;
public class DefaultEventLoop extends SingleThreadEventLoop {

    public DefaultEventLoop() {
        this((EventLoopGroup) null);
    }

    public DefaultEventLoop(Executor executor) {
        this(null, executor);
    }

    public DefaultEventLoop(EventLoopGroup parent) {
        this(parent, new DefaultExecutorFactory(DefaultEventLoop.class).newExecutor(1));
    }

    public DefaultEventLoop(EventLoopGroup parent, Executor executor) {
        super(parent, executor, true);
    }

    @Override
    protected void run() {
        Runnable task = takeTask();
        if (task != null) {
            task.run();
            updateLastExecutionTime();
        }

        if (confirmShutdown()) {
            cleanupAndTerminate(true);
        } else {
            executeRun();
        }
    }
}
