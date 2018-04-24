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
package io.netty.channel.nio;

import io.netty.channel.AbstractEventLoopTest;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.Future;
import org.junit.Test;

import java.nio.channels.Selector;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class NioEventLoopTest extends AbstractEventLoopTest {

    @Override
    protected EventLoopGroup newEventLoopGroup() {
        return new NioEventLoopGroup();
    }

    @Override
    protected Class<? extends ServerSocketChannel> newChannel() {
        return NioServerSocketChannel.class;
    }

    @Test
    public void testRebuildSelector() throws Exception {
        EventLoopGroup group = new NioEventLoopGroup(1);
        final NioEventLoop loop = (NioEventLoop) group.next();
        try {
            Channel channel = new NioServerSocketChannel();
            loop.register(channel).syncUninterruptibly();

            Selector selector = loop.unwrappedSelector();
            assertSame(selector, ((NioEventLoop) channel.eventLoop()).unwrappedSelector());
            assertTrue(selector.isOpen());

            // Submit to the EventLoop so we are sure its really executed in a non-async manner.
            loop.submit(new Runnable() {
                @Override
                public void run() {
                    loop.rebuildSelector();
                }
            }).syncUninterruptibly();

            Selector newSelector = ((NioEventLoop) channel.eventLoop()).unwrappedSelector();
            assertTrue(newSelector.isOpen());
            assertNotSame(selector, newSelector);
            assertFalse(selector.isOpen());

            channel.close().syncUninterruptibly();
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test(timeout = 5000L)
    public void testScheduleBigDelayOverMax() {
        EventLoopGroup group = new NioEventLoopGroup(1);
        final EventLoop el = group.next();
        try {
            el.schedule(new Runnable() {
                @Override
                public void run() {
                    // NOOP
                }
            }, Integer.MAX_VALUE, TimeUnit.DAYS);
            fail();
        } catch (IllegalArgumentException expected) {
            // expected
        }

        group.shutdownGracefully();
    }

    @Test
    public void testScheduleBigDelay() {
        EventLoopGroup group = new NioEventLoopGroup(1);

        final EventLoop el = group.next();
        Future<?> future = el.schedule(new Runnable() {
            @Override
            public void run() {
                // NOOP
            }
        }, NioEventLoop.MAX_SCHEDULED_DAYS, TimeUnit.DAYS);

        assertFalse(future.awaitUninterruptibly(1000));
        assertTrue(future.cancel(true));
        group.shutdownGracefully();
    }
}
