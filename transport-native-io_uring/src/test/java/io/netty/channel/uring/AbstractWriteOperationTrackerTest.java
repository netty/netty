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

import io.netty.channel.ChannelShutdownType;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// Shared by the tests that exercise WriteOperationTracker bookkeeping directly, both of which need a
// registered channel's real event loop to run their assertions on.
abstract class AbstractWriteOperationTrackerTest {

    @BeforeAll
    static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    interface BookkeepingTask {
        void run(IoUringSocketChannel channel);
    }

    // The bookkeeping is event-loop state and the channel can only be closed once registered, so the task
    // runs on a real event loop.
    static void runOnEventLoop(final BookkeepingTask task) throws Exception {
        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory());
        final IoUringSocketChannel channel = new IoUringSocketChannel(group.next());
        try {
            channel.register().sync();
            channel.executor().submit(new Runnable() {
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

    // Calls doShutdown(...) directly instead of going through Channel.shutdown(...), which would bail out on
    // isActive() before ever reaching it, since this channel is never connected. Going through doShutdown(...)
    // directly is what this exercises: the retain has to happen before the shutdown(2) syscall runs, not after.
    // The channel is not connected, so that syscall fails with ENOTCONN, which IoUringSocketChannel.doShutdown0(...)
    // treats as an already-effectively-shutdown socket and completes the promise successfully instead of
    // propagating the failure.
    static void shutdownOutput(IoUringSocketChannel channel) {
        Promise<Void> promise = channel.executor().newPromise();
        channel.doShutdown(ChannelShutdownType.newOutbound(), promise);
        assertTrue(promise.isSuccess(), "doShutdown should complete the promise successfully");
    }
}
