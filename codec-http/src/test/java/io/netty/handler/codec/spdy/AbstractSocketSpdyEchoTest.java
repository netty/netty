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

import static org.junit.Assert.*;
import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.Channel;
import io.netty.channel.ChannelBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInboundHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.channel.ChannelInboundStreamHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ServerChannelBootstrap;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public abstract class AbstractSocketSpdyEchoTest {

    private static final Random random = new Random();
    static final ChannelBuffer frames = ChannelBuffers.buffer(1160);
    static final int ignoredBytes = 20;

    static {
        // SPDY UNKNOWN Frame
        frames.writeByte(0x80);
        frames.writeByte(2);
        frames.writeShort(0xFFFF);
        frames.writeByte(0xFF);
        frames.writeMedium(4);
        frames.writeInt(random.nextInt());

        // SPDY NOOP Frame
        frames.writeByte(0x80);
        frames.writeByte(2);
        frames.writeShort(5);
        frames.writeInt(0);

        // SPDY Data Frame
        frames.writeInt(random.nextInt() & 0x7FFFFFFF);
        frames.writeByte(0x01);
        frames.writeMedium(1024);
        for (int i = 0; i < 256; i ++) {
            frames.writeInt(random.nextInt());
        }

        // SPDY SYN_STREAM Frame
        frames.writeByte(0x80);
        frames.writeByte(2);
        frames.writeShort(1);
        frames.writeByte(0x03);
        frames.writeMedium(12);
        frames.writeInt(random.nextInt() & 0x7FFFFFFF);
        frames.writeInt(random.nextInt() & 0x7FFFFFFF);
        frames.writeShort(0x8000);
        frames.writeShort(0);

        // SPDY SYN_REPLY Frame
        frames.writeByte(0x80);
        frames.writeByte(2);
        frames.writeShort(2);
        frames.writeByte(0x01);
        frames.writeMedium(8);
        frames.writeInt(random.nextInt() & 0x7FFFFFFF);
        frames.writeInt(0);

        // SPDY RST_STREAM Frame
        frames.writeByte(0x80);
        frames.writeByte(2);
        frames.writeShort(3);
        frames.writeInt(8);
        frames.writeInt(random.nextInt() & 0x7FFFFFFF);
        frames.writeInt(random.nextInt() | 0x01);

        // SPDY SETTINGS Frame
        frames.writeByte(0x80);
        frames.writeByte(2);
        frames.writeShort(4);
        frames.writeByte(0x01);
        frames.writeMedium(12);
        frames.writeInt(1);
        frames.writeMedium(random.nextInt());
        frames.writeByte(0x03);
        frames.writeInt(random.nextInt());

        // SPDY PING Frame
        frames.writeByte(0x80);
        frames.writeByte(2);
        frames.writeShort(6);
        frames.writeInt(4);
        frames.writeInt(random.nextInt());

        // SPDY GOAWAY Frame
        frames.writeByte(0x80);
        frames.writeByte(2);
        frames.writeShort(7);
        frames.writeInt(4);
        frames.writeInt(random.nextInt() & 0x7FFFFFFF);

        // SPDY HEADERS Frame
        frames.writeByte(0x80);
        frames.writeByte(2);
        frames.writeShort(8);
        frames.writeInt(4);
        frames.writeInt(random.nextInt() & 0x7FFFFFFF);
    }

    private ServerChannelBootstrap sb;
    private ChannelBootstrap cb;

    @Before
    public void initBootstrap() {
        sb = newServerBootstrap();
        cb = newClientBootstrap();
    }

    @After
    public void destroyBootstrap() {
        sb.shutdown();
        cb.shutdown();
    }

    protected abstract ServerChannelBootstrap newServerBootstrap();
    protected abstract ChannelBootstrap newClientBootstrap();

    @Test(timeout = 10000)
    public void testSpdyEcho() throws Throwable {
        final ServerHandler sh = new ServerHandler();
        final ClientHandler ch = new ClientHandler();

        sb.childInitializer(new ChannelInitializer() {
            @Override
            public void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(
                        new SpdyFrameDecoder(),
                        new SpdyFrameEncoder(),
                        sh);
            }
        });

        cb.initializer(new ChannelInitializer() {
            @Override
            public void initChannel(Channel channel) throws Exception {
                channel.pipeline().addLast(ch);
            }
        });

        Channel sc = sb.localAddress(new InetSocketAddress(0)).bind().sync().channel();
        int port = ((InetSocketAddress) sc.localAddress()).getPort();

        ChannelFuture ccf = cb.remoteAddress(new InetSocketAddress(InetAddress.getLocalHost(), port)).connect();
        assertTrue(ccf.awaitUninterruptibly().isSuccess());

        Channel cc = ccf.channel();
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

        sh.channel.close().awaitUninterruptibly();
        ch.channel.close().awaitUninterruptibly();
        sc.close().awaitUninterruptibly();

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

    private class ServerHandler extends ChannelInboundMessageHandlerAdapter<Object> {
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();

        @Override
        public void channelRegistered(ChannelInboundHandlerContext<Object> ctx)
                throws Exception {
            channel = ctx.channel();
        }

        @Override
        public void messageReceived(ChannelInboundHandlerContext<Object> ctx, Object msg)
                throws Exception {
            ctx.write(msg);
        }

        @Override
        public void exceptionCaught(ChannelInboundHandlerContext<Object> ctx,
                Throwable cause) throws Exception {
            if (exception.compareAndSet(null, cause)) {
                ctx.close();
            }
        }
    }

    private class ClientHandler extends ChannelInboundStreamHandlerAdapter {
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        volatile int counter;

        @Override
        public void channelRegistered(ChannelInboundHandlerContext<Byte> ctx)
                throws Exception {
            channel = ctx.channel();
        }

        @Override
        public void inboundBufferUpdated(ChannelInboundHandlerContext<Byte> ctx)
                throws Exception {
            ChannelBuffer m = ctx.in().byteBuffer().readBytes(ctx.in().byteBuffer().readableBytes());
            byte[] actual = new byte[m.readableBytes()];
            m.getBytes(0, actual);

            int lastIdx = counter;
            for (int i = 0; i < actual.length; i ++) {
                assertEquals(frames.getByte(ignoredBytes + i + lastIdx), actual[i]);
            }

            counter += actual.length;
        }

        @Override
        public void exceptionCaught(ChannelInboundHandlerContext<Byte> ctx,
                Throwable cause) throws Exception {
            if (exception.compareAndSet(null, cause)) {
                ctx.close();
            }
        }
    }
}
