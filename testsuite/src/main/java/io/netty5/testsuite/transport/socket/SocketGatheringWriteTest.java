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
package io.netty5.testsuite.transport.socket;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.CompositeByteBuf;
import io.netty5.buffer.Unpooled;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.CompositeBuffer;
import io.netty5.buffer.api.MemoryManager;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.testsuite.util.TestUtils;
import io.netty5.util.ReferenceCountUtil;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.ImmediateEventExecutor;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.StringUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty5.buffer.Unpooled.compositeBuffer;
import static io.netty5.buffer.Unpooled.wrappedBuffer;
import static io.netty5.buffer.api.DefaultBufferAllocators.preferredAllocator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SocketGatheringWriteTest extends AbstractSocketTest {
    private static final long TIMEOUT = 120000;

    private static final SplittableRandom random = new SplittableRandom();
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
    public void testGatheringWriteByteBuf(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testGatheringWriteByteBuf);
    }

    public void testGatheringWriteByteBuf(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testGatheringWrite0(sb, cb, data, false, true, false);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = TimeUnit.MILLISECONDS)
    public void testGatheringWriteNotAutoReadByteBuf(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testGatheringWriteNotAutoReadByteBuf);
    }

    public void testGatheringWriteNotAutoReadByteBuf(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testGatheringWrite0(sb, cb, data, false, false, false);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = TimeUnit.MILLISECONDS)
    public void testGatheringWriteWithCompositeByteBuf(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testGatheringWriteWithCompositeByteBuf);
    }

    public void testGatheringWriteWithCompositeByteBuf(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testGatheringWrite0(sb, cb, data, true, true, false);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = TimeUnit.MILLISECONDS)
    public void testGatheringWriteWithCompositeNotAutoReadByteBuf(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testGatheringWriteWithCompositeNotAutoReadByteBuf);
    }

    public void testGatheringWriteWithCompositeNotAutoReadByteBuf(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testGatheringWrite0(sb, cb, data, true, false, false);
    }

    // Test for https://github.com/netty/netty/issues/2647
    @Test
    @Timeout(value = TIMEOUT, unit = TimeUnit.MILLISECONDS)
    public void testGatheringWriteBigByteBuf(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testGatheringWriteBigByteBuf);
    }

    public void testGatheringWriteBigByteBuf(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        byte[] bigData = new byte[1024 * 1024 * 50];
        random.nextBytes(bigData);
        testGatheringWrite0(sb, cb, bigData, false, true, false);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = TimeUnit.MILLISECONDS)
    public void testGatheringWrite(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testGatheringWrite);
    }

    public void testGatheringWrite(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testGatheringWrite0(sb, cb, data, false, true, true);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = TimeUnit.MILLISECONDS)
    public void testGatheringWriteNotAutoRead(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testGatheringWriteNotAutoRead);
    }

    public void testGatheringWriteNotAutoRead(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testGatheringWrite0(sb, cb, data, false, false, true);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = TimeUnit.MILLISECONDS)
    public void testGatheringWriteWithComposite(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testGatheringWriteWithComposite);
    }

    public void testGatheringWriteWithComposite(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testGatheringWrite0(sb, cb, data, true, true, true);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = TimeUnit.MILLISECONDS)
    public void testGatheringWriteWithCompositeNotAutoRead(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testGatheringWriteWithCompositeNotAutoRead);
    }

    public void testGatheringWriteWithCompositeNotAutoRead(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testGatheringWrite0(sb, cb, data, true, false, true);
    }

    // Test for https://github.com/netty/netty/issues/2647
    @Test
    @Timeout(value = TIMEOUT, unit = TimeUnit.MILLISECONDS)
    public void testGatheringWriteBig(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testGatheringWriteBig);
    }

    public void testGatheringWriteBig(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        byte[] bigData = new byte[1024 * 1024 * 50];
        random.nextBytes(bigData);
        testGatheringWrite0(sb, cb, bigData, false, true, true);
    }

    private void testGatheringWrite0(
            ServerBootstrap sb, Bootstrap cb, byte[] data, boolean composite, boolean autoRead, boolean newBufferAPI)
            throws Throwable {
        if (newBufferAPI) {
            enableNewBufferAPI(sb, cb);
        }
        sb.childOption(ChannelOption.AUTO_READ, autoRead);
        cb.option(ChannelOption.AUTO_READ, autoRead);

        Promise<Void> serverDonePromise = ImmediateEventExecutor.INSTANCE.newPromise();
        final TestServerHandler sh = new TestServerHandler(autoRead, serverDonePromise, data.length);
        final TestHandler ch = new TestHandler(autoRead);

        cb.handler(ch);
        sb.childHandler(sh);

        Channel sc = sb.bind().get();
        Channel cc = cb.connect(sc.localAddress()).get();

        if (newBufferAPI) {
            BufferAllocator alloc = preferredAllocator();
            try (Buffer src = MemoryManager.unsafeWrap(data)) {
                for (int i = 0; i < data.length;) {
                    int length = Math.min(random.nextInt(1024 * 8), data.length - i);
                    if (composite && i % 2 == 0) {
                        int firstBufLength = length / 2;
                        CompositeBuffer comp = CompositeBuffer.compose(
                                alloc,
                                src.readSplit(firstBufLength).send(),
                                src.readSplit(length - firstBufLength).send());
                        cc.write(comp);
                    } else {
                        cc.write(src.readSplit(length));
                    }
                    i += length;
                }
            }
        } else {
            for (int i = 0; i < data.length;) {
                int length = Math.min(random.nextInt(1024 * 8), data.length - i);
                if (composite && i % 2 == 0) {
                    int firstBufLength = length / 2;
                    CompositeByteBuf comp = compositeBuffer();
                    comp.addComponent(true, wrappedBuffer(data, i, firstBufLength))
                        .addComponent(true, wrappedBuffer(data, i + firstBufLength, length - firstBufLength));
                    cc.write(comp);
                } else {
                    cc.write(wrappedBuffer(data, i, length));
                }
                i += length;
            }
        }

        Future<Void> cf = cc.writeAndFlush(newBufferAPI ? preferredAllocator().allocate(0) : Unpooled.EMPTY_BUFFER);
        try {
            assertTrue(cf.await(60000));
            cf.sync();
        } catch (Throwable t) {
            // TODO: Remove this once we fix this test.
            TestUtils.dump(StringUtil.simpleClassName(this));
            throw t;
        }

        serverDonePromise.asFuture().sync();
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
        Object expected = newBufferAPI ? MemoryManager.unsafeWrap(data) : wrappedBuffer(data);
        assertEquals(expected, sh.received);
        if (sh.received instanceof Buffer) {
            ((Buffer) sh.received).close();
            ((Buffer) expected).close();
        } else {
            ReferenceCountUtil.release(sh.received);
            ReferenceCountUtil.release(expected);
        }
    }

    private static final class TestServerHandler extends TestHandler {
        private final int expectedBytes;
        private final Promise<Void> doneReadingPromise;
        Object received;

        TestServerHandler(boolean autoRead, Promise<Void> doneReadingPromise, int expectedBytes) {
            super(autoRead);
            this.doneReadingPromise = doneReadingPromise;
            this.expectedBytes = expectedBytes;
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, Object in) throws Exception {
            if (in instanceof Buffer) {
                Buffer buf = (Buffer) in;
                Buffer recv = (Buffer) received;
                if (recv == null) {
                    received = recv = ctx.bufferAllocator().allocate(256);
                }
                recv.ensureWritable(buf.readableBytes(), recv.capacity(), true);
                recv.writeBytes(buf);
                if (recv.readableBytes() >= expectedBytes) {
                    doneReadingPromise.setSuccess(null);
                }
            } else {
                ByteBuf buf = (ByteBuf) in;
                ByteBuf recv = (ByteBuf) received;
                if (recv == null) {
                    received = recv = Unpooled.buffer();
                }
                recv.writeBytes(buf);
                if (recv.readableBytes() >= expectedBytes) {
                    doneReadingPromise.setSuccess(null);
                }
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

    private static class TestHandler extends SimpleChannelInboundHandler<Object> {
        private final boolean autoRead;
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<>();

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
        public void messageReceived(ChannelHandlerContext ctx, Object in) throws Exception {
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
