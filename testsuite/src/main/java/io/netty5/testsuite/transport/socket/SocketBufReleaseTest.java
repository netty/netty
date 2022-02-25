/*
 * Copyright 2012 The Netty Project
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
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.util.concurrent.DefaultEventExecutorGroup;
import io.netty5.util.concurrent.DefaultThreadFactory;
import io.netty5.util.concurrent.EventExecutor;
import io.netty5.util.concurrent.Promise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.util.Random;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class SocketBufReleaseTest extends AbstractSocketTest {

    private static final EventExecutor executor =
            new DefaultEventExecutorGroup(1, new DefaultThreadFactory(SocketBufReleaseTest.class, true)).next();

    @Test
    public void testByteBufRelease(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testByteBufRelease);
    }

    public void testByteBufRelease(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testRelease(sb, cb, false);
    }

    @Test
    public void testBufferRelease(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testBufferRelease);
    }

    public void testBufferRelease(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testRelease(sb, cb, true);
    }

    public void testRelease(ServerBootstrap sb, Bootstrap cb, boolean useNewBufferAPI) throws Throwable {
        final WriteHandler serverHandler;
        final WriteHandler clientHandler;
        if (useNewBufferAPI) {
            enableNewBufferAPI(sb, cb);
            serverHandler = new BufferWriterHandler();
            clientHandler = new BufferWriterHandler();
        } else {
            serverHandler = new ByteBufWriterHandler();
            clientHandler = new ByteBufWriterHandler();
        }

        sb.childHandler(serverHandler);
        cb.handler(clientHandler);

        Channel sc = sb.bind().get();
        Channel cc = cb.connect(sc.localAddress()).get();

        // Ensure the server socket accepted the client connection *and* initialized pipeline successfully.
        serverHandler.awaitPipelineInit();

        // and then close all sockets.
        sc.close().sync();
        cc.close().sync();

        serverHandler.check();
        clientHandler.check();

        serverHandler.release();
        clientHandler.release();
    }

    private abstract static class WriteHandler extends SimpleChannelInboundHandler<Object> {
        abstract void awaitPipelineInit() throws InterruptedException;
        abstract void check() throws InterruptedException;
        abstract void release();
    }

    private static final class ByteBufWriterHandler extends WriteHandler {

        private final Random random = new Random();
        private final CountDownLatch latch = new CountDownLatch(1);
        private ByteBuf buf;
        private final Promise<Channel> channelFuture = executor.newPromise();

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            channelFuture.setSuccess(ctx.channel());
        }

        @Override
        public void channelActive(final ChannelHandlerContext ctx) throws Exception {
            byte[] data = new byte[1024];
            random.nextBytes(data);

            buf = ctx.alloc().buffer();
            // call retain on it so it can't be put back on the pool
            buf.writeBytes(data).retain();

            ctx.writeAndFlush(buf).addListener(future -> latch.countDown());
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
            // discard
        }

        @Override
        void awaitPipelineInit() throws InterruptedException {
            channelFuture.asFuture().sync();
        }

        @Override
        void check() throws InterruptedException {
            latch.await();
            assertEquals(1, buf.refCnt());
        }

        @Override
        void release() {
            buf.release();
        }
    }

    private static final class BufferWriterHandler extends WriteHandler {

        private final Random random = new Random();
        private final CountDownLatch latch = new CountDownLatch(1);
        private Buffer buf;
        private final Promise<Channel> channelFuture = executor.newPromise();

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            channelFuture.setSuccess(ctx.channel());
        }

        @Override
        public void channelActive(final ChannelHandlerContext ctx) throws Exception {
            byte[] data = new byte[1024];
            random.nextBytes(data);
            buf = ctx.bufferAllocator().copyOf(data);
            ctx.writeAndFlush(buf).addListener(future -> latch.countDown());
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
            // discard
        }

        @Override
        void awaitPipelineInit() throws InterruptedException {
            channelFuture.asFuture().sync();
        }

        @Override
        void check() throws InterruptedException {
            latch.await();
            assertFalse(buf.isAccessible());
        }

        @Override
        void release() {
        }
    }
}
