/*
 * Copyright 2017 The Netty Project
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
package io.netty5.channel.kqueue;

import io.netty5.channel.EventLoop;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.IoHandlerFactory;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.ServerChannel;
import io.netty5.testsuite.transport.AbstractSingleThreadEventLoopTest;
import io.netty5.util.concurrent.Future;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KQueueEventLoopTest extends AbstractSingleThreadEventLoopTest {

    @Test
    public void testScheduleBigDelayNotOverflow() {
        EventLoopGroup group = new MultithreadEventLoopGroup(1, newIoHandlerFactory());

        final EventLoop el = group.next();
        Future<?> future = el.schedule(() -> {
            // NOOP
        }, Long.MAX_VALUE, TimeUnit.MILLISECONDS);

        assertFalse(future.awaitUninterruptibly(1000));
        assertTrue(future.cancel());
        group.shutdownGracefully();
    }

    @Override
    protected IoHandlerFactory newIoHandlerFactory() {
        return KQueueHandler.newFactory();
    }

    @Override
    protected Class<? extends ServerChannel> serverChannelClass() {
        return KQueueServerSocketChannel.class;
    }
}
