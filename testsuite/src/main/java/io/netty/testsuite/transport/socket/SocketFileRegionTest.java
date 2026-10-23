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
import io.netty.util.CharsetUtil;
import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.WritableByteChannel;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.testsuite.transport.TestsuitePermutation.randomBufferType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class SocketFileRegionTest extends AbstractSocketTest {

    static final byte[] data = new byte[1048576 * 10];

    static {
        PlatformDependent.threadLocalRandom().nextBytes(data);
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

    @Test
    public void testCustomFileRegion(TestInfo testInfo) throws Throwable {
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
    public void testFileRegionBeyond2GiB(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testFileRegionBeyond2GiB(serverBootstrap, bootstrap);
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

    /**
     * Sends {@link DefaultFileRegion}s which start beyond 2 GiB and beyond 4 GiB of a file, so that a transport
     * which truncates the file offset to an {@code int} either fails or sends the bytes from the wrong offset.
     */
    public void testFileRegionBeyond2GiB(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        final String wrong = "wrong file offset";
        final String beyond2GiB = "beyond 2 GiB !!!!";
        final String beyond4GiB = "beyond 4 GiB !!!!";
        final int length = wrong.length();
        final long position2GiB = (1L << 31) + 100;
        final long position4GiB = (1L << 32) + 100;

        // Other file systems (for example NTFS) zero-fill the gap when writing beyond the end of the file, which
        // would mean writing 4 GiB to the disk.
        assumeFalse(PlatformDependent.isWindows());

        File file = PlatformDependent.createTempFile("netty-", ".tmp", null);
        file.deleteOnExit();
        // Writing beyond the end of the file creates a sparse file on most file systems, so only a few KiB are used.
        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        try {
            // Where the offset ends up if it is truncated to an int.
            write(raf, wrong, position4GiB & 0xFFFFFFFFL);
            write(raf, beyond2GiB, position2GiB);
            write(raf, beyond4GiB, position4GiB);
        } finally {
            raf.close();
        }

        final BlockingQueue<String> received = new LinkedBlockingQueue<String>();
        sb.childHandler(new ChannelInboundHandlerAdapter() {
            private final StringBuilder builder = new StringBuilder();

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                ByteBuf buf = (ByteBuf) msg;
                try {
                    builder.append(buf.toString(CharsetUtil.US_ASCII));
                } finally {
                    buf.release();
                }
                while (builder.length() >= length) {
                    received.add(builder.substring(0, length));
                    builder.delete(0, length);
                }
            }
        });
        cb.handler(new ChannelInboundHandlerAdapter());

        Channel sc = sb.bind().sync().channel();
        Channel cc = cb.connect(sc.localAddress()).sync().channel();
        try {
            cc.writeAndFlush(new DefaultFileRegion(file, position4GiB, length)).sync();
            assertEquals(beyond4GiB, received.poll(10, TimeUnit.SECONDS));
            cc.writeAndFlush(new DefaultFileRegion(file, position2GiB, length)).sync();
            assertEquals(beyond2GiB, received.poll(10, TimeUnit.SECONDS));
        } finally {
            cc.close().sync();
            sc.close().sync();
            file.delete();
        }
    }

    private static void write(RandomAccessFile raf, String value, long position) throws IOException {
        raf.seek(position);
        raf.write(value.getBytes(CharsetUtil.US_ASCII));
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

    private static void testFileRegion0(
            ServerBootstrap sb, Bootstrap cb, boolean voidPromise, final boolean autoRead, boolean defaultFileRegion)
            throws Throwable {
        sb.childOption(ChannelOption.AUTO_READ, autoRead);
        cb.option(ChannelOption.AUTO_READ, autoRead);

        final int bufferSize = 1024;
        final File file = PlatformDependent.createTempFile("netty-", ".tmp", null);
        file.deleteOnExit();

        final FileOutputStream out = new FileOutputStream(file);
        final Random random = PlatformDependent.threadLocalRandom();

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
}
