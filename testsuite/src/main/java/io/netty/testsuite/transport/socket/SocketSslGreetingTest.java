/*
* Copyright 2014 The Netty Project
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
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.io.File;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class SocketSslGreetingTest extends AbstractSocketTest {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SocketSslGreetingTest.class);

    private static final LogLevel LOG_LEVEL = LogLevel.TRACE;
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

    @Parameters(name = "{index}: serverEngine = {0}, clientEngine = {1}, delegate = {2}")
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
                params.add(new Object[] { sc, cc, true });
                params.add(new Object[] { sc, cc, false });
            }
        }
        return params;
    }

    private final SslContext serverCtx;
    private final SslContext clientCtx;
    private final boolean delegate;

    public SocketSslGreetingTest(SslContext serverCtx, SslContext clientCtx, boolean delegate) {
        this.serverCtx = serverCtx;
        this.clientCtx = clientCtx;
        this.delegate = delegate;
    }

    private static SslHandler newSslHandler(SslContext sslCtx, ByteBufAllocator allocator, Executor executor) {
        if (executor == null) {
            return sslCtx.newHandler(allocator);
        } else {
            return sslCtx.newHandler(allocator, executor);
        }
    }

    // Test for https://github.com/netty/netty/pull/2437
    @Test(timeout = 30000)
    public void testSslGreeting() throws Throwable {
        run();
    }

    public void testSslGreeting(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        final ServerHandler sh = new ServerHandler();
        final ClientHandler ch = new ClientHandler();

        final ExecutorService executorService = delegate ? Executors.newCachedThreadPool() : null;
        try {
            sb.childHandler(new ChannelInitializer<Channel>() {
                @Override
                public void initChannel(Channel sch) throws Exception {
                    ChannelPipeline p = sch.pipeline();
                    p.addLast(newSslHandler(serverCtx, sch.alloc(), executorService));
                    p.addLast(new LoggingHandler(LOG_LEVEL));
                    p.addLast(sh);
                }
            });

            cb.handler(new ChannelInitializer<Channel>() {
                @Override
                public void initChannel(Channel sch) throws Exception {
                    ChannelPipeline p = sch.pipeline();
                    p.addLast(newSslHandler(clientCtx, sch.alloc(), executorService));
                    p.addLast(new LoggingHandler(LOG_LEVEL));
                    p.addLast(ch);
                }
            });

            Channel sc = sb.bind().sync().channel();
            Channel cc = cb.connect(sc.localAddress()).sync().channel();

            ch.latch.await();

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
        } finally {
            if (executorService != null) {
                executorService.shutdown();
            }
        }
    }

    private static class ClientHandler extends SimpleChannelInboundHandler<ByteBuf> {

        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void channelRead0(ChannelHandlerContext ctx, ByteBuf buf) throws Exception {
            assertEquals('a', buf.readByte());
            assertFalse(buf.isReadable());
            latch.countDown();
            ctx.close();
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

    private static class ServerHandler extends SimpleChannelInboundHandler<String> {
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
            // discard
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx)
                throws Exception {
            channel = ctx.channel();
            channel.writeAndFlush(ctx.alloc().buffer().writeByte('a'));
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

        @Override
        public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception {
            if (evt instanceof SslHandshakeCompletionEvent) {
                final SslHandshakeCompletionEvent event = (SslHandshakeCompletionEvent) evt;
                if (event.isSuccess()) {
                    SSLSession session = ctx.pipeline().get(SslHandler.class).engine().getSession();
                    try {
                        session.getPeerCertificates();
                        fail();
                    } catch (SSLPeerUnverifiedException e) {
                        // expected
                    }
                    try {
                        session.getPeerCertificateChain();
                        fail();
                    } catch (SSLPeerUnverifiedException e) {
                        // expected
                    } catch (UnsupportedOperationException e) {
                        // Starting from Java15 this method throws UnsupportedOperationException as it was
                        // deprecated before and getPeerCertificates() should be used
                        if (PlatformDependent.javaVersion() < 15) {
                            throw e;
                        }
                    }
                    try {
                        session.getPeerPrincipal();
                        fail();
                    } catch (SSLPeerUnverifiedException e) {
                        // expected
                    }
                }
            }
            ctx.fireUserEventTriggered(evt);
        }
    }
}
