/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.flow;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.SocketAddress;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Exchanger;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class FlowControlHandlerTest {
    private static EventLoopGroup GROUP;

    @BeforeAll
    public static void init() {
        GROUP = new NioEventLoopGroup();
    }

    @AfterAll
    public static void destroy() {
        GROUP.shutdownGracefully();
    }

    /**
     * The {@link OneByteToThreeStringsDecoder} decodes this {@code byte[]} into three messages.
     */
    private static ByteBuf newOneMessage() {
        return Unpooled.wrappedBuffer(new byte[]{ 1 });
    }

    private static Channel newServer(final boolean autoRead, final ChannelHandler... handlers) {
        assertTrue(handlers.length >= 1);

        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(GROUP)
            .channel(NioServerSocketChannel.class)
            .childOption(ChannelOption.AUTO_READ, autoRead)
            .childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ChannelPipeline pipeline = ch.pipeline();
                    pipeline.addLast(new OneByteToThreeStringsDecoder());
                    pipeline.addLast(handlers);
                }
            });

        return serverBootstrap.bind(0)
                .syncUninterruptibly()
                .channel();
    }

    private static Channel newClient(SocketAddress server) {
        Bootstrap bootstrap = new Bootstrap();

        bootstrap.group(GROUP)
            .channel(NioSocketChannel.class)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
            .handler(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    fail("In this test the client is never receiving a message from the server.");
                }
            });

        return bootstrap.connect(server)
                .syncUninterruptibly()
                .channel();
    }

    /**
     * This test demonstrates the default behavior if auto reading
     * is turned on from the get-go and you're trying to turn it off
     * once you've received your first message.
     *
     * NOTE: This test waits for the client to disconnect which is
     * interpreted as the signal that all {@code byte}s have been
     * transferred to the server.
     */
    @Test
    public void testAutoReadingOn() throws Exception {
        final CountDownLatch latch = new CountDownLatch(3);

        ChannelInboundHandlerAdapter handler = new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                ReferenceCountUtil.release(msg);
                // We're turning off auto reading in the hope that no
                // new messages are being sent but that is not true.
                ctx.channel().config().setAutoRead(false);

                latch.countDown();
            }
        };

        Channel server = newServer(true, handler);
        Channel client = newClient(server.localAddress());

        try {
            client.writeAndFlush(newOneMessage())
                .syncUninterruptibly();

            // We received three messages even through auto reading
            // was turned off after we received the first message.
            assertTrue(latch.await(1L, SECONDS));
        } finally {
            client.close();
            server.close();
        }
    }

    /**
     * This test demonstrates the default behavior if auto reading
     * is turned off from the get-go and you're calling read() in
     * the hope that only one message will be returned.
     *
     * NOTE: This test waits for the client to disconnect which is
     * interpreted as the signal that all {@code byte}s have been
     * transferred to the server.
     */
    @Test
    public void testAutoReadingOff() throws Exception {
        final Exchanger<Channel> peerRef = new Exchanger<Channel>();
        final CountDownLatch latch = new CountDownLatch(3);

        ChannelInboundHandlerAdapter handler = new ChannelInboundHandlerAdapter() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                peerRef.exchange(ctx.channel(), 1L, SECONDS);
                ctx.fireChannelActive();
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                ReferenceCountUtil.release(msg);
                latch.countDown();
            }
        };

        Channel server = newServer(false, handler);
        Channel client = newClient(server.localAddress());

        try {
            // The client connection on the server side
            Channel peer = peerRef.exchange(null, 1L, SECONDS);

            // Write the message
            client.writeAndFlush(newOneMessage())
                .syncUninterruptibly();

            // Read the message
            peer.read();

            // We received all three messages but hoped that only one
            // message was read because auto reading was off and we
            // invoked the read() method only once.
            assertTrue(latch.await(1L, SECONDS));
        } finally {
            client.close();
            server.close();
        }
    }

    /**
     * The {@link FlowControlHandler} will simply pass-through all messages
     * if auto reading is on and remains on.
     */
    @Test
    public void testFlowAutoReadOn() throws Exception {
        final CountDownLatch latch = new CountDownLatch(3);
        final Exchanger<Channel> peerRef = new Exchanger<Channel>();

        ChannelInboundHandlerAdapter handler = new ChannelDuplexHandler() {

            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                peerRef.exchange(ctx.channel(), 1L, SECONDS);
                super.channelActive(ctx);
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                ReferenceCountUtil.release(msg);
                latch.countDown();
            }
        };

        final FlowControlHandler flow = new FlowControlHandler();
        Channel server = newServer(true, flow, handler);
        Channel client = newClient(server.localAddress());
        try {
            // The client connection on the server side
            Channel peer = peerRef.exchange(null, 1L, SECONDS);

            // Write the message
            client.writeAndFlush(newOneMessage())
                .syncUninterruptibly();

            // We should receive 3 messages
            assertTrue(latch.await(1L, SECONDS));

            assertTrue(peer.eventLoop().submit(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return flow.isQueueEmpty();
                }
            }).get());
        } finally {
            client.close();
            server.close();
        }
    }

    /**
     * The {@link FlowControlHandler} will pass down messages one by one
     * if {@link ChannelConfig#setAutoRead(boolean)} is being toggled.
     */
    @Test
    public void testFlowToggleAutoRead() throws Exception {
        final Exchanger<Channel> peerRef = new Exchanger<Channel>();
        final CountDownLatch msgRcvLatch1 = new CountDownLatch(1);
        final CountDownLatch msgRcvLatch2 = new CountDownLatch(1);
        final CountDownLatch msgRcvLatch3 = new CountDownLatch(1);
        final CountDownLatch setAutoReadLatch1 = new CountDownLatch(1);
        final CountDownLatch setAutoReadLatch2 = new CountDownLatch(1);

        ChannelInboundHandlerAdapter handler = new ChannelInboundHandlerAdapter() {
            private int msgRcvCount;
            private int expectedMsgCount;
            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                peerRef.exchange(ctx.channel(), 1L, SECONDS);
                ctx.fireChannelActive();
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws InterruptedException {
                ReferenceCountUtil.release(msg);

                // Disable auto reading after each message
                ctx.channel().config().setAutoRead(false);

                if (msgRcvCount++ != expectedMsgCount) {
                    return;
                }
                switch (msgRcvCount) {
                    case 1:
                        msgRcvLatch1.countDown();
                        if (setAutoReadLatch1.await(1L, SECONDS)) {
                            ++expectedMsgCount;
                        }
                        break;
                    case 2:
                        msgRcvLatch2.countDown();
                        if (setAutoReadLatch2.await(1L, SECONDS)) {
                            ++expectedMsgCount;
                        }
                        break;
                    default:
                        msgRcvLatch3.countDown();
                        break;
                }
            }
        };

        final FlowControlHandler flow = new FlowControlHandler();
        Channel server = newServer(true, flow, handler);
        Channel client = newClient(server.localAddress());
        try {
            // The client connection on the server side
            Channel peer = peerRef.exchange(null, 1L, SECONDS);

            client.writeAndFlush(newOneMessage())
                .syncUninterruptibly();

            // channelRead(1)
            assertTrue(msgRcvLatch1.await(1L, SECONDS));

            // channelRead(2)
            peer.config().setAutoRead(true);
            setAutoReadLatch1.countDown();
            assertTrue(msgRcvLatch1.await(1L, SECONDS));

            // channelRead(3)
            peer.config().setAutoRead(true);
            setAutoReadLatch2.countDown();
            assertTrue(msgRcvLatch3.await(1L, SECONDS));

            assertTrue(peer.eventLoop().submit(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return flow.isQueueEmpty();
                }
            }).get());
        } finally {
            client.close();
            server.close();
        }
    }

    /**
     * The {@link FlowControlHandler} will pass down messages one by one
     * if auto reading is off and the user is calling {@code read()} on
     * their own.
     */
    @Test
    public void testFlowAutoReadOff() throws Exception {
        final Exchanger<Channel> peerRef = new Exchanger<Channel>();
        final CountDownLatch msgRcvLatch1 = new CountDownLatch(1);
        final CountDownLatch msgRcvLatch2 = new CountDownLatch(2);
        final CountDownLatch msgRcvLatch3 = new CountDownLatch(3);

        ChannelInboundHandlerAdapter handler = new ChannelDuplexHandler() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                ctx.fireChannelActive();
                peerRef.exchange(ctx.channel(), 1L, SECONDS);
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                msgRcvLatch1.countDown();
                msgRcvLatch2.countDown();
                msgRcvLatch3.countDown();
            }
        };

        final FlowControlHandler flow = new FlowControlHandler();
        Channel server = newServer(false, flow, handler);
        Channel client = newClient(server.localAddress());
        try {
            // The client connection on the server side
            Channel peer = peerRef.exchange(null, 1L, SECONDS);

            // Write the message
            client.writeAndFlush(newOneMessage())
                .syncUninterruptibly();

            // channelRead(1)
            peer.read();
            assertTrue(msgRcvLatch1.await(1L, SECONDS));

            // channelRead(2)
            peer.read();
            assertTrue(msgRcvLatch2.await(1L, SECONDS));

            // channelRead(3)
            peer.read();
            assertTrue(msgRcvLatch3.await(1L, SECONDS));

            assertTrue(peer.eventLoop().submit(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return flow.isQueueEmpty();
                }
            }).get());
        } finally {
            client.close();
            server.close();
        }
    }

    /**
     * The {@link FlowControlHandler} will not pass read events onto the
     * pipeline when the user is calling {@code read()} on their own if the
     * queue is not empty and auto-reading is turned off for the channel.
     */
    @Test
    public void testFlowAutoReadOffAndQueueNonEmpty() throws Exception {
        final Exchanger<Channel> peerRef = new Exchanger<Channel>();
        final CountDownLatch msgRcvLatch1 = new CountDownLatch(1);
        final CountDownLatch msgRcvLatch2 = new CountDownLatch(2);
        final CountDownLatch msgRcvLatch3 = new CountDownLatch(3);

        ChannelInboundHandlerAdapter handler = new ChannelDuplexHandler() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                ctx.fireChannelActive();
                peerRef.exchange(ctx.channel(), 1L, SECONDS);
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                msgRcvLatch1.countDown();
                msgRcvLatch2.countDown();
                msgRcvLatch3.countDown();
            }
        };

        final FlowControlHandler flow = new FlowControlHandler();
        Channel server = newServer(false, flow, handler);
        Channel client = newClient(server.localAddress());
        try {
            // The client connection on the server side
            Channel peer = peerRef.exchange(null, 1L, SECONDS);

            // Write the first message
            client.writeAndFlush(newOneMessage())
                .syncUninterruptibly();

            // channelRead(1)
            peer.read();
            assertTrue(msgRcvLatch1.await(1L, SECONDS));
            assertFalse(peer.eventLoop().submit(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return flow.isQueueEmpty();
                }
            }).get());

            // Write the second message
            client.writeAndFlush(newOneMessage())
                .syncUninterruptibly();

            // channelRead(2)
            peer.read();
            assertTrue(msgRcvLatch2.await(1L, SECONDS));

            // channelRead(3)
            peer.read();
            assertTrue(msgRcvLatch3.await(1L, SECONDS));

            assertTrue(peer.eventLoop().submit(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return flow.isQueueEmpty();
                }
            }).get());
        } finally {
            client.close();
            server.close();
        }
    }

    @Test
    public void testReentranceNotCausesNPE() throws Throwable {
        final Exchanger<Channel> peerRef = new Exchanger<Channel>();
        final CountDownLatch latch = new CountDownLatch(3);
        final AtomicReference<Throwable> causeRef = new AtomicReference<Throwable>();
        ChannelInboundHandlerAdapter handler = new ChannelDuplexHandler() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                ctx.fireChannelActive();
                peerRef.exchange(ctx.channel(), 1L, SECONDS);
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                latch.countDown();
                ctx.read();
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                causeRef.set(cause);
            }
        };

        final FlowControlHandler flow = new FlowControlHandler();
        Channel server = newServer(false, flow, handler);
        Channel client = newClient(server.localAddress());
        try {
            // The client connection on the server side
            Channel peer = peerRef.exchange(null, 1L, SECONDS);

            // Write the message
            client.writeAndFlush(newOneMessage())
                    .syncUninterruptibly();

            // channelRead(1)
            peer.read();
            assertTrue(latch.await(1L, SECONDS));

            assertTrue(peer.eventLoop().submit(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return flow.isQueueEmpty();
                }
            }).get());

            Throwable cause = causeRef.get();
            if (cause != null) {
                throw cause;
            }
        } finally {
            client.close();
            server.close();
        }
    }

    @Test
    public void testSwallowedReadComplete() throws Exception {
        final long delayMillis = 100;
        final Queue<IdleStateEvent> userEvents = new LinkedBlockingQueue<IdleStateEvent>();
        final EmbeddedChannel channel = new EmbeddedChannel(false, false,
            new FlowControlHandler(),
            new IdleStateHandler(delayMillis, 0, 0, MILLISECONDS),
            new ChannelInboundHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) {
                    ctx.fireChannelActive();
                    ctx.read();
                }

                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    ctx.fireChannelRead(msg);
                    ctx.read();
                }

                @Override
                public void channelReadComplete(ChannelHandlerContext ctx) {
                    ctx.fireChannelReadComplete();
                    ctx.read();
                }

                @Override
                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                    if (evt instanceof IdleStateEvent) {
                        userEvents.add((IdleStateEvent) evt);
                    }
                    ctx.fireUserEventTriggered(evt);
                }
            }
        );

        channel.config().setAutoRead(false);
        assertFalse(channel.config().isAutoRead());

        channel.register();

        // Reset read timeout by some message
        assertTrue(channel.writeInbound(Unpooled.EMPTY_BUFFER));
        channel.flushInbound();
        assertEquals(Unpooled.EMPTY_BUFFER, channel.readInbound());

        // Emulate 'no more messages in NIO channel' on the next read attempt.
        channel.flushInbound();
        assertNull(channel.readInbound());

        Thread.sleep(delayMillis + 20L);
        channel.runPendingTasks();
        assertEquals(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT, userEvents.poll());
        assertFalse(channel.finish());
    }

    @Test
    public void testRemoveFlowControl() throws Exception {
        final Exchanger<Channel> peerRef = new Exchanger<Channel>();

        final CountDownLatch latch = new CountDownLatch(3);

        ChannelInboundHandlerAdapter handler = new ChannelDuplexHandler() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                peerRef.exchange(ctx.channel(), 1L, SECONDS);
                //do the first read
                ctx.read();
                super.channelActive(ctx);
            }
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                latch.countDown();
                super.channelRead(ctx, msg);
            }
        };

        final FlowControlHandler flow = new FlowControlHandler() {
            private int num;
            @Override
            public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
                super.channelRead(ctx, msg);
                ++num;
                if (num >= 3) {
                    //We have received 3 messages. Remove myself later
                    final ChannelHandler handler = this;
                    ctx.channel().eventLoop().execute(new Runnable() {
                        @Override
                        public void run() {
                            ctx.pipeline().remove(handler);
                        }
                    });
                }
            }
        };
        ChannelInboundHandlerAdapter tail = new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                //consume this msg
                ReferenceCountUtil.release(msg);
            }
        };

        Channel server = newServer(false /* no auto read */, flow, handler, tail);
        Channel client = newClient(server.localAddress());
        try {
            // The client connection on the server side
            Channel peer = peerRef.exchange(null, 1L, SECONDS);

            // Write one message
            client.writeAndFlush(newOneMessage()).sync();

            // We should receive 3 messages
            assertTrue(latch.await(1L, SECONDS));
            assertTrue(peer.eventLoop().submit(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return flow.isQueueEmpty();
                }
            }).get());
        } finally {
            client.close();
            server.close();
        }
    }

    /**
     * This is a fictional message decoder. It decodes each {@code byte}
     * into three strings.
     */
    private static final class OneByteToThreeStringsDecoder extends ByteToMessageDecoder {
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            for (int i = 0; i < in.readableBytes(); i++) {
                out.add("1");
                out.add("2");
                out.add("3");
            }
            in.readerIndex(in.readableBytes());
        }
    }
}
