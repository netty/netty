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
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SocketReadPendingTest extends AbstractSocketTest {
    @Test
    @Timeout(value = 60000, unit = TimeUnit.MILLISECONDS)
    public void testReadPendingIsResetAfterEachReadByteBuf(TestInfo testInfo) throws Throwable {
        run(testInfo, (sb, cb) -> testReadPendingIsResetAfterEachRead(sb, cb, false));
    }

    @Test
    @Timeout(value = 60000, unit = TimeUnit.MILLISECONDS)
    public void testReadPendingIsResetAfterEachRead(TestInfo testInfo) throws Throwable {
        run(testInfo, (sb, cb) -> testReadPendingIsResetAfterEachRead(sb, cb, true));
    }

    public void testReadPendingIsResetAfterEachRead(ServerBootstrap sb, Bootstrap cb, boolean newBufferAPI)
            throws Throwable {
        if (newBufferAPI) {
            enableNewBufferAPI(sb, cb);
        }
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            ReadPendingInitializer serverInitializer = new ReadPendingInitializer();
            ReadPendingInitializer clientInitializer = new ReadPendingInitializer();
            sb.option(ChannelOption.SO_BACKLOG, 1024)
              .option(ChannelOption.AUTO_READ, true)
              .childOption(ChannelOption.AUTO_READ, false)
              // We intend to do 2 reads per read loop wakeup
              .childOption(ChannelOption.RCVBUF_ALLOCATOR, new TestNumReadsRecvBufferAllocator(2))
              .childHandler(serverInitializer);

            serverChannel = sb.bind().get();

            cb.option(ChannelOption.AUTO_READ, false)
              // We intend to do 2 reads per read loop wakeup
              .option(ChannelOption.RCVBUF_ALLOCATOR, new TestNumReadsRecvBufferAllocator(2))
              .handler(clientInitializer);
            clientChannel = cb.connect(serverChannel.localAddress()).get();

            // 4 bytes means 2 read loops for TestNumReadsRecvBufferAllocator
            clientChannel.writeAndFlush(newBufferAPI ?
                                                DefaultBufferAllocators.preferredAllocator().copyOf(new byte[4]) :
                                                Unpooled.wrappedBuffer(new byte[4]));

            // 4 bytes means 2 read loops for TestNumReadsRecvBufferAllocator
            assertTrue(serverInitializer.channelInitLatch.await(5, TimeUnit.SECONDS));
            serverInitializer.channel.writeAndFlush(
                    newBufferAPI ? DefaultBufferAllocators.preferredAllocator().copyOf(new byte[4]) :
                            Unpooled.wrappedBuffer(new byte[4]));

            serverInitializer.channel.read();
            serverInitializer.readPendingHandler.assertAllRead();

            clientChannel.read();
            clientInitializer.readPendingHandler.assertAllRead();
        } finally {
            if (serverChannel != null) {
                serverChannel.close().syncUninterruptibly();
            }
            if (clientChannel != null) {
                clientChannel.close().syncUninterruptibly();
            }
        }
    }

    private static class ReadPendingInitializer extends ChannelInitializer<Channel> {
        final ReadPendingReadHandler readPendingHandler = new ReadPendingReadHandler();
        final CountDownLatch channelInitLatch = new CountDownLatch(1);
        volatile Channel channel;

        @Override
        protected void initChannel(Channel ch) throws Exception {
            channel = ch;
            ch.pipeline().addLast(readPendingHandler);
            channelInitLatch.countDown();
        }
    }

    private static final class ReadPendingReadHandler implements ChannelHandler {
        private final AtomicInteger count = new AtomicInteger();
        private final CountDownLatch latch = new CountDownLatch(1);
        private final CountDownLatch latch2 = new CountDownLatch(2);

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof Resource<?>) {
                ((Resource<?>) msg).close();
            } else {
                ReferenceCountUtil.release(msg);
            }
            if (count.incrementAndGet() == 1) {
                // Call read the first time, to ensure it is not reset the second time.
                ctx.read();
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            latch.countDown();
            latch2.countDown();
        }

        void assertAllRead() throws InterruptedException {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            // We should only do 1 read loop, because we only called read() on the first channelRead.
            assertFalse(latch2.await(1, TimeUnit.SECONDS));
            assertEquals(2, count.get());
        }
    }

    /**
     * Designed to read a single byte at a time to control the number of reads done at a fine granularity.
     */
    private static final class TestNumReadsRecvBufferAllocator implements RecvBufferAllocator {
        private final int numReads;
        TestNumReadsRecvBufferAllocator(int numReads) {
            this.numReads = numReads;
        }

        @Override
        public Handle newHandle() {
            return new Handle() {
                private int attemptedBytesRead;
                private int lastBytesRead;
                private int numMessagesRead;

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
                    return continueReading();
                }

                @Override
                public void readComplete() {
                    // Nothing needs to be done or adjusted after each read cycle is completed.
                }
            };
        }
    }
}
