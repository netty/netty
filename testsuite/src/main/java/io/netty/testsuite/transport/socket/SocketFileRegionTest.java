/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.FileRegion;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.internal.PlatformDependent;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class SocketFileRegionTest extends AbstractSocketTest {

    static final byte[] data = new byte[1048576 * 10];

    static {
        PlatformDependent.threadLocalRandom().nextBytes(data);
    }

    @Test
    public void testFileRegion() throws Throwable {
        run();
    }

    @Test
    public void testCustomFileRegion() throws Throwable {
        run();
    }

    @Test
    public void testFileRegionNotAutoRead() throws Throwable {
        run();
    }

    @Test
    public void testFileRegionVoidPromise() throws Throwable {
        run();
    }

    @Test
    public void testFileRegionVoidPromiseNotAutoRead() throws Throwable {
        run();
    }

    @Test
    public void testFileRegionCountLargerThenFile() throws Throwable {
        run();
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
        File file = File.createTempFile("netty-", ".tmp");
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
                new FileInputStream(file).getChannel(), 0, data.length + 1024);

        assertThat(cc.writeAndFlush(region).await().cause(), CoreMatchers.<Throwable>instanceOf(IOException.class));
        cc.close().sync();
        sc.close().sync();
    }

    private static void testFileRegion0(
            ServerBootstrap sb, Bootstrap cb, boolean voidPromise, final boolean autoRead, boolean defaultFileRegion)
            throws Throwable {
        sb.childOption(ChannelOption.AUTO_READ, autoRead);
        cb.option(ChannelOption.AUTO_READ, autoRead);

        final int bufferSize = 1024;
        final File file = File.createTempFile("netty-", ".tmp");
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
                new FileInputStream(file).getChannel(), startOffset, data.length - bufferSize);
        FileRegion emptyRegion = new DefaultFileRegion(new FileInputStream(file).getChannel(), 0, 0);

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
            assertEquals(cc.voidPromise(), cc.write(Unpooled.wrappedBuffer(data, 0, bufferSize), cc.voidPromise()));
            assertEquals(cc.voidPromise(), cc.write(emptyRegion, cc.voidPromise()));
            assertEquals(cc.voidPromise(), cc.writeAndFlush(region, cc.voidPromise()));
        } else {
            assertNotEquals(cc.voidPromise(), cc.write(Unpooled.wrappedBuffer(data, 0, bufferSize)));
            assertNotEquals(cc.voidPromise(), cc.write(emptyRegion));
            assertNotEquals(cc.voidPromise(), cc.writeAndFlush(region));
        }

        while (sh.counter < data.length) {
            if (sh.exception.get() != null) {
                break;
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                // Ignore.
            }
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
        assertThat(sh.counter, is(data.length));
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
