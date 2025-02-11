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
package io.netty.channel.unix.tests;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.ServerChannel;
import io.netty.channel.SimpleChannelInboundHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class DetectPeerCloseWithoutReadTest {
    protected abstract EventLoopGroup newGroup();
    protected abstract Class<? extends ServerChannel> serverChannel();
    protected abstract Class<? extends Channel> clientChannel();

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void clientCloseWithoutServerReadIsDetectedNoExtraReadRequested() throws InterruptedException {
        clientCloseWithoutServerReadIsDetected0(false);
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void clientCloseWithoutServerReadIsDetectedExtraReadRequested() throws InterruptedException {
        clientCloseWithoutServerReadIsDetected0(true);
    }

    private void clientCloseWithoutServerReadIsDetected0(final boolean extraReadRequested)
            throws InterruptedException {
        EventLoopGroup serverGroup = null;
        EventLoopGroup clientGroup = null;
        Channel serverChannel = null;
        try {
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicInteger bytesRead = new AtomicInteger();
            final int expectedBytes = 100;
            serverGroup = newGroup();
            clientGroup = newGroup();
            ServerBootstrap sb = new ServerBootstrap();
            sb.group(serverGroup);
            sb.channel(serverChannel());
            // Ensure we read only one message per read() call and that we need multiple read()
            // calls to consume everything.
            sb.childOption(ChannelOption.AUTO_READ, false);
            sb.childOption(ChannelOption.MAX_MESSAGES_PER_READ, 1);
            sb.childOption(ChannelOption.RECVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(expectedBytes / 10));
            sb.childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new TestHandler(bytesRead, extraReadRequested, latch));
                }
            });

            serverChannel = sb.bind(new InetSocketAddress(0)).syncUninterruptibly().channel();

            Bootstrap cb = new Bootstrap();
            cb.group(serverGroup);
            cb.channel(clientChannel());
            cb.handler(new ChannelInboundHandlerAdapter());
            Channel clientChannel = cb.connect(serverChannel.localAddress()).syncUninterruptibly().channel();
            ByteBuf buf = clientChannel.alloc().buffer(expectedBytes);
            buf.writerIndex(buf.writerIndex() + expectedBytes);
            clientChannel.writeAndFlush(buf).addListener(ChannelFutureListener.CLOSE);

            latch.await();
            assertEquals(expectedBytes, bytesRead.get());
        } finally {
            if (serverChannel != null) {
                serverChannel.close().syncUninterruptibly();
            }
            if (serverGroup != null) {
                serverGroup.shutdownGracefully();
            }
            if (clientGroup != null) {
                clientGroup.shutdownGracefully();
            }
        }
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void serverCloseWithoutClientReadIsDetectedNoExtraReadRequested() throws InterruptedException {
        serverCloseWithoutClientReadIsDetected0(false);
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void serverCloseWithoutClientReadIsDetectedExtraReadRequested() throws InterruptedException {
        serverCloseWithoutClientReadIsDetected0(true);
    }

    private void serverCloseWithoutClientReadIsDetected0(final boolean extraReadRequested) throws InterruptedException {
        EventLoopGroup serverGroup = null;
        EventLoopGroup clientGroup = null;
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicInteger bytesRead = new AtomicInteger();
            final int expectedBytes = 100;
            serverGroup = newGroup();
            clientGroup = newGroup();
            ServerBootstrap sb = new ServerBootstrap();
            sb.group(serverGroup);
            sb.channel(serverChannel());
            sb.childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                            ByteBuf buf = ctx.alloc().buffer(expectedBytes);
                            buf.writerIndex(buf.writerIndex() + expectedBytes);
                            ctx.writeAndFlush(buf).addListener(ChannelFutureListener.CLOSE);
                            ctx.fireChannelActive();
                        }
                    });
                }
            });

            serverChannel = sb.bind(new InetSocketAddress(0)).syncUninterruptibly().channel();

            Bootstrap cb = new Bootstrap();
            cb.group(serverGroup);
            cb.channel(clientChannel());
            // Ensure we read only one message per read() call and that we need multiple read()
            // calls to consume everything.
            cb.option(ChannelOption.AUTO_READ, false);
            cb.option(ChannelOption.MAX_MESSAGES_PER_READ, 1);
            cb.option(ChannelOption.RECVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(expectedBytes / 10));
            cb.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(new TestHandler(bytesRead, extraReadRequested, latch));
                }
            });
            clientChannel = cb.connect(serverChannel.localAddress()).syncUninterruptibly().channel();

            latch.await();
            assertEquals(expectedBytes, bytesRead.get());
        } finally {
            if (serverChannel != null) {
                serverChannel.close().syncUninterruptibly();
            }
            if (clientChannel != null) {
                clientChannel.close().syncUninterruptibly();
            }
            if (serverGroup != null) {
                serverGroup.shutdownGracefully();
            }
            if (clientGroup != null) {
                clientGroup.shutdownGracefully();
            }
        }
    }

    private static final class TestHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final AtomicInteger bytesRead;
        private final boolean extraReadRequested;
        private final CountDownLatch latch;

        TestHandler(AtomicInteger bytesRead, boolean extraReadRequested, CountDownLatch latch) {
            this.bytesRead = bytesRead;
            this.extraReadRequested = extraReadRequested;
            this.latch = latch;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            bytesRead.addAndGet(msg.readableBytes());

            if (extraReadRequested) {
                // Because autoread is off, we call read to consume all data until we detect the close.
                ctx.read();
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            latch.countDown();
            ctx.fireChannelInactive();
        }
    }
}
