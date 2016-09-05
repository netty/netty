/*
 * Copyright 2015 The Netty Project
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
package io.netty.handler.ssl;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.EmptyArrays;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

public abstract class SSLEngineTest {

    protected static final String PROTOCOL_TLS_V1_2 = "TLSv1.2";
    protected static final String PROTOCOL_SSL_V2_HELLO = "SSLv2Hello";

    private static final byte[] CERT_BYTES = {48, -126, 2, -2, 48, -126, 1, -26, -96, 3, 2, 1, 2, 2, 8, 32, -61,
            -115, -60, 73, 102, -48, 2, 48, 13, 6, 9, 42, -122, 72, -122, -9, 13, 1, 1, 11, 5, 0, 48, 62, 49, 60, 48,
            58, 6, 3, 85, 4, 3, 12, 51, 101, 56, 97, 99, 48, 50, 102, 97, 48, 100, 54, 53, 97, 56, 52, 50, 49, 57, 48,
            49, 54, 48, 52, 53, 100, 98, 56, 98, 48, 53, 99, 52, 56, 53, 98, 52, 101, 99, 100, 102, 46, 110, 101, 116,
            116, 121, 46, 116, 101, 115, 116, 48, 32, 23, 13, 49, 51, 48, 56, 48, 50, 48, 55, 53, 49, 51, 54, 90, 24,
            15, 57, 57, 57, 57, 49, 50, 51, 49, 50, 51, 53, 57, 53, 57, 90, 48, 62, 49, 60, 48, 58, 6, 3, 85, 4, 3,
            12, 51, 101, 56, 97, 99, 48, 50, 102, 97, 48, 100, 54, 53, 97, 56, 52, 50, 49, 57, 48, 49, 54, 48, 52,
            53, 100, 98, 56, 98, 48, 53, 99, 52, 56, 53, 98, 52, 101, 99, 100, 102, 46, 110, 101, 116, 116, 121, 46,
            116, 101, 115, 116, 48, -126, 1, 34, 48, 13, 6, 9, 42, -122, 72, -122, -9, 13, 1, 1, 1, 5, 0, 3, -126, 1,
            15, 0, 48, -126, 1, 10, 2, -126, 1, 1, 0, -37, -8, 112, 78, -36, 45, 20, 68, 18, -81, 13, 72, 9, 29, -72,
            72, -108, 28, -98, -15, 127, -36, 108, -47, -9, -108, 58, -73, 92, -29, -123, 7, 62, -53, -31, 118, 74, 44,
            50, 23, 75, -31, 94, 66, -92, -128, 80, -54, 54, -94, -39, -108, -7, 89, 35, -48, -86, 43, -78, 19, 35,
            109, 69, -33, 19, 82, -92, 78, 40, -45, 48, -103, 90, -127, -83, -116, -37, 21, 85, -73, 109, 95, 68, -119,
            9, 53, 102, -56, 47, 71, 86, 20, -75, -78, 70, -82, -50, 93, -36, -96, -56, 89, 8, -119, 111, 91, -37, -14,
            -40, 105, -29, -63, -128, 68, -10, -38, 70, -19, 29, 32, -128, 18, 63, -127, -107, 39, -10, -21, -97, -75,
            -84, -36, 114, 1, 112, 70, 24, 103, 28, 8, -84, -60, 109, -54, -128, 72, 18, -121, 58, 5, 105, -22, -110,
            -22, -107, 0, 31, -71, 44, -70, -125, -13, -77, 27, 55, 30, -77, 124, -41, 70, -79, -82, -44, -35, -23, 4,
            -116, -64, 35, 0, -106, -29, 111, 103, -25, 102, 101, 97, -10, 17, -46, 122, -2, 68, 66, -125, -99, 26,
            -49, 32, -128, -20, 88, 4, -90, 16, 120, 65, 123, 52, -61, -6, -3, 42, 8, -108, 114, 47, 61, -82, -80, 88,
            22, 99, -18, -38, -127, 66, 68, -37, 33, -57, 35, 105, -109, -69, 100, 64, 22, 120, 1, -118, 82, 87, -108,
            -64, -83, 87, 4, -12, -60, 107, -112, -58, 70, -57, 2, 3, 1, 0, 1, 48, 13, 6, 9, 42, -122, 72, -122, -9,
            13, 1, 1, 11, 5, 0, 3, -126, 1, 1, 0, 75, -4, 55, -75, -26, -14, -90, -104, -40, 88, 43, 57, -50, -113,
            107, 81, -109, -128, 15, -128, 57, -67, -38, 83, 125, -45, 27, 0, 17, -13, -89, -2, -100, -73, -6, 5, 35,
            -38, -94, 23, 16, 124, -25, -119, -119, -34, -59, -112, 91, -104, 34, 123, -105, -105, -22, 42, -77, -28,
            106, 51, -8, -4, 71, 65, 57, 6, -31, -104, 99, 108, 14, 42, -110, -1, 61, -79, 98, -41, 39, -1, 43, 43,
            -33, -73, -78, -107, -121, -57, -75, 33, 69, 30, 115, -8, -75, 13, -42, 19, 12, 29, 37, 53, 107, -41, 95,
            24, -33, 48, -95, -117, 114, -35, -58, 49, -79, 7, 42, -14, -33, 31, 30, 54, 35, 12, -1, -7, -5, -38, -24,
            -75, 43, 59, -117, -74, 76, 55, -17, -45, 39, 7, -71, 30, -44, 100, 75, -126, -44, 50, 120, -58, -47, 97,
            110, -102, -65, 65, 16, 35, 11, 39, -51, -57, 119, 3, 115, -78, -10, 18, -46, 86, -100, 41, -94, -67, 49,
            64, -10, 95, 12, 23, 86, 79, 48, 52, -107, 119, -121, -100, 67, -80, 116, -59, -110, 5, 67, -105, 18, 72,
            91, 123, 88, 102, -119, 10, -63, -116, -51, -119, 20, -32, 90, 120, 35, 41, 16, 113, 108, 93, -108, -43,
            -5, -64, -106, 81, -63, 13, -109, 100, -111, 69, -126, 90, 83, -120, 86, 93, 122, -82, -120, -24, 7, 125,
            2, 125, 68, -99, -54, 115, -27, 111, 20, 39, -117, 111, -122, 108};
    private static final String PRINCIPAL_NAME = "CN=e8ac02fa0d65a84219016045db8b05c485b4ecdf.netty.test";

    @Mock
    protected MessageReceiver serverReceiver;
    @Mock
    protected MessageReceiver clientReceiver;

    protected Throwable serverException;
    protected Throwable clientException;
    protected SslContext serverSslCtx;
    protected SslContext clientSslCtx;
    protected ServerBootstrap sb;
    protected Bootstrap cb;
    protected Channel serverChannel;
    protected Channel serverConnectedChannel;
    protected Channel clientChannel;
    protected CountDownLatch serverLatch;
    protected CountDownLatch clientLatch;

    interface MessageReceiver {
        void messageReceived(ByteBuf msg);
    }

    protected static final class MessageDelegatorChannelHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final MessageReceiver receiver;
        private final CountDownLatch latch;

        public MessageDelegatorChannelHandler(MessageReceiver receiver, CountDownLatch latch) {
            super(false);
            this.receiver = receiver;
            this.latch = latch;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            receiver.messageReceived(msg);
            latch.countDown();
        }
    }

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        serverLatch = new CountDownLatch(1);
        clientLatch = new CountDownLatch(1);
    }

    @After
    public void tearDown() throws InterruptedException {
        ChannelFuture clientCloseFuture = null;
        ChannelFuture serverConnectedCloseFuture = null;
        ChannelFuture serverCloseFuture = null;
        if (clientChannel != null) {
            clientCloseFuture = clientChannel.close();
            clientChannel = null;
        }
        if (serverConnectedChannel != null) {
            serverConnectedCloseFuture = serverConnectedChannel.close();
            serverConnectedChannel = null;
        }
        if (serverChannel != null) {
            serverCloseFuture = serverChannel.close();
            serverChannel = null;
        }
        // We must wait for the Channel cleanup to finish. In the case if the ReferenceCountedOpenSslEngineTest
        // the ReferenceCountedOpenSslEngine depends upon the SslContext and so we must wait the cleanup the
        // SslContext to avoid JVM core dumps!
        //
        // See https://github.com/netty/netty/issues/5692
        if (clientCloseFuture != null) {
            clientCloseFuture.sync();
        }
        if (serverConnectedCloseFuture != null) {
            serverConnectedCloseFuture.sync();
        }
        if (serverCloseFuture != null) {
            serverCloseFuture.sync();
        }
        if (serverSslCtx != null) {
            cleanupServerSslContext(serverSslCtx);
            serverSslCtx = null;
        }
        if (clientSslCtx != null) {
            cleanupClientSslContext(clientSslCtx);
            clientSslCtx = null;
        }
        Future<?> serverGroupShutdownFuture = null;
        Future<?> serverChildGroupShutdownFuture = null;
        Future<?> clientGroupShutdownFuture = null;
        if (sb != null) {
            serverGroupShutdownFuture = sb.config().group().shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
            serverChildGroupShutdownFuture = sb.config().childGroup().shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
        }
        if (cb != null) {
            clientGroupShutdownFuture = cb.config().group().shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
        }
        if (serverGroupShutdownFuture != null) {
            serverGroupShutdownFuture.sync();
            serverChildGroupShutdownFuture.sync();
        }
        if (clientGroupShutdownFuture != null) {
            clientGroupShutdownFuture.sync();
        }
        serverException = null;
    }

    @Test
    public void testMutualAuthSameCerts() throws Exception {
        mySetupMutualAuth(new File(getClass().getResource("test_unencrypted.pem").getFile()),
                          new File(getClass().getResource("test.crt").getFile()),
                          null);
        runTest(null);
        assertTrue(serverLatch.await(2, TimeUnit.SECONDS));
        assertNull(serverException);
    }

    @Test
    public void testMutualAuthDiffCerts() throws Exception {
        File serverKeyFile = new File(getClass().getResource("test_encrypted.pem").getFile());
        File serverCrtFile = new File(getClass().getResource("test.crt").getFile());
        String serverKeyPassword = "12345";
        File clientKeyFile = new File(getClass().getResource("test2_encrypted.pem").getFile());
        File clientCrtFile = new File(getClass().getResource("test2.crt").getFile());
        String clientKeyPassword = "12345";
        mySetupMutualAuth(clientCrtFile, serverKeyFile, serverCrtFile, serverKeyPassword,
                          serverCrtFile, clientKeyFile, clientCrtFile, clientKeyPassword);
        runTest(null);
        assertTrue(serverLatch.await(2, TimeUnit.SECONDS));
    }

    @Test
    public void testMutualAuthDiffCertsServerFailure() throws Exception {
        File serverKeyFile = new File(getClass().getResource("test_encrypted.pem").getFile());
        File serverCrtFile = new File(getClass().getResource("test.crt").getFile());
        String serverKeyPassword = "12345";
        File clientKeyFile = new File(getClass().getResource("test2_encrypted.pem").getFile());
        File clientCrtFile = new File(getClass().getResource("test2.crt").getFile());
        String clientKeyPassword = "12345";
        // Client trusts server but server only trusts itself
        mySetupMutualAuth(serverCrtFile, serverKeyFile, serverCrtFile, serverKeyPassword,
                          serverCrtFile, clientKeyFile, clientCrtFile, clientKeyPassword);
        assertTrue(serverLatch.await(2, TimeUnit.SECONDS));
        assertTrue(serverException instanceof SSLHandshakeException);
    }

    @Test
    public void testMutualAuthDiffCertsClientFailure() throws Exception {
        File serverKeyFile = new File(getClass().getResource("test_unencrypted.pem").getFile());
        File serverCrtFile = new File(getClass().getResource("test.crt").getFile());
        String serverKeyPassword = null;
        File clientKeyFile = new File(getClass().getResource("test2_unencrypted.pem").getFile());
        File clientCrtFile = new File(getClass().getResource("test2.crt").getFile());
        String clientKeyPassword = null;
        // Server trusts client but client only trusts itself
        mySetupMutualAuth(clientCrtFile, serverKeyFile, serverCrtFile, serverKeyPassword,
                          clientCrtFile, clientKeyFile, clientCrtFile, clientKeyPassword);
        assertTrue(clientLatch.await(2, TimeUnit.SECONDS));
        assertTrue(clientException instanceof SSLHandshakeException);
    }

    private void mySetupMutualAuth(File keyFile, File crtFile, String keyPassword)
            throws SSLException, InterruptedException {
        mySetupMutualAuth(crtFile, keyFile, crtFile, keyPassword, crtFile, keyFile, crtFile, keyPassword);
    }

    private void mySetupMutualAuth(
            File servertTrustCrtFile, File serverKeyFile, File serverCrtFile, String serverKeyPassword,
            File clientTrustCrtFile, File clientKeyFile, File clientCrtFile, String clientKeyPassword)
            throws InterruptedException, SSLException {
        serverSslCtx = SslContextBuilder.forServer(serverCrtFile, serverKeyFile, serverKeyPassword)
                .sslProvider(sslServerProvider())
                .trustManager(servertTrustCrtFile)
                .ciphers(null, IdentityCipherSuiteFilter.INSTANCE)
                .sessionCacheSize(0)
                .sessionTimeout(0)
                .build();

        clientSslCtx = SslContextBuilder.forClient()
                .sslProvider(sslClientProvider())
                .trustManager(clientTrustCrtFile)
                .keyManager(clientCrtFile, clientKeyFile, clientKeyPassword)
                .ciphers(null, IdentityCipherSuiteFilter.INSTANCE)
                .sessionCacheSize(0)
                .sessionTimeout(0)
                .build();

        serverConnectedChannel = null;
        sb = new ServerBootstrap();
        cb = new Bootstrap();

        sb.group(new NioEventLoopGroup(), new NioEventLoopGroup());
        sb.channel(NioServerSocketChannel.class);
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                SSLEngine engine = serverSslCtx.newEngine(ch.alloc());
                engine.setUseClientMode(false);
                engine.setNeedClientAuth(true);
                p.addLast(new SslHandler(engine));
                p.addLast(new MessageDelegatorChannelHandler(serverReceiver, serverLatch));
                p.addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                        if (cause.getCause() instanceof SSLHandshakeException) {
                            serverException = cause.getCause();
                            serverLatch.countDown();
                        } else {
                            serverException = cause;
                            ctx.fireExceptionCaught(cause);
                        }
                    }

                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                        if (evt == SslHandshakeCompletionEvent.SUCCESS) {
                            // Verify session
                            SSLSession session = ctx.pipeline().get(SslHandler.class).engine().getSession();
                            assertEquals(1, session.getPeerCertificates().length);
                            assertArrayEquals(CERT_BYTES, session.getPeerCertificates()[0].getEncoded());

                            assertEquals(1, session.getPeerCertificateChain().length);
                            assertArrayEquals(CERT_BYTES, session.getPeerCertificateChain()[0].getEncoded());

                            assertEquals(1, session.getLocalCertificates().length);
                            assertArrayEquals(CERT_BYTES, session.getLocalCertificates()[0].getEncoded());

                            assertEquals(PRINCIPAL_NAME, session.getLocalPrincipal().getName());
                            assertEquals(PRINCIPAL_NAME, session.getPeerPrincipal().getName());
                        }
                    }
                });
                serverConnectedChannel = ch;
            }
        });

        cb.group(new NioEventLoopGroup());
        cb.channel(NioSocketChannel.class);
        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                p.addLast(clientSslCtx.newHandler(ch.alloc()));
                p.addLast(new MessageDelegatorChannelHandler(clientReceiver, clientLatch));
                p.addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                        cause.printStackTrace();
                        if (cause.getCause() instanceof SSLHandshakeException) {
                            clientException = cause.getCause();
                            clientLatch.countDown();
                        } else {
                            ctx.fireExceptionCaught(cause);
                        }
                    }
                });
            }
        });

        serverChannel = sb.bind(new InetSocketAddress(0)).sync().channel();
        int port = ((InetSocketAddress) serverChannel.localAddress()).getPort();

        ChannelFuture ccf = cb.connect(new InetSocketAddress(NetUtil.LOCALHOST, port));
        assertTrue(ccf.awaitUninterruptibly().isSuccess());
        clientChannel = ccf.channel();
    }

    protected void runTest(String expectedApplicationProtocol) throws Exception {
        final ByteBuf clientMessage = Unpooled.copiedBuffer("I am a client".getBytes());
        final ByteBuf serverMessage = Unpooled.copiedBuffer("I am a server".getBytes());
        try {
            writeAndVerifyReceived(clientMessage.retain(), clientChannel, serverLatch, serverReceiver);
            writeAndVerifyReceived(serverMessage.retain(), serverConnectedChannel, clientLatch, clientReceiver);
            if (expectedApplicationProtocol != null) {
                verifyApplicationLevelProtocol(clientChannel, expectedApplicationProtocol);
                verifyApplicationLevelProtocol(serverConnectedChannel, expectedApplicationProtocol);
            }
        } finally {
            clientMessage.release();
            serverMessage.release();
        }
    }

    private static void verifyApplicationLevelProtocol(Channel channel, String expectedApplicationProtocol) {
        SslHandler handler = channel.pipeline().get(SslHandler.class);
        assertNotNull(handler);
        String appProto = handler.applicationProtocol();
        assertEquals(appProto, expectedApplicationProtocol);
    }

    private static void writeAndVerifyReceived(ByteBuf message, Channel sendChannel, CountDownLatch receiverLatch,
                                               MessageReceiver receiver) throws Exception {
        List<ByteBuf> dataCapture = null;
        try {
            sendChannel.writeAndFlush(message);
            receiverLatch.await(5, TimeUnit.SECONDS);
            message.resetReaderIndex();
            ArgumentCaptor<ByteBuf> captor = ArgumentCaptor.forClass(ByteBuf.class);
            verify(receiver).messageReceived(captor.capture());
            dataCapture = captor.getAllValues();
            assertEquals(message, dataCapture.get(0));
        } finally {
            if (dataCapture != null) {
                for (ByteBuf data : dataCapture) {
                    data.release();
                }
            }
        }
    }

    @Test
    public void testGetCreationTime() throws Exception {
        clientSslCtx = SslContextBuilder.forClient().sslProvider(sslClientProvider()).build();
        SSLEngine engine = null;
        try {
            engine = clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);
            assertTrue(engine.getSession().getCreationTime() <= System.currentTimeMillis());
        } finally {
            cleanupClientSslEngine(engine);
        }
    }

    @Test
    public void testSessionInvalidate() throws Exception {
        clientSslCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(sslClientProvider())
                .build();
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(sslServerProvider())
                .build();
        SSLEngine clientEngine = null;
        SSLEngine serverEngine = null;
        try {
            clientEngine = clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);
            serverEngine = serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);
            handshake(clientEngine, serverEngine);

            SSLSession session = serverEngine.getSession();
            assertTrue(session.isValid());
            session.invalidate();
            assertFalse(session.isValid());
        } finally {
            cleanupClientSslEngine(clientEngine);
            cleanupServerSslEngine(serverEngine);
        }
    }

    @Test
    public void testSSLSessionId() throws Exception {
        clientSslCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(sslClientProvider())
                .build();
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(sslServerProvider())
                .build();
        SSLEngine clientEngine = null;
        SSLEngine serverEngine = null;
        try {
            clientEngine = clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);
            serverEngine = serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);

            // Before the handshake the id should have length == 0
            assertEquals(0, clientEngine.getSession().getId().length);
            assertEquals(0, serverEngine.getSession().getId().length);

            handshake(clientEngine, serverEngine);

            // After the handshake the id should have length > 0
            assertNotEquals(0, clientEngine.getSession().getId().length);
            assertNotEquals(0, serverEngine.getSession().getId().length);
            assertArrayEquals(clientEngine.getSession().getId(), serverEngine.getSession().getId());
        } finally {
            cleanupClientSslEngine(clientEngine);
            cleanupServerSslEngine(serverEngine);
        }
    }

    @Test(timeout = 3000)
    public void clientInitiatedRenegotiationWithFatalAlertDoesNotInfiniteLoopServer()
            throws CertificateException, SSLException, InterruptedException, ExecutionException {
        final SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(sslServerProvider()).build();
        sb = new ServerBootstrap()
                .group(new NioEventLoopGroup(1))
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(serverSslCtx.newHandler(ch.alloc()));
                        p.addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                                if (evt instanceof SslHandshakeCompletionEvent &&
                                        ((SslHandshakeCompletionEvent) evt).isSuccess()) {
                                    // This data will be sent to the client before any of the re-negotiation data can be
                                    // sent. The client will read this, detect that it is not the response to
                                    // renegotiation which was expected, and respond with a fatal alert.
                                    ctx.writeAndFlush(ctx.alloc().buffer(1).writeByte(100));
                                }
                                ctx.fireUserEventTriggered(evt);
                            }

                            @Override
                            public void channelRead(final ChannelHandlerContext ctx, Object msg) {
                                ReferenceCountUtil.release(msg);
                                // The server then attempts to trigger a flush operation once the application data is
                                // received from the client. The flush will encrypt all data and should not result in
                                // deadlock.
                                ctx.channel().eventLoop().schedule(new Runnable() {
                                    @Override
                                    public void run() {
                                        ctx.writeAndFlush(ctx.alloc().buffer(1).writeByte(101));
                                    }
                                }, 500, TimeUnit.MILLISECONDS);
                            }

                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) {
                                serverLatch.countDown();
                            }
                        });
                        serverConnectedChannel = ch;
                    }
                });

        serverChannel = sb.bind(new InetSocketAddress(0)).syncUninterruptibly().channel();

        clientSslCtx = SslContextBuilder.forClient()
                .sslProvider(SslProvider.JDK) // OpenSslEngine doesn't support renegotiation on client side
                .trustManager(InsecureTrustManagerFactory.INSTANCE).build();

        cb = new Bootstrap();
        cb.group(new NioEventLoopGroup(1))
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        SslHandler sslHandler = clientSslCtx.newHandler(ch.alloc());
                        // The renegotiate is not expected to succeed, so we should stop trying in a timely manner so
                        // the unit test can terminate relativley quicly.
                        sslHandler.setHandshakeTimeout(1, TimeUnit.SECONDS);
                        p.addLast(sslHandler);
                        p.addLast(new ChannelInboundHandlerAdapter() {
                            private int handshakeCount;
                            @Override
                            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                                // OpenSSL SSLEngine sends a fatal alert for the renegotiation handshake because the
                                // user data read as part of the handshake. The client receives this fatal alert and is
                                // expected to shutdown the connection. The "invalid data" during the renegotiation
                                // handshake is also delivered to channelRead(..) on the server.
                                // JDK SSLEngine completes the renegotiation handshake and delivers the "invalid data"
                                // is also delivered to channelRead(..) on the server. JDK SSLEngine does not send a
                                // fatal error and so for testing purposes we close the connection after we have
                                // completed the first renegotiation handshake (which is the second handshake).
                                if (evt instanceof SslHandshakeCompletionEvent && ++handshakeCount == 2) {
                                    ctx.close();
                                    return;
                                }
                                ctx.fireUserEventTriggered(evt);
                            }

                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                ReferenceCountUtil.release(msg);
                                // Simulate a request that the server's application logic will think is invalid.
                                ctx.writeAndFlush(ctx.alloc().buffer(1).writeByte(102));
                                ctx.pipeline().get(SslHandler.class).renegotiate();
                            }
                        });
                    }
                });

        ChannelFuture ccf = cb.connect(serverChannel.localAddress());
        assertTrue(ccf.syncUninterruptibly().isSuccess());
        clientChannel = ccf.channel();

        serverLatch.await();
    }

    protected void testEnablingAnAlreadyDisabledSslProtocol(String[] protocols1, String[] protocols2) throws Exception {
        SSLEngine sslEngine = null;
        try {
            File serverKeyFile = new File(getClass().getResource("test_unencrypted.pem").getFile());
            File serverCrtFile = new File(getClass().getResource("test.crt").getFile());
            serverSslCtx = SslContextBuilder.forServer(serverCrtFile, serverKeyFile)
               .sslProvider(sslServerProvider())
               .build();

            sslEngine = serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);

            // Disable all protocols
            sslEngine.setEnabledProtocols(EmptyArrays.EMPTY_STRINGS);

            // The only protocol that should be enabled is SSLv2Hello
            String[] enabledProtocols = sslEngine.getEnabledProtocols();
            assertEquals(protocols1.length, enabledProtocols.length);
            assertArrayEquals(protocols1, enabledProtocols);

            // Enable a protocol that is currently disabled
            sslEngine.setEnabledProtocols(new String[]{PROTOCOL_TLS_V1_2});

            // The protocol that was just enabled should be returned
            enabledProtocols = sslEngine.getEnabledProtocols();
            assertEquals(protocols2.length, enabledProtocols.length);
            assertArrayEquals(protocols2, enabledProtocols);
        } finally {
            if (sslEngine != null) {
                sslEngine.closeInbound();
                sslEngine.closeOutbound();
                cleanupServerSslEngine(sslEngine);
            }
        }
    }

    protected static void handshake(SSLEngine clientEngine, SSLEngine serverEngine) throws SSLException {
        int netBufferSize = 17 * 1024;
        ByteBuffer cTOs = ByteBuffer.allocateDirect(netBufferSize);
        ByteBuffer sTOc = ByteBuffer.allocateDirect(netBufferSize);

        ByteBuffer serverAppReadBuffer = ByteBuffer.allocateDirect(
                serverEngine.getSession().getApplicationBufferSize());
        ByteBuffer clientAppReadBuffer = ByteBuffer.allocateDirect(
                clientEngine.getSession().getApplicationBufferSize());

        clientEngine.beginHandshake();
        serverEngine.beginHandshake();

        ByteBuffer empty = ByteBuffer.allocate(0);

        SSLEngineResult clientResult;
        SSLEngineResult serverResult;

        do {
            clientResult = clientEngine.wrap(empty, cTOs);
            runDelegatedTasks(clientResult, clientEngine);
            serverResult = serverEngine.wrap(empty, sTOc);
            runDelegatedTasks(serverResult, serverEngine);
            cTOs.flip();
            sTOc.flip();
            clientResult = clientEngine.unwrap(sTOc, clientAppReadBuffer);
            runDelegatedTasks(clientResult, clientEngine);
            serverResult = serverEngine.unwrap(cTOs, serverAppReadBuffer);
            runDelegatedTasks(serverResult, serverEngine);
            cTOs.compact();
            sTOc.compact();
        } while (isHandshaking(clientResult) || isHandshaking(serverResult));
    }

    private static boolean isHandshaking(SSLEngineResult result) {
        return result.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING &&
                result.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.FINISHED;
    }

    private static void runDelegatedTasks(SSLEngineResult result, SSLEngine engine) {
        if (result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK) {
            for (;;) {
                Runnable task = engine.getDelegatedTask();
                if (task == null) {
                    break;
                }
                task.run();
            }
        }
    }

    protected abstract SslProvider sslClientProvider();

    protected abstract SslProvider sslServerProvider();

    /**
     * Called from the test cleanup code and can be used to release the {@code ctx} if it must be done manually.
     */
    protected void cleanupClientSslContext(SslContext ctx) {
    }

    /**
     * Called from the test cleanup code and can be used to release the {@code ctx} if it must be done manually.
     */
    protected void cleanupServerSslContext(SslContext ctx) {
    }

    /**
     * Called when ever an SSLEngine is not wrapped by a {@link SslHandler} and inserted into a pipeline.
     */
    protected void cleanupClientSslEngine(SSLEngine engine) {
    }

    /**
     * Called when ever an SSLEngine is not wrapped by a {@link SslHandler} and inserted into a pipeline.
     */
    protected void cleanupServerSslEngine(SSLEngine engine) {
    }

    protected void setupHandlers(ApplicationProtocolConfig apn) throws InterruptedException, SSLException,
                                                                       CertificateException {
        setupHandlers(apn, apn);
    }

    protected void setupHandlers(ApplicationProtocolConfig serverApn, ApplicationProtocolConfig clientApn)
            throws InterruptedException, SSLException, CertificateException {
        SelfSignedCertificate ssc = new SelfSignedCertificate();

        setupHandlers(SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey(), null)
                        .sslProvider(sslServerProvider())
                        .ciphers(null, IdentityCipherSuiteFilter.INSTANCE)
                        .applicationProtocolConfig(serverApn)
                        .sessionCacheSize(0)
                        .sessionTimeout(0)
                        .build(),

                SslContextBuilder.forClient()
                        .sslProvider(sslClientProvider())
                        .applicationProtocolConfig(clientApn)
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .ciphers(null, IdentityCipherSuiteFilter.INSTANCE)
                        .sessionCacheSize(0)
                        .sessionTimeout(0)
                        .build());
    }

    protected void setupHandlers(SslContext serverCtx, SslContext clientCtx)
            throws InterruptedException, SSLException, CertificateException {

        serverSslCtx = serverCtx;
        clientSslCtx = clientCtx;

        serverConnectedChannel = null;
        sb = new ServerBootstrap();
        cb = new Bootstrap();

        sb.group(new NioEventLoopGroup(), new NioEventLoopGroup());
        sb.channel(NioServerSocketChannel.class);
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                p.addLast(serverSslCtx.newHandler(ch.alloc()));
                p.addLast(new MessageDelegatorChannelHandler(serverReceiver, serverLatch));
                p.addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                        if (cause.getCause() instanceof SSLHandshakeException) {
                            serverException = cause.getCause();
                            serverLatch.countDown();
                        } else {
                            ctx.fireExceptionCaught(cause);
                        }
                    }
                });
                serverConnectedChannel = ch;
            }
        });

        cb.group(new NioEventLoopGroup());
        cb.channel(NioSocketChannel.class);
        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                p.addLast(clientSslCtx.newHandler(ch.alloc()));
                p.addLast(new MessageDelegatorChannelHandler(clientReceiver, clientLatch));
                p.addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                        if (cause.getCause() instanceof SSLHandshakeException) {
                            clientException = cause.getCause();
                            clientLatch.countDown();
                        } else {
                            ctx.fireExceptionCaught(cause);
                        }
                    }
                });
            }
        });

        serverChannel = sb.bind(new InetSocketAddress(0)).syncUninterruptibly().channel();

        ChannelFuture ccf = cb.connect(serverChannel.localAddress());
        assertTrue(ccf.syncUninterruptibly().isSuccess());
        clientChannel = ccf.channel();
    }

}
