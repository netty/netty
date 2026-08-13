/*
 * Copyright 2026 The Netty Project
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
package io.netty.channel.uring;

import io.netty.channel.MultiThreadIoEventLoopGroup;

// Shared by the tests that exercise WriteOperationTracker bookkeeping directly, both of which need a
// registered channel's real event loop to run their assertions on.
abstract class AbstractWriteOperationTrackerTest {

    interface BookkeepingTask {
        void run(IoUringSocketChannel channel);
    }

    // The bookkeeping is event-loop state and the channel can only be closed once registered, so the task
    // runs on a real event loop.
    static void runOnEventLoop(final BookkeepingTask task) throws Exception {
        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory());
        final IoUringSocketChannel channel = new IoUringSocketChannel();
        try {
            group.register(channel).sync();
            channel.eventLoop().submit(new Runnable() {
                @Override
                public void run() {
                    task.run(channel);
                }
            }).sync();
        } finally {
            channel.close().syncUninterruptibly();
            group.shutdownGracefully().syncUninterruptibly();
        }
    }
}
