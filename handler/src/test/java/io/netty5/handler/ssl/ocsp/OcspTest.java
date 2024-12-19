/*
 * Copyright 2017 The Netty Project
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

package io.netty5.handler.ssl.ocsp;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.local.LocalAddress;
import io.netty5.channel.local.LocalChannel;
import io.netty5.channel.local.LocalIoHandler;
import io.netty5.channel.local.LocalServerChannel;
import io.netty5.handler.ssl.OpenSsl;
import io.netty5.handler.ssl.ReferenceCountedOpenSslEngine;
import io.netty5.handler.ssl.SslContext;
import io.netty5.handler.ssl.SslContextBuilder;
import io.netty5.handler.ssl.SslHandler;
import io.netty5.handler.ssl.SslProvider;
import io.netty5.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty5.pkitesting.CertificateBuilder;
import io.netty5.pkitesting.X509Bundle;
import io.netty5.util.Resource;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.net.ssl.SSLHandshakeException;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty5.buffer.DefaultBufferAllocators.offHeapAllocator;
import static io.netty5.util.internal.SilentDispose.autoClosing;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class OcspTest {
    private static X509Bundle ssc;

    @BeforeAll
    public static void checkOcspSupported() throws Exception {
        assumeTrue(OpenSsl.isOcspSupported());
        ssc = new CertificateBuilder()
                .subject("cn=localhost")
                .setIsCertificateAuthority(true)
                .buildSelfSigned();
    }

    @Test
    public void testJdkClientEnableOcsp() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> {
            SslContextBuilder.forClient()
                    .sslProvider(SslProvider.JDK)
                    .enableOcsp(true)
                    .build();
        });
    }

    @Test
    public void testJdkServerEnableOcsp() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> {
                SslContextBuilder.forServer(ssc.getKeyPair().getPrivate(), ssc.getCertificatePath())
                        .sslProvider(SslProvider.JDK)
                        .enableOcsp(true)
                        .build();
        });
    }

    @Test
    public void testClientOcspNotEnabledOpenSsl() throws Exception {
        testClientOcspNotEnabled(SslProvider.OPENSSL);
    }

    @Test
    public void testClientOcspNotEnabledOpenSslRefCnt() throws Exception {
        testClientOcspNotEnabled(SslProvider.OPENSSL_REFCNT);
    }

    private static void testClientOcspNotEnabled(SslProvider sslProvider) throws Exception {
        SslContext context = SslContextBuilder.forClient()
                .sslProvider(sslProvider)
                .build();
        try (AutoCloseable ignore1 = autoClosing(context)) {
            SslHandler sslHandler = context.newHandler(offHeapAllocator());
            final ReferenceCountedOpenSslEngine engine = (ReferenceCountedOpenSslEngine) sslHandler.engine();
            try (AutoCloseable ignore2 = autoClosing(engine)) {
                assertThrows(IllegalStateException.class, () -> engine.getOcspResponse());
            }
        }
    }

    @Test
    public void testServerOcspNotEnabledOpenSsl() throws Exception {
        testServerOcspNotEnabled(SslProvider.OPENSSL);
    }

    @Test
    public void testServerOcspNotEnabledOpenSslRefCnt() throws Exception {
        testServerOcspNotEnabled(SslProvider.OPENSSL_REFCNT);
    }

    private static void testServerOcspNotEnabled(SslProvider sslProvider) throws Exception {
        SslContext context = SslContextBuilder.forServer(ssc.getKeyPair().getPrivate(), ssc.getCertificatePath())
                .sslProvider(sslProvider)
                .build();
        try (AutoCloseable ignore1 = autoClosing(context)) {
            SslHandler sslHandler = context.newHandler(offHeapAllocator());
            final ReferenceCountedOpenSslEngine engine = (ReferenceCountedOpenSslEngine) sslHandler.engine();
            try (AutoCloseable ignore2 = autoClosing(engine)) {
                assertThrows(IllegalStateException.class, () -> engine.setOcspResponse(new byte[] { 1, 2, 3 }));
            }
        }
    }

    @Test
    @Timeout(value = 10000L, unit = TimeUnit.MILLISECONDS)
    public void testClientAcceptingOcspStapleOpenSsl() throws Exception {
        testClientAcceptingOcspStaple(SslProvider.OPENSSL);
    }

    @Test
    @Timeout(value = 10000L, unit = TimeUnit.MILLISECONDS)
    public void testClientAcceptingOcspStapleOpenSslRefCnt() throws Exception {
        testClientAcceptingOcspStaple(SslProvider.OPENSSL_REFCNT);
    }

    /**
     * The Server provides an OCSP staple and the Client accepts it.
     */
    private static void testClientAcceptingOcspStaple(SslProvider sslProvider) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        ChannelHandler serverHandler = new ChannelHandler() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                ctx.writeAndFlush(ctx.bufferAllocator().copyOf("Hello, World!", UTF_8));
                ctx.fireChannelActive();
            }
        };

        ChannelHandler clientHandler = new ChannelHandler() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                try {
                    Resource.dispose(msg);
                } finally {
                    latch.countDown();
                }
            }
        };

        byte[] response = newOcspResponse();
        TestClientOcspContext callback = new TestClientOcspContext(true);

        handshake(sslProvider, latch, serverHandler, response, clientHandler, callback);

        byte[] actual = callback.response();

        assertNotNull(actual);
        assertNotSame(response, actual);
        assertArrayEquals(response, actual);
    }

    @Test
    @Timeout(value = 10000L, unit = TimeUnit.MILLISECONDS)
    public void testClientRejectingOcspStapleOpenSsl() throws Exception {
        testClientRejectingOcspStaple(SslProvider.OPENSSL);
    }

    @Test
    @Timeout(value = 10000L, unit = TimeUnit.MILLISECONDS)
    public void testClientRejectingOcspStapleOpenSslRefCnt() throws Exception {
        testClientRejectingOcspStaple(SslProvider.OPENSSL_REFCNT);
    }

    /**
     * The Server provides an OCSP staple and the Client rejects it.
     */
    private static void testClientRejectingOcspStaple(SslProvider sslProvider) throws Exception {
        final AtomicReference<Throwable> causeRef = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);

        ChannelHandler clientHandler = new ChannelHandler() {
            @Override
            public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                try {
                    causeRef.set(cause);
                } finally {
                    latch.countDown();
                }
            }
        };

        byte[] response = newOcspResponse();
        TestClientOcspContext callback = new TestClientOcspContext(false);

        handshake(sslProvider, latch, null, response, clientHandler, callback);

        byte[] actual = callback.response();

        assertNotNull(actual);
        assertNotSame(response, actual);
        assertArrayEquals(response, actual);

        Throwable cause = causeRef.get();
        assertThat(cause, CoreMatchers.instanceOf(SSLHandshakeException.class));
    }

    @Test
    @Timeout(value = 10000L, unit = TimeUnit.MILLISECONDS)
    public void testServerHasNoStapleOpenSsl() throws Exception {
        testServerHasNoStaple(SslProvider.OPENSSL);
    }

    @Test
    @Timeout(value = 10000L, unit = TimeUnit.MILLISECONDS)
    public void testServerHasNoStapleOpenSslRefCnt() throws Exception {
        testServerHasNoStaple(SslProvider.OPENSSL_REFCNT);
    }

    /**
     * The server has OCSP stapling enabled but doesn't provide a staple.
     */
    private static void testServerHasNoStaple(SslProvider sslProvider) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        ChannelHandler serverHandler = new ChannelHandler() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                ctx.writeAndFlush(ctx.bufferAllocator().copyOf("Hello, World!", UTF_8));
                ctx.fireChannelActive();
            }
        };

        ChannelHandler clientHandler = new ChannelHandler() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                try {
                    Resource.dispose(msg);
                } finally {
                    latch.countDown();
                }
            }
        };

        byte[] response = null;
        TestClientOcspContext callback = new TestClientOcspContext(true);

        handshake(sslProvider, latch, serverHandler, response, clientHandler, callback);

        byte[] actual = callback.response();

        assertNull(response);
        assertNull(actual);
    }

    @Test
    @Timeout(value = 10000L, unit = TimeUnit.MILLISECONDS)
    public void testClientExceptionOpenSsl() throws Exception {
        testClientException(SslProvider.OPENSSL);
    }

    @Test
    @Timeout(value = 10000L, unit = TimeUnit.MILLISECONDS)
    public void testClientExceptionOpenSslRefCnt() throws Exception {
        testClientException(SslProvider.OPENSSL_REFCNT);
    }

    /**
     * Testing what happens if the {@link OcspClientCallback} throws an {@link Exception}.
     * <p>
     * The exception should bubble up on the client side and the connection should get closed.
     */
    private static void testClientException(SslProvider sslProvider) throws Exception {
        final AtomicReference<Throwable> causeRef = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);

        ChannelHandler clientHandler = new ChannelHandler() {
            @Override
            public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                try {
                    causeRef.set(cause);
                } finally {
                    latch.countDown();
                }
            }
        };

        final OcspTestException clientException = new OcspTestException("testClientException");
        byte[] response = newOcspResponse();
        OcspClientCallback callback = response1 -> {
            throw clientException;
        };

        handshake(sslProvider, latch, null, response, clientHandler, callback);

        assertSame(clientException, causeRef.get());
    }

    private static void handshake(SslProvider sslProvider, CountDownLatch latch, ChannelHandler serverHandler,
            byte[] response, ChannelHandler clientHandler, OcspClientCallback callback) throws Exception {

        SslContext serverSslContext = SslContextBuilder.forServer(ssc.getKeyPair().getPrivate(),
                        ssc.getCertificatePath())
                .sslProvider(sslProvider)
                .enableOcsp(true)
                .build();

        try (AutoCloseable ignore1 = autoClosing(serverSslContext)) {
            SslContext clientSslContext = SslContextBuilder.forClient()
                    .sslProvider(sslProvider)
                    .enableOcsp(true)
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();

            try (AutoCloseable ignore2 = autoClosing(clientSslContext)) {
                EventLoopGroup group = new MultithreadEventLoopGroup(LocalIoHandler.newFactory());
                try {
                    LocalAddress address = new LocalAddress(OcspTest.class);
                    Channel server = newServer(group, address, serverSslContext, response, serverHandler);
                    Channel client = newClient(group, address, clientSslContext, callback, clientHandler);
                    try {
                        assertTrue(latch.await(10L, TimeUnit.SECONDS));
                    } finally {
                        client.close().asStage().sync();
                        server.close().asStage().sync();
                    }
                } finally {
                    group.shutdownGracefully(1L, 1L, TimeUnit.SECONDS);
                }
            }
        }
    }

    private static Channel newServer(EventLoopGroup group, SocketAddress address,
            SslContext context, byte[] response, ChannelHandler handler) throws Exception {

        ServerBootstrap bootstrap = new ServerBootstrap()
                .channel(LocalServerChannel.class)
                .group(group)
                .childHandler(newServerHandler(context, response, handler));

        return bootstrap.bind(address).asStage().get();
    }

    private static Channel newClient(EventLoopGroup group, SocketAddress address,
            SslContext context, OcspClientCallback callback, ChannelHandler handler) throws Exception {

        Bootstrap bootstrap = new Bootstrap()
                .channel(LocalChannel.class)
                .group(group)
                .handler(newClientHandler(context, callback, handler));

        return bootstrap.connect(address).asStage().get();
    }

    private static ChannelHandler newServerHandler(final SslContext context,
            final byte[] response, final ChannelHandler handler) {
        return new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                SslHandler sslHandler = context.newHandler(ch.bufferAllocator());

                if (response != null) {
                    ReferenceCountedOpenSslEngine engine = (ReferenceCountedOpenSslEngine) sslHandler.engine();
                    engine.setOcspResponse(response);
                }

                pipeline.addLast(sslHandler);

                if (handler != null) {
                    pipeline.addLast(handler);
                }
            }
        };
    }

    private static ChannelHandler newClientHandler(final SslContext context,
            final OcspClientCallback callback, final ChannelHandler handler) {
        return new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();

                SslHandler sslHandler = context.newHandler(ch.bufferAllocator());
                ReferenceCountedOpenSslEngine engine = (ReferenceCountedOpenSslEngine) sslHandler.engine();

                pipeline.addLast(sslHandler);
                pipeline.addLast(new OcspClientCallbackHandler(engine, callback));

                if (handler != null) {
                    pipeline.addLast(handler);
                }
            }
        };
    }

    private static byte[] newOcspResponse() {
        // Assume we got the OCSP staple from somewhere. Using a bogus byte[]
        // in the test because getting a true staple from the CA is quite involved.
        // It requires HttpCodec and Bouncycastle and the test may be very unreliable
        // because the OCSP responder servers are basically being DDoS'd by the
        // Internet.

        return "I am a bogus OCSP staple. OpenSSL does not care about the format of the byte[]!"
                .getBytes(StandardCharsets.US_ASCII);
    }

    private interface OcspClientCallback {
        boolean verify(byte[] staple) throws Exception;
    }

    private static final class TestClientOcspContext implements OcspClientCallback {

        private final CountDownLatch latch = new CountDownLatch(1);
        private final boolean valid;

        private volatile byte[] response;

        TestClientOcspContext(boolean valid) {
            this.valid = valid;
        }

        public byte[] response() throws InterruptedException, TimeoutException {
            assertTrue(latch.await(10L, TimeUnit.SECONDS));
            return response;
        }

        @Override
        public boolean verify(byte[] response) throws Exception {
            this.response = response;
            latch.countDown();

            return valid;
        }
    }

    private static final class OcspClientCallbackHandler extends OcspClientHandler {

        private final OcspClientCallback callback;

        OcspClientCallbackHandler(ReferenceCountedOpenSslEngine engine, OcspClientCallback callback) {
            super(engine);
            this.callback = callback;
        }

        @Override
        protected boolean verify(ChannelHandlerContext ctx, ReferenceCountedOpenSslEngine engine) throws Exception {
            byte[] response = engine.getOcspResponse();
            return callback.verify(response);
        }
    }

    private static final class OcspTestException extends IllegalStateException {
        private static final long serialVersionUID = 4516426833250228159L;

        OcspTestException(String message) {
            super(message);
        }
    }
}
