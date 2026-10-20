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
package io.netty.channel.nio;

import io.netty.channel.IoEventLoop;
import io.netty.channel.IoEventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class AbstractNioChannelTest {

    // https://github.com/netty/netty/issues/17103
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testRemoveReadOpDoesNotThrowAfterDeregister() throws Exception {
        IoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        try {
            IoEventLoop loop = group.next();
            AbstractNioChannel channel = new NioSocketChannel();
            loop.register(channel).syncUninterruptibly();

            // Deregistering runs doDeregister() which sets the IoRegistration field to null. This mimics
            // a deregistration that races with clearReadPending(): the isRegistered() check there reads the
            // separate "registered" boolean and may pass, after which the scheduled clearReadPendingRunnable
            // ends up calling removeReadOp() on the EventLoop once the registration has already been cleared.
            channel.deregister().syncUninterruptibly();
            assertFalse(channel.isRegistered());

            AbstractNioChannel.AbstractNioUnsafe unsafe = (AbstractNioChannel.AbstractNioUnsafe) channel.unsafe();
            // Before the fix this threw a NullPointerException (or AssertionError with -ea) because
            // removeReadOp() dereferenced the now-null registration via registration().
            assertDoesNotThrow(unsafe::removeReadOp);
        } finally {
            group.shutdownGracefully();
        }
    }
}
