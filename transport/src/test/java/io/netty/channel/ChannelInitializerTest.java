/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalEventLoopGroup;
import io.netty.channel.local.LocalServerChannel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

public class ChannelInitializerTest {
    private static final int TIMEOUT_MILLIS = 1000;
    private static final LocalAddress SERVER_ADDRESS = new LocalAddress("addr");
    private EventLoopGroup group;
    private ServerBootstrap server;
    private Bootstrap client;
    private InspectableHandler testHandler;

    @Before
    public void setUp() {
        group = new LocalEventLoopGroup(1);
        server = new ServerBootstrap()
                .group(group)
                .channel(LocalServerChannel.class)
                .localAddress(SERVER_ADDRESS);
        client = new Bootstrap()
                .group(group)
                .channel(LocalChannel.class)
                .handler(new ChannelInboundHandlerAdapter());
        testHandler = new InspectableHandler();
    }

    @After
    public void tearDown() {
        group.shutdownGracefully(0, TIMEOUT_MILLIS, TimeUnit.MILLISECONDS).syncUninterruptibly();
    }

    @Test
    public void testChannelInitializerInInitializerCorrectOrdering() {
        final ChannelInboundHandlerAdapter handler1 = new ChannelInboundHandlerAdapter();
        final ChannelInboundHandlerAdapter handler2 = new ChannelInboundHandlerAdapter();
        final ChannelInboundHandlerAdapter handler3 = new ChannelInboundHandlerAdapter();
        final ChannelInboundHandlerAdapter handler4 = new ChannelInboundHandlerAdapter();

        client.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(handler1);
                ch.pipeline().addLast(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline().addLast(handler2);
                        ch.pipeline().addLast(handler3);
                    }
                });
                ch.pipeline().addLast(handler4);
            }
        }).localAddress(LocalAddress.ANY);

        Channel channel = client.bind().syncUninterruptibly().channel();
        try {
            // Execute some task on the EventLoop and wait until its done to be sure all handlers are added to the
            // pipeline.
            channel.eventLoop().submit(new Runnable() {
                @Override
                public void run() {
                    // NOOP
                }
            }).syncUninterruptibly();
            Iterator<Map.Entry<String, ChannelHandler>> handlers = channel.pipeline().iterator();
            assertSame(handler1, handlers.next().getValue());
            assertSame(handler2, handlers.next().getValue());
            assertSame(handler3, handlers.next().getValue());
            assertSame(handler4, handlers.next().getValue());
            assertFalse(handlers.hasNext());
        } finally {
            channel.close().syncUninterruptibly();
        }
    }

    @Test
    public void testChannelInitializerReentrance() {
        final AtomicInteger registeredCalled = new AtomicInteger(0);
        final ChannelInboundHandlerAdapter handler1 = new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
                registeredCalled.incrementAndGet();
            }
        };
        final AtomicInteger initChannelCalled = new AtomicInteger(0);
        client.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                initChannelCalled.incrementAndGet();
                ch.pipeline().addLast(handler1);
                ch.pipeline().fireChannelRegistered();
            }
        }).localAddress(LocalAddress.ANY);

        Channel channel = client.bind().syncUninterruptibly().channel();
        try {
            // Execute some task on the EventLoop and wait until its done to be sure all handlers are added to the
            // pipeline.
            channel.eventLoop().submit(new Runnable() {
                @Override
                public void run() {
                    // NOOP
                }
            }).syncUninterruptibly();
            assertEquals(1, initChannelCalled.get());
            assertEquals(2, registeredCalled.get());
        } finally {
            channel.close().syncUninterruptibly();
        }
    }

    @Test(timeout = TIMEOUT_MILLIS)
    public void firstHandlerInPipelineShouldReceiveChannelRegisteredEvent() {
        testChannelRegisteredEventPropagation(new ChannelInitializer<LocalChannel>() {
            @Override
            public void initChannel(LocalChannel channel) {
                channel.pipeline().addFirst(testHandler);
            }
        });
    }

    @Test(timeout = TIMEOUT_MILLIS)
    public void lastHandlerInPipelineShouldReceiveChannelRegisteredEvent() {
        testChannelRegisteredEventPropagation(new ChannelInitializer<LocalChannel>() {
            @Override
            public void initChannel(LocalChannel channel) {
                channel.pipeline().addLast(testHandler);
            }
        });
    }

    @Test
    public void testAddFirstChannelInitializer() {
        testAddChannelInitializer(true);
    }

    @Test
    public void testAddLastChannelInitializer() {
        testAddChannelInitializer(false);
    }

    private static void testAddChannelInitializer(final boolean first) {
        final AtomicBoolean called = new AtomicBoolean();
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelHandler handler = new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        called.set(true);
                    }
                };
                if (first) {
                    ch.pipeline().addFirst(handler);
                } else {
                    ch.pipeline().addLast(handler);
                }
            }
        });
        channel.finish();
        assertTrue(called.get());
    }

    private void testChannelRegisteredEventPropagation(ChannelInitializer<LocalChannel> init) {
        Channel clientChannel = null, serverChannel = null;
        try {
            server.childHandler(init);
            serverChannel = server.bind().syncUninterruptibly().channel();
            clientChannel = client.connect(SERVER_ADDRESS).syncUninterruptibly().channel();
            assertEquals(1, testHandler.channelRegisteredCount.get());
        } finally {
            closeChannel(clientChannel);
            closeChannel(serverChannel);
        }
    }

    private static void closeChannel(Channel c) {
        if (c != null) {
            c.close().syncUninterruptibly();
        }
    }

    private static final class InspectableHandler extends ChannelDuplexHandler {
        final AtomicInteger channelRegisteredCount = new AtomicInteger(0);

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) {
            channelRegisteredCount.incrementAndGet();
            ctx.fireChannelRegistered();
        }
    }
}
