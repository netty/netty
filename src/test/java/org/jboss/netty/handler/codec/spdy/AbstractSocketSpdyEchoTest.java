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
package org.jboss.netty.handler.codec.spdy;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.util.TestUtil;
import org.junit.Test;

public abstract class AbstractSocketSpdyEchoTest {

    private static final Random random = new Random();
    static final int ignoredBytes = 20;

    private static ChannelBuffer createFrames(int version) {
        ChannelBuffer frames = ChannelBuffers.buffer(1174);

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

        // SPDY SYN_REPLY Frame
        frames.writeByte(0x80);
        frames.writeByte(version);
        frames.writeShort(2);
        frames.writeByte(0x01);
        frames.writeMedium(4);
        frames.writeInt(random.nextInt() & 0x7FFFFFFF | 0x01);

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


    protected abstract ChannelFactory newServerSocketChannelFactory(Executor executor);
    protected abstract ChannelFactory newClientSocketChannelFactory(Executor executor);

    @Test
    public void testSpdyEcho() throws Throwable {
        testSpdyEcho(SpdyVersion.SPDY_3_1);
    }

    private void testSpdyEcho(SpdyVersion version) throws Throwable {
        ServerBootstrap sb = new ServerBootstrap(newServerSocketChannelFactory(Executors.newCachedThreadPool()));
        ClientBootstrap cb = new ClientBootstrap(newClientSocketChannelFactory(Executors.newCachedThreadPool()));

        ChannelBuffer frames = createFrames(version.getVersion());

        EchoHandler sh = new EchoHandler(frames, true);
        EchoHandler ch = new EchoHandler(frames, false);

        sb.getPipeline().addLast("decoder", new SpdyFrameDecoder(version));
        sb.getPipeline().addLast("encoder", new SpdyFrameEncoder(version));
        sb.getPipeline().addLast("handler", sh);

        cb.getPipeline().addLast("handler", ch);

        Channel sc = sb.bind(new InetSocketAddress(0));
        int port = ((InetSocketAddress) sc.getLocalAddress()).getPort();

        ChannelFuture ccf = cb.connect(new InetSocketAddress(TestUtil.getLocalHost(), port));
        assertTrue(ccf.awaitUninterruptibly().isSuccess());

        Channel cc = ccf.getChannel();
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
        cb.shutdown();
        sb.shutdown();
        cb.releaseExternalResources();
        sb.releaseExternalResources();

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

    private static class EchoHandler extends SimpleChannelUpstreamHandler {
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        final ChannelBuffer frames;
        volatile int counter;
        final boolean server;

        EchoHandler(ChannelBuffer frames, boolean server) {
            this.frames = frames;
            this.server = server;
        }

        @Override
        public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
                throws Exception {
            channel = e.getChannel();
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
                throws Exception {
            if (server) {
                Channels.write(channel, e.getMessage(), e.getRemoteAddress());
            } else {
                ChannelBuffer m = (ChannelBuffer) e.getMessage();
                byte[] actual = new byte[m.readableBytes()];
                m.getBytes(0, actual);

                int lastIdx = counter;
                for (int i = 0; i < actual.length; i ++) {
                    assertEquals(frames.getByte(ignoredBytes + i + lastIdx), actual[i]);
                }

                counter += actual.length;
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
                throws Exception {
            if (exception.compareAndSet(null, e.getCause())) {
                e.getChannel().close();
            }
        }
    }
}
