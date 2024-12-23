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
package io.netty5.testsuite.transport.socket;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferUtil;
import io.netty5.buffer.DefaultBufferAllocators;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelFutureListeners;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.handler.ssl.ResumableX509ExtendedTrustManager;
import io.netty5.handler.ssl.SslContext;
import io.netty5.handler.ssl.SslContextBuilder;
import io.netty5.handler.ssl.SslHandler;
import io.netty5.handler.ssl.SslHandshakeCompletionEvent;
import io.netty5.handler.ssl.SslProvider;
import io.netty5.pkitesting.CertificateBuilder;
import io.netty5.pkitesting.X509Bundle;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

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

    private static final Logger logger = LoggerFactory.getLogger(SocketSslSessionReuseTest.class);

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

    public static Collection<Object[]> jdkOnly() throws Exception {
        return Collections.singletonList(new Object[] {
          SslContextBuilder.forServer(CERT.toKeyManagerFactory())
                  .sslProvider(SslProvider.JDK)
                  .endpointIdentificationAlgorithm(""),
          SslContextBuilder.forClient()
                  .trustManager(CERT.toTrustManagerFactory())
                  .sslProvider(SslProvider.JDK)
                  .endpointIdentificationAlgorithm("")
        });
    }

    public static Collection<Object[]> jdkAndOpenSSL() throws Exception {
        return Arrays.asList(new Object[]{
                        SslContextBuilder.forServer(CERT.toKeyManagerFactory())
                                .sslProvider(SslProvider.JDK)
                                .endpointIdentificationAlgorithm(""),
                        SslContextBuilder.forClient()
                                .trustManager(CERT.toTrustManagerFactory())
                                .sslProvider(SslProvider.JDK)
                                .endpointIdentificationAlgorithm("")
                },
                new Object[]{
                        SslContextBuilder.forServer(CERT.getKeyPair().getPrivate(), CERT.getCertificatePath())
                                .sslProvider(SslProvider.OPENSSL)
                                .endpointIdentificationAlgorithm(""),
                        SslContextBuilder.forClient()
                                .trustManager(CERT.toTrustManagerFactory())
                                .sslProvider(SslProvider.OPENSSL)
                                .endpointIdentificationAlgorithm("")
                });
    }

    @ParameterizedTest(name = "{index}: serverEngine = {0}, clientEngine = {1}")
    @MethodSource("jdkOnly")
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testSslSessionReuse(SslContextBuilder serverCtx, SslContextBuilder clientCtx, TestInfo testInfo)
            throws Throwable {
        run(testInfo, (sb, cb) -> testSslSessionReuse(sb, cb, serverCtx.build(), clientCtx.build()));
    }

    public void testSslSessionReuse(ServerBootstrap sb, Bootstrap cb,
                                    SslContext serverCtx, SslContext clientCtx) throws Throwable {
        final ReadAndDiscardHandler sh = new ReadAndDiscardHandler(true, true);
        final ReadAndDiscardHandler ch = new ReadAndDiscardHandler(false, true);
        final String[] protocols = { "TLSv1", "TLSv1.1", "TLSv1.2" };

        sb.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel sch) throws Exception {
                SSLEngine engine = serverCtx.newEngine(sch.bufferAllocator());
                engine.setUseClientMode(false);
                engine.setEnabledProtocols(protocols);

                sch.pipeline().addLast(new SslHandler(engine));
                sch.pipeline().addLast(sh);
            }
        });
        final Channel sc = sb.bind().asStage().get();

        cb.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel sch) throws Exception {
                InetSocketAddress serverAddr = (InetSocketAddress) sc.localAddress();
                SSLEngine engine = clientCtx.newEngine(
                        sch.bufferAllocator(), serverAddr.getHostString(), serverAddr.getPort());
                engine.setUseClientMode(true);
                engine.setEnabledProtocols(protocols);

                sch.pipeline().addLast(new SslHandler(engine));
                sch.pipeline().addLast(ch);
            }
        });

        try {
            SSLSessionContext clientSessionCtx = clientCtx.sessionContext();
            Buffer msg = DefaultBufferAllocators.preferredAllocator().copyOf(new byte[] { 0xa, 0xb, 0xc, 0xd });
            Channel cc = cb.connect(sc.localAddress()).asStage().get();
            cc.writeAndFlush(msg).addListener(cc, ChannelFutureListeners.CLOSE).asStage().sync();
            cc.closeFuture().asStage().sync();
            rethrowHandlerExceptions(sh, ch);
            Set<String> sessions = sessionIdSet(clientSessionCtx.getIds());

            msg = DefaultBufferAllocators.preferredAllocator().copyOf(new byte[] { 0xa, 0xb, 0xc, 0xd });
            cc = cb.connect(sc.localAddress()).asStage().get();
            cc.writeAndFlush(msg).addListener(cc, ChannelFutureListeners.CLOSE).asStage().sync();
            cc.closeFuture().asStage().sync();
            assertEquals(sessions, sessionIdSet(clientSessionCtx.getIds()), "Expected no new sessions");
            rethrowHandlerExceptions(sh, ch);
        } finally {
            sc.close().asStage().await();
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
            public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) throws Exception {
                if (evt instanceof SslHandshakeCompletionEvent) {
                    SslHandshakeCompletionEvent handshakeCompletionEvent = (SslHandshakeCompletionEvent) evt;
                    if (handshakeCompletionEvent.isSuccess()) {
                        SSLSession session = ctx.pipeline().get(SslHandler.class).engine().getSession();
                        assertTrue(sessionValue.offer(String.valueOf(session.getValue("key"))));
                    } else {
                        logger.error("SSL handshake failed", handshakeCompletionEvent.cause());
                    }
                }
                super.channelInboundEvent(ctx, evt);
            }
        };

        sb.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel sch) throws Exception {
                sch.pipeline().addLast(serverContext.newHandler(sch.bufferAllocator()));
                sch.pipeline().addLast(sh);
            }
        });
        final Channel sc = sb.bind().asStage().get();

        cb.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel sch) throws Exception {
                InetSocketAddress serverAddr = (InetSocketAddress) sc.localAddress();
                SslHandler sslHandler = clientContext.newHandler(
                        sch.bufferAllocator(), serverAddr.getHostString(), serverAddr.getPort());

                sch.pipeline().addLast(sslHandler);
                sch.pipeline().addLast(ch);
            }
        });

        try {
            Buffer msg = DefaultBufferAllocators.preferredAllocator().copyOf(new byte[] { 0xa, 0xb, 0xc, 0xd });
            Channel cc = cb.connect(sc.localAddress()).asStage().get();
            cc.writeAndFlush(msg).addListener(cc, ChannelFutureListeners.CLOSE).asStage().sync();
            cc.closeFuture().asStage().sync();
            rethrowHandlerExceptions(sh, ch);
            assertEquals("value", sessionValue.poll(10, TimeUnit.SECONDS));

            msg = DefaultBufferAllocators.preferredAllocator().copyOf(new byte[] { 0xa, 0xb, 0xc, 0xd });
            cc = cb.connect(sc.localAddress()).asStage().get();
            cc.writeAndFlush(msg).addListener(cc, ChannelFutureListeners.CLOSE).asStage().sync();
            cc.closeFuture().asStage().sync();
            rethrowHandlerExceptions(sh, ch);
            assertEquals("value", sessionValue.poll(10, TimeUnit.SECONDS));
        } finally {
            sc.close().asStage().await();
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
        Set<String> idSet = new HashSet<>();
        byte[] id;
        while (sessionIds.hasMoreElements()) {
            id = sessionIds.nextElement();
            idSet.add(BufferUtil.hexDump(id));
        }
        return idSet;
    }

    private static class ReadAndDiscardHandler extends SimpleChannelInboundHandler<Buffer> {
        final AtomicReference<Throwable> exception = new AtomicReference<>();
        private final boolean server;
        private final boolean autoRead;

        ReadAndDiscardHandler(boolean server, boolean autoRead) {
            this.server = server;
            this.autoRead = autoRead;
        }

        @Override
        public boolean isSharable() {
            return true;
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, Buffer in) throws Exception {
            in.skipReadableBytes(in.readableBytes());
            ctx.close();
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
        public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
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
