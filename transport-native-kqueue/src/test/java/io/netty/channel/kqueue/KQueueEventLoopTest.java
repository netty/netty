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
package io.netty.channel.kqueue;

import io.netty.channel.DefaultSelectStrategyFactory;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.testsuite.transport.AbstractSingleThreadEventLoopTest;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KQueueEventLoopTest extends AbstractSingleThreadEventLoopTest {

    @Override
    protected boolean supportsChannelIteration() {
        return true;
    }

    @Override
    protected EventLoopGroup newEventLoopGroup() {
        return new KQueueEventLoopGroup();
    }

    @Override
    protected EventLoopGroup newAutoScalingEventLoopGroup() {
        return new KQueueEventLoopGroup(SCALING_MAX_THREADS, (Executor) null, AUTO_SCALING_CHOOSER_FACTORY,
                                        DefaultSelectStrategyFactory.INSTANCE);
    }

    @Override
    protected ServerSocketChannel newChannel() {
        return new KQueueServerSocketChannel();
    }

    @Override
    protected Class<? extends ServerChannel> serverChannelClass() {
        return KQueueServerSocketChannel.class;
    }

    @Test
    public void testScheduleBigDelayNotOverflow() {
        EventLoopGroup group = new KQueueEventLoopGroup(1);

        final EventLoop el = group.next();
        Future<?> future = el.schedule(new Runnable() {
            @Override
            public void run() {
                // NOOP
            }
        }, Long.MAX_VALUE, TimeUnit.MILLISECONDS);

        assertFalse(future.awaitUninterruptibly(1000));
        assertTrue(future.cancel(true));
        group.shutdownGracefully();
    }
}
