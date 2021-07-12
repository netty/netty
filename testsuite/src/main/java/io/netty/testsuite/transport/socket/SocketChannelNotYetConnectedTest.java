/*
 * Copyright 2016 The Netty Project
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
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.net.SocketException;
import java.nio.channels.NotYetConnectedException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

public class SocketChannelNotYetConnectedTest extends AbstractClientSocketTest {
    @Test
    @Timeout(30)
    public void testShutdownNotYetConnected(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap) throws Throwable {
                testShutdownNotYetConnected(bootstrap);
            }
        });
    }

    public void testShutdownNotYetConnected(Bootstrap cb) throws Throwable {
        SocketChannel ch = (SocketChannel) cb.handler(new ChannelInboundHandlerAdapter())
                .bind(newSocketAddress()).syncUninterruptibly().channel();
        try {
            try {
                ch.shutdownInput().syncUninterruptibly();
                fail();
            } catch (Throwable cause) {
                checkThrowable(cause);
            }

            try {
                ch.shutdownOutput().syncUninterruptibly();
                fail();
            } catch (Throwable cause) {
                checkThrowable(cause);
            }
        } finally {
            ch.close().syncUninterruptibly();
        }
    }

    private static void checkThrowable(Throwable cause) throws Throwable {
        // Depending on OIO / NIO both are ok
        if (!(cause instanceof NotYetConnectedException) && !(cause instanceof SocketException)) {
            throw cause;
        }
    }

    @Test
    @Timeout(30)
    public void readMustBePendingUntilChannelIsActive(TestInfo info) throws Throwable {
        run(info, new Runner<Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap) throws Throwable {
                NioEventLoopGroup group = new NioEventLoopGroup(1);
                ServerBootstrap sb = new ServerBootstrap().group(group);
                Channel serverChannel = sb.childHandler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                        ctx.writeAndFlush(Unpooled.copyInt(42));
                    }
                }).channel(NioServerSocketChannel.class).bind(0).sync().channel();

                final CountDownLatch readLatch = new CountDownLatch(1);
                bootstrap.handler(new ByteToMessageDecoder() {
                    @Override
                    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                        assertFalse(ctx.channel().isActive());
                        ctx.read();
                    }

                    @Override
                    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
                        assertThat(in.readableBytes()).isLessThanOrEqualTo(Integer.BYTES);
                        if (in.readableBytes() == Integer.BYTES) {
                            assertThat(in.readInt()).isEqualTo(42);
                            readLatch.countDown();
                        }
                    }
                });
                bootstrap.connect(serverChannel.localAddress()).sync();

                readLatch.await();
                group.shutdownGracefully().await();
            }
        });
    }
}
