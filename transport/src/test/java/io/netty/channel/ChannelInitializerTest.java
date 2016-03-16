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
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class ChannelInitializerTest {
    private static final int TIMEOUT_MILLIS = 1000;
    private static final LocalAddress SERVER_ADDRESS = new LocalAddress("addr");
    private EventLoopGroup group;
    private ServerBootstrap server;
    private Bootstrap client;
    private InspectableHandler testHandler;

    @Before
    public void setUp() {
        group = new DefaultEventLoopGroup(1);
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
