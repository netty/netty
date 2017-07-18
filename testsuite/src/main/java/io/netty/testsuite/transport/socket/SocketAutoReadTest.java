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
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.UncheckedBooleanSupplier;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SocketAutoReadTest extends AbstractSocketTest {
    @Test
    public void testAutoReadOffDuringReadOnlyReadsOneTime() throws Throwable {
        run();
    }

    public void testAutoReadOffDuringReadOnlyReadsOneTime(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testAutoReadOffDuringReadOnlyReadsOneTime(true, sb, cb);
        testAutoReadOffDuringReadOnlyReadsOneTime(false, sb, cb);
    }

    private static void testAutoReadOffDuringReadOnlyReadsOneTime(boolean readOutsideEventLoopThread,
                                                           ServerBootstrap sb, Bootstrap cb) throws Throwable {
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            AutoReadInitializer serverInitializer = new AutoReadInitializer(!readOutsideEventLoopThread);
            AutoReadInitializer clientInitializer = new AutoReadInitializer(!readOutsideEventLoopThread);
            sb.option(ChannelOption.SO_BACKLOG, 1024)
                    .option(ChannelOption.AUTO_READ, true)
                    .childOption(ChannelOption.AUTO_READ, true)
                    // We want to ensure that we attempt multiple individual read operations per read loop so we can
                    // test the auto read feature being turned off when data is first read.
                    .childOption(ChannelOption.RCVBUF_ALLOCATOR, new TestRecvByteBufAllocator())
                    .childHandler(serverInitializer);

            serverChannel = sb.bind().syncUninterruptibly().channel();

            cb.option(ChannelOption.AUTO_READ, true)
                    // We want to ensure that we attempt multiple individual read operations per read loop so we can
                    // test the auto read feature being turned off when data is first read.
                    .option(ChannelOption.RCVBUF_ALLOCATOR, new TestRecvByteBufAllocator())
                    .handler(clientInitializer);

            clientChannel = cb.connect(serverChannel.localAddress()).syncUninterruptibly().channel();

            // 3 bytes means 3 independent reads for TestRecvByteBufAllocator
            clientChannel.writeAndFlush(Unpooled.wrappedBuffer(new byte[3]));
            serverInitializer.autoReadHandler.assertSingleRead();

            // 3 bytes means 3 independent reads for TestRecvByteBufAllocator
            serverInitializer.channel.writeAndFlush(Unpooled.wrappedBuffer(new byte[3]));
            clientInitializer.autoReadHandler.assertSingleRead();

            if (readOutsideEventLoopThread) {
                serverInitializer.channel.read();
            }
            serverInitializer.autoReadHandler.assertSingleReadSecondTry();

            if (readOutsideEventLoopThread) {
                clientChannel.read();
            }
            clientInitializer.autoReadHandler.assertSingleReadSecondTry();
        } finally {
            if (clientChannel != null) {
                clientChannel.close().sync();
            }
            if (serverChannel != null) {
                serverChannel.close().sync();
            }
        }
    }

    private static class AutoReadInitializer extends ChannelInitializer<Channel> {
        final AutoReadHandler autoReadHandler;
        volatile Channel channel;

        AutoReadInitializer(boolean readInEventLoop) {
            autoReadHandler = new AutoReadHandler(readInEventLoop);
        }

        @Override
        protected void initChannel(Channel ch) throws Exception {
            channel = ch;
            ch.pipeline().addLast(autoReadHandler);
        }
    }

    private static final class AutoReadHandler extends ChannelInboundHandlerAdapter {
        private final AtomicInteger count = new AtomicInteger();
        private final CountDownLatch latch = new CountDownLatch(1);
        private final CountDownLatch latch2;
        private final boolean callRead;

        AutoReadHandler(boolean callRead) {
            this.callRead = callRead;
            latch2 = new CountDownLatch(callRead ? 3 : 2);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ReferenceCountUtil.release(msg);
            if (count.incrementAndGet() == 1) {
                ctx.channel().config().setAutoRead(false);
            }
            if (callRead) {
                // Test calling read in the EventLoop thread to ensure a read is eventually done.
                ctx.read();
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            latch.countDown();
            latch2.countDown();
        }

        void assertSingleRead() throws InterruptedException {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertTrue(count.get() > 0);
        }

        void assertSingleReadSecondTry() throws InterruptedException {
            assertTrue(latch2.await(5, TimeUnit.SECONDS));
            assertEquals(callRead ? 3 : 2, count.get());
        }
    }

    /**
     * Designed to keep reading as long as autoread is enabled.
     */
    private static final class TestRecvByteBufAllocator implements RecvByteBufAllocator {
        @Override
        public ExtendedHandle newHandle() {
            return new ExtendedHandle() {
                private ChannelConfig config;
                private int attemptedBytesRead;
                private int lastBytesRead;
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
                    this.config = config;
                }

                @Override
                public void incMessagesRead(int numMessages) {
                    // No need to track the number of messages read because it is not used.
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
                    return config.isAutoRead();
                }

                @Override
                public boolean continueReading(UncheckedBooleanSupplier maybeMoreDataSupplier) {
                    return config.isAutoRead();
                }

                @Override
                public void readComplete() {
                    // Nothing needs to be done or adjusted after each read cycle is completed.
                }
            };
        }
    }
}
