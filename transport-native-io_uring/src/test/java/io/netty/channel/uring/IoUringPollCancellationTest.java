/*
 * Copyright 2026 The Netty Project
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
package io.netty.channel.uring;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.IoEventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.util.NetUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The polls of a channel must each be cancelled once it closes: a poll that is still armed keeps the socket open
 * after its fd is closed, so the peer never sees the FIN, and the event loop cannot terminate.
 */
public class IoUringPollCancellationTest {

    // The ioState bit of AbstractIoUringChannel for an armed POLLIN poll.
    private static final int POLL_IN_SCHEDULED = 1;

    private IoEventLoopGroup group;
    private ServerSocket server;

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    @BeforeEach
    public void setUp() throws IOException {
        group = new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory());
        server = new ServerSocket(0, 1, NetUtil.LOCALHOST);
        server.setSoTimeout(10_000);
    }

    @AfterEach
    public void tearDown() throws Exception {
        server.close();
        assertThat(group.shutdownGracefully(0, 1, SECONDS).await(10, SECONDS))
                .as("the event loop terminated").isTrue();
    }

    // A listener of the connect future runs before channelActive, whose auto-read then calls read() on a channel
    // that is closing: the write in flight delayed the close, which has already cancelled the channel's polls.
    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    public void closeFromConnectListenerWithWriteInFlightClosesTheSocket() throws Exception {
        Channel channel = newClient(new ChannelInboundHandlerAdapter(), true);
        // Connect on the event loop, so that the listener is added before the connect can complete.
        channel.eventLoop().submit(() -> channel.connect(server.getLocalSocketAddress()).addListener(f -> {
            channel.writeAndFlush(Unpooled.wrappedBuffer(new byte[] {1, 2, 3}));
            channel.close();
        })).sync();

        try (Socket accepted = server.accept()) {
            assertEof(accepted);
        }
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    public void readDuringDelayedCloseArmsNoPoll() throws Exception {
        AtomicBoolean pollInArmed = new AtomicBoolean(true);
        Channel channel = newClient(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                // Fired after the connect listener closed the channel.
                ctx.fireChannelActive();
                ctx.read();
                pollInArmed.set((ioState(ctx.channel()) & POLL_IN_SCHEDULED) != 0);
            }
        }, true);
        channel.eventLoop().submit(() -> channel.connect(server.getLocalSocketAddress()).addListener(f -> {
            channel.writeAndFlush(Unpooled.wrappedBuffer(new byte[] {1}));
            channel.close();
        })).sync();

        try (Socket accepted = server.accept()) {
            assertThat(channel.closeFuture().await(10, SECONDS)).isTrue();
            assertThat(pollInArmed.get()).as("a POLLIN poll armed on a channel whose close was pending").isFalse();
            assertEof(accepted);
        }
    }

    private Channel newClient(ChannelInboundHandlerAdapter handler, boolean autoRead) throws InterruptedException {
        return new Bootstrap()
                .group(group)
                .channel(IoUringSocketChannel.class)
                .handler(handler)
                .option(ChannelOption.AUTO_READ, autoRead)
                .remoteAddress(new InetSocketAddress(NetUtil.LOCALHOST, server.getLocalPort()))
                .register().sync().channel();
    }

    private static void assertEof(Socket socket) throws IOException {
        socket.setSoTimeout(10_000);
        InputStream in = socket.getInputStream();
        byte[] buffer = new byte[65536];
        // Whatever of the data arrived; a SocketTimeoutException means no FIN came.
        while (in.read(buffer) != -1) {
            continue;
        }
    }

    private static int ioState(Channel channel) throws ReflectiveOperationException {
        return field("ioState").getByte(channel) & 0xff;
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = AbstractIoUringChannel.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
