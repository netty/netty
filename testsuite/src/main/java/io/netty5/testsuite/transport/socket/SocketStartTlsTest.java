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
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.handler.codec.LineBasedFrameDecoder;
import io.netty5.handler.codec.string.StringDecoder;
import io.netty5.handler.codec.string.StringEncoder;
import io.netty5.handler.logging.LogLevel;
import io.netty5.handler.logging.LoggingHandler;
import io.netty5.handler.ssl.OpenSsl;
import io.netty5.handler.ssl.SslContext;
import io.netty5.handler.ssl.SslContextBuilder;
import io.netty5.handler.ssl.SslHandler;
import io.netty5.handler.ssl.SslProvider;
import io.netty5.pkitesting.CertificateBuilder;
import io.netty5.pkitesting.X509Bundle;
import io.netty5.util.concurrent.Future;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty5.buffer.DefaultBufferAllocators.offHeapAllocator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SocketStartTlsTest extends AbstractSocketTest {
    private static final String PARAMETERIZED_NAME = "{index}: serverEngine = {0}, clientEngine = {1}";

    private static final Logger logger = LoggerFactory.getLogger(SocketStartTlsTest.class);

    private static final LogLevel LOG_LEVEL = LogLevel.TRACE;
    private static final X509Bundle CERT;

    static {
        try {
            CERT = new CertificateBuilder()
                    .subject("cn=localhost")
                    .setIsCertificateAuthority(true)
                    .buildSelfSigned();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static Collection<Object[]> data() throws Exception {
        List<SslContext> serverContexts = new ArrayList<>();
        serverContexts.add(SslContextBuilder.forServer(CERT.toKeyManagerFactory())
                .sslProvider(SslProvider.JDK).build());

        List<SslContext> clientContexts = new ArrayList<>();
        clientContexts.add(SslContextBuilder.forClient()
                .sslProvider(SslProvider.JDK)
                .trustManager(CERT.toTrustManagerFactory())
                .endpointIdentificationAlgorithm(null)
                .build());
        boolean hasOpenSsl = OpenSsl.isAvailable();
        if (hasOpenSsl) {
            serverContexts.add(SslContextBuilder.forServer(CERT.getKeyPair().getPrivate(), CERT.getCertificatePath())
                                                .sslProvider(SslProvider.OPENSSL).build());
            clientContexts.add(SslContextBuilder.forClient()
                    .sslProvider(SslProvider.OPENSSL)
                    .trustManager(CERT.toTrustManagerFactory())
                    .endpointIdentificationAlgorithm(null)
                    .build());
        } else {
            logger.warn("OpenSSL is unavailable and thus will not be tested.", OpenSsl.unavailabilityCause());
        }

        List<Object[]> params = new ArrayList<>();
        for (SslContext sc: serverContexts) {
            for (SslContext cc: clientContexts) {
                params.add(new Object[] { sc, cc });
            }
        }
        return params;
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("data")
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testStartTls(SslContext serverCtx, SslContext clientCtx, TestInfo testInfo) throws Throwable {
        run(testInfo, (sb, cb) -> testStartTls(sb, cb, serverCtx, clientCtx));
    }

    public static void testStartTls(ServerBootstrap sb, Bootstrap cb,
                                    SslContext serverCtx, SslContext clientCtx) throws Throwable {
        testStartTls(sb, cb, serverCtx, clientCtx, true);
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("data")
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testStartTlsNotAutoRead(SslContext serverCtx, SslContext clientCtx,
                                        TestInfo testInfo) throws Throwable {
        run(testInfo, (sb, cb) -> testStartTlsNotAutoRead(sb, cb, serverCtx, clientCtx));
    }

    public static void testStartTlsNotAutoRead(ServerBootstrap sb, Bootstrap cb,
                                               SslContext serverCtx, SslContext clientCtx) throws Throwable {
        testStartTls(sb, cb, serverCtx, clientCtx, false);
    }

    private static void testStartTls(ServerBootstrap sb, Bootstrap cb,
                                     SslContext serverCtx, SslContext clientCtx, boolean autoRead) throws Throwable {
        sb.childOption(ChannelOption.AUTO_READ, autoRead);
        cb.option(ChannelOption.AUTO_READ, autoRead);

        SSLEngine sse = serverCtx.newEngine(offHeapAllocator());
        SSLEngine cse = clientCtx.newEngine(offHeapAllocator());

        final StartTlsServerHandler sh = new StartTlsServerHandler(sse, autoRead);
        final StartTlsClientHandler ch = new StartTlsClientHandler(cse, autoRead);

        sb.childHandler(new ChannelInitializer<>() {
            @Override
            public void initChannel(Channel sch) {
                ChannelPipeline p = sch.pipeline();
                p.addLast("logger", new LoggingHandler(LOG_LEVEL));
                p.addLast(new LineBasedFrameDecoder(64));
                p.addLast(new StringDecoder(), new StringEncoder());
                p.addLast(sh);
            }
        });

        cb.handler(new ChannelInitializer<>() {
            @Override
            public void initChannel(Channel sch) {
                ChannelPipeline p = sch.pipeline();
                p.addLast("logger", new LoggingHandler(LOG_LEVEL));
                p.addLast(new LineBasedFrameDecoder(64));
                p.addLast(new StringDecoder(), new StringEncoder());
                p.addLast(ch);
            }
        });

        Channel sc = sb.bind().asStage().get();
        Channel cc = cb.connect(sc.localAddress()).asStage().get();

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

        sh.channel.close().asStage().await();
        cc.close().asStage().await();
        sc.close().asStage().await();

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
        final AtomicReference<Throwable> exception = new AtomicReference<>();

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
        public void messageReceived(ChannelHandlerContext ctx, String msg) throws Exception {
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
        public void channelExceptionCaught(ChannelHandlerContext ctx,
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
        final AtomicReference<Throwable> exception = new AtomicReference<>();

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
        public void messageReceived(ChannelHandlerContext ctx, String msg) throws Exception {
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
        public void channelExceptionCaught(ChannelHandlerContext ctx,
                                           Throwable cause) throws Exception {
            if (logger.isWarnEnabled()) {
                logger.warn("Unexpected exception from the server side", cause);
            }

            exception.compareAndSet(null, cause);
            ctx.close();
        }
    }
}
