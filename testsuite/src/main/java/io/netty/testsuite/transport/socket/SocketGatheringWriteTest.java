/*
 * Copyright 2013 The Netty Project
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
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.testsuite.util.TestUtils;
import io.netty.util.internal.StringUtil;
import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class SocketGatheringWriteTest extends AbstractSocketTest {

    @Rule
    public final Timeout globalTimeout = new Timeout(60000);

    private static final Random random = new Random();
    static final byte[] data = new byte[1048576];

    static {
        random.nextBytes(data);
    }

    @AfterClass
    public static void compressHeapDumps() throws Exception {
        TestUtils.compressHeapDumps();
    }

    @Test
    public void testGatheringWrite() throws Throwable {
        run();
    }

    public void testGatheringWrite(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testGatheringWrite0(sb, cb, data, false, true);
    }

    @Test
    public void testGatheringWriteNotAutoRead() throws Throwable {
        run();
    }

    public void testGatheringWriteNotAutoRead(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testGatheringWrite0(sb, cb, data, false, false);
    }

    @Test
    public void testGatheringWriteWithComposite() throws Throwable {
        run();
    }

    public void testGatheringWriteWithCompositeNotAutoRead(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testGatheringWrite0(sb, cb, data, true, false);
    }

    @Test
    public void testGatheringWriteWithCompositeNotAutoRead() throws Throwable {
        run();
    }

    public void testGatheringWriteWithComposite(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testGatheringWrite0(sb, cb, data, true, true);
    }

    // Test for https://github.com/netty/netty/issues/2647
    @Test
    public void testGatheringWriteBig() throws Throwable {
        run();
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

        final TestHandler sh = new TestHandler(autoRead);
        final TestHandler ch = new TestHandler(autoRead);

        cb.handler(ch);
        sb.childHandler(sh);

        Channel sc = sb.bind().sync().channel();
        Channel cc = cb.connect().sync().channel();

        for (int i = 0; i < data.length;) {
            int length = Math.min(random.nextInt(1024 * 8), data.length - i);
            ByteBuf buf = Unpooled.wrappedBuffer(data, i, length);
            if (composite && i % 2 == 0) {
                int split =  buf.readableBytes() / 2;
                int size = buf.readableBytes() - split;
                int oldIndex = buf.writerIndex();
                buf.writerIndex(split);
                ByteBuf buf2 = Unpooled.buffer(size).writeBytes(buf, split, oldIndex - split);
                CompositeByteBuf comp = Unpooled.compositeBuffer();
                comp.addComponent(true, buf).addComponent(true, buf2);
                cc.write(comp);
            } else {
                cc.write(buf);
            }
            i += length;
        }

        ChannelFuture cf = cc.writeAndFlush(Unpooled.EMPTY_BUFFER);
        assertNotEquals(cc.voidPromise(), cf);
        try {
            assertTrue(cf.await(30000));
            cf.sync();
        } catch (Throwable t) {
            // TODO: Remove this once we fix this test.
            TestUtils.dump(StringUtil.simpleClassName(this));
            throw t;
        }

        while (sh.counter < data.length) {
            if (sh.exception.get() != null) {
                break;
            }
            if (ch.exception.get() != null) {
                break;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                // Ignore.
            }
        }
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
        assertEquals(0, ch.counter);
        assertEquals(Unpooled.wrappedBuffer(data), sh.received);
    }

    private static class TestHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final boolean autoRead;
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        volatile int counter;
        final ByteBuf received = Unpooled.buffer();

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
            counter += in.readableBytes();
            received.writeBytes(in);
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
