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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.AfterClass;
import org.junit.Test;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.ReferenceCountUtil;

public class ChannelCloseTest {
    private static final EventLoopGroup GROUP = new NioEventLoopGroup();

    @AfterClass
    public static void destroy() {
        GROUP.shutdownGracefully();
    }

    private static ByteBuf newMessage() {
        return Unpooled.wrappedBuffer(new byte[]{ (byte) '1', (byte) '2', (byte) '3' });
    }

    private Channel newServer(boolean autoRead, final ChannelHandler... handlers) {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(GROUP)
            .channel(NioServerSocketChannel.class)
            .option(ChannelOption.SO_REUSEADDR, true)
            .option(ChannelOption.SO_TIMEOUT, 1000)
            .childOption(ChannelOption.AUTO_READ, autoRead)
            .childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ChannelPipeline pipeline = ch.pipeline();
                    pipeline.addLast(handlers);
                }
            });

        return serverBootstrap.bind(0)
                .syncUninterruptibly()
                .channel();
    }

    private Channel newClient(Channel server) {
        Bootstrap bootstrap = new Bootstrap();

        bootstrap.group(GROUP)
            .channel(NioSocketChannel.class)
            .option(ChannelOption.SO_REUSEADDR, true)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
            .handler(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    ReferenceCountUtil.release(msg);
                    throw new IllegalStateException();
                }
            });

        return bootstrap.connect(server.localAddress())
                .syncUninterruptibly()
                .channel();
    }

    /**
     * Scenario 0
     *
     * Same as scenario 1 and 2 but we do nothing to disable auto reading
     */
    @Test
    public void testDisconnectDoNothingWithAutoRead() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        DisconnectHandler disconnect = new DisconnectHandler();

        ChannelInboundHandlerAdapter handler = new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                ReferenceCountUtil.release(msg);
                latch.countDown();
            }
        };

        Channel server = newServer(true, handler, disconnect);
        Channel client = newClient(server);

        try {
            // The clients sends the message
            client.writeAndFlush(newMessage())
                .syncUninterruptibly();

            // The server receives the message
            assertTrue(latch.await(1L, TimeUnit.SECONDS));

            // The client disconnects
            client.close().syncUninterruptibly();

            // The server should get notified
            if (!disconnect.await(1L, TimeUnit.SECONDS)) {
                fail("The server didn't receive a disconnect event.");
            }
        } finally {
            client.close();
            server.close();
        }
    }

    /**
     * Scenario 1
     *
     * 1. The server starts with auto reading turned on
     * 2. The client connects
     * 3. The client sends a message
     * 4. The server receives the message
     * 5. The server turns off auto reading
     * 6. The client disconnects
     * 7. The server (should) receive an channelInactive() event
     */
    @Test
    public void testDisconnectToggleAutoRead() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        DisconnectHandler disconnect = new DisconnectHandler();

        ChannelInboundHandlerAdapter handler = new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                ReferenceCountUtil.release(msg);

                // We receive a message from the client and turn auto reading off.
                // The client closes the connection in the meantime and there should
                // be a channelInactive() event but there isn't.

                latch.countDown();
                ctx.channel().config().setAutoRead(false);
            }
        };

        Channel server = newServer(true, handler, disconnect);
        Channel client = newClient(server);

        try {
            // The clients sends the message
            client.writeAndFlush(newMessage())
                .syncUninterruptibly();

            // The server receives the message
            assertTrue(latch.await(1L, TimeUnit.SECONDS));

            // The client disconnects
            client.close().syncUninterruptibly();

            // The server should get notified
            if (!disconnect.await(1L, TimeUnit.SECONDS)) {
                fail("The server didn't receive a disconnect event.");
            }
        } finally {
            client.close();
            server.close();
        }
    }

    /**
     * Scenario 2
     *
     * 1. The server starts with auto reading turned off
     * 2. The client connects
     * 3. The client sends a message
     * 4. We call read() and the server receives the message
     * 5. The client disconnects
     * 6. The server (should) receive an channelInactive() event
     */
    @Test
    public void testDisconnectAutoReadOff() throws Exception {

        final Exchanger<Channel> clientRef = new Exchanger<Channel>();

        final CountDownLatch latch = new CountDownLatch(1);
        DisconnectHandler disconnect = new DisconnectHandler();

        ChannelInboundHandlerAdapter handler = new ChannelInboundHandlerAdapter() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                clientRef.exchange(ctx.channel());
                ctx.fireChannelActive();
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                ReferenceCountUtil.release(msg);
                latch.countDown();
            }
        };

        Channel server = newServer(false, handler, disconnect);
        Channel client = newClient(server);

        try {
            // The clients sends the message
            client.writeAndFlush(newMessage())
                .syncUninterruptibly();

            // The server receives the message
            Channel serverSide = clientRef.exchange(null, 1L, TimeUnit.SECONDS);
            serverSide.read();
            assertTrue(latch.await(1L, TimeUnit.SECONDS));

            // The client disconnects
            client.close().syncUninterruptibly();

            // The server should get notified
            if (!disconnect.await(1L, TimeUnit.SECONDS)) {
                fail("The server didn't receive a disconnect event.");
            }
        } finally {
            client.close();
            server.close();
        }
    }

    private static class DisconnectHandler extends ChannelInboundHandlerAdapter {
        private final CountDownLatch latch = new CountDownLatch(1);

        public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            latch.countDown();
            ctx.fireChannelInactive();
        }
    }
}
