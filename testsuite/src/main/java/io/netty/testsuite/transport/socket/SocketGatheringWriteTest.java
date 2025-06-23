/*
 * Copyright 2013 The Netty Project
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
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.testsuite.util.TestUtils;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.StringUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.buffer.Unpooled.compositeBuffer;
import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.testsuite.transport.TestsuitePermutation.randomBufferType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SocketGatheringWriteTest extends AbstractSocketTest {
    private static final long TIMEOUT = 120000;

    private static final Random random = new Random();
    static final byte[] data = new byte[1048576];

    static {
        random.nextBytes(data);
    }

    @AfterAll
    public static void compressHeapDumps() throws Exception {
        TestUtils.compressHeapDumps();
    }

    @Test
    @Timeout(value = TIMEOUT, unit = TimeUnit.MILLISECONDS)
    public void testGatheringWrite(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testGatheringWrite(serverBootstrap, bootstrap);
            }
        });
    }

    public void testGatheringWrite(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testGatheringWrite0(sb, cb, data, false, true);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = TimeUnit.MILLISECONDS)
    public void testGatheringWriteNotAutoRead(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testGatheringWriteNotAutoRead(serverBootstrap, bootstrap);
            }
        });
    }

    public void testGatheringWriteNotAutoRead(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testGatheringWrite0(sb, cb, data, false, false);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = TimeUnit.MILLISECONDS)
    public void testGatheringWriteWithComposite(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testGatheringWriteWithComposite(serverBootstrap, bootstrap);
            }
        });
    }

    public void testGatheringWriteWithComposite(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testGatheringWrite0(sb, cb, data, true, true);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = TimeUnit.MILLISECONDS)
    public void testGatheringWriteWithCompositeNotAutoRead(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testGatheringWriteWithCompositeNotAutoRead(serverBootstrap, bootstrap);
            }
        });
    }

    public void testGatheringWriteWithCompositeNotAutoRead(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testGatheringWrite0(sb, cb, data, true, false);
    }

    // Test for https://github.com/netty/netty/issues/2647
    @Test
    @Timeout(value = TIMEOUT, unit = TimeUnit.MILLISECONDS)
    public void testGatheringWriteBig(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testGatheringWriteBig(serverBootstrap, bootstrap);
            }
        });
    }

    public void testGatheringWriteBig(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        byte[] bigData = new byte[1024 * 1024 * 50];
        random.nextBytes(bigData);
        testGatheringWrite0(sb, cb, bigData, false, true);
    }

    private void testGatheringWrite0(
            ServerBootstrap sb, Bootstrap cb, byte[] data, boolean composite, boolean autoRead) throws Throwable {
        sb.childOption(ChannelOption.AUTO_READ, autoRead);
        cb.option(ChannelOption.AUTO_READ, autoRead);

        Promise<Void> serverDonePromise = ImmediateEventExecutor.INSTANCE.newPromise();
        final TestServerHandler sh = new TestServerHandler(autoRead, serverDonePromise, data.length);
        final TestHandler ch = new TestHandler(autoRead);

        cb.handler(ch);
        sb.childHandler(sh);

        Channel sc = sb.bind().sync().channel();
        Channel cc = cb.connect(sc.localAddress()).sync().channel();

        for (int i = 0; i < data.length;) {
            int length = Math.min(random.nextInt(1024 * 8), data.length - i);
            if (composite && i % 2 == 0) {
                int firstBufLength = length / 2;
                CompositeByteBuf comp = compositeBuffer();
                comp.addComponent(true,
                                randomBufferType(cc.alloc(), data, i, firstBufLength))
                    .addComponent(true,
                            randomBufferType(cc.alloc(), data, i + firstBufLength, length - firstBufLength));
                cc.write(comp);
            } else {
                cc.write(randomBufferType(cc.alloc(), data, i, length));
            }
            i += length;
        }

        ChannelFuture cf = cc.writeAndFlush(Unpooled.EMPTY_BUFFER);
        assertNotEquals(cc.voidPromise(), cf);
        try {
            assertTrue(cf.await(60000));
            cf.sync();
        } catch (Throwable t) {
            // TODO: Remove this once we fix this test.
            TestUtils.dump(StringUtil.simpleClassName(this));
            throw t;
        }

        serverDonePromise.sync();
        sh.channel.close().sync();
        ch.channel.close().sync();
        sc.close().sync();

        if (sh.exception.get() != null && !(sh.exception.get() instanceof IOException)) {
            throw sh.exception.get();
        }
        if (sh.exception.get() != null) {
            throw sh.exception.get();
        }
        if (ch.exception.get() != null && !(ch.exception.get() instanceof IOException)) {
            throw ch.exception.get();
        }
        if (ch.exception.get() != null) {
            throw ch.exception.get();
        }
        ByteBuf expected = wrappedBuffer(data);
        assertEquals(expected, sh.received);
        expected.release();
        sh.received.release();
    }

    private static final class TestServerHandler extends TestHandler {
        private final int expectedBytes;
        private final Promise<Void> doneReadingPromise;
        final ByteBuf received = Unpooled.buffer();

        TestServerHandler(boolean autoRead, Promise<Void> doneReadingPromise, int expectedBytes) {
            super(autoRead);
            this.doneReadingPromise = doneReadingPromise;
            this.expectedBytes = expectedBytes;
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
            received.writeBytes(in);
            if (received.readableBytes() >= expectedBytes) {
                doneReadingPromise.setSuccess(null);
            }
        }

        @Override
        void handleException(ChannelHandlerContext ctx, Throwable cause) {
            doneReadingPromise.tryFailure(cause);
            super.handleException(ctx, cause);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            doneReadingPromise.tryFailure(new IllegalStateException("server closed!"));
            super.channelInactive(ctx);
        }
    }

    private static class TestHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final boolean autoRead;
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();

        TestHandler(boolean autoRead) {
            this.autoRead = autoRead;
        }

        @Override
        public final void channelActive(ChannelHandlerContext ctx) throws Exception {
            channel = ctx.channel();
            if (!autoRead) {
                ctx.read();
            }
            super.channelActive(ctx);
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        }

        @Override
        public final void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            if (!autoRead) {
                ctx.read();
            }
            super.channelReadComplete(ctx);
        }

        @Override
        public final void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            if (exception.compareAndSet(null, cause)) {
                handleException(ctx, cause);
            }
            super.exceptionCaught(ctx, cause);
        }

        void handleException(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }
}
