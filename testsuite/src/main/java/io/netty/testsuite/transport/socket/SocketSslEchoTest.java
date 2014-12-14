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
import io.netty.handler.ssl.JdkSslClientContext;
import io.netty.handler.ssl.JdkSslServerContext;
import io.netty.handler.ssl.OpenSsl;
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import javax.net.ssl.SSLEngine;
import java.io.File;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
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
            "{index}: serverEngine = {0}, clientEngine = {1}, renegotiation = {2}, " +
            "serverUsesDelegatedTaskExecutor = {3}, clientUsesDelegatedTaskExecutor = {4}, " +
            "autoRead = {5}, useChunkedWriteHandler = {6}, useCompositeByteBuf = {7}")
    public static Collection<Object[]> data() throws Exception {
        List<SslContext> serverContexts = new ArrayList<SslContext>();
        serverContexts.add(new JdkSslServerContext(CERT_FILE, KEY_FILE));

        List<SslContext> clientContexts = new ArrayList<SslContext>();
        clientContexts.add(new JdkSslClientContext(CERT_FILE));

        boolean hasOpenSsl = OpenSsl.isAvailable();
        if (hasOpenSsl) {
            serverContexts.add(new OpenSslServerContext(CERT_FILE, KEY_FILE));

            // TODO: Client mode is not supported yet.
            // clientContexts.add(new OpenSslContext(CERT_FILE));
        } else {
            logger.warn("OpenSSL is unavailable and thus will not be tested.", OpenSsl.unavailabilityCause());
        }

        List<Object[]> params = new ArrayList<Object[]>();
        for (SslContext sc: serverContexts) {
            for (SslContext cc: clientContexts) {
                for (RenegotiationType rt: RenegotiationType.values()) {
                    if (rt != RenegotiationType.NONE &&
                        (sc instanceof OpenSslServerContext || cc instanceof OpenSslServerContext)) {
                        // TODO: OpenSslEngine does not support renegotiation yet.
                        continue;
                    }

                    Renegotiation r;
                    if (rt == RenegotiationType.NONE) {
                        r = Renegotiation.NONE;
                    } else {
                        r = new Renegotiation(rt, "SSL_RSA_WITH_RC4_128_SHA");
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

    private final SslContext serverCtx;
    private final SslContext clientCtx;
    private final Renegotiation renegotiation;
    private final boolean serverUsesDelegatedTaskExecutor;
    private final boolean clientUsesDelegatedTaskExecutor;
    private final boolean autoRead;
    private final boolean useChunkedWriteHandler;
    private final boolean useCompositeByteBuf;

    public SocketSslEchoTest(
            SslContext serverCtx, SslContext clientCtx, Renegotiation renegotiation,
            boolean serverUsesDelegatedTaskExecutor, boolean clientUsesDelegatedTaskExecutor,
            boolean autoRead, boolean useChunkedWriteHandler, boolean useCompositeByteBuf) {
        this.serverCtx = serverCtx;
        this.clientCtx = clientCtx;
        this.serverUsesDelegatedTaskExecutor = serverUsesDelegatedTaskExecutor;
        this.clientUsesDelegatedTaskExecutor = clientUsesDelegatedTaskExecutor;
        this.renegotiation = renegotiation;
        this.autoRead = autoRead;
        this.useChunkedWriteHandler = useChunkedWriteHandler;
        this.useCompositeByteBuf = useCompositeByteBuf;
    }

    @Test(timeout = 30000)
    public void testSslEcho() throws Throwable {
        run();
    }

    public void testSslEcho(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        final ExecutorService delegatedTaskExecutor = Executors.newCachedThreadPool();
        final EchoHandler sh = new EchoHandler(true);
        final EchoHandler ch = new EchoHandler(false);

        sb.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            @SuppressWarnings("deprecation")
            public void initChannel(SocketChannel sch) throws Exception {
                if (serverUsesDelegatedTaskExecutor) {
                    SSLEngine sse = serverCtx.newEngine(sch.alloc());
                    sch.pipeline().addFirst("ssl", new SslHandler(sse, delegatedTaskExecutor));
                } else {
                    sch.pipeline().addFirst("ssl", serverCtx.newHandler(sch.alloc()));
                }
                if (useChunkedWriteHandler) {
                    sch.pipeline().addLast(new ChunkedWriteHandler());
                }
                sch.pipeline().addLast("handler", sh);
            }
        });

        cb.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            @SuppressWarnings("deprecation")
            public void initChannel(SocketChannel sch) throws Exception {
                if (clientUsesDelegatedTaskExecutor) {
                    SSLEngine cse = clientCtx.newEngine(sch.alloc());
                    sch.pipeline().addFirst("ssl", new SslHandler(cse, delegatedTaskExecutor));
                } else {
                    sch.pipeline().addFirst("ssl", clientCtx.newHandler(sch.alloc()));
                }
                if (useChunkedWriteHandler) {
                    sch.pipeline().addLast(new ChunkedWriteHandler());
                }
                sch.pipeline().addLast("handler", ch);
            }
        });

        Channel sc = sb.bind().sync().channel();
        Channel cc = cb.connect().sync().channel();
        Future<Channel> hf = ch.sslHandler.handshakeFuture();
        cc.writeAndFlush(Unpooled.wrappedBuffer(data, 0, FIRST_MESSAGE_SIZE));
        final AtomicBoolean firstByteWriteFutureDone = new AtomicBoolean();

        hf.sync();

        assertFalse(firstByteWriteFutureDone.get());

        boolean needsRenegotiation = renegotiation.type == RenegotiationType.CLIENT_INITIATED;
        Future<Channel> renegoFuture = null;
        for (int i = FIRST_MESSAGE_SIZE; i < data.length;) {
            int length = Math.min(random.nextInt(1024 * 64), data.length - i);
            ByteBuf buf = Unpooled.wrappedBuffer(data, i, length);
            if (useCompositeByteBuf) {
                buf = Unpooled.compositeBuffer().addComponent(buf).writerIndex(buf.writerIndex());
            }
            ChannelFuture future = cc.writeAndFlush(buf);
            future.sync();
            i += length;

            if (needsRenegotiation && i >= data.length / 2) {
                needsRenegotiation = false;
                ch.sslHandler.engine().setEnabledCipherSuites(new String[] { renegotiation.cipherSuite });
                renegoFuture = ch.sslHandler.renegotiate();
                assertThat(renegoFuture, is(not(sameInstance(hf))));
                assertThat(renegoFuture.isDone(), is(false));
            }
        }

        // Wait until renegotiation is done.
        if (renegoFuture != null) {
            renegoFuture.sync();
        }
        if (sh.renegoFuture != null) {
            sh.renegoFuture.sync();
        }

        // Ensure all data has been exchanged.
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
        delegatedTaskExecutor.shutdown();

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

        // When renegotiation is done, both the client and server side should be notified.
        try {
            if (renegotiation.type != RenegotiationType.NONE) {
                assertThat(sh.sslHandler.engine().getSession().getCipherSuite(), is(renegotiation.cipherSuite));
                assertThat(sh.negoCounter, is(2));
                assertThat(ch.sslHandler.engine().getSession().getCipherSuite(), is(renegotiation.cipherSuite));
                assertThat(ch.negoCounter, is(2));
            } else {
                assertThat(sh.negoCounter, is(1));
                assertThat(ch.negoCounter, is(1));
            }
        } catch (Throwable t) {
            // TODO: Remove this once we fix this test.
            logger.warn(sh.channel +
                        "[S] cipherSuite: " + sh.sslHandler.engine().getSession().getCipherSuite() +
                        ", negoCounter: " + sh.negoCounter);
            logger.warn(ch.channel +
                        "[C] cipherSuite: " + ch.sslHandler.engine().getSession().getCipherSuite() +
                        ", negoCounter: " + ch.negoCounter);

            TestUtils.dump(StringUtil.simpleClassName(this));
            throw t;
        }
    }

    private class EchoHandler extends SimpleChannelInboundHandler<ByteBuf> {
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        volatile int counter;
        private final boolean server;
        volatile SslHandler sslHandler;
        volatile Future<Channel> renegoFuture;
        volatile int negoCounter;

        EchoHandler(boolean server) {
            this.server = server;
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            channel = ctx.channel();
            sslHandler = channel.pipeline().get(SslHandler.class);
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
                if (useCompositeByteBuf) {
                    buf = Unpooled.compositeBuffer().addComponent(buf).writerIndex(buf.writerIndex());
                }
                channel.write(buf);
            }

            counter += actual.length;

            // Perform server-initiated renegotiation if necessary.
            if (server && renegotiation.type == RenegotiationType.SERVER_INITIATED &&
                counter > data.length / 2 && renegoFuture == null) {

                SslHandler sslHandler = ctx.pipeline().get(SslHandler.class);

                Future<Channel> hf = sslHandler.handshakeFuture();
                assertThat(hf.isDone(), is(true));

                sslHandler.engine().setEnabledCipherSuites(new String[] { renegotiation.cipherSuite });
                renegoFuture = sslHandler.renegotiate();
                assertThat(renegoFuture, is(not(sameInstance(hf))));
                assertThat(renegoFuture, is(sameInstance(sslHandler.handshakeFuture())));
                assertThat(renegoFuture.isDone(), is(false));
            }
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
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof SslHandshakeCompletionEvent) {
                assertSame(SslHandshakeCompletionEvent.SUCCESS, evt);
                negoCounter ++;
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
}
