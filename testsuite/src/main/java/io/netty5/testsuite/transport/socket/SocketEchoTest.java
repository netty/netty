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
package io.netty5.testsuite.transport.socket;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.Unpooled;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.DefaultBufferAllocators;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.SimpleChannelInboundHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SocketEchoTest extends AbstractSocketTest {

    private static final Random random = new Random();
    static final byte[] data = new byte[1048576];

    static {
        random.nextBytes(data);
    }

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testSimpleEchoByteBuf(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testSimpleEchoByteBuf);
    }

    public void testSimpleEchoByteBuf(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleEcho0(sb, cb, true, false);
    }

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testSimpleEchoNotAutoReadByteBuf(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testSimpleEchoNotAutoReadByteBuf);
    }

    public void testSimpleEchoNotAutoReadByteBuf(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleEcho0(sb, cb, false, false);
    }

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testSimpleEcho(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testSimpleEcho);
    }

    public void testSimpleEcho(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        enableNewBufferAPI(sb, cb);
        testSimpleEcho0(sb, cb, true, true);
    }

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testSimpleEchoNotAutoRead(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testSimpleEchoNotAutoRead);
    }

    public void testSimpleEchoNotAutoRead(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        enableNewBufferAPI(sb, cb);
        testSimpleEcho0(sb, cb, false, true);
    }

    private static void testSimpleEcho0(
            ServerBootstrap sb, Bootstrap cb, boolean autoRead, boolean newBufferAPI)
            throws Throwable {
        final EchoHandler sh = new EchoHandler(autoRead);
        final EchoHandler ch = new EchoHandler(autoRead);

        sb.childHandler(sh);
        sb.handler(new ChannelHandler() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                cause.printStackTrace();
            }
        });
        cb.handler(ch);
        sb.childOption(ChannelOption.AUTO_READ, autoRead);
        cb.option(ChannelOption.AUTO_READ, autoRead);

        Channel sc = sb.bind().get();
        Channel cc = cb.connect(sc.localAddress()).get();

        if (newBufferAPI) {
            try (Buffer src = DefaultBufferAllocators.preferredAllocator().copyOf(data)) {
                for (int i = 0; i < data.length;) {
                    int length = Math.min(random.nextInt(1024 * 64), data.length - i);
                    cc.writeAndFlush(src.readSplit(length));
                    i += length;
                }
            }
        } else {
            for (int i = 0; i < data.length;) {
                int length = Math.min(random.nextInt(1024 * 64), data.length - i);
                ByteBuf buf = Unpooled.wrappedBuffer(data, i, length);
                cc.writeAndFlush(buf);
                i += length;
            }
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

    private static class EchoHandler extends SimpleChannelInboundHandler<Object> {
        private final boolean autoRead;
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<>();
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
        public void messageReceived(ChannelHandlerContext ctx, Object in) throws Exception {
            if (in instanceof Buffer) {
                Buffer buf = (Buffer) in;
                byte[] actual = new byte[buf.readableBytes()];
                buf.readBytes(actual, 0, actual.length);

                int lastIdx = counter;
                for (int i = 0; i < actual.length; i ++) {
                    assertEquals(data[i + lastIdx], actual[i]);
                }

                if (channel.parent() != null) {
                    channel.write(ctx.bufferAllocator().copyOf(actual));
                }

                counter += actual.length;
            } else {
                ByteBuf buf = (ByteBuf) in;
                byte[] actual = new byte[buf.readableBytes()];
                buf.readBytes(actual);

                int lastIdx = counter;
                for (int i = 0; i < actual.length; i ++) {
                    assertEquals(data[i + lastIdx], actual[i]);
                }

                if (channel.parent() != null) {
                    channel.write(Unpooled.wrappedBuffer(actual));
                }

                counter += actual.length;
            }
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
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            if (exception.compareAndSet(null, cause)) {
                cause.printStackTrace();
                ctx.close();
            }
        }
    }
}
