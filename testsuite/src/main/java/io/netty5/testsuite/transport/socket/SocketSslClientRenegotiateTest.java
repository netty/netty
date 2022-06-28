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
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.handler.codec.DecoderException;
import io.netty5.handler.ssl.OpenSsl;
import io.netty5.handler.ssl.SslContext;
import io.netty5.handler.ssl.SslContextBuilder;
import io.netty5.handler.ssl.SslHandler;
import io.netty5.handler.ssl.SslHandshakeCompletionEvent;
import io.netty5.handler.ssl.SslProvider;
import io.netty5.handler.ssl.util.SelfSignedCertificate;
import io.netty5.util.concurrent.Future;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.SSLHandshakeException;
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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

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
        List<SslContext> serverContexts = new ArrayList<>();
        List<SslContext> clientContexts = new ArrayList<>();
        clientContexts.add(
          SslContextBuilder.forClient()
            .trustManager(CERT_FILE)
            .sslProvider(SslProvider.JDK)
            .build()
        );

        boolean hasOpenSsl = OpenSsl.isAvailable();
        if (hasOpenSsl) {
            serverContexts.add(
              SslContextBuilder.forServer(CERT_FILE, KEY_FILE)
                .sslProvider(SslProvider.OPENSSL)
                .build()
            );
        } else {
            logger.warn("OpenSSL is unavailable and thus will not be tested.", OpenSsl.unavailabilityCause());
        }

        List<Object[]> params = new ArrayList<>();
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

    private final AtomicReference<Throwable> clientException = new AtomicReference<>();
    private final AtomicReference<Throwable> serverException = new AtomicReference<>();

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
    public void testSslRenegotiationRejected(SslContext serverCtx, SslContext clientCtx, boolean delegate,
                                             TestInfo testInfo) throws Throwable {
        // BoringSSL does not support renegotiation intentionally.
        assumeFalse("BoringSSL".equals(OpenSsl.versionString()));
        run(testInfo, (sb, cb) -> testSslRenegotiationRejected(sb, cb, serverCtx, clientCtx, delegate));
    }

    private static SslHandler newSslHandler(SslContext sslCtx, BufferAllocator allocator, Executor executor) {
        if (executor == null) {
            return sslCtx.newHandler(allocator);
        } else {
            return sslCtx.newHandler(allocator, executor);
        }
    }

    public void testSslRenegotiationRejected(ServerBootstrap sb, Bootstrap cb, SslContext serverCtx,
                                             SslContext clientCtx, boolean delegate) throws Throwable {
        reset();

        final ExecutorService executorService = delegate ? Executors.newCachedThreadPool() : null;

        try {
            sb.childHandler(new ChannelInitializer<>() {
                @Override
                public void initChannel(Channel sch) throws Exception {
                    serverChannel = sch;
                    serverSslHandler = newSslHandler(serverCtx, sch.bufferAllocator(), executorService);
                    // As we test renegotiation we should use a protocol that support it.
                    serverSslHandler.engine().setEnabledProtocols(new String[]{"TLSv1.2"});
                    sch.pipeline().addLast("ssl", serverSslHandler);
                    sch.pipeline().addLast("handler", serverHandler);
                }
            });

            cb.handler(new ChannelInitializer<>() {
                @Override
                public void initChannel(Channel sch) throws Exception {
                    clientChannel = sch;
                    clientSslHandler = newSslHandler(clientCtx, sch.bufferAllocator(), executorService);
                    // As we test renegotiation we should use a protocol that support it.
                    clientSslHandler.engine().setEnabledProtocols(new String[]{"TLSv1.2"});
                    sch.pipeline().addLast("ssl", clientSslHandler);
                    sch.pipeline().addLast("handler", clientHandler);
                }
            });

            Channel sc = sb.bind().get();
            cb.connect(sc.localAddress()).sync();

            Future<Channel> clientHandshakeFuture = clientSslHandler.handshakeFuture();
            clientHandshakeFuture.sync();

            String renegotiation = clientSslHandler.engine().getEnabledCipherSuites()[0];
            // Use the first previous enabled ciphersuite and try to renegotiate.
            clientSslHandler.engine().setEnabledCipherSuites(new String[]{renegotiation});
            clientSslHandler.renegotiate().await();
            serverChannel.close().await();
            clientChannel.close().await();
            sc.close().await();
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

    private static final class TestHandler extends SimpleChannelInboundHandler<Buffer> {

        private final AtomicReference<Throwable> exception;
        private int handshakeCounter;

        TestHandler(AtomicReference<Throwable> exception) {
            this.exception = exception;
        }

        @Override
        public boolean isSharable() {
            return true;
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            ctx.flush();
        }

        @Override
        public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            exception.compareAndSet(null, cause);
            ctx.close();
        }

        @Override
        public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) throws Exception {
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
        public void messageReceived(ChannelHandlerContext ctx, Buffer in) throws Exception { }
    }
}
