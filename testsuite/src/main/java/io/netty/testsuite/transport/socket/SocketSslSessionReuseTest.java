/*
 * Copyright 2015 The Netty Project
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
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.ResumableX509ExtendedTrustManager;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.handler.ssl.SslProvider;
import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SocketSslSessionReuseTest extends AbstractSocketTest {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SocketSslSessionReuseTest.class);

    private static final File CERT_FILE;
    private static final File KEY_FILE;

    static {
        try {
            X509Bundle cert = new CertificateBuilder()
                    .subject("cn=localhost")
                    .setIsCertificateAuthority(true)
                    .buildSelfSigned();
            CERT_FILE = cert.toTempCertChainPem();
            KEY_FILE = cert.toTempPrivateKeyPem();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static Collection<Object[]> jdkOnly() throws Exception {
        return Collections.singleton(new Object[]{
                SslContextBuilder.forServer(CERT_FILE, KEY_FILE).sslProvider(SslProvider.JDK),
                SslContextBuilder.forClient().trustManager(CERT_FILE).sslProvider(SslProvider.JDK)
                        .endpointIdentificationAlgorithm(null)
        });
    }

    public static Collection<Object[]> jdkAndOpenSSL() throws Exception {
        return Arrays.asList(new Object[]{
                        SslContextBuilder.forServer(CERT_FILE, KEY_FILE).sslProvider(SslProvider.JDK),
                        SslContextBuilder.forClient().trustManager(CERT_FILE).sslProvider(SslProvider.JDK)
                                .endpointIdentificationAlgorithm(null)
                },
                new Object[]{
                        SslContextBuilder.forServer(CERT_FILE, KEY_FILE).sslProvider(SslProvider.OPENSSL),
                        SslContextBuilder.forClient().trustManager(CERT_FILE).sslProvider(SslProvider.OPENSSL)
                                .endpointIdentificationAlgorithm(null)
                });
    }

    @ParameterizedTest(name = "{index}: serverEngine = {0}, clientEngine = {1}")
    @MethodSource("jdkOnly")
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testSslSessionReuse(
            final SslContextBuilder serverCtx, final SslContextBuilder clientCtx, TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testSslSessionReuse(sb, cb, serverCtx.build(), clientCtx.build());
            }
        });
    }

    public void testSslSessionReuse(ServerBootstrap sb, Bootstrap cb,
                                    final SslContext serverCtx, final SslContext clientCtx) throws Throwable {
        final ReadAndDiscardHandler sh = new ReadAndDiscardHandler(true, true);
        final ReadAndDiscardHandler ch = new ReadAndDiscardHandler(false, true);
        final String[] protocols = { "TLSv1", "TLSv1.1", "TLSv1.2" };

        sb.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel sch) throws Exception {
                SSLEngine engine = serverCtx.newEngine(sch.alloc());
                engine.setUseClientMode(false);
                engine.setEnabledProtocols(protocols);

                sch.pipeline().addLast(new SslHandler(engine));
                sch.pipeline().addLast(sh);
            }
        });
        final Channel sc = sb.bind().sync().channel();

        cb.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel sch) throws Exception {
                InetSocketAddress serverAddr = (InetSocketAddress) sc.localAddress();
                SSLEngine engine = clientCtx.newEngine(sch.alloc(), serverAddr.getHostString(), serverAddr.getPort());
                engine.setUseClientMode(true);
                engine.setEnabledProtocols(protocols);

                sch.pipeline().addLast(new SslHandler(engine));
                sch.pipeline().addLast(ch);
            }
        });

        try {
            SSLSessionContext clientSessionCtx = clientCtx.sessionContext();
            ByteBuf msg = Unpooled.wrappedBuffer(new byte[] { 0xa, 0xb, 0xc, 0xd }, 0, 4);
            Channel cc = cb.connect(sc.localAddress()).sync().channel();
            cc.writeAndFlush(msg).addListener(ChannelFutureListener.CLOSE).sync();
            cc.closeFuture().sync();
            rethrowHandlerExceptions(sh, ch);
            Set<String> sessions = sessionIdSet(clientSessionCtx.getIds());

            msg = Unpooled.wrappedBuffer(new byte[] { 0xa, 0xb, 0xc, 0xd }, 0, 4);
            cc = cb.connect(sc.localAddress()).sync().channel();
            cc.writeAndFlush(msg).addListener(ChannelFutureListener.CLOSE).sync();
            cc.closeFuture().sync();
            assertEquals(sessions, sessionIdSet(clientSessionCtx.getIds()), "Expected no new sessions");
            rethrowHandlerExceptions(sh, ch);
        } finally {
            sc.close().awaitUninterruptibly();
        }
    }

    @ParameterizedTest(name = "{index}: serverEngine = {0}, clientEngine = {1}")
    @MethodSource("jdkAndOpenSSL")
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testSslSessionTrustManagerResumption(
            final SslContextBuilder serverCtx, final SslContextBuilder clientCtx, TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testSslSessionTrustManagerResumption(sb, cb, serverCtx, clientCtx);
            }
        });
    }

    public void testSslSessionTrustManagerResumption(
            ServerBootstrap sb, Bootstrap cb,
            SslContextBuilder serverCtxBldr, final SslContextBuilder clientCtxBldr) throws Throwable {
        final String[] protocols = { "TLSv1", "TLSv1.1", "TLSv1.2" };
        serverCtxBldr.protocols(protocols);
        clientCtxBldr.protocols(protocols);
        TrustManager clientTrustManager = new SessionSettingTrustManager();
        clientCtxBldr.trustManager(clientTrustManager);
        final SslContext serverContext = serverCtxBldr.build();
        final SslContext clientContext = clientCtxBldr.build();

        final BlockingQueue<String> sessionValue = new LinkedBlockingQueue<String>();
        final ReadAndDiscardHandler sh = new ReadAndDiscardHandler(true, true);
        final ReadAndDiscardHandler ch = new ReadAndDiscardHandler(false, true) {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                if (evt instanceof SslHandshakeCompletionEvent) {
                    SslHandshakeCompletionEvent handshakeCompletionEvent = (SslHandshakeCompletionEvent) evt;
                    if (handshakeCompletionEvent.isSuccess()) {
                        SSLSession session = ctx.pipeline().get(SslHandler.class).engine().getSession();
                        assertTrue(sessionValue.offer(String.valueOf(session.getValue("key"))));
                    } else {
                        logger.error("SSL handshake failed", handshakeCompletionEvent.cause());
                    }
                }
                super.userEventTriggered(ctx, evt);
            }
        };

        sb.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel sch) throws Exception {
                sch.pipeline().addLast(serverContext.newHandler(sch.alloc()));
                sch.pipeline().addLast(sh);
            }
        });
        final Channel sc = sb.bind().sync().channel();

        cb.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel sch) throws Exception {
                InetSocketAddress serverAddr = (InetSocketAddress) sc.localAddress();
                SslHandler sslHandler = clientContext.newHandler(
                        sch.alloc(), serverAddr.getHostString(), serverAddr.getPort());

                sch.pipeline().addLast(sslHandler);
                sch.pipeline().addLast(ch);
            }
        });

        try {
            ByteBuf msg = Unpooled.wrappedBuffer(new byte[] { 0xa, 0xb, 0xc, 0xd }, 0, 4);
            Channel cc = cb.connect(sc.localAddress()).sync().channel();
            cc.writeAndFlush(msg).addListener(ChannelFutureListener.CLOSE).sync();
            cc.closeFuture().sync();
            rethrowHandlerExceptions(sh, ch);
            assertEquals("value", sessionValue.poll(10, TimeUnit.SECONDS));

            msg = Unpooled.wrappedBuffer(new byte[] { 0xa, 0xb, 0xc, 0xd }, 0, 4);
            cc = cb.connect(sc.localAddress()).sync().channel();
            cc.writeAndFlush(msg).addListener(ChannelFutureListener.CLOSE).sync();
            cc.closeFuture().sync();
            rethrowHandlerExceptions(sh, ch);
            assertEquals("value", sessionValue.poll(10, TimeUnit.SECONDS));
        } finally {
            sc.close().awaitUninterruptibly();
        }
    }

    private static void rethrowHandlerExceptions(ReadAndDiscardHandler sh, ReadAndDiscardHandler ch) throws Throwable {
        if (sh.exception.get() != null && !(sh.exception.get() instanceof IOException)) {
            throw new ExecutionException(sh.exception.get());
        }
        if (ch.exception.get() != null && !(ch.exception.get() instanceof IOException)) {
            throw new ExecutionException(ch.exception.get());
        }
        if (sh.exception.get() != null) {
            throw new ExecutionException(sh.exception.get());
        }
        if (ch.exception.get() != null) {
            throw new ExecutionException(ch.exception.get());
        }
    }

    private static Set<String> sessionIdSet(Enumeration<byte[]> sessionIds) {
        Set<String> idSet = new HashSet<String>();
        byte[] id;
        while (sessionIds.hasMoreElements()) {
            id = sessionIds.nextElement();
            idSet.add(ByteBufUtil.hexDump(Unpooled.wrappedBuffer(id)));
        }
        return idSet;
    }

    @Sharable
    private static class ReadAndDiscardHandler extends SimpleChannelInboundHandler<ByteBuf> {
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        private final boolean server;
        private final boolean autoRead;

        ReadAndDiscardHandler(boolean server, boolean autoRead) {
            this.server = server;
            this.autoRead = autoRead;
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
            in.skipBytes(in.readableBytes());
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
            if (logger.isWarnEnabled()) {
                logger.warn(
                        "Unexpected exception from the " +
                        (server? "server" : "client") + " side", cause);
            }

            exception.compareAndSet(null, cause);
            ctx.close();
        }
    }

    private static final class SessionSettingTrustManager extends X509ExtendedTrustManager
            implements ResumableX509ExtendedTrustManager {
        @Override
        public void resumeServerTrusted(X509Certificate[] chain, SSLEngine engine) throws CertificateException {
            engine.getSession().putValue("key", "value");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            engine.getHandshakeSession().putValue("key", "value");
        }

        @Override
        public void resumeClientTrusted(X509Certificate[] chain, SSLEngine engine) throws CertificateException {
            throw new CertificateException("Unsupported operation");
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            throw new CertificateException("Unsupported operation");
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            throw new CertificateException("Unsupported operation");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            throw new CertificateException("Unsupported operation");
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new CertificateException("Unsupported operation");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new CertificateException("Unsupported operation");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
