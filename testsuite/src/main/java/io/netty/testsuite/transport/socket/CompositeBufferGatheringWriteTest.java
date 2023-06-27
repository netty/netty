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
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CompositeBufferGatheringWriteTest extends AbstractSocketTest {
    private static final int EXPECTED_BYTES = 20;

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void testSingleCompositeBufferWrite(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testSingleCompositeBufferWrite(serverBootstrap, bootstrap);
            }
        });
    }

    public void testSingleCompositeBufferWrite(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Object> clientReceived = new AtomicReference<Object>();
            sb.childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) throws Exception {
                            ctx.writeAndFlush(newCompositeBuffer(ctx.alloc()))
                                    .addListener(ChannelFutureListener.CLOSE);
                        }
                    });
                }
            });
            cb.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        private ByteBuf aggregator;
                        @Override
                        public void handlerAdded(ChannelHandlerContext ctx) {
                            aggregator = ctx.alloc().buffer(EXPECTED_BYTES);
                        }

                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            try {
                                if (msg instanceof ByteBuf) {
                                    aggregator.writeBytes((ByteBuf) msg);
                                }
                            } finally {
                                ReferenceCountUtil.release(msg);
                            }
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                            // IOException is fine as it will also close the channel and may just be a connection reset.
                            if (!(cause instanceof IOException)) {
                                clientReceived.set(cause);
                                latch.countDown();
                            }
                        }

                        @Override
                        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                            if (clientReceived.compareAndSet(null, aggregator)) {
                                try {
                                    assertEquals(EXPECTED_BYTES, aggregator.readableBytes());
                                } catch (Throwable cause) {
                                    aggregator.release();
                                    aggregator = null;
                                    clientReceived.set(cause);
                                } finally {
                                    latch.countDown();
                                }
                            }
                        }
                    });
                }
            });

            serverChannel = sb.bind().syncUninterruptibly().channel();
            clientChannel = cb.connect(serverChannel.localAddress()).syncUninterruptibly().channel();

            ByteBuf expected = newCompositeBuffer(clientChannel.alloc());
            latch.await();
            Object received = clientReceived.get();
            if (received instanceof ByteBuf) {
                ByteBuf actual = (ByteBuf) received;
                assertEquals(expected, actual);
                expected.release();
                actual.release();
            } else {
                expected.release();
                throw (Throwable) received;
            }
        } finally {
            if (clientChannel != null) {
                clientChannel.close().sync();
            }
            if (serverChannel != null) {
                serverChannel.close().sync();
            }
        }
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void testCompositeBufferPartialWriteDoesNotCorruptData(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testCompositeBufferPartialWriteDoesNotCorruptData(serverBootstrap, bootstrap);
            }
        });
    }

    protected void compositeBufferPartialWriteDoesNotCorruptDataInitServerConfig(ChannelConfig config,
                                                                                 int soSndBuf) {
    }

    public void testCompositeBufferPartialWriteDoesNotCorruptData(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        // The scenario is the following:
        // Limit SO_SNDBUF so that a single buffer can be written, and part of a CompositeByteBuf at the same time.
        // We then write the single buffer, the CompositeByteBuf, and another single buffer and verify the data is not
        // corrupted when we read it on the other side.
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            Random r = new Random();
            final int soSndBuf = 1024;
            ByteBufAllocator alloc = ByteBufAllocator.DEFAULT;
            final ByteBuf expectedContent = alloc.buffer(soSndBuf * 2);
            expectedContent.writeBytes(newRandomBytes(expectedContent.writableBytes(), r));
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Object> clientReceived = new AtomicReference<Object>();
            sb.childOption(ChannelOption.SO_SNDBUF, soSndBuf)
              .childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) throws Exception {
                            compositeBufferPartialWriteDoesNotCorruptDataInitServerConfig(ctx.channel().config(),
                                    soSndBuf);
                            // First single write
                            int offset = soSndBuf - 100;
                            ctx.write(expectedContent.retainedSlice(expectedContent.readerIndex(), offset));

                            // Build and write CompositeByteBuf
                            CompositeByteBuf compositeByteBuf = ctx.alloc().compositeBuffer();
                            compositeByteBuf.addComponent(true,
                                    expectedContent.retainedSlice(expectedContent.readerIndex() + offset, 50));
                            offset += 50;
                            compositeByteBuf.addComponent(true,
                                    expectedContent.retainedSlice(expectedContent.readerIndex() + offset, 200));
                            offset += 200;
                            ctx.write(compositeByteBuf);

                            // Write a single buffer that is smaller than the second component of the CompositeByteBuf
                            // above but small enough to fit in the remaining space allowed by the soSndBuf amount.
                            ctx.write(expectedContent.retainedSlice(expectedContent.readerIndex() + offset, 50));
                            offset += 50;

                            // Write the remainder of the content
                            ctx.writeAndFlush(expectedContent.retainedSlice(expectedContent.readerIndex() + offset,
                                    expectedContent.readableBytes() - expectedContent.readerIndex() - offset))
                                    .addListener(ChannelFutureListener.CLOSE);
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                            // IOException is fine as it will also close the channel and may just be a connection reset.
                            if (!(cause instanceof IOException)) {
                                clientReceived.set(cause);
                                latch.countDown();
                            }
                        }
                    });
                }
            });
            cb.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        private ByteBuf aggregator;
                        @Override
                        public void handlerAdded(ChannelHandlerContext ctx) {
                            aggregator = ctx.alloc().buffer(expectedContent.readableBytes());
                        }

                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            try {
                                if (msg instanceof ByteBuf) {
                                    aggregator.writeBytes((ByteBuf) msg);
                                }
                            } finally {
                                ReferenceCountUtil.release(msg);
                            }
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                            // IOException is fine as it will also close the channel and may just be a connection reset.
                            if (!(cause instanceof IOException)) {
                                clientReceived.set(cause);
                                latch.countDown();
                            }
                        }

                        @Override
                        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                            if (clientReceived.compareAndSet(null, aggregator)) {
                                try {
                                    assertEquals(expectedContent.readableBytes(), aggregator.readableBytes());
                                } catch (Throwable cause) {
                                    aggregator.release();
                                    aggregator = null;
                                    clientReceived.set(cause);
                                } finally {
                                    latch.countDown();
                                }
                            }
                        }
                    });
                }
            });

            serverChannel = sb.bind().syncUninterruptibly().channel();
            clientChannel = cb.connect(serverChannel.localAddress()).syncUninterruptibly().channel();

            latch.await();
            Object received = clientReceived.get();
            if (received instanceof ByteBuf) {
                ByteBuf actual = (ByteBuf) received;
                assertEquals(expectedContent, actual);
                expectedContent.release();
                actual.release();
            } else {
                expectedContent.release();
                throw (Throwable) received;
            }
        } finally {
            if (clientChannel != null) {
                clientChannel.close().sync();
            }
            if (serverChannel != null) {
                serverChannel.close().sync();
            }
        }
    }

    private static ByteBuf newCompositeBuffer(ByteBufAllocator alloc) {
        CompositeByteBuf compositeByteBuf = alloc.compositeBuffer();
        compositeByteBuf.addComponent(true, alloc.directBuffer(4).writeInt(100));
        compositeByteBuf.addComponent(true, alloc.directBuffer(8).writeLong(123));
        compositeByteBuf.addComponent(true, alloc.directBuffer(8).writeLong(456));
        assertEquals(EXPECTED_BYTES, compositeByteBuf.readableBytes());
        return compositeByteBuf;
    }

    private static byte[] newRandomBytes(int size, Random r) {
        byte[] bytes = new byte[size];
        r.nextBytes(bytes);
        return bytes;
    }
}
