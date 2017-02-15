/*
 * Copyright 2017 The Netty Project
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
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.DuplexChannel;
import io.netty.util.UncheckedBooleanSupplier;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertTrue;

public class SocketHalfClosedTest extends AbstractSocketTest {
    @Test
    public void testAllDataReadAfterHalfClosure() throws Throwable {
        run();
    }

    public void testAllDataReadAfterHalfClosure(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testAllDataReadAfterHalfClosure(true, sb, cb);
        testAllDataReadAfterHalfClosure(false, sb, cb);
    }

    public void testAllDataReadAfterHalfClosure(final boolean autoRead,
                                                ServerBootstrap sb, Bootstrap cb) throws Throwable {
        final int totalServerBytesWritten = 1024 * 16;
        final int numReadsPerReadLoop = 2;
        final CountDownLatch serverInitializedLatch = new CountDownLatch(1);
        final CountDownLatch clientReadAllDataLatch = new CountDownLatch(1);
        final CountDownLatch clientHalfClosedLatch = new CountDownLatch(1);
        final AtomicInteger clientReadCompletes = new AtomicInteger();
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            cb.option(ChannelOption.ALLOW_HALF_CLOSURE, true)
              .option(ChannelOption.AUTO_READ, autoRead)
              .option(ChannelOption.RCVBUF_ALLOCATOR, new TestNumReadsRecvByteBufAllocator(numReadsPerReadLoop));

            sb.childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) throws Exception {
                            ByteBuf buf = ctx.alloc().buffer(totalServerBytesWritten);
                            buf.writerIndex(buf.capacity());
                            ctx.writeAndFlush(buf).addListener(new ChannelFutureListener() {
                                @Override
                                public void operationComplete(ChannelFuture future) throws Exception {
                                    ((DuplexChannel) future.channel()).shutdownOutput();
                                }
                            });
                            serverInitializedLatch.countDown();
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            ctx.close();
                        }
                    });
                }
            });

            cb.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        private int bytesRead;

                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            ByteBuf buf = (ByteBuf) msg;
                            bytesRead += buf.readableBytes();
                            buf.release();
                        }

                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                            if (evt == ChannelInputShutdownEvent.INSTANCE) {
                                clientHalfClosedLatch.countDown();
                            } else if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
                                ctx.close();
                            }
                        }

                        @Override
                        public void channelReadComplete(ChannelHandlerContext ctx) {
                            clientReadCompletes.incrementAndGet();
                            if (bytesRead == totalServerBytesWritten) {
                                clientReadAllDataLatch.countDown();
                            }
                            if (!autoRead) {
                                ctx.read();
                            }
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            ctx.close();
                        }
                    });
                }
            });

            serverChannel = sb.bind().sync().channel();
            clientChannel = cb.connect(serverChannel.localAddress()).sync().channel();
            clientChannel.read();

            serverInitializedLatch.await();
            clientReadAllDataLatch.await();
            clientHalfClosedLatch.await();
            assertTrue("too many read complete events: " + clientReadCompletes.get(),
                    totalServerBytesWritten / numReadsPerReadLoop + 10 > clientReadCompletes.get());
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
    public void testAllDataReadClosure() throws Throwable {
        run();
    }

    public void testAllDataReadClosure(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testAllDataReadClosure(true, false, sb, cb);
        testAllDataReadClosure(true, true, sb, cb);
        testAllDataReadClosure(false, false, sb, cb);
        testAllDataReadClosure(false, true, sb, cb);
    }

    public void testAllDataReadClosure(final boolean autoRead, final boolean allowHalfClosed,
                                       ServerBootstrap sb, Bootstrap cb) throws Throwable {
        final int totalServerBytesWritten = 1024 * 16;
        final int numReadsPerReadLoop = 2;
        final CountDownLatch serverInitializedLatch = new CountDownLatch(1);
        final CountDownLatch clientReadAllDataLatch = new CountDownLatch(1);
        final CountDownLatch clientHalfClosedLatch = new CountDownLatch(1);
        final AtomicInteger clientReadCompletes = new AtomicInteger();
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            cb.option(ChannelOption.ALLOW_HALF_CLOSURE, allowHalfClosed)
                    .option(ChannelOption.AUTO_READ, autoRead)
                    .option(ChannelOption.RCVBUF_ALLOCATOR, new TestNumReadsRecvByteBufAllocator(numReadsPerReadLoop));

            sb.childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) throws Exception {
                            ByteBuf buf = ctx.alloc().buffer(totalServerBytesWritten);
                            buf.writerIndex(buf.capacity());
                            ctx.writeAndFlush(buf).addListener(ChannelFutureListener.CLOSE);
                            serverInitializedLatch.countDown();
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            ctx.close();
                        }
                    });
                }
            });

            cb.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        private int bytesRead;

                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            ByteBuf buf = (ByteBuf) msg;
                            bytesRead += buf.readableBytes();
                            buf.release();
                        }

                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                            if (evt == ChannelInputShutdownEvent.INSTANCE && allowHalfClosed) {
                                clientHalfClosedLatch.countDown();
                            } else if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
                                ctx.close();
                            }
                        }

                        @Override
                        public void channelInactive(ChannelHandlerContext ctx) {
                            if (!allowHalfClosed) {
                                clientHalfClosedLatch.countDown();
                            }
                        }

                        @Override
                        public void channelReadComplete(ChannelHandlerContext ctx) {
                            clientReadCompletes.incrementAndGet();
                            if (bytesRead == totalServerBytesWritten) {
                                clientReadAllDataLatch.countDown();
                            }
                            if (!autoRead) {
                                ctx.read();
                            }
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            ctx.close();
                        }
                    });
                }
            });

            serverChannel = sb.bind().sync().channel();
            clientChannel = cb.connect(serverChannel.localAddress()).sync().channel();
            clientChannel.read();

            serverInitializedLatch.await();
            clientReadAllDataLatch.await();
            clientHalfClosedLatch.await();
            assertTrue("too many read complete events: " + clientReadCompletes.get(),
                    totalServerBytesWritten / numReadsPerReadLoop + 10 > clientReadCompletes.get());
        } finally {
            if (clientChannel != null) {
                clientChannel.close().sync();
            }
            if (serverChannel != null) {
                serverChannel.close().sync();
            }
        }
    }

    /**
     * Designed to read a single byte at a time to control the number of reads done at a fine granularity.
     */
    private static final class TestNumReadsRecvByteBufAllocator implements RecvByteBufAllocator {
        private final int numReads;
        TestNumReadsRecvByteBufAllocator(int numReads) {
            this.numReads = numReads;
        }

        @Override
        public ExtendedHandle newHandle() {
            return new ExtendedHandle() {
                private int attemptedBytesRead;
                private int lastBytesRead;
                private int numMessagesRead;
                @Override
                public ByteBuf allocate(ByteBufAllocator alloc) {
                    return alloc.ioBuffer(guess(), guess());
                }

                @Override
                public int guess() {
                    return 1; // only ever allocate buffers of size 1 to ensure the number of reads is controlled.
                }

                @Override
                public void reset(ChannelConfig config) {
                    numMessagesRead = 0;
                }

                @Override
                public void incMessagesRead(int numMessages) {
                    numMessagesRead += numMessages;
                }

                @Override
                public void lastBytesRead(int bytes) {
                    lastBytesRead = bytes;
                }

                @Override
                public int lastBytesRead() {
                    return lastBytesRead;
                }

                @Override
                public void attemptedBytesRead(int bytes) {
                    attemptedBytesRead = bytes;
                }

                @Override
                public int attemptedBytesRead() {
                    return attemptedBytesRead;
                }

                @Override
                public boolean continueReading() {
                    return numMessagesRead < numReads;
                }

                @Override
                public boolean continueReading(UncheckedBooleanSupplier maybeMoreDataSupplier) {
                    return continueReading() && maybeMoreDataSupplier.get();
                }

                @Override
                public void readComplete() {
                    // Nothing needs to be done or adjusted after each read cycle is completed.
                }
            };
        }
    }
}
