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

import io.netty.channel.Channel;
import io.netty.util.ReferenceCounted;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// Shared by the write-lifecycle tests, which all submit outbound memory that a terminal CQE releases
// asynchronously and then need to wait for that release to confirm nothing leaked.
final class IoUringRefCntZeroAwaiter {

    private IoUringRefCntZeroAwaiter() {
    }

    static boolean awaitRefCntZero(Channel channel, ReferenceCounted referenceCounted, long timeout, TimeUnit unit)
            throws InterruptedException {
        CountDownLatch released = new CountDownLatch(1);
        channel.executor().execute(new Runnable() {
            @Override
            public void run() {
                if (referenceCounted.refCnt() == 0) {
                    released.countDown();
                } else {
                    channel.executor().schedule(this, 10, TimeUnit.MILLISECONDS);
                }
            }
        });
        return released.await(timeout, unit);
    }
}
