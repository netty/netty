/*
 * Copyright 2014 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.util.ReferenceCountUtil;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class EpollSocketChannelTest {

    @Test
    public void testTcpInfo() throws Exception {
        EventLoopGroup group = new EpollEventLoopGroup(1);

        try {
            Bootstrap bootstrap = new Bootstrap();
            EpollSocketChannel ch = (EpollSocketChannel) bootstrap.group(group)
                    .channel(EpollSocketChannel.class)
                    .handler(new ChannelInboundHandlerAdapter())
                    .bind(new InetSocketAddress(0)).syncUninterruptibly().channel();
            EpollTcpInfo info = ch.tcpInfo();
            assertTcpInfo0(info);
            ch.close().syncUninterruptibly();
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testTcpInfoReuse() throws Exception {
        EventLoopGroup group = new EpollEventLoopGroup(1);

        try {
            Bootstrap bootstrap = new Bootstrap();
            EpollSocketChannel ch = (EpollSocketChannel) bootstrap.group(group)
                    .channel(EpollSocketChannel.class)
                    .handler(new ChannelInboundHandlerAdapter())
                    .bind(new InetSocketAddress(0)).syncUninterruptibly().channel();
            EpollTcpInfo info = new EpollTcpInfo();
            ch.tcpInfo(info);
            assertTcpInfo0(info);
            ch.close().syncUninterruptibly();
        } finally {
            group.shutdownGracefully();
        }
    }

    private static void assertTcpInfo0(EpollTcpInfo info) throws Exception {
        Assert.assertNotNull(info);

        Assert.assertTrue(info.state() >= 0);
        Assert.assertTrue(info.caState() >= 0);
        Assert.assertTrue(info.retransmits() >= 0);
        Assert.assertTrue(info.probes() >= 0);
        Assert.assertTrue(info.backoff() >= 0);
        Assert.assertTrue(info.options() >= 0);
        Assert.assertTrue(info.sndWscale() >= 0);
        Assert.assertTrue(info.rcvWscale() >= 0);
        Assert.assertTrue(info.rto() >= 0);
        Assert.assertTrue(info.ato() >= 0);
        Assert.assertTrue(info.sndMss() >= 0);
        Assert.assertTrue(info.rcvMss() >= 0);
        Assert.assertTrue(info.unacked() >= 0);
        Assert.assertTrue(info.sacked() >= 0);
        Assert.assertTrue(info.lost() >= 0);
        Assert.assertTrue(info.retrans() >= 0);
        Assert.assertTrue(info.fackets() >= 0);
        Assert.assertTrue(info.lastDataSent() >= 0);
        Assert.assertTrue(info.lastAckSent() >= 0);
        Assert.assertTrue(info.lastDataRecv() >= 0);
        Assert.assertTrue(info.lastAckRecv() >= 0);
        Assert.assertTrue(info.pmtu() >= 0);
        Assert.assertTrue(info.rcvSsthresh() >= 0);
        Assert.assertTrue(info.rtt() >= 0);
        Assert.assertTrue(info.rttvar() >= 0);
        Assert.assertTrue(info.sndSsthresh() >= 0);
        Assert.assertTrue(info.sndCwnd() >= 0);
        Assert.assertTrue(info.advmss() >= 0);
        Assert.assertTrue(info.reordering() >= 0);
        Assert.assertTrue(info.rcvRtt() >= 0);
        Assert.assertTrue(info.rcvSpace() >= 0);
        Assert.assertTrue(info.totalRetrans() >= 0);
    }

    @Test
    public void testExceptionHandlingDoesNotInfiniteLoop() throws InterruptedException {
        EventLoopGroup group = new EpollEventLoopGroup();
        try {
            runExceptionHandleFeedbackLoop(group, EpollServerSocketChannel.class, EpollSocketChannel.class,
                    new InetSocketAddress(0));
            runExceptionHandleFeedbackLoop(group, EpollServerDomainSocketChannel.class, EpollDomainSocketChannel.class,
                    EpollSocketTestPermutation.newSocketAddress());
        } finally {
            group.shutdownGracefully();
        }
    }

    private void runExceptionHandleFeedbackLoop(EventLoopGroup group, Class<? extends ServerChannel> serverChannelClass,
                                                Class<? extends Channel> channelClass, SocketAddress bindAddr)
            throws InterruptedException {
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            MyInitializer serverInitializer = new MyInitializer();
            ServerBootstrap sb = new ServerBootstrap();
            sb.option(ChannelOption.SO_BACKLOG, 1024);
            sb.group(group)
            .channel(serverChannelClass)
            .childHandler(serverInitializer);

            serverChannel = sb.bind(bindAddr).syncUninterruptibly().channel();

            Bootstrap b = new Bootstrap();
            b.group(group);
            b.channel(channelClass);
            b.remoteAddress(serverChannel.localAddress());
            b.handler(new MyInitializer());
            clientChannel = b.connect().syncUninterruptibly().channel();

            clientChannel.writeAndFlush(Unpooled.wrappedBuffer(new byte[1024]));

            // We expect to get 2 exceptions (1 from BuggyChannelHandler and 1 from ExceptionHandler).
            Assert.assertTrue(serverInitializer.exceptionHandler.latch1.await(2, TimeUnit.SECONDS));

            // After we get the first exception, we should get no more, this is expected to timeout.
            Assert.assertFalse("Encountered " + serverInitializer.exceptionHandler.count.get() +
                    " exceptions when 1 was expected",
                    serverInitializer.exceptionHandler.latch2.await(2, TimeUnit.SECONDS));
        } finally {
            if (serverChannel != null) {
                serverChannel.close().syncUninterruptibly();
            }
            if (clientChannel != null) {
                clientChannel.close().syncUninterruptibly();
            }
        }
    }

    private static class MyInitializer extends ChannelInitializer<Channel> {
        final ExceptionHandler exceptionHandler = new ExceptionHandler();
        @Override
        protected void initChannel(Channel ch) throws Exception {
            ChannelPipeline pipeline = ch.pipeline();

            pipeline.addLast(new BuggyChannelHandler());
            pipeline.addLast(exceptionHandler);
        }
    }

    private static class BuggyChannelHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ReferenceCountUtil.release(msg);
            throw new NullPointerException("I am a bug!");
        }
    }

    private static class ExceptionHandler extends ChannelInboundHandlerAdapter {
        final AtomicLong count = new AtomicLong();
        /**
         * We expect to get 1 call to {@link #exceptionCaught(ChannelHandlerContext, Throwable)}.
         */
        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            if (count.incrementAndGet() <= 2) {
                latch1.countDown();
            } else {
                latch2.countDown();
            }
            // This should not throw any exception.
            ctx.close();
        }
    }
}
