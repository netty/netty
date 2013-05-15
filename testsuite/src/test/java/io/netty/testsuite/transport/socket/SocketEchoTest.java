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
import io.netty.channel.ChannelHandlerUtil;
import io.netty.channel.ChannelInboundByteHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class SocketEchoTest extends AbstractSocketTest {

    private static final Random random = new Random();
    static final byte[] data = new byte[1048576];

    private static EventExecutorGroup group;

    static {
        random.nextBytes(data);
    }

    @BeforeClass
    public static void createGroup() {
        group = new DefaultEventExecutorGroup(2);
    }

    @AfterClass
    public static void destroyGroup() {
        group.shutdownGracefully();
    }

    @Test(timeout = 30000)
    public void testSimpleEcho() throws Throwable {
        run();
    }

    public void testSimpleEcho(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleEcho0(sb, cb, Integer.MAX_VALUE, false, false);
    }

    @Test(timeout = 30000)
    public void testSimpleEchoWithBridge() throws Throwable {
        run();
    }

    public void testSimpleEchoWithBridge(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleEcho0(sb, cb, Integer.MAX_VALUE, true, false);
    }

    @Test(timeout = 30000)
    public void testSimpleEchoWithBoundedBuffer() throws Throwable {
        run();
    }

    public void testSimpleEchoWithBoundedBuffer(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleEcho0(sb, cb, 32, false, false);
    }

    @Test(timeout = 30000)
    public void testSimpleEchoWithBridgedBoundedBuffer() throws Throwable {
        run();
    }

    public void testSimpleEchoWithBridgedBoundedBuffer(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleEcho0(sb, cb, 32, true, false);
    }

    @Test(timeout = 30000)
    public void testSimpleEchoWithVoidPromise() throws Throwable {
        run();
    }

    public void testSimpleEchoWithVoidPromise(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleEcho0(sb, cb, Integer.MAX_VALUE, false, true);
    }

    @Test(timeout = 30000)
    public void testSimpleEchoWithBridgeAndVoidPromise() throws Throwable {
        run();
    }

    public void testSimpleEchoWithBridgeAndVoidPromise(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleEcho0(sb, cb, Integer.MAX_VALUE, true, true);
    }

    private static void testSimpleEcho0(
            ServerBootstrap sb, Bootstrap cb, int maxInboundBufferSize, boolean bridge, boolean voidPromise)
            throws Throwable {

        final EchoHandler sh = new EchoHandler(maxInboundBufferSize);
        final EchoHandler ch = new EchoHandler(maxInboundBufferSize);

        if (bridge) {
            sb.childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel c) throws Exception {
                    c.pipeline().addLast(group, sh);
                }
            });
            cb.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel c) throws Exception {
                    c.pipeline().addLast(group, ch);
                }
            });
        } else {
            sb.childHandler(sh);
            cb.handler(ch);
        }

        Channel sc = sb.bind().sync().channel();
        Channel cc = cb.connect().sync().channel();

        for (int i = 0; i < data.length;) {
            int length = Math.min(random.nextInt(1024 * 64), data.length - i);
            ByteBuf buf = Unpooled.wrappedBuffer(data, i, length);
            if (voidPromise) {
                assertEquals(cc.voidPromise(), cc.write(buf, cc.voidPromise()));
            } else {
                assertNotEquals(cc.voidPromise(), cc.write(buf));
            }
            i += length;
        }

        while (ch.counter < data.length) {
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
        if (ch.exception.get() != null && !(ch.exception.get() instanceof IOException)) {
            throw ch.exception.get();
        }
        if (sh.exception.get() != null) {
            throw sh.exception.get();
        }
        if (ch.exception.get() != null) {
            throw ch.exception.get();
        }
    }

    private static class EchoHandler extends ChannelInboundByteHandlerAdapter {
        private final int maxInboundBufferSize;
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        volatile int counter;

        EchoHandler(int maxInboundBufferSize) {
            this.maxInboundBufferSize = maxInboundBufferSize;
        }

        @Override
        public ByteBuf newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
            return ChannelHandlerUtil.allocate(ctx, 0, maxInboundBufferSize);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx)
                throws Exception {
            channel = ctx.channel();
        }

        @Override
        public void inboundBufferUpdated(
                ChannelHandlerContext ctx, ByteBuf in)
                throws Exception {
            byte[] actual = new byte[in.readableBytes()];
            in.readBytes(actual);

            int lastIdx = counter;
            for (int i = 0; i < actual.length; i ++) {
                assertEquals(data[i + lastIdx], actual[i]);
            }

            if (channel.parent() != null) {
                channel.write(Unpooled.wrappedBuffer(actual));
            }

            counter += actual.length;
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
