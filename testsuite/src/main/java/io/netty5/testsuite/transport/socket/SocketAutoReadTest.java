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
import io.netty5.buffer.BufferAllocator;
import io.netty5.buffer.DefaultBufferAllocators;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ReadHandleFactory;
import io.netty5.util.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SocketAutoReadTest extends AbstractSocketTest {
    @Test
    public void testAutoReadOffDuringRead(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testAutoReadOffDuringRead);
    }

    public void testAutoReadOffDuringRead(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testAutoReadOffDuringRead(true, sb, cb);
        testAutoReadOffDuringRead(false, sb, cb);
    }

    private static void testAutoReadOffDuringRead(boolean readOutsideEventLoopThread,
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
                    .childOption(ChannelOption.READ_HANDLE_FACTORY, new TestReadHandleFactory())
                    .childHandler(serverInitializer);

            serverChannel = sb.bind().asStage().get();

            cb.option(ChannelOption.AUTO_READ, true)
                    // We want to ensure that we attempt multiple individual read operations per read loop so we can
                    // test the auto read feature being turned off when data is first read.
                    .option(ChannelOption.READ_HANDLE_FACTORY, new TestReadHandleFactory())
                    .handler(clientInitializer);

            clientChannel = cb.connect(serverChannel.localAddress()).asStage().get();
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
                clientChannel.close().asStage().sync();
            }
            if (serverChannel != null) {
                serverChannel.close().asStage().sync();
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
            latch2 = new CountDownLatch(1);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof Resource<?>) {
                ((Resource<?>) msg).close();
            } else {
                Resource.dispose(msg);
            }
            if (count.incrementAndGet() == 1) {
                ctx.channel().setOption(ChannelOption.AUTO_READ, false);
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
            assertEquals(3, count.get());
        }
    }

    /**
     * Designed to keep reading as long as autoread is enabled.
     */
    private static final class TestReadHandleFactory implements ReadHandleFactory {
        @Override
        public ReadHandle newHandle(Channel channel) {
            return new ReadHandle() {

                @Override
                public int prepareRead() {
                    return 1; // only ever allocate buffers of size 1 to ensure the number of reads is controlled.
                }

                @Override
                public boolean lastRead(int attemptedBytesRead, int actualBytesRead, int numMessagesRead) {
                    // Reading until there is nothing left to read.
                    return true;
                }

                @Override
                public void readComplete() {
                    // Nothing needs to be done or adjusted after each read cycle is completed.
                }
            };
        }
    }
}
