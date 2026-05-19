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
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.FileRegion;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.testsuite.transport.TestsuitePermutation.randomBufferType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class SocketFileRegionTest extends AbstractSocketTest {

    static final byte[] data = new byte[1048576 * 10];

    static {
        ThreadLocalRandom.current().nextBytes(data);
    }

    @Test
    public void testFileRegion(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testFileRegion(serverBootstrap, bootstrap);
            }
        });
    }

    protected boolean supportsCustomFileRegion() {
        return true;
    }

    /**
     * Largest per-call output (in bytes) the transport can carry from a custom
     * {@link FileRegion#transferTo} into the socket without truncation. Defaults to
     * {@link Long#MAX_VALUE}; transports that copy through an intermediate buffer should
     * override this to their per-call cap so {@link #testFileRegionEmittingMoreThanCount}
     * skips when the cap is below the test payload.
     */
    protected long maxFileRegionPerCallEmit() {
        return Long.MAX_VALUE;
    }

    @Test
    public void testCustomFileRegion(TestInfo testInfo) throws Throwable {
        assumeTrue(supportsCustomFileRegion());
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testCustomFileRegion(serverBootstrap, bootstrap);
            }
        });
    }

    @Test
    public void testFileRegionNotAutoRead(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testFileRegionNotAutoRead(serverBootstrap, bootstrap);
            }
        });
    }

    @Test
    public void testFileRegionVoidPromise(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testFileRegionVoidPromise(serverBootstrap, bootstrap);
            }
        });
    }

    @Test
    public void testFileRegionVoidPromiseNotAutoRead(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testFileRegionVoidPromiseNotAutoRead(serverBootstrap, bootstrap);
            }
        });
    }

    @Test
    public void testFileRegionCountLargerThenFile(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testFileRegionCountLargerThenFile(serverBootstrap, bootstrap);
            }
        });
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void testFileRegionEmittingMoreThanCount(TestInfo testInfo) throws Throwable {
        assumeTrue(supportsCustomFileRegion());
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testFileRegionEmittingMoreThanCount(serverBootstrap, bootstrap);
            }
        });
    }

    public void testFileRegion(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testFileRegion0(sb, cb, false, true, true);
    }

    public void testCustomFileRegion(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testFileRegion0(sb, cb, false, true, false);
    }

    public void testFileRegionVoidPromise(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testFileRegion0(sb, cb, true, true, true);
    }

    public void testFileRegionNotAutoRead(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testFileRegion0(sb, cb, false, false, true);
    }

    public void testFileRegionVoidPromiseNotAutoRead(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testFileRegion0(sb, cb, true, false, true);
    }

    public void testFileRegionCountLargerThenFile(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        File file = PlatformDependent.createTempFile("netty-", ".tmp", null);
        file.deleteOnExit();

        final FileOutputStream out = new FileOutputStream(file);
        out.write(data);
        out.close();

        sb.childHandler(new SimpleChannelInboundHandler<ByteBuf>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                // Just drop the message.
            }
        });
        cb.handler(new ChannelInboundHandlerAdapter());

        Channel sc = sb.bind().sync().channel();
        Channel cc = cb.connect(sc.localAddress()).sync().channel();

        // Request file region which is bigger then the underlying file.
        FileRegion region = new DefaultFileRegion(
                new RandomAccessFile(file, "r").getChannel(), 0, data.length + 1024);

        assertInstanceOf(IOException.class, cc.writeAndFlush(region).await().cause());
        cc.close().sync();
        sc.close().sync();
    }

    /**
     * Regression for the case where a custom {@link FileRegion} emits more bytes through
     * {@link FileRegion#transferTo} than {@link FileRegion#count()} advertises -- the shape
     * of an on-the-fly encryption or framing layer whose {@code count()} reports the source
     * size while {@code transferTo()} writes larger output. Every transport that supports
     * custom FileRegions must deliver all emitted bytes to the peer; this test pins that
     * contract.
     */
    public void testFileRegionEmittingMoreThanCount(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        // Small payload sized to exercise the truncation path without bulk data.
        final int reportedCount = 5;
        final int actualEmitted = 20;
        // Below the transport's per-call cap the truncation is unavoidable; skip.
        assumeTrue(actualEmitted <= maxFileRegionPerCallEmit(),
                "transport's per-call output cap is smaller than the region's emit; "
                        + "scenario is not covered");

        byte[] expected = new byte[actualEmitted];
        for (int i = 0; i < actualEmitted; i++) {
            expected[i] = (byte) i;
        }
        ReceivingHandler sh = new ReceivingHandler(expected);
        sb.childOption(ChannelOption.AUTO_READ, true);
        cb.option(ChannelOption.AUTO_READ, true);
        sb.childHandler(sh);
        cb.handler(new SimpleChannelInboundHandler<Object>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
                // drop
            }
        });

        Channel sc = sb.bind().sync().channel();
        Channel cc = cb.connect(sc.localAddress()).sync().channel();
        try {
            EmitMoreThanCountFileRegion region =
                    new EmitMoreThanCountFileRegion(reportedCount, expected);
            cc.writeAndFlush(region).sync();
            sh.awaitCompletion();
            assertNull(sh.exception.get());
            assertEquals(expected.length, sh.counter,
                    "expected " + expected.length + " bytes from a region whose transferTo() "
                            + "emits more than count(); receiver got " + sh.counter);
            // Pin the fixture's post-write state so a future transport change that calls
            // transferTo() twice fails here rather than going unnoticed.
            assertEquals(reportedCount, region.transferred(),
                    "fixture should advance transferred() to reportedCount after one call");
            assertEquals(reportedCount, region.count(),
                    "fixture count() must stay at reportedCount");
        } finally {
            cc.close().sync();
            sc.close().sync();
        }
    }

    private static void testFileRegion0(
            ServerBootstrap sb, Bootstrap cb, boolean voidPromise, final boolean autoRead, boolean defaultFileRegion)
            throws Throwable {
        sb.childOption(ChannelOption.AUTO_READ, autoRead);
        cb.option(ChannelOption.AUTO_READ, autoRead);

        final int bufferSize = 1024;
        final File file = PlatformDependent.createTempFile("netty-", ".tmp", null);
        file.deleteOnExit();

        final FileOutputStream out = new FileOutputStream(file);
        final Random random = ThreadLocalRandom.current();

        // Prepend random data which will not be transferred, so that we can test non-zero start offset
        final int startOffset = random.nextInt(8192);
        for (int i = 0; i < startOffset; i ++) {
            out.write(random.nextInt());
        }

        // .. and here comes the real data to transfer.
        out.write(data, bufferSize, data.length - bufferSize);

        // .. and then some extra data which is not supposed to be transferred.
        for (int i = random.nextInt(8192); i > 0; i --) {
            out.write(random.nextInt());
        }

        out.close();

        ChannelInboundHandler ch = new SimpleChannelInboundHandler<Object>() {
            @Override
            public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            }

            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                if (!autoRead) {
                    ctx.read();
                }
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                ctx.close();
            }
        };
        TestHandler sh = new TestHandler(autoRead);

        sb.childHandler(sh);
        cb.handler(ch);

        Channel sc = sb.bind().sync().channel();

        Channel cc = cb.connect(sc.localAddress()).sync().channel();
        FileRegion region = new DefaultFileRegion(
                new RandomAccessFile(file, "r").getChannel(), startOffset, data.length - bufferSize);
        FileRegion emptyRegion = new DefaultFileRegion(new RandomAccessFile(file, "r").getChannel(), 0, 0);

        if (!defaultFileRegion) {
            region = new FileRegionWrapper(region);
            emptyRegion = new FileRegionWrapper(emptyRegion);
        }
        // Do write ByteBuf and then FileRegion to ensure that mixed writes work
        // Also, write an empty FileRegion to test if writing an empty FileRegion does not cause any issues.
        //
        // See https://github.com/netty/netty/issues/2769
        //     https://github.com/netty/netty/issues/2964
        if (voidPromise) {
            assertEquals(cc.voidPromise(), cc.write(
                    randomBufferType(cc.alloc(), data, 0, bufferSize), cc.voidPromise()));
            assertEquals(cc.voidPromise(), cc.write(emptyRegion, cc.voidPromise()));
            assertEquals(cc.voidPromise(), cc.writeAndFlush(region, cc.voidPromise()));
        } else {
            assertNotEquals(cc.voidPromise(), cc.write(
                    randomBufferType(cc.alloc(), data, 0, bufferSize)));
            assertNotEquals(cc.voidPromise(), cc.write(emptyRegion));
            assertNotEquals(cc.voidPromise(), cc.writeAndFlush(region));
        }

        while (sh.counter < data.length) {
            if (sh.exception.get() != null) {
                break;
            }

            Thread.sleep(50);
        }

        sh.channel.close().sync();
        cc.close().sync();
        sc.close().sync();

        if (sh.exception.get() != null && !(sh.exception.get() instanceof IOException)) {
            throw sh.exception.get();
        }

        if (sh.exception.get() != null) {
            throw sh.exception.get();
        }

        // Make sure we did not receive more than we expected.
        assertEquals(data.length, sh.counter);
    }

    private static class TestHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final boolean autoRead;
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        volatile int counter;

        TestHandler(boolean autoRead) {
            this.autoRead = autoRead;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx)
                throws Exception {
            channel = ctx.channel();
            if (!autoRead) {
                ctx.read();
            }
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
            byte[] actual = new byte[in.readableBytes()];
            in.readBytes(actual);

            int lastIdx = counter;
            for (int i = 0; i < actual.length; i ++) {
                assertEquals(data[i + lastIdx], actual[i]);
            }
            counter += actual.length;
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            if (!autoRead) {
                ctx.read();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx,
                Throwable cause) throws Exception {
            if (exception.compareAndSet(null, cause)) {
                ctx.close();
            }
        }
    }

    private static final class FileRegionWrapper implements FileRegion {
        private final FileRegion region;

        FileRegionWrapper(FileRegion region) {
            this.region = region;
        }

        @Override
        public int refCnt() {
            return region.refCnt();
        }

        @Override
        public long position() {
            return region.position();
        }

        @Override
        @Deprecated
        public long transfered() {
            return region.transferred();
        }

        @Override
        public boolean release() {
            return region.release();
        }

        @Override
        public long transferred() {
            return region.transferred();
        }

        @Override
        public long count() {
            return region.count();
        }

        @Override
        public boolean release(int decrement) {
            return region.release(decrement);
        }

        @Override
        public long transferTo(WritableByteChannel target, long position) throws IOException {
            return region.transferTo(target, position);
        }

        @Override
        public FileRegion retain() {
            region.retain();
            return this;
        }

        @Override
        public FileRegion retain(int increment) {
            region.retain(increment);
            return this;
        }

        @Override
        public FileRegion touch() {
            region.touch();
            return this;
        }

        @Override
        public FileRegion touch(Object hint) {
            region.touch(hint);
            return this;
        }
    }

    /**
     * Inbound handler used by {@link #testFileRegionEmittingMoreThanCount}: compares each
     * inbound chunk against the expected payload and counts down {@code done} once the
     * full payload has arrived or an error is observed.
     */
    protected static final class ReceivingHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final byte[] expected;
        private final CountDownLatch done = new CountDownLatch(1);
        public final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        // The happens-before edge from CountDownLatch.countDown -> await already makes the
        // final value visible to the reader; volatile lets a stalled awaitCompletion read
        // partial progress for diagnostics before the latch releases.
        public volatile int counter;

        public ReceivingHandler(byte[] expected) {
            this.expected = expected;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf in) {
            int readable = in.readableBytes();
            byte[] actual = new byte[readable];
            in.readBytes(actual);
            int offset = counter;
            if (offset + readable > expected.length) {
                failed(new AssertionError("Received more than " + expected.length + " bytes"));
                ctx.close();
                return;
            }
            for (int i = 0; i < actual.length; i++) {
                if (actual[i] != expected[offset + i]) {
                    failed(new AssertionError("Byte mismatch at index " + (offset + i)));
                    ctx.close();
                    return;
                }
            }
            counter += readable;
            if (counter == expected.length) {
                done.countDown();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            failed(cause);
            ctx.close();
        }

        private void failed(Throwable cause) {
            if (exception.compareAndSet(null, cause)) {
                done.countDown();
            }
        }

        public void awaitCompletion() throws InterruptedException {
            if (!done.await(30, TimeUnit.SECONDS)) {
                fail("Timed out waiting for " + expected.length
                        + " bytes, received " + counter);
            }
        }
    }

    /**
     * Single-shot {@link FileRegion} that emits {@code payload.length} bytes through
     * {@link #transferTo} while reporting only {@code reportedCount} via {@link #count()} --
     * the shape of an on-the-fly encryption or framing layer.
     *
     * <ul>
     *   <li>{@link #count()} returns {@code reportedCount} for the lifetime of the region.</li>
     *   <li>The first {@code transferTo} call writes the supplied {@code payload} to the
     *       target in a single {@code write(ByteBuffer)} call, advances {@code transferred}
     *       to {@code reportedCount}, and returns {@code payload.length}. A short write
     *       throws {@link IOException} -- the call site upholds
     *       {@code maxFileRegionPerCallEmit() >= payload.length} so this should not happen.</li>
     *   <li>Subsequent calls return 0.</li>
     * </ul>
     */
    private static final class EmitMoreThanCountFileRegion extends AbstractReferenceCounted
            implements FileRegion {
        private final long reportedCount;
        private final byte[] payload;
        // emitted/transferred are accessed only on the EventLoop, so no synchronization
        // is needed.
        private boolean emitted;
        private long transferred;

        EmitMoreThanCountFileRegion(long reportedCount, byte[] payload) {
            if (reportedCount < 1 || payload.length <= reportedCount) {
                throw new IllegalArgumentException(
                        "reportedCount must be >= 1 and payload.length must be > reportedCount; "
                                + "got reportedCount=" + reportedCount
                                + ", payload.length=" + payload.length);
            }
            this.reportedCount = reportedCount;
            this.payload = payload;
        }

        @Override
        public long position() {
            return 0;
        }

        @Override
        public long count() {
            return reportedCount;
        }

        @Override
        public long transferred() {
            return transferred;
        }

        @Override
        @Deprecated
        public long transfered() {
            return transferred;
        }

        @Override
        public long transferTo(WritableByteChannel target, long position) throws IOException {
            if (emitted) {
                return 0;
            }
            int n = target.write(ByteBuffer.wrap(payload));
            // The maxFileRegionPerCallEmit() gate at the call site guarantees the target
            // accepts the full payload; a short write here means a transport regression.
            if (n < payload.length) {
                throw new IOException("EmitMoreThanCountFileRegion expected target to accept "
                        + payload.length + " bytes in one call but only " + n + " were accepted");
            }
            // Advance transferred() to count() to model an encryption/framing region whose
            // source is reported "delivered" once the wider output is emitted.
            emitted = true;
            transferred = reportedCount;
            return n;
        }

        @Override
        protected void deallocate() {
            // No native resources held.
        }

        @Override
        public FileRegion retain() {
            super.retain();
            return this;
        }

        @Override
        public FileRegion retain(int increment) {
            super.retain(increment);
            return this;
        }

        @Override
        public FileRegion touch() {
            return this;
        }

        @Override
        public FileRegion touch(Object hint) {
            return this;
        }
    }
}
