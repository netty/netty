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
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.testsuite.util.BogusSslContextFactory;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class SocketStartTlsTest extends AbstractSocketTest {

    private static final LogLevel LOG_LEVEL = LogLevel.TRACE;
    private static EventExecutorGroup executor;

    @BeforeClass
    public static void createExecutor() {
        executor = new DefaultEventExecutorGroup(2);
    }

    @AfterClass
    public static void shutdownExecutor() throws Exception {
        executor.shutdownGracefully().sync();
    }

    @Test(timeout = 30000)
    public void testStartTls() throws Throwable {
        run();
    }

    public void testStartTls(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testStartTls(sb, cb, true);
    }

    @Test(timeout = 30000)
    public void testStartTlsNotAutoRead() throws Throwable {
        run();
    }

    public void testStartTlsNotAutoRead(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testStartTls(sb, cb, false);
    }

    private void testStartTls(ServerBootstrap sb, Bootstrap cb, boolean autoRead) throws Throwable {
        final EventExecutorGroup executor = SocketStartTlsTest.executor;
        final SSLEngine sse = BogusSslContextFactory.getServerContext().createSSLEngine();
        final SSLEngine cse = BogusSslContextFactory.getClientContext().createSSLEngine();

        final StartTlsServerHandler sh = new StartTlsServerHandler(sse, autoRead);
        final StartTlsClientHandler ch = new StartTlsClientHandler(cse, autoRead);

        sb.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel sch) throws Exception {
                ChannelPipeline p = sch.pipeline();
                p.addLast("logger", new LoggingHandler(LOG_LEVEL));
                p.addLast(new LineBasedFrameDecoder(64), new StringDecoder(), new StringEncoder());
                p.addLast(executor, sh);
            }
        });

        cb.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel sch) throws Exception {
                ChannelPipeline p = sch.pipeline();
                p.addLast("logger", new LoggingHandler(LOG_LEVEL));
                p.addLast(new LineBasedFrameDecoder(64), new StringDecoder(), new StringEncoder());
                p.addLast(executor, ch);
            }
        });

        Channel sc = sb.bind().sync().channel();
        Channel cc = cb.connect().sync().channel();

        while (cc.isActive()) {
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

        while (sh.channel.isActive()) {
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
        cc.close().awaitUninterruptibly();
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

    private class StartTlsClientHandler extends SimpleChannelInboundHandler<String> {
        private final SslHandler sslHandler;
        private final boolean autoRead;
        private Future<Channel> handshakeFuture;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();

        StartTlsClientHandler(SSLEngine engine, boolean autoRead) {
            engine.setUseClientMode(true);
            sslHandler = new SslHandler(engine);
            this.autoRead = autoRead;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx)
                throws Exception {
            ctx.writeAndFlush("StartTlsRequest\n");
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
            if ("StartTlsResponse".equals(msg)) {
                ctx.pipeline().addAfter("logger", "ssl", sslHandler);
                handshakeFuture = sslHandler.handshakeFuture();
                ctx.writeAndFlush("EncryptedRequest\n");
                return;
            }

            assertEquals("EncryptedResponse", msg);
            assertNotNull(handshakeFuture);
            assertTrue(handshakeFuture.isSuccess());
            ctx.close();
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
            if (logger.isWarnEnabled()) {
                logger.warn("Unexpected exception from the client side", cause);
            }

            exception.compareAndSet(null, cause);
            ctx.close();
        }
    }

    private class StartTlsServerHandler extends SimpleChannelInboundHandler<String> {
        private final SslHandler sslHandler;
        private final boolean autoRead;
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();

        StartTlsServerHandler(SSLEngine engine, boolean autoRead) {
            engine.setUseClientMode(false);
            sslHandler = new SslHandler(engine, true);
            this.autoRead = autoRead;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            channel = ctx.channel();
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
            if ("StartTlsRequest".equals(msg)) {
                ctx.pipeline().addAfter("logger", "ssl", sslHandler);
                ctx.writeAndFlush("StartTlsResponse\n");
                return;
            }

            assertEquals("EncryptedRequest", msg);
            ctx.writeAndFlush("EncryptedResponse\n");
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
            if (logger.isWarnEnabled()) {
                logger.warn("Unexpected exception from the server side", cause);
            }

            exception.compareAndSet(null, cause);
            ctx.close();
        }
    }
}
