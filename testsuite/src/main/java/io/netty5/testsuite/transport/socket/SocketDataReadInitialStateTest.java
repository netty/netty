/*
 * Copyright 2018 The Netty Project
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
import io.netty5.buffer.api.Buffer;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.SimpleChannelInboundHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty5.channel.ChannelOption.AUTO_READ;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SocketDataReadInitialStateTest extends AbstractSocketTest {
    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void testAutoReadOffNoDataReadUntilReadCalledByteBuf(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testAutoReadOffNoDataReadUntilReadCalledByteBuf);
    }

    public void testAutoReadOffNoDataReadUntilReadCalledByteBuf(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        Channel serverChannel = null;
        Channel clientChannel = null;
        final int sleepMs = 100;
        try {
            sb.option(AUTO_READ, false);
            sb.childOption(AUTO_READ, false);
            cb.option(AUTO_READ, false);
            final CountDownLatch serverReadyLatch = new CountDownLatch(1);
            final CountDownLatch acceptorReadLatch = new CountDownLatch(1);
            final CountDownLatch serverReadLatch = new CountDownLatch(1);
            final CountDownLatch clientReadLatch = new CountDownLatch(1);
            final AtomicReference<Channel> serverConnectedChannelRef = new AtomicReference<>();

            sb.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new ChannelHandler() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            acceptorReadLatch.countDown();
                            ctx.fireChannelRead(msg);
                        }
                    });
                }
            });

            sb.childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    serverConnectedChannelRef.set(ch);
                    ch.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                        @Override
                        protected void messageReceived(ChannelHandlerContext ctx, ByteBuf msg) {
                            ctx.writeAndFlush(msg.retainedDuplicate());
                            serverReadLatch.countDown();
                        }
                    });
                    serverReadyLatch.countDown();
                }
            });

            cb.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new SimpleChannelInboundHandler<Object>() {
                        @Override
                        protected void messageReceived(ChannelHandlerContext ctx, Object msg) {
                            clientReadLatch.countDown();
                        }
                    });
                }
            });

            serverChannel = sb.bind().get();
            clientChannel = cb.connect(serverChannel.localAddress()).get();
            clientChannel.writeAndFlush(clientChannel.alloc().buffer().writeZero(1)).syncUninterruptibly();

            // The acceptor shouldn't read any data until we call read() below, but give it some time to see if it will.
            Thread.sleep(sleepMs);
            assertEquals(1, acceptorReadLatch.getCount());
            serverChannel.read();
            serverReadyLatch.await();

            Channel serverConnectedChannel = serverConnectedChannelRef.get();
            assertNotNull(serverConnectedChannel);

            // Allow some amount of time for the server peer to receive the message (which isn't expected to happen
            // until we call read() below).
            Thread.sleep(sleepMs);
            assertEquals(1, serverReadLatch.getCount());
            serverConnectedChannel.read();
            serverReadLatch.await();

            // Allow some amount of time for the client to read the echo.
            Thread.sleep(sleepMs);
            assertEquals(1, clientReadLatch.getCount());
            clientChannel.read();
            clientReadLatch.await();
        } finally {
            if (serverChannel != null) {
                serverChannel.close().sync();
            }
            if (clientChannel != null) {
                clientChannel.close().sync();
            }
        }
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void testAutoReadOffNoDataReadUntilReadCalled(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testAutoReadOffNoDataReadUntilReadCalled);
    }

    public void testAutoReadOffNoDataReadUntilReadCalled(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        enableNewBufferAPI(sb, cb);
        Channel serverChannel = null;
        Channel clientChannel = null;
        final int sleepMs = 100;
        try {
            sb.option(AUTO_READ, false);
            sb.childOption(AUTO_READ, false);
            cb.option(AUTO_READ, false);
            final CountDownLatch serverReadyLatch = new CountDownLatch(1);
            final CountDownLatch acceptorReadLatch = new CountDownLatch(1);
            final CountDownLatch serverReadLatch = new CountDownLatch(1);
            final CountDownLatch clientReadLatch = new CountDownLatch(1);
            final AtomicReference<Channel> serverConnectedChannelRef = new AtomicReference<>();

            sb.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new ChannelHandler() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            acceptorReadLatch.countDown();
                            ctx.fireChannelRead(msg);
                        }
                    });
                }
            });

            sb.childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    serverConnectedChannelRef.set(ch);
                    ch.pipeline().addLast(new SimpleChannelInboundHandler<Buffer>() {
                        @Override
                        protected void messageReceived(ChannelHandlerContext ctx, Buffer msg) {
                            ctx.writeAndFlush(msg.split());
                            serverReadLatch.countDown();
                        }
                    });
                    serverReadyLatch.countDown();
                }
            });

            cb.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new SimpleChannelInboundHandler<Object>() {
                        @Override
                        protected void messageReceived(ChannelHandlerContext ctx, Object msg) {
                            clientReadLatch.countDown();
                        }
                    });
                }
            });

            serverChannel = sb.bind().get();
            clientChannel = cb.connect(serverChannel.localAddress()).get();
            clientChannel.writeAndFlush(clientChannel.bufferAllocator().copyOf(new byte[] {0})).syncUninterruptibly();

            // The acceptor shouldn't read any data until we call read() below, but give it some time to see if it will.
            Thread.sleep(sleepMs);
            assertEquals(1, acceptorReadLatch.getCount());
            serverChannel.read();
            serverReadyLatch.await();

            Channel serverConnectedChannel = serverConnectedChannelRef.get();
            assertNotNull(serverConnectedChannel);

            // Allow some amount of time for the server peer to receive the message (which isn't expected to happen
            // until we call read() below).
            Thread.sleep(sleepMs);
            assertEquals(1, serverReadLatch.getCount());
            serverConnectedChannel.read();
            serverReadLatch.await();

            // Allow some amount of time for the client to read the echo.
            Thread.sleep(sleepMs);
            assertEquals(1, clientReadLatch.getCount());
            clientChannel.read();
            clientReadLatch.await();
        } finally {
            if (serverChannel != null) {
                serverChannel.close().sync();
            }
            if (clientChannel != null) {
                clientChannel.close().sync();
            }
        }
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void testAutoReadOnDataReadImmediatelyByteBuf(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testAutoReadOnDataReadImmediatelyByteBuf);
    }

    public void testAutoReadOnDataReadImmediatelyByteBuf(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            sb.option(AUTO_READ, true);
            sb.childOption(AUTO_READ, true);
            cb.option(AUTO_READ, true);
            final CountDownLatch serverReadLatch = new CountDownLatch(1);
            final CountDownLatch clientReadLatch = new CountDownLatch(1);

            sb.childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                        @Override
                        protected void messageReceived(ChannelHandlerContext ctx, ByteBuf msg) {
                            ctx.writeAndFlush(msg.retainedDuplicate());
                            serverReadLatch.countDown();
                        }
                    });
                }
            });

            cb.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new SimpleChannelInboundHandler<Object>() {
                        @Override
                        protected void messageReceived(ChannelHandlerContext ctx, Object msg) {
                            clientReadLatch.countDown();
                        }
                    });
                }
            });

            serverChannel = sb.bind().get();
            clientChannel = cb.connect(serverChannel.localAddress()).get();
            clientChannel.writeAndFlush(clientChannel.alloc().buffer().writeZero(1)).syncUninterruptibly();
            serverReadLatch.await();
            clientReadLatch.await();
        } finally {
            if (serverChannel != null) {
                serverChannel.close().sync();
            }
            if (clientChannel != null) {
                clientChannel.close().sync();
            }
        }
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void testAutoReadOnDataReadImmediately(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testAutoReadOnDataReadImmediately);
    }

    public void testAutoReadOnDataReadImmediately(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        enableNewBufferAPI(sb, cb);
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            sb.option(AUTO_READ, true);
            sb.childOption(AUTO_READ, true);
            cb.option(AUTO_READ, true);
            final CountDownLatch serverReadLatch = new CountDownLatch(1);
            final CountDownLatch clientReadLatch = new CountDownLatch(1);

            sb.childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new SimpleChannelInboundHandler<Buffer>() {
                        @Override
                        protected void messageReceived(ChannelHandlerContext ctx, Buffer msg) {
                            ctx.writeAndFlush(msg.split());
                            serverReadLatch.countDown();
                        }
                    });
                }
            });

            cb.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new SimpleChannelInboundHandler<Object>() {
                        @Override
                        protected void messageReceived(ChannelHandlerContext ctx, Object msg) {
                            clientReadLatch.countDown();
                        }
                    });
                }
            });

            serverChannel = sb.bind().get();
            clientChannel = cb.connect(serverChannel.localAddress()).get();
            clientChannel.writeAndFlush(clientChannel.bufferAllocator().copyOf(new byte[] {0})).syncUninterruptibly();
            serverReadLatch.await();
            clientReadLatch.await();
        } finally {
            if (serverChannel != null) {
                serverChannel.close().sync();
            }
            if (clientChannel != null) {
                clientChannel.close().sync();
            }
        }
    }
}
