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
package io.netty5.testsuite.transport.socket;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.Unpooled;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.DefaultBufferAllocators;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.SingleThreadEventLoop;
import io.netty5.channel.nio.NioHandler;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.channel.socket.nio.NioServerSocketChannel;
import io.netty5.handler.codec.ByteToMessageDecoder;
import io.netty5.handler.codec.ByteToMessageDecoderForBuffer;
import io.netty5.util.concurrent.DefaultThreadFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.net.SocketException;
import java.nio.channels.NotYetConnectedException;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

public class SocketChannelNotYetConnectedTest extends AbstractClientSocketTest {
    @Test
    @Timeout(30)
    public void testShutdownNotYetConnected(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testShutdownNotYetConnected);
    }

    public void testShutdownNotYetConnected(Bootstrap cb) throws Throwable {
        SocketChannel ch = (SocketChannel) cb.handler(new ChannelHandler() { })
                .bind(newSocketAddress()).get();
        try {
            try {
                ch.shutdownInput().syncUninterruptibly();
                fail();
            } catch (Throwable cause) {
                assertThat(cause).hasCauseInstanceOf(NotYetConnectedException.class);
            }

            try {
                ch.shutdownOutput().syncUninterruptibly();
                fail();
            } catch (Throwable cause) {
                assertThat(cause).hasCauseInstanceOf(NotYetConnectedException.class);
            }
        } finally {
            ch.close().syncUninterruptibly();
        }
    }

    @Test
    @Timeout(30)
    public void readMustBePendingUntilChannelIsActiveByteBuf(TestInfo info) throws Throwable {
        run(info, new Runner<Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap) throws Throwable {
                SingleThreadEventLoop group = new SingleThreadEventLoop(
                        new DefaultThreadFactory(getClass()), NioHandler.newFactory().newHandler());
                ServerBootstrap sb = new ServerBootstrap().group(group);
                Channel serverChannel = sb.childHandler(new ChannelHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                        ctx.writeAndFlush(Unpooled.copyInt(42));
                    }
                }).channel(NioServerSocketChannel.class).bind(0).get();

                final CountDownLatch readLatch = new CountDownLatch(1);
                bootstrap.handler(new ByteToMessageDecoder() {
                    @Override
                    public void handlerAdded0(ChannelHandlerContext ctx) throws Exception {
                        assertFalse(ctx.channel().isActive());
                        ctx.read();
                    }

                    @Override
                    protected void decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
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

    @Test
    @Timeout(30)
    public void readMustBePendingUntilChannelIsActive(TestInfo info) throws Throwable {
        run(info, new Runner<Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap) throws Throwable {
                bootstrap.option(ChannelOption.RCVBUF_ALLOCATOR_USE_BUFFER, true);
                SingleThreadEventLoop group = new SingleThreadEventLoop(
                        new DefaultThreadFactory(getClass()), NioHandler.newFactory().newHandler());
                ServerBootstrap sb = new ServerBootstrap().group(group);
                Channel serverChannel = sb.childHandler(new ChannelHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                        ctx.writeAndFlush(DefaultBufferAllocators.preferredAllocator().allocate(4).writeInt(42));
                    }
                }).channel(NioServerSocketChannel.class).bind(0).get();

                final CountDownLatch readLatch = new CountDownLatch(1);
                bootstrap.handler(new ByteToMessageDecoderForBuffer() {
                    @Override
                    public void handlerAdded0(ChannelHandlerContext ctx) throws Exception {
                        assertFalse(ctx.channel().isActive());
                        ctx.read();
                    }

                    @Override
                    protected void decode(ChannelHandlerContext ctx, Buffer in) throws Exception {
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
