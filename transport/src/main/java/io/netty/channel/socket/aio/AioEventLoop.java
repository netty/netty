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
package io.netty.channel.socket.aio;

import io.netty.channel.SingleThreadEventLoop;
import io.netty.channel.TaskScheduler;

import java.util.concurrent.ThreadFactory;

final class AioEventLoop extends SingleThreadEventLoop {

    AioEventLoop(AioEventLoopGroup parent, ThreadFactory threadFactory, TaskScheduler scheduler) {
        super(parent, threadFactory, scheduler);
    }

    @Override
    protected void run() {
        for (;;) {
            Runnable task;
            try {
                task = takeTask();
                task.run();
            } catch (InterruptedException e) {
                // Waken up by interruptThread()
            }

            if (isShutdown()) {
                task = pollTask();
                if (task == null) {
                    break;
                }
                task.run();
            }
        }
    }

    @Override
    protected void wakeup(boolean inEventLoop) {
        if (!inEventLoop && isShutdown()) {
            interruptThread();
        }
    }
}
