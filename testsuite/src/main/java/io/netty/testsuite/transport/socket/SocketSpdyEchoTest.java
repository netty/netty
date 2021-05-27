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
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.spdy.SpdyFrameCodec;
import io.netty.handler.codec.spdy.SpdyVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class SocketSpdyEchoTest extends AbstractSocketTest {

    private static final Random random = new Random();
    static final int ignoredBytes = 20;

    private static ByteBuf createFrames(int version) {
        ByteBuf frames = Unpooled.buffer(1174);

        // SPDY UNKNOWN Frame
        frames.writeByte(0x80);
        frames.writeByte(version);
        frames.writeShort(0xFFFF);
        frames.writeByte(0xFF);
        frames.writeMedium(4);
        frames.writeInt(random.nextInt());

        // SPDY NOOP Frame
        frames.writeByte(0x80);
        frames.writeByte(version);
        frames.writeShort(5);
        frames.writeInt(0);

        // SPDY Data Frame
        frames.writeInt(random.nextInt() & 0x7FFFFFFF | 0x01);
        frames.writeByte(0x01);
        frames.writeMedium(1024);
        for (int i = 0; i < 256; i ++) {
            frames.writeInt(random.nextInt());
        }

        // SPDY SYN_STREAM Frame
        frames.writeByte(0x80);
        frames.writeByte(version);
        frames.writeShort(1);
        frames.writeByte(0x03);
        frames.writeMedium(10);
        frames.writeInt(random.nextInt() & 0x7FFFFFFF | 0x01);
        frames.writeInt(random.nextInt() & 0x7FFFFFFF);
        frames.writeShort(0x8000);
        if (version < 3) {
            frames.writeShort(0);
        }

        // SPDY SYN_REPLY Frame
        frames.writeByte(0x80);
        frames.writeByte(version);
        frames.writeShort(2);
        frames.writeByte(0x01);
        frames.writeMedium(4);
        frames.writeInt(random.nextInt() & 0x7FFFFFFF | 0x01);
        if (version < 3) {
            frames.writeInt(0);
        }

        // SPDY RST_STREAM Frame
        frames.writeByte(0x80);
        frames.writeByte(version);
        frames.writeShort(3);
        frames.writeInt(8);
        frames.writeInt(random.nextInt() & 0x7FFFFFFF | 0x01);
        frames.writeInt(random.nextInt() | 0x01);

        // SPDY SETTINGS Frame
        frames.writeByte(0x80);
        frames.writeByte(version);
        frames.writeShort(4);
        frames.writeByte(0x01);
        frames.writeMedium(12);
        frames.writeInt(1);
        frames.writeByte(0x03);
        frames.writeMedium(random.nextInt());
        frames.writeInt(random.nextInt());

        // SPDY PING Frame
        frames.writeByte(0x80);
        frames.writeByte(version);
        frames.writeShort(6);
        frames.writeInt(4);
        frames.writeInt(random.nextInt());

        // SPDY GOAWAY Frame
        frames.writeByte(0x80);
        frames.writeByte(version);
        frames.writeShort(7);
        frames.writeInt(8);
        frames.writeInt(random.nextInt() & 0x7FFFFFFF);
        frames.writeInt(random.nextInt() | 0x01);

        // SPDY HEADERS Frame
        frames.writeByte(0x80);
        frames.writeByte(version);
        frames.writeShort(8);
        frames.writeByte(0x01);
        frames.writeMedium(4);
        frames.writeInt(random.nextInt() & 0x7FFFFFFF | 0x01);

        // SPDY WINDOW_UPDATE Frame
        frames.writeByte(0x80);
        frames.writeByte(version);
        frames.writeShort(9);
        frames.writeInt(8);
        frames.writeInt(random.nextInt() & 0x7FFFFFFF | 0x01);
        frames.writeInt(random.nextInt() & 0x7FFFFFFF | 0x01);

        return frames;
    }

    @Test
    @Timeout(value = 15000, unit = TimeUnit.MILLISECONDS)
    public void testSpdyEcho(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testSpdyEcho(serverBootstrap, bootstrap);
            }
        });
    }

    public void testSpdyEcho(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        logger.info("Testing against SPDY v3.1");
        testSpdyEcho(sb, cb, SpdyVersion.SPDY_3_1, true);
    }

    @Test
    @Timeout(value = 15000, unit = TimeUnit.MILLISECONDS)
    public void testSpdyEchoNotAutoRead(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testSpdyEchoNotAutoRead(serverBootstrap, bootstrap);
            }
        });
    }

    public void testSpdyEchoNotAutoRead(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        logger.info("Testing against SPDY v3.1");
        testSpdyEcho(sb, cb, SpdyVersion.SPDY_3_1, false);
    }

    private static void testSpdyEcho(
            ServerBootstrap sb, Bootstrap cb, final SpdyVersion version, boolean autoRead) throws Throwable {

        ByteBuf frames;
        switch (version) {
        case SPDY_3_1:
            frames = createFrames(3);
            break;
        default:
            throw new IllegalArgumentException("unknown version");
        }

        sb.childOption(ChannelOption.AUTO_READ, autoRead);
        cb.option(ChannelOption.AUTO_READ, autoRead);

        final SpdyEchoTestServerHandler sh = new SpdyEchoTestServerHandler(autoRead);
        final SpdyEchoTestClientHandler ch = new SpdyEchoTestClientHandler(frames.copy(), autoRead);

        sb.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel channel) throws Exception {
                channel.pipeline().addLast(
                        new SpdyFrameCodec(version),
                        sh);
            }
        });

        cb.handler(ch);

        Channel sc = sb.bind().sync().channel();

        Channel cc = cb.connect(sc.localAddress()).sync().channel();
        cc.writeAndFlush(frames);

        while (ch.counter < frames.writerIndex() - ignoredBytes) {
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

    private static class SpdyEchoTestServerHandler extends ChannelInboundHandlerAdapter {
        private final boolean autoRead;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();

        SpdyEchoTestServerHandler(boolean autoRead) {
            this.autoRead = autoRead;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            if (!autoRead) {
                ctx.read();
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ctx.write(msg);
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
                ctx.close();
            }
        }
    }

    private static class SpdyEchoTestClientHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final boolean autoRead;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        final ByteBuf frames;
        volatile int counter;

        SpdyEchoTestClientHandler(ByteBuf frames, boolean autoRead) {
            this.frames = frames;
            this.autoRead = autoRead;
        }
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
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
                assertEquals(frames.getByte(ignoredBytes + i + lastIdx), actual[i]);
            }

            counter += actual.length;
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            if (exception.compareAndSet(null, cause)) {
                ctx.close();
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            if (!autoRead) {
                ctx.read();
            }
        }
    }
}
