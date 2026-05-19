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
import io.netty.channel.socket.oio.OioSocketChannel;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.internal.ObjectUtil;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.testsuite.transport.TestsuitePermutation.randomBufferType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
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
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void testFileRegionDrainStopsAtCompletion(TestInfo testInfo) throws Throwable {
        assumeTrue(supportsCustomFileRegion());
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testFileRegionDrainStopsAtCompletion(serverBootstrap, bootstrap);
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
     * Reproducer for a {@code FileRegion} drain-loop overshoot. Transports must short-circuit
     * as soon as the region reports {@code transferred() >= count()}; any extra
     * {@code transferTo} call violates the contract and would corrupt
     * implementations that lazily emit per-chunk framing.
     *
     * <p>A custom {@link FileRegion} whose {@code transferTo} advances {@code transferred}
     * past the bytes it writes to the target on a single call (legal: an encryption- or
     * framing-on-write FileRegion that flushes a complete inner chunk in one call behaves
     * this way) records every {@code transferTo} invocation made past
     * {@code transferred == count}. The expected behaviour for every transport is that
     * {@code transferToCallsPastCompletion} stays zero.
     */
    public void testFileRegionDrainStopsAtCompletion(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        // Region size > 1 so any chunked transport (e.g. io_uring's generic FileRegion fallback)
        // that sizes its chunk buffer from count() retains spare capacity after the first
        // (and only) source byte is written -- without that spare capacity an inner drain loop
        // would exit on writableBytes() == 0 and the overshoot path would not be exercised.
        final int regionSize = 16;

        sb.childHandler(new SimpleChannelInboundHandler<ByteBuf>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                // drain
            }
        });
        cb.handler(new ChannelInboundHandlerAdapter());

        Channel sc = sb.bind().sync().channel();
        Channel cc = cb.connect(sc.localAddress()).sync().channel();
        try {
            // OioByteStreamChannel#doWriteFileRegion drains using a locally-tracked
            // bytes-written counter as the transferTo position (ignoring transferred()), so an
            // ill-behaved FileRegion that advances transferred() past actual bytes written --
            // the exact pattern this fixture uses to exercise the overshoot path -- cannot
            // satisfy OIO's loop without violating the position invariant. The overshoot
            // detection is meaningless on OIO for the same reason; skip the permutation.
            assumeFalse(cc instanceof OioSocketChannel,
                    "OIO transport does not honour transferred() for drain-loop termination");

            OvershootDetectingFileRegion region = new OvershootDetectingFileRegion(regionSize);
            // sync() blocks until the write future completes, by which point every
            // transferTo() call the transport is going to make has already been made --
            // the fixture's call counter is observable here without any timing wait. Ref
            // ownership is transferred to the pipeline on writeAndFlush(), which releases
            // it as the write completes (success or failure).
            cc.writeAndFlush(region).sync();
            int overshoot = region.transferToCallsPastCompletion.get();
            assertEquals(0, overshoot,
                    "transferTo() invoked " + overshoot
                            + " time(s) after region.transferred() reached region.count()="
                            + regionSize);
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

    /**
     * Custom {@link FileRegion} that advances {@code transferred()} past the bytes it writes
     * to the target on a single {@code transferTo} call -- the same pattern an
     * encryption-on-write or framing FileRegion follows when it reports an inner chunk as
     * "delivered" once that chunk fully drains. Records every {@code transferTo} invocation
     * made after the region has been fully transferred so a drain-loop overshoot is visible
     * synchronously on the writer side.
     *
     * <ul>
     *   <li>{@link #count()} returns the constant configured size.</li>
     *   <li>The first {@code transferTo} call writes one byte to the target; once that
     *       byte is accepted it advances {@code transferred} to {@code count} and returns
     *       the bytes written.</li>
     *   <li>Every subsequent {@code transferTo} call increments
     *       {@link #transferToCallsPastCompletion} and writes one phantom byte to mimic
     *       the protocol-corrupting side effect a real overshoot would produce.</li>
     * </ul>
     * The {@code transferred} field is written and read only on the EventLoop (in
     * {@code transferTo} and the surrounding drain loop), so no synchronization is needed.
     * The cross-thread observation channel is the {@link AtomicInteger} counter, which the
     * test reads after {@code writeAndFlush(...).sync()} establishes the happens-before.
     */
    private static final class OvershootDetectingFileRegion extends AbstractReferenceCounted
            implements FileRegion {
        private final long count;
        private long transferred;
        final AtomicInteger transferToCallsPastCompletion = new AtomicInteger();

        OvershootDetectingFileRegion(long count) {
            // count must be > 1 so the chunk buffer retains writable capacity after the first
            // source byte is written -- otherwise the drain-loop overshoot path is not exercised.
            this.count = ObjectUtil.checkInRange(count, 2L, Long.MAX_VALUE, "count");
        }

        @Override
        public long position() {
            return 0;
        }

        @Override
        public long count() {
            return count;
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
            // Per FileRegion's contract, the caller passes transferred() as position. Surface
            // a violation via IOException so the transport's catch (Exception) at the write
            // site routes it to the write future's cause -- a JUnit AssertionError would
            // bypass that catch and wedge the EventLoop.
            if (position != transferred) {
                throw new IOException("transferTo position " + position + " != transferred() "
                        + transferred);
            }
            if (transferred < count) {
                int n = target.write(ByteBuffer.wrap(new byte[] { 0x42 }));
                if (n > 0) {
                    transferred = count;
                }
                return n;
            }
            transferToCallsPastCompletion.incrementAndGet();
            return target.write(ByteBuffer.wrap(new byte[] { (byte) 0xFF }));
        }

        @Override
        protected void deallocate() {
            // No native resources to release. The overshoot counter is observed on the
            // writer thread immediately after sync() returns; nothing else needs cleanup.
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
}
