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

import io.netty.channel.AbstractSingleThreadEventLoopTest;
import io.netty.channel.DefaultSelectStrategyFactory;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.EventLoopTaskQueueFactory;
import io.netty.channel.ServerChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.util.concurrent.DefaultEventExecutorChooserFactory;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.RejectedExecutionHandlers;
import io.netty.util.concurrent.ThreadPerTaskExecutor;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KQueueEventLoopTest extends AbstractSingleThreadEventLoopTest {

    @Override
    protected KQueueEventLoopGroup newEventLoopGroup(EventLoopTaskQueueFactory taskQueueFactory,
                                                    EventLoopTaskQueueFactory tailTaskQueueFactory) {
        return new KQueueEventLoopGroup(
                0,
                new ThreadPerTaskExecutor(new DefaultThreadFactory("kqueue-test-pool")),
                DefaultEventExecutorChooserFactory.INSTANCE,
                DefaultSelectStrategyFactory.INSTANCE,
                RejectedExecutionHandlers.reject(),
                taskQueueFactory, tailTaskQueueFactory);
    }
    @Override
    protected EventLoopGroup newEventLoopGroup() {
        return new KQueueEventLoopGroup();
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
