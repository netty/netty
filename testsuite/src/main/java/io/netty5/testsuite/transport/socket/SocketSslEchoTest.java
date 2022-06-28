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
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.DefaultBufferAllocators;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.handler.ssl.OpenSsl;
import io.netty5.handler.ssl.OpenSslContext;
import io.netty5.handler.ssl.SslContext;
import io.netty5.handler.ssl.SslContextBuilder;
import io.netty5.handler.ssl.SslHandler;
import io.netty5.handler.ssl.SslHandshakeCompletionEvent;
import io.netty5.handler.ssl.SslProvider;
import io.netty5.handler.ssl.util.SelfSignedCertificate;
import io.netty5.handler.stream.ChunkedWriteHandler;
import io.netty5.testsuite.util.TestUtils;
import io.netty5.util.concurrent.Future;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;
import org.junit.jupiter.api.AfterAll;
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
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class SocketSslEchoTest extends AbstractSocketTest {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SocketSslEchoTest.class);

    private static final int FIRST_MESSAGE_SIZE = 16384;
    private static final Random random = new Random();
    private static final File CERT_FILE;
    private static final File KEY_FILE;
    static final byte[] data = new byte[1048576];

    static {
        random.nextBytes(data);

        SelfSignedCertificate ssc;
        try {
            ssc = new SelfSignedCertificate();
        } catch (CertificateException e) {
            throw new Error(e);
        }
        CERT_FILE = ssc.certificate();
        KEY_FILE = ssc.privateKey();
    }

    private static final BufferAllocator bufferAllocator = DefaultBufferAllocators.preferredAllocator();

    protected enum RenegotiationType {
        NONE, // no renegotiation
        CLIENT_INITIATED, // renegotiation from client
        SERVER_INITIATED, // renegotiation from server
    }

    protected static class Renegotiation {
        static final Renegotiation NONE = new Renegotiation(RenegotiationType.NONE, null);

        final RenegotiationType type;
        final String cipherSuite;

        Renegotiation(RenegotiationType type, String cipherSuite) {
            this.type = type;
            this.cipherSuite = cipherSuite;
        }

        @Override
        public String toString() {
            if (type == RenegotiationType.NONE) {
                return "NONE";
            }

            return type + "(" + cipherSuite + ')';
        }
    }

    public static Collection<Object[]> data() throws Exception {
        List<SslContext> serverContexts = new ArrayList<>();
        serverContexts.add(SslContextBuilder.forServer(CERT_FILE, KEY_FILE)
                                            .sslProvider(SslProvider.JDK)
                                            // As we test renegotiation we should use a protocol that support it.
                                            .protocols("TLSv1.2")
                                            .build());

        List<SslContext> clientContexts = new ArrayList<>();
        clientContexts.add(SslContextBuilder.forClient()
                                            .sslProvider(SslProvider.JDK)
                                            .trustManager(CERT_FILE)
                                            // As we test renegotiation we should use a protocol that support it.
                                            .protocols("TLSv1.2")
                                            .build());

        boolean hasOpenSsl = OpenSsl.isAvailable();
        if (hasOpenSsl) {
            serverContexts.add(SslContextBuilder.forServer(CERT_FILE, KEY_FILE)
                                                .sslProvider(SslProvider.OPENSSL)
                                                // As we test renegotiation we should use a protocol that support it.
                                                .protocols("TLSv1.2")
                                                .build());
            clientContexts.add(SslContextBuilder.forClient()
                                                .sslProvider(SslProvider.OPENSSL)
                                                .trustManager(CERT_FILE)
                                                // As we test renegotiation we should use a protocol that support it.
                                                .protocols("TLSv1.2")
                                                .build());
        } else {
            logger.warn("OpenSSL is unavailable and thus will not be tested.", OpenSsl.unavailabilityCause());
        }

        List<Object[]> params = new ArrayList<>();
        for (SslContext sc: serverContexts) {
            for (SslContext cc: clientContexts) {
                for (RenegotiationType rt: RenegotiationType.values()) {
                    if (rt != RenegotiationType.NONE &&
                        (sc instanceof OpenSslContext || cc instanceof OpenSslContext)) {
                        // TODO: OpenSslEngine does not support renegotiation yet.
                        continue;
                    }

                    final Renegotiation r;
                    switch (rt) {
                        case NONE:
                            r = Renegotiation.NONE;
                            break;
                        case SERVER_INITIATED:
                            r = new Renegotiation(rt, sc.cipherSuites().get(sc.cipherSuites().size() - 1));
                            break;
                        case CLIENT_INITIATED:
                            r = new Renegotiation(rt, cc.cipherSuites().get(cc.cipherSuites().size() - 1));
                            break;
                        default:
                            throw new Error();
                    }

                    for (int i = 0; i < 32; i++) {
                        params.add(new Object[] {
                                sc, cc, r,
                                (i & 16) != 0, (i & 8) != 0, (i & 4) != 0, (i & 2) != 0, (i & 1) != 0 });
                    }
                }
            }
        }

        return params;
    }

    private final AtomicReference<Throwable> clientException = new AtomicReference<>();
    private final AtomicReference<Throwable> serverException = new AtomicReference<>();

    private final AtomicInteger clientSendCounter = new AtomicInteger();
    private final AtomicInteger clientRecvCounter = new AtomicInteger();
    private final AtomicInteger serverRecvCounter = new AtomicInteger();

    private final AtomicInteger clientNegoCounter = new AtomicInteger();
    private final AtomicInteger serverNegoCounter = new AtomicInteger();

    private volatile Channel clientChannel;
    private volatile Channel serverChannel;

    private volatile SslHandler clientSslHandler;
    private volatile SslHandler serverSslHandler;

    private final EchoClientHandler clientHandler =
            new EchoClientHandler(clientRecvCounter, clientNegoCounter, clientException);

    private final EchoServerHandler serverHandler =
            new EchoServerHandler(serverRecvCounter, serverNegoCounter, serverException);

    private SslContext serverCtx;
    private SslContext clientCtx;
    private Renegotiation renegotiation;
    private boolean serverUsesDelegatedTaskExecutor;
    private boolean clientUsesDelegatedTaskExecutor;
    private boolean autoRead;
    private boolean useChunkedWriteHandler;
    private boolean useCompositeBuffer;

    @AfterAll
    public static void compressHeapDumps() throws Exception {
        TestUtils.compressHeapDumps();
    }

    @ParameterizedTest(name =
            "{index}: serverEngine = {0}, clientEngine = {1}, renegotiation = {2}, " +
            "serverUsesDelegatedTaskExecutor = {3}, clientUsesDelegatedTaskExecutor = {4}, " +
            "autoRead = {5}, useChunkedWriteHandler = {6}, useCompositeBuffer = {7}")
    @MethodSource("data")
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testSslEcho(
            SslContext serverCtx, SslContext clientCtx, Renegotiation renegotiation,
            boolean serverUsesDelegatedTaskExecutor, boolean clientUsesDelegatedTaskExecutor,
            boolean autoRead, boolean useChunkedWriteHandler, boolean useCompositeBuffer,
            TestInfo testInfo) throws Throwable {
        this.serverCtx = serverCtx;
        this.clientCtx = clientCtx;
        this.serverUsesDelegatedTaskExecutor = serverUsesDelegatedTaskExecutor;
        this.clientUsesDelegatedTaskExecutor = clientUsesDelegatedTaskExecutor;
        this.renegotiation = renegotiation;
        this.autoRead = autoRead;
        this.useChunkedWriteHandler = useChunkedWriteHandler;
        this.useCompositeBuffer = useCompositeBuffer;
        run(testInfo, this::testSslEcho);
    }

    public void testSslEcho(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        final ExecutorService delegatedTaskExecutor = Executors.newCachedThreadPool();
        reset();

        sb.childOption(ChannelOption.AUTO_READ, autoRead);
        cb.option(ChannelOption.AUTO_READ, autoRead);

        sb.childHandler(new ChannelInitializer<>() {
            @Override
            public void initChannel(Channel sch) {
                serverChannel = sch;

                if (serverUsesDelegatedTaskExecutor) {
                    SSLEngine sse = serverCtx.newEngine(sch.bufferAllocator());
                    serverSslHandler = new SslHandler(sse, delegatedTaskExecutor);
                } else {
                    serverSslHandler = serverCtx.newHandler(sch.bufferAllocator());
                }
                serverSslHandler.setHandshakeTimeoutMillis(0);

                sch.pipeline().addLast("ssl", serverSslHandler);
                if (useChunkedWriteHandler) {
                    sch.pipeline().addLast(new ChunkedWriteHandler());
                }
                sch.pipeline().addLast("serverHandler", serverHandler);
            }
        });

        final CountDownLatch clientHandshakeEventLatch = new CountDownLatch(1);
        cb.handler(new ChannelInitializer<>() {
            @Override
            public void initChannel(Channel sch) {
                clientChannel = sch;

                if (clientUsesDelegatedTaskExecutor) {
                    SSLEngine cse = clientCtx.newEngine(sch.bufferAllocator());
                    clientSslHandler = new SslHandler(cse, delegatedTaskExecutor);
                } else {
                    clientSslHandler = clientCtx.newHandler(sch.bufferAllocator());
                }
                clientSslHandler.setHandshakeTimeoutMillis(0);

                sch.pipeline().addLast("ssl", clientSslHandler);
                if (useChunkedWriteHandler) {
                    sch.pipeline().addLast(new ChunkedWriteHandler());
                }
                sch.pipeline().addLast("clientHandler", clientHandler);
                sch.pipeline().addLast(new ChannelHandler() {
                    @Override
                    public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) {
                        if (evt instanceof SslHandshakeCompletionEvent) {
                            clientHandshakeEventLatch.countDown();
                        }
                        ctx.fireChannelInboundEvent(evt);
                    }
                });
            }
        });

        final Channel sc = sb.bind().get();
        cb.connect(sc.localAddress()).sync();

        final Future<Channel> clientHandshakeFuture = clientSslHandler.handshakeFuture();

        // Wait for the handshake to complete before we flush anything. SslHandler should flush non-application data.
        clientHandshakeFuture.sync();
        clientHandshakeEventLatch.await();
        Buffer dataBuffer = bufferAllocator.copyOf(data);

        clientChannel.writeAndFlush(dataBuffer.readSplit(FIRST_MESSAGE_SIZE));
        clientSendCounter.set(FIRST_MESSAGE_SIZE);

        boolean needsRenegotiation = renegotiation.type == RenegotiationType.CLIENT_INITIATED;
        Future<Channel> renegoFuture = null;
        while (clientSendCounter.get() < data.length) {
            int clientSendCounterVal = clientSendCounter.get();
            int length = Math.min(random.nextInt(1024 * 64), data.length - clientSendCounterVal);
            Buffer buf = dataBuffer.readSplit(length);
            if (useCompositeBuffer) {
                buf = bufferAllocator.compose(buf.send());
            }

            Future<Void> future = clientChannel.writeAndFlush(buf);
            clientSendCounter.set(clientSendCounterVal += length);
            future.sync();

            if (needsRenegotiation && clientSendCounterVal >= data.length / 2) {
                needsRenegotiation = false;
                clientSslHandler.engine().setEnabledCipherSuites(new String[] { renegotiation.cipherSuite });
                renegoFuture = clientSslHandler.renegotiate();
                logStats("CLIENT RENEGOTIATES");
                assertThat(renegoFuture, is(not(sameInstance(clientHandshakeFuture))));
            }
        }

        // Ensure all data has been exchanged.
        while (clientRecvCounter.get() < data.length) {
            if (serverException.get() != null) {
                break;
            }
            if (serverException.get() != null) {
                break;
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                // Ignore.
            }
        }

        while (serverRecvCounter.get() < data.length) {
            if (serverException.get() != null) {
                break;
            }
            if (clientException.get() != null) {
                break;
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                // Ignore.
            }
        }

        // Wait until renegotiation is done.
        if (renegoFuture != null) {
            renegoFuture.sync();
        }
        if (serverHandler.renegoFuture != null) {
            serverHandler.renegoFuture.sync();
        }

        serverChannel.close().await();
        clientChannel.close().await();
        sc.close().await();
        delegatedTaskExecutor.shutdown();

        if (serverException.get() != null && !(serverException.get() instanceof IOException)) {
            throw serverException.get();
        }
        if (clientException.get() != null && !(clientException.get() instanceof IOException)) {
            throw clientException.get();
        }
        if (serverException.get() != null) {
            throw serverException.get();
        }
        if (clientException.get() != null) {
            throw clientException.get();
        }

        // When renegotiation is done, at least the initiating side should be notified.
        try {
            switch (renegotiation.type) {
            case SERVER_INITIATED:
                assertThat(serverSslHandler.engine().getSession().getCipherSuite(), is(renegotiation.cipherSuite));
                assertThat(serverNegoCounter.get(), is(2));
                assertThat(clientNegoCounter.get(), anyOf(is(1), is(2)));
                break;
            case CLIENT_INITIATED:
                assertThat(serverNegoCounter.get(), anyOf(is(1), is(2)));
                assertThat(clientSslHandler.engine().getSession().getCipherSuite(), is(renegotiation.cipherSuite));
                assertThat(clientNegoCounter.get(), is(2));
                break;
            case NONE:
                assertThat(serverNegoCounter.get(), is(1));
                assertThat(clientNegoCounter.get(), is(1));
            }
        } finally {
            logStats("STATS");
        }
    }

    private void reset() {
        clientException.set(null);
        serverException.set(null);

        clientSendCounter.set(0);
        clientRecvCounter.set(0);
        serverRecvCounter.set(0);

        clientNegoCounter.set(0);
        serverNegoCounter.set(0);

        clientChannel = null;
        serverChannel = null;

        clientSslHandler = null;
        serverSslHandler = null;
    }

    void logStats(String message) {
        logger.debug(
                "{}:\n" +
                "\tclient { sent: {}, rcvd: {}, nego: {}, cipher: {} },\n" +
                "\tserver { rcvd: {}, nego: {}, cipher: {} }",
                message,
                clientSendCounter, clientRecvCounter, clientNegoCounter,
                clientSslHandler.engine().getSession().getCipherSuite(),
                serverRecvCounter, serverNegoCounter,
                serverSslHandler.engine().getSession().getCipherSuite());
    }

    private abstract class EchoHandler extends SimpleChannelInboundHandler<Buffer> {

        protected final AtomicInteger recvCounter;
        protected final AtomicInteger negoCounter;
        protected final AtomicReference<Throwable> exception;

        EchoHandler(
                AtomicInteger recvCounter, AtomicInteger negoCounter,
                AtomicReference<Throwable> exception) {

            this.recvCounter = recvCounter;
            this.negoCounter = negoCounter;
            this.exception = exception;
        }

        @Override
        public boolean isSharable() {
            return true;
        }

        @Override
        public final void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            // We intentionally do not ctx.flush() here because we want to verify the SslHandler correctly flushing
            // non-application and previously flushed writes internally.
            if (!autoRead) {
                ctx.read();
            }
            ctx.fireChannelReadComplete();
        }

        @Override
        public final void channelInboundEvent(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof SslHandshakeCompletionEvent) {
                SslHandshakeCompletionEvent handshakeEvt = (SslHandshakeCompletionEvent) evt;
                if (handshakeEvt.cause() != null) {
                    logger.warn("Handshake failed:", handshakeEvt.cause());
                }
                assertSame(SslHandshakeCompletionEvent.SUCCESS, evt);
                negoCounter.incrementAndGet();
                logStats("HANDSHAKEN");
            }
            ctx.fireChannelInboundEvent(evt);
        }

        @Override
        public final void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            if (logger.isWarnEnabled()) {
                logger.warn("Unexpected exception from the client side:", cause);
            }

            exception.compareAndSet(null, cause);
            ctx.close();
        }
    }

    private class EchoClientHandler extends EchoHandler {

        EchoClientHandler(
                AtomicInteger recvCounter, AtomicInteger negoCounter,
                AtomicReference<Throwable> exception) {

            super(recvCounter, negoCounter, exception);
        }

        @Override
        public void handlerAdded(final ChannelHandlerContext ctx) {
            if (!autoRead) {
                ctx.pipeline().get(SslHandler.class).handshakeFuture().addListener(ctx, (c, f) -> c.read());
            }
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, Buffer in) throws Exception {
            byte[] actual = new byte[in.readableBytes()];
            in.readBytes(actual, 0, actual.length);

            int lastIdx = recvCounter.get();
            for (int i = 0; i < actual.length; i ++) {
                assertEquals(data[i + lastIdx], actual[i]);
            }

            recvCounter.addAndGet(actual.length);
        }
    }

    private class EchoServerHandler extends EchoHandler {
        volatile Future<Channel> renegoFuture;

        EchoServerHandler(
                AtomicInteger recvCounter, AtomicInteger negoCounter,
                AtomicReference<Throwable> exception) {

            super(recvCounter, negoCounter, exception);
        }

        @Override
        public final void channelRegistered(ChannelHandlerContext ctx) {
            renegoFuture = null;
        }

        @Override
        public void channelActive(final ChannelHandlerContext ctx) throws Exception {
            if (!autoRead) {
                ctx.read();
            }
            ctx.fireChannelActive();
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, Buffer in) throws Exception {
            byte[] actual = new byte[in.readableBytes()];
            in.readBytes(actual, 0, actual.length);

            int lastIdx = recvCounter.get();
            for (int i = 0; i < actual.length; i ++) {
                assertEquals(data[i + lastIdx], actual[i]);
            }

            Buffer buf = bufferAllocator.copyOf(actual);
            if (useCompositeBuffer) {
                buf = bufferAllocator.compose(buf.send());
            }
            ctx.writeAndFlush(buf);

            recvCounter.addAndGet(actual.length);

            // Perform server-initiated renegotiation if necessary.
            if (renegotiation.type == RenegotiationType.SERVER_INITIATED &&
                recvCounter.get() > data.length / 2 && renegoFuture == null) {

                SslHandler sslHandler = ctx.pipeline().get(SslHandler.class);

                Future<Channel> hf = sslHandler.handshakeFuture();
                assertThat(hf.isDone(), is(true));

                sslHandler.engine().setEnabledCipherSuites(new String[] { renegotiation.cipherSuite });
                logStats("SERVER RENEGOTIATES");
                renegoFuture = sslHandler.renegotiate();
                assertThat(renegoFuture, is(not(sameInstance(hf))));
                assertThat(renegoFuture, is(sameInstance(sslHandler.handshakeFuture())));
                assertThat(renegoFuture.isDone(), is(false));
            }
        }
    }
}
