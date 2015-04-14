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
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.ssl.JdkSslClientContext;
import io.netty.handler.ssl.JdkSslServerContext;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.OpenSslClientContext;
import io.netty.handler.ssl.OpenSslContext;
import io.netty.handler.ssl.OpenSslServerContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.testsuite.util.TestUtils;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
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

    @Parameters(name =
            "{index}: serverEngine = {0}, clientEngine = {1}, " +
            "renegotiation = {2}, autoRead = {3}, useChunkedWriteHandler = {4}, useCompositeByteBuf = {5}")
    public static Collection<Object[]> data() throws Exception {
        List<SslContext> serverContexts = new ArrayList<SslContext>();
        serverContexts.add(new JdkSslServerContext(CERT_FILE, KEY_FILE));

        List<SslContext> clientContexts = new ArrayList<SslContext>();
        clientContexts.add(new JdkSslClientContext(CERT_FILE));

        boolean hasOpenSsl = OpenSsl.isAvailable();
        if (hasOpenSsl) {
            serverContexts.add(new OpenSslServerContext(CERT_FILE, KEY_FILE));
            clientContexts.add(new OpenSslClientContext(CERT_FILE));
        } else {
            logger.warn("OpenSSL is unavailable and thus will not be tested.", OpenSsl.unavailabilityCause());
        }

        List<Object[]> params = new ArrayList<Object[]>();
        for (SslContext sc: serverContexts) {
            for (SslContext cc: clientContexts) {
                for (RenegotiationType rt: RenegotiationType.values()) {
                    if (rt != RenegotiationType.NONE &&
                        (sc instanceof OpenSslContext || cc instanceof OpenSslContext)) {
                        // TODO: OpenSslEngine does not support renegotiation yet.
                        continue;
                    }

                    Renegotiation r;
                    if (rt == RenegotiationType.NONE) {
                        r = Renegotiation.NONE;
                    } else {
                        r = new Renegotiation(rt, "SSL_RSA_WITH_RC4_128_SHA");
                    }

                    for (int i = 0; i < 8; i++) {
                        params.add(new Object[] { sc, cc, r, (i & 4) != 0, (i & 2) != 0, (i & 1) != 0 });
                    }
                }
            }
        }

        return params;
    }

    private final SslContext serverCtx;
    private final SslContext clientCtx;
    private final Renegotiation renegotiation;
    private final boolean autoRead;
    private final boolean useChunkedWriteHandler;
    private final boolean useCompositeByteBuf;

    private final AtomicReference<Throwable> clientException = new AtomicReference<Throwable>();
    private final AtomicReference<Throwable> serverException = new AtomicReference<Throwable>();

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

    public SocketSslEchoTest(
            SslContext serverCtx, SslContext clientCtx, Renegotiation renegotiation,
            boolean autoRead, boolean useChunkedWriteHandler, boolean useCompositeByteBuf) {
        this.serverCtx = serverCtx;
        this.clientCtx = clientCtx;
        this.renegotiation = renegotiation;
        this.autoRead = autoRead;
        this.useChunkedWriteHandler = useChunkedWriteHandler;
        this.useCompositeByteBuf = useCompositeByteBuf;
    }

    @Test(timeout = 30000)
    public void testSslEcho() throws Throwable {
        run();
    }

    @AfterClass
    public static void compressHeapDumps() throws Exception {
        TestUtils.compressHeapDumps();
    }

    public void testSslEcho(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        reset();

        sb.childOption(ChannelOption.AUTO_READ, autoRead);
        cb.option(ChannelOption.AUTO_READ, autoRead);

        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            @SuppressWarnings("deprecation")
            public void initChannel(Channel sch) throws Exception {
                serverChannel = sch;
                serverSslHandler = serverCtx.newHandler(sch.alloc());

                sch.pipeline().addLast("ssl", serverSslHandler);
                if (useChunkedWriteHandler) {
                    sch.pipeline().addLast(new ChunkedWriteHandler());
                }
                sch.pipeline().addLast("handler", serverHandler);
            }
        });

        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            @SuppressWarnings("deprecation")
            public void initChannel(Channel sch) throws Exception {
                clientChannel = sch;
                clientSslHandler = clientCtx.newHandler(sch.alloc());

                sch.pipeline().addLast("ssl", clientSslHandler);
                if (useChunkedWriteHandler) {
                    sch.pipeline().addLast(new ChunkedWriteHandler());
                }
                sch.pipeline().addLast("handler", clientHandler);
            }
        });

        final Channel sc = sb.bind().sync().channel();
        cb.connect().sync();

        final Future<Channel> clientHandshakeFuture = clientSslHandler.handshakeFuture();

        clientChannel.writeAndFlush(Unpooled.wrappedBuffer(data, 0, FIRST_MESSAGE_SIZE));
        clientSendCounter.set(FIRST_MESSAGE_SIZE);
        clientHandshakeFuture.sync();

        boolean needsRenegotiation = renegotiation.type == RenegotiationType.CLIENT_INITIATED;
        Future<Channel> renegoFuture = null;
        while (clientSendCounter.get() < data.length) {
            int clientSendCounterVal = clientSendCounter.get();
            int length = Math.min(random.nextInt(1024 * 64), data.length - clientSendCounterVal);
            ByteBuf buf = Unpooled.wrappedBuffer(data, clientSendCounterVal, length);
            if (useCompositeByteBuf) {
                buf = Unpooled.compositeBuffer().addComponent(buf).writerIndex(buf.writerIndex());
            }

            ChannelFuture future = clientChannel.writeAndFlush(buf);
            clientSendCounter.set(clientSendCounterVal += length);
            future.sync();

            if (needsRenegotiation && clientSendCounterVal >= data.length / 2) {
                needsRenegotiation = false;
                clientSslHandler.engine().setEnabledCipherSuites(new String[] { renegotiation.cipherSuite });
                renegoFuture = clientSslHandler.renegotiate();
                logStats("CLIENT RENEGOTIATES");
                assertThat(renegoFuture, is(not(sameInstance(clientHandshakeFuture))));
                assertThat(renegoFuture.isDone(), is(false));
            }
        }

        // Wait until renegotiation is done.
        if (renegoFuture != null) {
            renegoFuture.sync();
        }
        if (serverHandler.renegoFuture != null) {
            serverHandler.renegoFuture.sync();
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

        serverChannel.close().awaitUninterruptibly();
        clientChannel.close().awaitUninterruptibly();
        sc.close().awaitUninterruptibly();

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

        // When renegotiation is done, both the client and server side should be notified.
        try {
            if (renegotiation.type != RenegotiationType.NONE) {
                assertThat(serverSslHandler.engine().getSession().getCipherSuite(), is(renegotiation.cipherSuite));
                assertThat(serverNegoCounter.get(), is(2));
                assertThat(clientSslHandler.engine().getSession().getCipherSuite(), is(renegotiation.cipherSuite));
                assertThat(clientNegoCounter.get(), is(2));
            } else {
                assertThat(serverNegoCounter.get(), is(1));
                assertThat(clientNegoCounter.get(), is(1));
            }
        } catch (Throwable t) {
            // TODO: Remove this once we fix this test.
            TestUtils.dump(StringUtil.simpleClassName(this));
            throw t;
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

    @Sharable
    private abstract class EchoHandler extends SimpleChannelInboundHandler<ByteBuf> {

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
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            if (!autoRead) {
                ctx.read();
            }
        }

        @Override
        public final void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            try {
                ctx.flush();
            } finally {
                if (!autoRead) {
                    ctx.read();
                }
            }
        }

        @Override
        public final void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof SslHandshakeCompletionEvent) {
                SslHandshakeCompletionEvent handshakeEvt = (SslHandshakeCompletionEvent) evt;
                if (handshakeEvt.cause() != null) {
                    logger.warn("Handshake failed:", handshakeEvt.cause());
                }
                assertSame(SslHandshakeCompletionEvent.SUCCESS, evt);
                negoCounter.incrementAndGet();
                logStats("HANDSHAKEN");
            }
        }

        @Override
        public final void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
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
        public void messageReceived(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
            byte[] actual = new byte[in.readableBytes()];
            in.readBytes(actual);

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
        public final void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            renegoFuture = null;
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
            byte[] actual = new byte[in.readableBytes()];
            in.readBytes(actual);

            int lastIdx = recvCounter.get();
            for (int i = 0; i < actual.length; i ++) {
                assertEquals(data[i + lastIdx], actual[i]);
            }

            ByteBuf buf = Unpooled.wrappedBuffer(actual);
            if (useCompositeByteBuf) {
                buf = Unpooled.compositeBuffer().addComponent(buf).writerIndex(buf.writerIndex());
            }
            ctx.write(buf);

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
