/*
 * Copyright 2022 The Netty Project
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
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.TunAddress;
import io.netty.channel.socket.TunChannel;
import io.netty.example.tun.Echo4Handler;
import io.netty.util.internal.StringUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import static io.netty.buffer.Unpooled.wrappedBuffer;

/**
 * This test creates a TUN device that echoes back all received IPv4 packets and a {@link DatagramChannel}
 * that sends random data and checks if it was echoed back.
 */
public abstract class TunChannelTest {
    private static final String ADDRESS = "10.1.29.60";
    private static final int NETMASK = 30;
    private static final String RECIPIENT = "10.1.29.61";
    private static final int BUF_LEN = 500;
    private static final int BUF_COUNT = 5;
    private final Random rand;
    private final EventLoopGroup group;
    private final Class<? extends TunChannel> tunChannelClass;
    private final Class<? extends DatagramChannel> udpChannelClass;

    protected TunChannelTest(EventLoopGroup group,
                             Class<? extends TunChannel> tunChannelClass,
                             Class<? extends DatagramChannel> udpChannelClass) {
        this.rand = new Random();
        this.group = group;
        this.tunChannelClass = tunChannelClass;
        this.udpChannelClass = udpChannelClass;
    }

    @AfterEach
    void tearDown() {
        group.shutdownGracefully();
    }

    @Test
    void testEchoIpPackets() throws InterruptedException, IOException {
        Channel tunCh = null;
        Channel udpCh = null;

        try {
            // create tun device
            tunCh = new Bootstrap()
                    .group(group)
                    .channel(tunChannelClass)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new Echo4Handler());
                        }
                    }).bind(new TunAddress()).syncUninterruptibly().channel();

            // assign ip address/netmask to tun device
            String name = ((TunAddress) tunCh.localAddress()).ifName();
            attachAddressAndNetmask(name, ADDRESS, NETMASK);

            // create udp channel
            final CountDownLatch latch = new CountDownLatch(BUF_COUNT);
            udpCh = new Bootstrap()
                    .group(group)
                    .channel(udpChannelClass)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new PingHandler(latch));
                        }
                    }).bind(0).syncUninterruptibly().channel();
            latch.await();
        } finally {
            if (tunCh != null) {
                tunCh.close().sync();
            }
            if (udpCh != null) {
                udpCh.close().sync();
            }
            group.shutdownGracefully().sync();
        }
    }

    protected abstract void attachAddressAndNetmask(String name, String address, int netmask) throws IOException;

    protected static void exec(String... command) throws IOException {
        try {
            int exitCode = Runtime.getRuntime().exec(command).waitFor();
            if (exitCode != 0) {
                CharSequence arguments = StringUtil.join(" ", Arrays.asList(command));
                throw new IOException("Executing `" + arguments + "` returned non-zero exit code (" + exitCode + ").");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private final class PingHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        private final CountDownLatch latch;
        private ByteBuf buf;
        private InetSocketAddress recipient;

        PingHandler(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.fireChannelActive();

            // use same port for sender and recipient as Echo4Handler only swap addresses
            final int port = ((InetSocketAddress) ctx.channel().localAddress()).getPort();
            recipient = new InetSocketAddress(RECIPIENT, port);

            sendBuf(ctx);
        }

        @Override
        public void channelInactive(final ChannelHandlerContext ctx) {
            buf.release();
            ctx.fireChannelInactive();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx,
                                    DatagramPacket msg) {
            if (buf.equals(msg.content())) {
                latch.countDown();
                sendBuf(ctx);
            }
        }

        private void sendBuf(ChannelHandlerContext ctx) {
            final byte[] bytes = new byte[BUF_LEN];
            rand.nextBytes(bytes);
            buf = wrappedBuffer(bytes);
            ctx.writeAndFlush(new DatagramPacket(buf.retain(), recipient));
        }
    }
}
