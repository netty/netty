/*
 * Copyright 2018 The Netty Project
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
package io.netty.handler.timeout;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ReadAfterWriteTimeoutHandlerTest {
    private static EventLoopGroup GROUP;

    /**
     * We should get {@link io.netty.handler.timeout.ReadTimeoutException} after specified timeout value if server
     * response with larger latency than the specified timeout.
     */
    @Test
    public void testTrivialTimeoutCase() throws Exception {
        final int TIMEOUT_MS = 300;
        final CountDownLatch timeoutGotLatch = new CountDownLatch(1);
        final CountDownLatch clientClosedChannelLatch = new CountDownLatch(1);
        final AtomicBoolean unexpectedExceptionInServerHandler = new AtomicBoolean();
        final AtomicBoolean unexpectedExceptionInClientHandler = new AtomicBoolean();

        ReadAfterWriteTimeoutHandler readAfterWriteTimeoutHandler =
                new ReadAfterWriteTimeoutHandler(TIMEOUT_MS, TimeUnit.MILLISECONDS);

        ChannelInboundHandler pongHandler = new ChannelInboundHandlerAdapter() {
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                Thread.sleep(2 * TIMEOUT_MS);
                ctx.writeAndFlush(newOneMessage());
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                if (cause instanceof IOException && "Connection reset by peer".equals(cause.getMessage())) {
                    //it's client closed connection already when we tried to send a message back - ignore
                } else {
                    unexpectedExceptionInServerHandler.set(true);
                }
            }

            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                clientClosedChannelLatch.countDown();
            }
        };

        ChannelInboundHandler trackingHandler = new ChannelInboundHandlerAdapter() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                if (cause instanceof ReadTimeoutException) {
                    timeoutGotLatch.countDown();
                } else {
                    unexpectedExceptionInClientHandler.set(true);
                }
            }
        };

        Channel server = newServer(pongHandler);
        Channel client = newClient(server.localAddress(), readAfterWriteTimeoutHandler, trackingHandler);
        try {
            client.writeAndFlush(newOneMessage());
            assertTrue("No ReadTimeoutException produced by ReadAfterWriteTimeoutHandler",
                       timeoutGotLatch.await(5 * TIMEOUT_MS, TimeUnit.MILLISECONDS));
            assertTrue("ReadAfterWriteTimeoutHandler should close channel by default",
                       clientClosedChannelLatch.await(3 * TIMEOUT_MS, TimeUnit.MILLISECONDS));
            assertFalse("Unexpected exception in server handler", unexpectedExceptionInServerHandler.get());
            assertFalse("Unexpected exception in client handler", unexpectedExceptionInClientHandler.get());
        } finally {
            client.close();
            server.close();
        }
    }

    @Test
    public void testOnlyOneTimeoutProducedForMultipleConsecutiveWrites() throws Exception {
        final int TIMEOUT_MS = 300;
        final int MESSAGES_COUNT = 2;

        final AtomicInteger serverReads = new AtomicInteger();
        final CountDownLatch timeoutGotLatch = new CountDownLatch(MESSAGES_COUNT);
        final AtomicBoolean unexpectedExceptionInServerHandler = new AtomicBoolean();
        final AtomicBoolean unexpectedExceptionInClientHandler = new AtomicBoolean();

        ReadAfterWriteTimeoutHandler readAfterWriteTimeoutHandler =
                new ReadAfterWriteTimeoutHandler(TIMEOUT_MS, TimeUnit.MILLISECONDS);

        ChannelInboundHandler pongAfterTwoMessagesHandler = new ChannelInboundHandlerAdapter() {
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                serverReads.incrementAndGet();
                //response once only after MESSAGES_COUNT messages and sleeping
                if (serverReads.get() == MESSAGES_COUNT) {
                    Thread.sleep(2 * TIMEOUT_MS);
                    ctx.writeAndFlush(newOneMessage());
                }
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                if (cause instanceof IOException && "Connection reset by peer".equals(cause.getMessage())) {
                    //it's client closed connection already when we tried to send a message back - ignore
                } else {
                    fail();
                }
            }
        };

        ChannelInboundHandler trackingHandler = new ChannelInboundHandlerAdapter() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                if (cause instanceof ReadTimeoutException) {
                    timeoutGotLatch.countDown();
                } else {
                    unexpectedExceptionInClientHandler.set(true);
                }
            }
        };

        Channel server = newServer(pongAfterTwoMessagesHandler);
        Channel client = newClient(server.localAddress(), readAfterWriteTimeoutHandler, trackingHandler);
        try {
            client.writeAndFlush(newOneMessage());
            Thread.sleep(TIMEOUT_MS / 10);
            client.writeAndFlush(newOneMessage());
            assertFalse("We should get only one timeout on the client side, while this latch initiated with value > 1",
                        timeoutGotLatch.await(2 * TIMEOUT_MS, TimeUnit.MILLISECONDS));
            assertEquals("Server should get 2 messages already", MESSAGES_COUNT, serverReads.get());
            assertEquals("We should get only one timeout on the client side (latch value decreased by 1)",
                         MESSAGES_COUNT - 1, timeoutGotLatch.getCount());
            assertFalse("Unexpected exception in server handler", unexpectedExceptionInServerHandler.get());
            assertFalse("Unexpected exception in client handler", unexpectedExceptionInClientHandler.get());
        } finally {
            client.close();
            server.close();
        }
    }

    @Test
    public void testNoTimeoutInCaseOfResponseInTime() throws Exception {
        final int TIMEOUT_MS = 300;
        final int MESSAGES_COUNT = 1;

        final AtomicBoolean clientReads = new AtomicBoolean();
        final AtomicBoolean unexpectedExceptionInServerHandler = new AtomicBoolean();
        final CountDownLatch unexpectedExceptionInClientHandlerLatch = new CountDownLatch(MESSAGES_COUNT);

        ReadAfterWriteTimeoutHandler readAfterWriteTimeoutHandler =
                new ReadAfterWriteTimeoutHandler(TIMEOUT_MS, TimeUnit.MILLISECONDS);

        ChannelInboundHandler pongAfterTwoMessagesHandler = new ChannelInboundHandlerAdapter() {
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                ctx.writeAndFlush(newOneMessage());
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                //shouldn't be any channel closing, RST, etc from client and no ReadTimeoutExceptions in this test
                unexpectedExceptionInServerHandler.set(true);
            }
        };

        ChannelInboundHandler trackingHandler = new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                clientReads.set(true);
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                unexpectedExceptionInClientHandlerLatch.countDown();
            }
        };

        Channel server = newServer(pongAfterTwoMessagesHandler);
        Channel client = newClient(server.localAddress(), readAfterWriteTimeoutHandler, trackingHandler);
        try {
            client.writeAndFlush(newOneMessage());
            assertFalse("Shouldn't get any timeouts or exceptions in client channel",
                        unexpectedExceptionInClientHandlerLatch.await(2 * TIMEOUT_MS, TimeUnit.MILLISECONDS));
            assertTrue("Client should get server response already", clientReads.get());
            assertFalse("Unexpected exception in server handler", unexpectedExceptionInServerHandler.get());
        } finally {
            client.close();
            server.close();
        }
    }

    /**
     * We should continue work after ReadTimeout
     */
    @Test
    public void testContinueCommunicationAfterTimeout() throws Exception {
        final int TIMEOUT_MS = 300;
        final int MESSAGES_COUNT = 2;

        final AtomicInteger serverReads = new AtomicInteger();
        final CountDownLatch timeoutGotLatch = new CountDownLatch(1);
        final CountDownLatch clientReadsLatch = new CountDownLatch(1);
        final CountDownLatch suppressedReadsLatch = new CountDownLatch(1);
        final AtomicBoolean unexpectedExceptionInServerHandler = new AtomicBoolean();
        final AtomicBoolean unexpectedExceptionInClientHandler = new AtomicBoolean();

        ReadAfterWriteTimeoutHandler readAfterWriteTimeoutHandler =
                new ReadAfterWriteTimeoutHandler(TIMEOUT_MS, TimeUnit.MILLISECONDS, false, true);

        ChannelInboundHandler pongHandler = new ChannelInboundHandlerAdapter() {
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                serverReads.incrementAndGet();
                if (serverReads.get() != MESSAGES_COUNT) {
                    Thread.sleep(2 * TIMEOUT_MS);
                }
                ctx.writeAndFlush(newOneMessage());
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                unexpectedExceptionInServerHandler.set(true);
            }
        };

        ChannelInboundHandler trackingHandler = new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                clientReadsLatch.countDown();
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                if (cause instanceof ReadTimeoutException) {
                    timeoutGotLatch.countDown();
                } else {
                    unexpectedExceptionInClientHandler.set(true);
                }
            }

            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                if (evt instanceof ReadAfterWriteTimeoutHandler.ReadSuppressed) {
                    suppressedReadsLatch.countDown();
                }
            }
        };

        Channel server = newServer(pongHandler);
        Channel client = newClient(server.localAddress(), readAfterWriteTimeoutHandler, trackingHandler);
        try {
            client.writeAndFlush(newOneMessage());
            assertTrue("No ReadTimeoutException got", timeoutGotLatch.await(3 * TIMEOUT_MS, TimeUnit.MILLISECONDS));
            assertFalse("No reads should be on client after timeout," +
                       "because ReadAfterWriteTimeoutHandler#ignoreReadAfterTimeout == true",
                       clientReadsLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            assertTrue("SuppressedRead event should be fired after getting response after timeout",
                       suppressedReadsLatch.await(2 * TIMEOUT_MS, TimeUnit.MILLISECONDS));

            client.writeAndFlush(newOneMessage());
            assertTrue("Read should be got after write after timeout",
                        clientReadsLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            assertFalse("Unexpected exception in server handler", unexpectedExceptionInServerHandler.get());
            assertFalse("Unexpected exception in client handler", unexpectedExceptionInClientHandler.get());
        } finally {
            client.close();
            server.close();
        }
    }

    @BeforeClass
    public static void init() {
        GROUP = new NioEventLoopGroup();
    }

    @AfterClass
    public static void destroy() {
        GROUP.shutdownGracefully();
    }

    private static Channel newServer(final ChannelHandler... handlers) {
        assertTrue(handlers.length >= 1);

        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(GROUP)
                       .channel(NioServerSocketChannel.class)
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

    private static Channel newClient(SocketAddress server, final ChannelHandler... handlers) {
        Bootstrap bootstrap = new Bootstrap();

        bootstrap.group(GROUP)
                 .channel(NioSocketChannel.class)
                 .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                 .handler(
                     new ChannelInitializer<Channel>() {
                         @Override
                         protected void initChannel(Channel ch) throws Exception {
                             ChannelPipeline pipeline = ch.pipeline();
                             pipeline.addLast(handlers);
                         }
                     }
                 );

        return bootstrap.connect(server)
                        .syncUninterruptibly()
                        .channel();
    }

    private static ByteBuf newOneMessage() {
        return Unpooled.wrappedBuffer(new byte[]{ 1 });
    }
}
