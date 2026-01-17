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
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DatagramConnectedWriteExceptionTest extends AbstractClientSocketTest {

    @Override
    protected List<TestsuitePermutation.BootstrapFactory<Bootstrap>> newFactories() {
        return SocketTestPermutation.INSTANCE.datagramSocket();
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    @DisabledOnOs(OS.WINDOWS)
    public void testWriteThrowsPortUnreachableException(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap) throws Throwable {
                testWriteExceptionAfterServerStop(bootstrap);
            }
        });
    }

    protected void testWriteExceptionAfterServerStop(Bootstrap clientBootstrap) throws Throwable {
        final CountDownLatch serverReceivedLatch = new CountDownLatch(1);
        Bootstrap serverBootstrap = clientBootstrap.clone()
                .option(ChannelOption.SO_BROADCAST, false)
                .handler(new SimpleChannelInboundHandler<DatagramPacket>() {

                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
                        serverReceivedLatch.countDown();
                    }
                });

        Channel serverChannel = serverBootstrap.bind(new InetSocketAddress(NetUtil.LOCALHOST, 0)).sync().channel();
        InetSocketAddress serverAddress = (InetSocketAddress) serverChannel.localAddress();

        clientBootstrap.option(ChannelOption.AUTO_READ, false)
                .handler(new SimpleChannelInboundHandler<DatagramPacket>() {

                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
                        // no-op
                    }
                });

        Channel clientChannel = clientBootstrap.connect(serverAddress).sync().channel();

        final CountDownLatch clientFirstSendLatch = new CountDownLatch(1);
        try {
            ByteBuf firstMessage = Unpooled.wrappedBuffer("First message".getBytes(CharsetUtil.UTF_8));
            clientChannel.writeAndFlush(firstMessage)
                    .addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) {
                            if (future.isSuccess()) {
                                clientFirstSendLatch.countDown();
                            }
                        }
                    });

            assertTrue(serverReceivedLatch.await(5, TimeUnit.SECONDS), "Server should receive first message");
            assertTrue(clientFirstSendLatch.await(5, TimeUnit.SECONDS), "Client should send first message");

            serverChannel.close().sync();

            final AtomicReference<Throwable> writeException = new AtomicReference<Throwable>();
            final CountDownLatch writesCompleteLatch = new CountDownLatch(10);

            for (int i = 0; i < 10; i++) {
                ByteBuf message = Unpooled.wrappedBuffer(("Message " + i).getBytes(CharsetUtil.UTF_8));
                clientChannel.writeAndFlush(message)
                        .addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) {
                                if (!future.isSuccess()) {
                                    writeException.compareAndSet(null, future.cause());
                                }
                                writesCompleteLatch.countDown();
                            }
                        });
                Thread.sleep(50);
            }

            assertTrue(writesCompleteLatch.await(5, TimeUnit.SECONDS), "All writes should complete");

            assertNotNull(writeException.get(), "Should have captured a write exception");

            assertInstanceOf(PortUnreachableException.class, writeException.get(), "Expected " +
                    "PortUnreachableException but got: " + writeException.get().getClass().getName());
        } finally {
            if (clientChannel != null) {
                clientChannel.close().sync();
            }
        }
    }
}
