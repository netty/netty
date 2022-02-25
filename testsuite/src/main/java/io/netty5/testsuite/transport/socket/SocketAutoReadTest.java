/*
 * Copyright 2014 The Netty Project
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
import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.Unpooled;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.DefaultBufferAllocators;
import io.netty5.buffer.api.Resource;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelConfig;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.RecvBufferAllocator;
import io.netty5.util.ReferenceCountUtil;
import io.netty5.util.UncheckedBooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SocketAutoReadTest extends AbstractSocketTest {
    @Test
    public void testAutoReadOffDuringReadOnlyReadsOneTimeByteBuf(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testAutoReadOffDuringReadOnlyReadsOneTimeByteBuf);
    }

    public void testAutoReadOffDuringReadOnlyReadsOneTimeByteBuf(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testAutoReadOffDuringReadOnlyReadsOneTimeByteBuf(true, sb, cb);
        testAutoReadOffDuringReadOnlyReadsOneTimeByteBuf(false, sb, cb);
    }

    private static void testAutoReadOffDuringReadOnlyReadsOneTimeByteBuf(boolean readOutsideEventLoopThread,
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
                    .childOption(ChannelOption.RCVBUF_ALLOCATOR, new TestRecvBufferAllocator())
                    .childHandler(serverInitializer);

            serverChannel = sb.bind().get();

            cb.option(ChannelOption.AUTO_READ, true)
                    // We want to ensure that we attempt multiple individual read operations per read loop so we can
                    // test the auto read feature being turned off when data is first read.
                    .option(ChannelOption.RCVBUF_ALLOCATOR, new TestRecvBufferAllocator())
                    .handler(clientInitializer);

            clientChannel = cb.connect(serverChannel.localAddress()).get();

            // 3 bytes means 3 independent reads for TestRecvBufferAllocator
            clientChannel.writeAndFlush(Unpooled.wrappedBuffer(new byte[3]));
            serverInitializer.autoReadHandler.assertSingleRead();

            // 3 bytes means 3 independent reads for TestRecvBufferAllocator
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

    @Test
    public void testAutoReadOffDuringReadOnlyReadsOne(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testAutoReadOffDuringReadOnlyReadsOne);
    }

    public void testAutoReadOffDuringReadOnlyReadsOne(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        enableNewBufferAPI(sb, cb);
        testAutoReadOffDuringReadOnlyReadsOne(true, sb, cb);
        testAutoReadOffDuringReadOnlyReadsOne(false, sb, cb);
    }

    private static void testAutoReadOffDuringReadOnlyReadsOne(boolean readOutsideEventLoopThread,
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
                    .childOption(ChannelOption.RCVBUF_ALLOCATOR, new TestRecvBufferAllocator())
                    .childHandler(serverInitializer);

            serverChannel = sb.bind().get();

            cb.option(ChannelOption.AUTO_READ, true)
                    // We want to ensure that we attempt multiple individual read operations per read loop so we can
                    // test the auto read feature being turned off when data is first read.
                    .option(ChannelOption.RCVBUF_ALLOCATOR, new TestRecvBufferAllocator())
                    .handler(clientInitializer);

            clientChannel = cb.connect(serverChannel.localAddress()).get();
            BufferAllocator alloc = DefaultBufferAllocators.onHeapAllocator();

            // 3 bytes means 3 independent reads for TestRecvBufferAllocator
            clientChannel.writeAndFlush(alloc.copyOf(new byte[3]));
            serverInitializer.autoReadHandler.assertSingleRead();

            // 3 bytes means 3 independent reads for TestRecvBufferAllocator
            serverInitializer.channel.writeAndFlush(alloc.copyOf(new byte[3]));
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

    private static final class AutoReadHandler implements ChannelHandler {
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
            if (msg instanceof Resource<?>) {
                ((Resource<?>) msg).close();
            } else {
                ReferenceCountUtil.release(msg);
            }
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
    private static final class TestRecvBufferAllocator implements RecvBufferAllocator {
        @Override
        public Handle newHandle() {
            return new Handle() {
                private ChannelConfig config;
                private int attemptedBytesRead;
                private int lastBytesRead;

                @Override
                public ByteBuf allocate(ByteBufAllocator alloc) {
                    return alloc.ioBuffer(guess(), guess());
                }

                @Override
                public Buffer allocate(BufferAllocator alloc) {
                    return alloc.allocate(guess());
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
