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
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.FileRegion;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.internal.ThreadLocalRandom;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class SocketFileRegionTest extends AbstractSocketTest {

    static final byte[] data = new byte[1048576 * 10];

    static {
        ThreadLocalRandom.current().nextBytes(data);
    }

    @Test
    public void testFileRegion() throws Throwable {
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

    public void testFileRegion(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testFileRegion0(sb, cb, false, true);
    }

    public void testFileRegionVoidPromise(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testFileRegion0(sb, cb, true, true);
    }

    public void testFileRegionNotAutoRead(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testFileRegion0(sb, cb, false, false);
    }

    public void testFileRegionVoidPromiseNotAutoRead(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testFileRegion0(sb, cb, true, false);
    }

    private static void testFileRegion0(
            ServerBootstrap sb, Bootstrap cb, boolean voidPromise, final boolean autoRead) throws Throwable {
        final int bufferSize = 1024;
        final File file = File.createTempFile("netty-", ".tmp");
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
            public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                if (!autoRead) {
                    ctx.read();
                }
            }

            @Override
            public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
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

        Channel cc = cb.connect().sync().channel();
        FileRegion region = new DefaultFileRegion(
                new FileInputStream(file).getChannel(), startOffset, data.length - bufferSize);
        // Do write ByteBuf and FileRegion to be sure that mixed writes work
        //
        // See https://github.com/netty/netty/issues/2769
        if (voidPromise) {
            assertEquals(cc.voidPromise(), cc.write(Unpooled.wrappedBuffer(data, 0, bufferSize), cc.voidPromise()));
            assertEquals(cc.voidPromise(), cc.writeAndFlush(region, cc.voidPromise()));
        } else {
            assertNotEquals(cc.voidPromise(), cc.write(Unpooled.wrappedBuffer(data, 0, bufferSize)));
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
}
