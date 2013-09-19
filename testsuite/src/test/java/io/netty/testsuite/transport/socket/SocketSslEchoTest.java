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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.testsuite.util.BogusSslContextFactory;
import io.netty.util.concurrent.Future;
import org.junit.Test;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class SocketSslEchoTest extends AbstractSocketTest {

    private static final int FIRST_MESSAGE_SIZE = 16384;
    private static final Random random = new Random();
    static final byte[] data = new byte[1048576];

    static {
        random.nextBytes(data);
    }

    @Test
    public void testSslEcho() throws Throwable {
        run();
    }

    public void testSslEcho(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testSslEcho0(sb, cb, false, false);
    }

    @Test
    public void testSslEchoComposite() throws Throwable {
        run();
    }

    public void testSslEchoComposite(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testSslEcho0(sb, cb, false, true);
    }

    @Test
    public void testSslEchoWithChunkHandler() throws Throwable {
        run();
    }

    public void testSslEchoWithChunkHandler(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testSslEcho0(sb, cb, true, false);
    }

    @Test
    public void testSslEchoWithChunkHandlerComposite() throws Throwable {
        run();
    }

    public void testSslEchoWithChunkHandlerComposite(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testSslEcho0(sb, cb, true, true);
    }

    private void testSslEcho0(ServerBootstrap sb, Bootstrap cb,
                              final boolean chunkWriteHandler, final boolean composite) throws Throwable {
        final EchoHandler sh = new EchoHandler(true, composite);
        final EchoHandler ch = new EchoHandler(false, composite);

        final SSLEngine sse = BogusSslContextFactory.getServerContext().createSSLEngine();
        final SSLEngine cse = BogusSslContextFactory.getClientContext().createSSLEngine();
        sse.setUseClientMode(false);
        cse.setUseClientMode(true);

        sb.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel sch) throws Exception {
                sch.pipeline().addFirst("ssl", new SslHandler(sse));
                if (chunkWriteHandler) {
                    sch.pipeline().addLast(new ChunkedWriteHandler());
                }
                sch.pipeline().addLast("handler", sh);
            }
        });

        cb.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel sch) throws Exception {
                sch.pipeline().addFirst("ssl", new SslHandler(cse));
                if (chunkWriteHandler) {
                    sch.pipeline().addLast(new ChunkedWriteHandler());
                }
                sch.pipeline().addLast("handler", ch);
            }
        });

        Channel sc = sb.bind().sync().channel();
        Channel cc = cb.connect().sync().channel();
        Future<Channel> hf = cc.pipeline().get(SslHandler.class).handshakeFuture();
        cc.writeAndFlush(Unpooled.wrappedBuffer(data, 0, FIRST_MESSAGE_SIZE));
        final AtomicBoolean firstByteWriteFutureDone = new AtomicBoolean();

        hf.sync();

        assertFalse(firstByteWriteFutureDone.get());

        for (int i = FIRST_MESSAGE_SIZE; i < data.length;) {
            int length = Math.min(random.nextInt(1024 * 64), data.length - i);
            ByteBuf buf = Unpooled.wrappedBuffer(data, i, length);
            if (composite) {
                buf = Unpooled.compositeBuffer().addComponent(buf).writerIndex(buf.writerIndex());
            }
            ChannelFuture future = cc.writeAndFlush(buf);
            future.sync();
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

    private class EchoHandler extends SimpleChannelInboundHandler<ByteBuf> {
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        volatile int counter;
        private final boolean server;
        private final boolean composite;

        EchoHandler(boolean server, boolean composite) {
            this.server = server;
            this.composite = composite;
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

            if (channel.parent() != null) {
                ByteBuf buf = Unpooled.wrappedBuffer(actual);
                if (composite) {
                    buf = Unpooled.compositeBuffer().addComponent(buf).writerIndex(buf.writerIndex());
                }
                channel.write(buf);
            }

            counter += actual.length;
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            ctx.flush();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx,
                Throwable cause) throws Exception {
            if (logger.isWarnEnabled()) {
                logger.warn(
                        "Unexpected exception from the " +
                        (server? "server" : "client") + " side", cause);
            }

            exception.compareAndSet(null, cause);
            ctx.close();
        }
    }
}
