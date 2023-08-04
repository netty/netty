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
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.SSLEngine;
import java.io.File;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SocketStartTlsTest extends AbstractSocketTest {
    private static final String PARAMETERIZED_NAME = "{index}: serverEngine = {0}, clientEngine = {1}";

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SocketStartTlsTest.class);

    private static final LogLevel LOG_LEVEL = LogLevel.TRACE;
    private static final File CERT_FILE;
    private static final File KEY_FILE;
    private static EventExecutorGroup executor;

    static {
        SelfSignedCertificate ssc;
        try {
            ssc = new SelfSignedCertificate();
        } catch (CertificateException e) {
            throw new Error(e);
        }
        CERT_FILE = ssc.certificate();
        KEY_FILE = ssc.privateKey();
    }

    public static Collection<Object[]> data() throws Exception {
        List<SslContext> serverContexts = new ArrayList<SslContext>();
        serverContexts.add(SslContextBuilder.forServer(CERT_FILE, KEY_FILE).sslProvider(SslProvider.JDK).build());

        List<SslContext> clientContexts = new ArrayList<SslContext>();
        clientContexts.add(SslContextBuilder.forClient().sslProvider(SslProvider.JDK).trustManager(CERT_FILE).build());

        boolean hasOpenSsl = OpenSsl.isAvailable();
        if (hasOpenSsl) {
            serverContexts.add(SslContextBuilder.forServer(CERT_FILE, KEY_FILE)
                                                .sslProvider(SslProvider.OPENSSL).build());
            clientContexts.add(SslContextBuilder.forClient().sslProvider(SslProvider.OPENSSL)
                                                .trustManager(CERT_FILE).build());
        } else {
            logger.warn("OpenSSL is unavailable and thus will not be tested.", OpenSsl.unavailabilityCause());
        }

        List<Object[]> params = new ArrayList<Object[]>();
        for (SslContext sc: serverContexts) {
            for (SslContext cc: clientContexts) {
                params.add(new Object[] { sc, cc });
            }
        }
        return params;
    }

    @BeforeAll
    public static void createExecutor() {
        executor = new DefaultEventExecutorGroup(2);
    }

    @AfterAll
    public static void shutdownExecutor() throws Exception {
        executor.shutdownGracefully().sync();
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("data")
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testStartTls(final SslContext serverCtx, final SslContext clientCtx, TestInfo testInfo)
            throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testStartTls(sb, cb, serverCtx, clientCtx);
            }
        });
    }

    public void testStartTls(ServerBootstrap sb, Bootstrap cb,
                             SslContext serverCtx, SslContext clientCtx) throws Throwable {
        testStartTls(sb, cb, serverCtx, clientCtx, true);
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("data")
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testStartTlsNotAutoRead(final SslContext serverCtx, final SslContext clientCtx,
                                        TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testStartTlsNotAutoRead(sb, cb, serverCtx, clientCtx);
            }
        });
    }

    public void testStartTlsNotAutoRead(ServerBootstrap sb, Bootstrap cb,
                                        SslContext serverCtx, SslContext clientCtx) throws Throwable {
        testStartTls(sb, cb, serverCtx, clientCtx, false);
    }

    private void testStartTls(ServerBootstrap sb, Bootstrap cb,
                              SslContext serverCtx, SslContext clientCtx, boolean autoRead) throws Throwable {
        sb.childOption(ChannelOption.AUTO_READ, autoRead);
        cb.option(ChannelOption.AUTO_READ, autoRead);

        final EventExecutorGroup executor = SocketStartTlsTest.executor;
        SSLEngine sse = serverCtx.newEngine(PooledByteBufAllocator.DEFAULT);
        SSLEngine cse = clientCtx.newEngine(PooledByteBufAllocator.DEFAULT);

        final StartTlsServerHandler sh = new StartTlsServerHandler(sse, autoRead);
        final StartTlsClientHandler ch = new StartTlsClientHandler(cse, autoRead);

        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            public void initChannel(Channel sch) throws Exception {
                ChannelPipeline p = sch.pipeline();
                p.addLast("logger", new LoggingHandler(LOG_LEVEL));
                p.addLast(new LineBasedFrameDecoder(64), new StringDecoder(), new StringEncoder());
                p.addLast(executor, sh);
            }
        });

        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            public void initChannel(Channel sch) throws Exception {
                ChannelPipeline p = sch.pipeline();
                p.addLast("logger", new LoggingHandler(LOG_LEVEL));
                p.addLast(new LineBasedFrameDecoder(64), new StringDecoder(), new StringEncoder());
                p.addLast(executor, ch);
            }
        });

        Channel sc = sb.bind().sync().channel();
        Channel cc = cb.connect(sc.localAddress()).sync().channel();

        while (cc.isActive()) {
            if (sh.exception.get() != null) {
                break;
            }
            if (ch.exception.get() != null) {
                break;
            }

            Thread.sleep(50);
        }

        while (sh.channel.isActive()) {
            if (sh.exception.get() != null) {
                break;
            }
            if (ch.exception.get() != null) {
                break;
            }

            Thread.sleep(50);
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

    private static class StartTlsClientHandler extends SimpleChannelInboundHandler<String> {
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
            if (!autoRead) {
                ctx.read();
            }
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

    private static class StartTlsServerHandler extends SimpleChannelInboundHandler<String> {
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
            if (!autoRead) {
                ctx.read();
            }
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
