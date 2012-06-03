/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.spdy;

import static io.netty.handler.codec.spdy.SpdyCodecUtil.*;
import static org.junit.Assert.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.channel.ChannelInboundStreamHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.SocketAddresses;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public abstract class AbstractSocketSpdyEchoTest {

    private static final Random random = new Random();
    static final int ignoredBytes = 20;

    private static ChannelBuffer createFrames(int version) {
        int length = version < 3 ? 1176 : 1174;
        ChannelBuffer frames = ChannelBuffers.buffer(length);

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
        if (version < 3) {
            frames.writeMedium(12);
        } else {
            frames.writeMedium(10);
        }
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
        if (version < 3) {
            frames.writeMedium(8);
        } else {
            frames.writeMedium(4);
        }
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
        if (version < 3) {
            frames.writeMedium(random.nextInt());
            frames.writeByte(0x03);
        } else {
            frames.writeByte(0x03);
            frames.writeMedium(random.nextInt());
        }
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
        if (version < 3) {
            frames.writeInt(4);
        } else {
            frames.writeInt(8);
        }
        frames.writeInt(random.nextInt() & 0x7FFFFFFF);
        if (version >= 3) {
            frames.writeInt(random.nextInt() | 0x01);
        }

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

    private ServerBootstrap sb;
    private Bootstrap cb;

    protected abstract ServerBootstrap newServerBootstrap();
    protected abstract Bootstrap newClientBootstrap();

    @Test(timeout = 10000)
    public void testSpdyEcho() throws Throwable {
        for (int version = SPDY_MIN_VERSION; version <= SPDY_MAX_VERSION; version ++) {
            sb = newServerBootstrap();
            cb = newClientBootstrap();
            try {
                testSpdyEcho(version);
            } finally {
                sb.shutdown();
                cb.shutdown();
            }
        }
    }

    private void testSpdyEcho(final int version) throws Throwable {

        ChannelBuffer frames = createFrames(version);

        final SpdyEchoTestServerHandler sh = new SpdyEchoTestServerHandler();
        final SpdyEchoTestClientHandler ch = new SpdyEchoTestClientHandler(frames);

        sb.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel channel) throws Exception {
                channel.pipeline().addLast(
                        new SpdyFrameDecoder(version),
                        new SpdyFrameEncoder(version),
                        sh);
            }
        });

        cb.handler(ch);

        Channel sc = sb.localAddress(0).bind().sync().channel();
        int port = ((InetSocketAddress) sc.localAddress()).getPort();

        Channel cc = cb.remoteAddress(SocketAddresses.LOCALHOST, port).connect().sync().channel();
        cc.write(frames);

        while (ch.counter < frames.writerIndex() - ignoredBytes) {
            if (sh.exception.get() != null) {
                break;
            }
            if (ch.exception.get() != null) {
                break;
            }

            try {
                Thread.sleep(1);
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

    private class SpdyEchoTestServerHandler extends ChannelInboundMessageHandlerAdapter<Object> {
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();

        @Override
        public void messageReceived(ChannelInboundHandlerContext<Object> ctx, Object msg) throws Exception {
            ctx.write(msg);
        }

        @Override
        public void exceptionCaught(ChannelInboundHandlerContext<Object> ctx, Throwable cause) throws Exception {
            if (exception.compareAndSet(null, cause)) {
                ctx.close();
            }
        }
    }

    private class SpdyEchoTestClientHandler extends ChannelInboundStreamHandlerAdapter {
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        final ChannelBuffer frames;
        volatile int counter;

        SpdyEchoTestClientHandler(ChannelBuffer frames) {
            this.frames = frames;
        }

        @Override
        public void inboundBufferUpdated(ChannelInboundHandlerContext<Byte> ctx) throws Exception {
            ChannelBuffer m = ctx.inbound().byteBuffer();
            byte[] actual = new byte[m.readableBytes()];
            m.readBytes(actual);

            int lastIdx = counter;
            for (int i = 0; i < actual.length; i ++) {
                assertEquals(frames.getByte(ignoredBytes + i + lastIdx), actual[i]);
            }

            counter += actual.length;
        }

        @Override
        public void exceptionCaught(ChannelInboundHandlerContext<Byte> ctx, Throwable cause) throws Exception {
            if (exception.compareAndSet(null, cause)) {
                ctx.close();
            }
        }
    }
}
