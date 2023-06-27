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
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.ssl.JdkSslClientContext;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.OpenSslServerContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.nio.channels.ClosedChannelException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLHandshakeException;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class SocketSslClientRenegotiateTest extends AbstractSocketTest {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(
            SocketSslClientRenegotiateTest.class);
    private static final File CERT_FILE;
    private static final File KEY_FILE;

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

    private static boolean openSslNotAvailable() {
        return !OpenSsl.isAvailable();
    }

    public static Collection<Object[]> data() throws Exception {
        List<SslContext> serverContexts = new ArrayList<SslContext>();
        List<SslContext> clientContexts = new ArrayList<SslContext>();
        clientContexts.add(new JdkSslClientContext(CERT_FILE));

        boolean hasOpenSsl = OpenSsl.isAvailable();
        if (hasOpenSsl) {
            OpenSslServerContext context = new OpenSslServerContext(CERT_FILE, KEY_FILE);
            serverContexts.add(context);
        } else {
            logger.warn("OpenSSL is unavailable and thus will not be tested.", OpenSsl.unavailabilityCause());
        }

        List<Object[]> params = new ArrayList<Object[]>();
        for (SslContext sc: serverContexts) {
            for (SslContext cc: clientContexts) {
                for (int i = 0; i < 32; i++) {
                    params.add(new Object[] { sc, cc, true});
                    params.add(new Object[] { sc, cc, false});
                }
            }
        }

        return params;
    }

    private final AtomicReference<Throwable> clientException = new AtomicReference<Throwable>();
    private final AtomicReference<Throwable> serverException = new AtomicReference<Throwable>();

    private volatile Channel clientChannel;
    private volatile Channel serverChannel;

    private volatile SslHandler clientSslHandler;
    private volatile SslHandler serverSslHandler;

    private final TestHandler clientHandler = new TestHandler(clientException);

    private final TestHandler serverHandler = new TestHandler(serverException);

    @DisabledIf("openSslNotAvailable")
    @ParameterizedTest(name = "{index}: serverEngine = {0}, clientEngine = {1}, delegate = {2}")
    @MethodSource("data")
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testSslRenegotiationRejected(final SslContext serverCtx, final SslContext clientCtx,
                                             final boolean delegate, TestInfo testInfo) throws Throwable {
        // BoringSSL does not support renegotiation intentionally.
        assumeFalse("BoringSSL".equals(OpenSsl.versionString()));
        assumeTrue(OpenSsl.isAvailable());
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testSslRenegotiationRejected(sb, cb, serverCtx, clientCtx, delegate);
            }
        });
    }

    private static SslHandler newSslHandler(SslContext sslCtx, ByteBufAllocator allocator, Executor executor) {
        if (executor == null) {
            return sslCtx.newHandler(allocator);
        } else {
            return sslCtx.newHandler(allocator, executor);
        }
    }

    public void testSslRenegotiationRejected(ServerBootstrap sb, Bootstrap cb, final SslContext serverCtx,
                                             final SslContext clientCtx, boolean delegate) throws Throwable {
        reset();

        final ExecutorService executorService = delegate ? Executors.newCachedThreadPool() : null;

        try {
            sb.childHandler(new ChannelInitializer<Channel>() {
                @Override
                @SuppressWarnings("deprecation")
                public void initChannel(Channel sch) throws Exception {
                    serverChannel = sch;
                    serverSslHandler = newSslHandler(serverCtx, sch.alloc(), executorService);
                    // As we test renegotiation we should use a protocol that support it.
                    serverSslHandler.engine().setEnabledProtocols(new String[]{"TLSv1.2"});
                    sch.pipeline().addLast("ssl", serverSslHandler);
                    sch.pipeline().addLast("handler", serverHandler);
                }
            });

            cb.handler(new ChannelInitializer<Channel>() {
                @Override
                @SuppressWarnings("deprecation")
                public void initChannel(Channel sch) throws Exception {
                    clientChannel = sch;
                    clientSslHandler = newSslHandler(clientCtx, sch.alloc(), executorService);
                    // As we test renegotiation we should use a protocol that support it.
                    clientSslHandler.engine().setEnabledProtocols(new String[]{"TLSv1.2"});
                    sch.pipeline().addLast("ssl", clientSslHandler);
                    sch.pipeline().addLast("handler", clientHandler);
                }
            });

            Channel sc = sb.bind().sync().channel();
            cb.connect(sc.localAddress()).sync();

            Future<Channel> clientHandshakeFuture = clientSslHandler.handshakeFuture();
            clientHandshakeFuture.sync();

            String renegotiation = clientSslHandler.engine().getEnabledCipherSuites()[0];
            // Use the first previous enabled ciphersuite and try to renegotiate.
            clientSslHandler.engine().setEnabledCipherSuites(new String[]{renegotiation});
            clientSslHandler.renegotiate().await();
            serverChannel.close().awaitUninterruptibly();
            clientChannel.close().awaitUninterruptibly();
            sc.close().awaitUninterruptibly();
            try {
                if (serverException.get() != null) {
                    throw serverException.get();
                }
                fail();
            } catch (DecoderException e) {
                assertTrue(e.getCause() instanceof SSLHandshakeException);
            }
            if (clientException.get() != null) {
                throw clientException.get();
            }
        } finally {
            if (executorService != null) {
                executorService.shutdown();
            }
        }
    }

    private void reset() {
        clientException.set(null);
        serverException.set(null);
        clientHandler.handshakeCounter = 0;
        serverHandler.handshakeCounter = 0;
        clientChannel = null;
        serverChannel = null;

        clientSslHandler = null;
        serverSslHandler = null;
    }

    @Sharable
    private static final class TestHandler extends SimpleChannelInboundHandler<ByteBuf> {

        protected final AtomicReference<Throwable> exception;
        private int handshakeCounter;

        TestHandler(AtomicReference<Throwable> exception) {
            this.exception = exception;
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            ctx.flush();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            exception.compareAndSet(null, cause);
            ctx.close();
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof SslHandshakeCompletionEvent) {
                SslHandshakeCompletionEvent handshakeEvt = (SslHandshakeCompletionEvent) evt;
                if (handshakeCounter == 0) {
                    handshakeCounter++;
                    if (handshakeEvt.cause() != null) {
                        logger.warn("Handshake failed:", handshakeEvt.cause());
                    }
                    assertSame(SslHandshakeCompletionEvent.SUCCESS, evt);
                } else {
                    if (ctx.channel().parent() == null) {
                        assertTrue(handshakeEvt.cause() instanceof ClosedChannelException);
                    }
                }
            }
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, ByteBuf in) throws Exception { }
    }
}
