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
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
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
    public static void destroyGroup() throws Exception {
        group.shutdownGracefully().sync();
    }

    @Test(timeout = 30000)
    public void testSimpleEcho() throws Throwable {
        run();
    }

    public void testSimpleEcho(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleEcho0(sb, cb, false, false, true);
    }

    @Test(timeout = 30000)
    public void testSimpleEchoNotAutoRead() throws Throwable {
        run();
    }

    public void testSimpleEchoNotAutoRead(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleEcho0(sb, cb, false, false, false);
    }

    @Test//(timeout = 30000)
    public void testSimpleEchoWithAdditionalExecutor() throws Throwable {
        run();
    }

    public void testSimpleEchoWithAdditionalExecutor(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleEcho0(sb, cb, true, false, true);
    }

    @Test//(timeout = 30000)
    public void testSimpleEchoWithAdditionalExecutorNotAutoRead() throws Throwable {
        run();
    }

    public void testSimpleEchoWithAdditionalExecutorNotAutoRead(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleEcho0(sb, cb, true, false, false);
    }

    @Test//(timeout = 30000)
    public void testSimpleEchoWithVoidPromise() throws Throwable {
        run();
    }

    public void testSimpleEchoWithVoidPromise(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleEcho0(sb, cb, false, true, true);
    }

    @Test//(timeout = 30000)
    public void testSimpleEchoWithVoidPromiseNotAutoRead() throws Throwable {
        run();
    }

    public void testSimpleEchoWithVoidPromiseNotAutoRead(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleEcho0(sb, cb, false, true, false);
    }

    @Test(timeout = 30000)
    public void testSimpleEchoWithAdditionalExecutorAndVoidPromise() throws Throwable {
        run();
    }

    public void testSimpleEchoWithAdditionalExecutorAndVoidPromise(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleEcho0(sb, cb, true, true, true);
    }

    private static void testSimpleEcho0(
            ServerBootstrap sb, Bootstrap cb, boolean additionalExecutor, boolean voidPromise, boolean autoRead)
            throws Throwable {

        final EchoHandler sh = new EchoHandler(autoRead);
        final EchoHandler ch = new EchoHandler(autoRead);

        if (additionalExecutor) {
            sb.childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel c) throws Exception {
                    c.pipeline().addLast(group, sh);
                }
            });
            cb.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel c) throws Exception {
                    c.pipeline().addLast(group, ch);
                }
            });
        } else {
            sb.childHandler(sh);
            sb.handler(new ChannelInboundHandlerAdapter() {
                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                    cause.printStackTrace();
                }
            });
            cb.handler(ch);
        }
        sb.childOption(ChannelOption.AUTO_READ, autoRead);
        cb.option(ChannelOption.AUTO_READ, autoRead);

        Channel sc = sb.bind().sync().channel();
        Channel cc = cb.connect().sync().channel();

        for (int i = 0; i < data.length;) {
            int length = Math.min(random.nextInt(1024 * 64), data.length - i);
            ByteBuf buf = Unpooled.wrappedBuffer(data, i, length);
            if (voidPromise) {
                assertEquals(cc.voidPromise(), cc.writeAndFlush(buf, cc.voidPromise()));
            } else {
                assertNotEquals(cc.voidPromise(), cc.writeAndFlush(buf));
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

    private static class EchoHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final boolean autoRead;
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        volatile int counter;

        EchoHandler(boolean autoRead) {
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

            if (channel.parent() != null) {
                channel.write(Unpooled.wrappedBuffer(actual));
            }

            counter += actual.length;
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            try {
                ctx.flush();
            } finally {
                if (!autoRead) {
                    ctx.read();
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx,
                Throwable cause) throws Exception {
            if (exception.compareAndSet(null, cause)) {
                cause.printStackTrace();
                ctx.close();
            }
        }
    }
}
