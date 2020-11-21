/*
 * Copyright 2020 The Netty Project
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
package io.netty.incubator.codec.quic;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.ConnectTimeoutException;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.channels.AlreadyConnectedException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class QuicChannelConnectTest {

    @Test
    public void testConnectTimeout() throws Throwable {
        // Bind to something so we can use the port to connect too and so can ensure we really timeout.
        DatagramSocket socket = new DatagramSocket();
        ChannelFuture future = null;
        try {
            ChannelStateVerifyHandler verifyHandler = new ChannelStateVerifyHandler();
            future = QuicTestUtils.newChannelBuilder(verifyHandler, null)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10)
                    .connect(QuicConnectionAddress.random((InetSocketAddress) socket.getLocalSocketAddress()));
            Throwable cause = future.await().cause();
            assertThat(cause, CoreMatchers.instanceOf(ConnectTimeoutException.class));
            ChannelFuture closeFuture = future.channel().closeFuture().await();
            assertTrue(closeFuture.isSuccess());
            verifyHandler.assertState();
        } finally {
            socket.close();
            // Close the parent Datagram channel as well.
            QuicTestUtils.closeParent(future);
        }
    }

    @Test
    public void testConnectAlreadyConnected() throws Throwable {
        ChannelActiveVerifyHandler serverQuicChannelHandler = new ChannelActiveVerifyHandler();
        ChannelStateVerifyHandler serverQuicStreamHandler = new ChannelStateVerifyHandler();

        Channel server = QuicTestUtils.newServer(serverQuicChannelHandler, serverQuicStreamHandler);
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        ChannelFuture future = null;
        try {
            ChannelActiveVerifyHandler clientQuicChannelHandler = new ChannelActiveVerifyHandler();
            future = QuicTestUtils.newChannelBuilder(clientQuicChannelHandler, null)
                    .connect(QuicConnectionAddress.random(address));
            assertTrue(future.await().isSuccess());

            // Try to connect again
            ChannelFuture connectFuture = future.channel().connect(QuicConnectionAddress.random(address));
            Throwable cause = connectFuture.await().cause();
            assertThat(cause, CoreMatchers.instanceOf(AlreadyConnectedException.class));
            assertTrue(future.channel().close().await().isSuccess());
            ChannelFuture closeFuture = future.channel().closeFuture().await();
            assertTrue(closeFuture.isSuccess());
            clientQuicChannelHandler.assertState();
        } finally {
            serverQuicChannelHandler.assertState();
            serverQuicStreamHandler.assertState();

            server.close().syncUninterruptibly();
            // Close the parent Datagram channel as well.
            QuicTestUtils.closeParent(future);
        }
    }

    @Test
    public void testConnectWithoutTokenValidation() throws Throwable {
        ChannelActiveVerifyHandler serverQuicChannelHandler = new ChannelActiveVerifyHandler();
        ChannelStateVerifyHandler serverQuicStreamHandler = new ChannelStateVerifyHandler();

        Channel server = QuicTestUtils.newServer(new QuicTokenHandler() {
            // Disable token validation
            @Override
            public boolean writeToken(ByteBuf out, ByteBuf dcid, InetSocketAddress address) {
                return false;
            }

            @Override
            public int validateToken(ByteBuf token, InetSocketAddress address) {
                return 0;
            }

            @Override
            public int maxTokenLength() {
                return 0;
            }
        }, serverQuicChannelHandler, serverQuicStreamHandler);
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        ChannelFuture future = null;
        try {
            ChannelActiveVerifyHandler clientQuicChannelHandler = new ChannelActiveVerifyHandler();
            future = QuicTestUtils.newChannelBuilder(clientQuicChannelHandler, null)
                    .connect(QuicConnectionAddress.random(address));
            assertTrue(future.await().isSuccess());

            assertTrue(future.channel().close().await().isSuccess());
            ChannelFuture closeFuture = future.channel().closeFuture().await();
            assertTrue(closeFuture.isSuccess());
            clientQuicChannelHandler.assertState();
        } finally {
            serverQuicChannelHandler.assertState();
            serverQuicStreamHandler.assertState();

            server.close().syncUninterruptibly();
            // Close the parent Datagram channel as well.
            QuicTestUtils.closeParent(future);
        }
    }

    private static final class ChannelStateVerifyHandler extends ChannelInboundHandlerAdapter {
        private volatile Throwable cause;
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.fireChannelActive();
            fail();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            ctx.fireChannelInactive();
            fail();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            this.cause = cause;
        }

        void assertState() throws Throwable {
            if (cause != null) {
                throw cause;
            }
        }
    }

    private static final class ChannelActiveVerifyHandler extends ChannelInboundHandlerAdapter {
        private final BlockingQueue<Integer> states = new LinkedBlockingQueue<>();
        @Override
        public void channelRegistered(ChannelHandlerContext ctx) {
            ctx.fireChannelRegistered();
            states.add(0);
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) {
            ctx.fireChannelUnregistered();
            states.add(3);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.fireChannelActive();
            states.add(1);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            ctx.fireChannelInactive();
            states.add(2);
        }

        void assertState() throws Throwable {
            // Check that we receive the different events in the correct order.
            for (long i = 0; i < 4; i++) {
                assertEquals(i, (int) states.take());
            }
            assertNull(states.poll());
        }
    }
}
