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
import io.netty5.buffer.Unpooled;
import io.netty5.buffer.api.DefaultBufferAllocators;
import io.netty5.buffer.api.Resource;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelPipeline;
import io.netty5.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SocketExceptionHandlingTest extends AbstractSocketTest {
    @Test
    public void testReadPendingIsResetAfterEachReadByteBuf(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testReadPendingIsResetAfterEachReadByteBuf);
    }

    public void testReadPendingIsResetAfterEachReadByteBuf(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            MyInitializer serverInitializer = new MyInitializer();
            sb.option(ChannelOption.SO_BACKLOG, 1024);
            sb.childHandler(serverInitializer);

            serverChannel = sb.bind().get();

            cb.handler(new MyInitializer());
            clientChannel = cb.connect(serverChannel.localAddress()).get();

            clientChannel.writeAndFlush(Unpooled.wrappedBuffer(new byte[1024]));

            // We expect to get 2 exceptions (1 from BuggyChannelHandler and 1 from ExceptionHandler).
            assertTrue(serverInitializer.exceptionHandler.latch1.await(5, TimeUnit.SECONDS));

            // After we get the first exception, we should get no more, this is expected to timeout.
            assertFalse(serverInitializer.exceptionHandler.latch2.await(1, TimeUnit.SECONDS),
                "Encountered " + serverInitializer.exceptionHandler.count.get() +
                                        " exceptions when 1 was expected");
        } finally {
            if (serverChannel != null) {
                serverChannel.close().syncUninterruptibly();
            }
            if (clientChannel != null) {
                clientChannel.close().syncUninterruptibly();
            }
        }
    }

    @Test
    public void testReadPendingIsResetAfterEachRead(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testReadPendingIsResetAfterEachRead);
    }

    public void testReadPendingIsResetAfterEachRead(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        enableNewBufferAPI(sb, cb);
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            MyInitializer serverInitializer = new MyInitializer();
            sb.option(ChannelOption.SO_BACKLOG, 1024);
            sb.childHandler(serverInitializer);

            serverChannel = sb.bind().get();

            cb.handler(new MyInitializer());
            clientChannel = cb.connect(serverChannel.localAddress()).get();

            clientChannel.writeAndFlush(DefaultBufferAllocators.preferredAllocator().copyOf(new byte[1024]));

            // We expect to get 2 exceptions (1 from BuggyChannelHandler and 1 from ExceptionHandler).
            assertTrue(serverInitializer.exceptionHandler.latch1.await(5, TimeUnit.SECONDS));

            // After we get the first exception, we should get no more, this is expected to timeout.
            assertFalse(serverInitializer.exceptionHandler.latch2.await(1, TimeUnit.SECONDS),
                "Encountered " + serverInitializer.exceptionHandler.count.get() +
                                        " exceptions when 1 was expected");
        } finally {
            if (serverChannel != null) {
                serverChannel.close().syncUninterruptibly();
            }
            if (clientChannel != null) {
                clientChannel.close().syncUninterruptibly();
            }
        }
    }

    private static class MyInitializer extends ChannelInitializer<Channel> {
        final ExceptionHandler exceptionHandler = new ExceptionHandler();
        @Override
        protected void initChannel(Channel ch) throws Exception {
            ChannelPipeline pipeline = ch.pipeline();

            pipeline.addLast(new BuggyChannelHandler());
            pipeline.addLast(exceptionHandler);
        }
    }

    private static class BuggyChannelHandler implements ChannelHandler {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof Resource<?>) {
                ((Resource<?>) msg).close();
            } else {
                ReferenceCountUtil.release(msg);
            }
            throw new NullPointerException("I am a bug!");
        }
    }

    private static class ExceptionHandler implements ChannelHandler {
        final AtomicLong count = new AtomicLong();
        /**
         * We expect to get 1 call to {@link #exceptionCaught(ChannelHandlerContext, Throwable)}.
         */
        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            if (count.incrementAndGet() <= 2) {
                latch1.countDown();
            } else {
                latch2.countDown();
            }
            // This should not throw any exception.
            ctx.close();
        }
    }
}
