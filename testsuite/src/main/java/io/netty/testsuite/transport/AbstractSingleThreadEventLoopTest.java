/*
 * Copyright 2019 The Netty Project
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
package io.netty.testsuite.transport;

import org.junit.Test;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.channel.socket.ServerSocketChannel;

import static org.junit.Assert.*;

public abstract class AbstractSingleThreadEventLoopTest {

    @Test
    public void testChannelsRegistered()  {
        EventLoopGroup group = newEventLoopGroup();
        final SingleThreadEventLoop loop = (SingleThreadEventLoop) group.next();

        try {
            final Channel ch1 = newChannel();
            final Channel ch2 = newChannel();

            assertEquals(0, loop.registeredChannels());

            assertTrue(loop.register(ch1).syncUninterruptibly().isSuccess());
            assertTrue(loop.register(ch2).syncUninterruptibly().isSuccess());
            assertEquals(2, loop.registeredChannels());

            assertTrue(ch1.deregister().syncUninterruptibly().isSuccess());
            assertEquals(1, loop.registeredChannels());
        } finally {
            group.shutdownGracefully();
        }
    }

    protected abstract EventLoopGroup newEventLoopGroup();
    protected abstract ServerSocketChannel newChannel();
}
