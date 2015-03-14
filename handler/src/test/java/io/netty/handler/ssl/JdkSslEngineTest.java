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
package io.netty.handler.ssl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;
import static org.mockito.Mockito.verify;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.JdkApplicationProtocolNegotiator.ProtocolSelector;
import io.netty.handler.ssl.JdkApplicationProtocolNegotiator.ProtocolSelectorFactory;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Future;

import java.io.File;
import java.net.InetSocketAddress;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class JdkSslEngineTest {
    private static final String PREFERRED_APPLICATION_LEVEL_PROTOCOL = "my-protocol-http2";
    private static final String FALLBACK_APPLICATION_LEVEL_PROTOCOL = "my-protocol-http1_1";
    private static final String APPLICATION_LEVEL_PROTOCOL_NOT_COMPATIBLE = "my-protocol-FOO";

    @Mock
    private MessageReciever serverReceiver;
    @Mock
    private MessageReciever clientReceiver;

    private Throwable serverException;
    private Throwable clientException;
    private SslContext serverSslCtx;
    private SslContext clientSslCtx;
    private ServerBootstrap sb;
    private Bootstrap cb;
    private Channel serverChannel;
    private Channel serverConnectedChannel;
    private Channel clientChannel;
    private CountDownLatch serverLatch;
    private CountDownLatch clientLatch;

    private interface MessageReciever {
        void messageReceived(ByteBuf msg);
    }

    private final class MessageDelegatorChannelHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final MessageReciever receiver;
        private final CountDownLatch latch;

        public MessageDelegatorChannelHandler(MessageReciever receiver, CountDownLatch latch) {
            super(false);
            this.receiver = receiver;
            this.latch = latch;
        }

        @Override
        protected void messageReceived(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
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
        if (serverChannel != null) {
            serverChannel.close().sync();
            Future<?> serverGroup = sb.group().shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
            Future<?> serverChildGroup = sb.childGroup().shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
            Future<?> clientGroup = cb.group().shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
            serverGroup.sync();
            serverChildGroup.sync();
            clientGroup.sync();
        }
        clientChannel = null;
        serverChannel = null;
        serverConnectedChannel = null;
        serverException = null;
    }

    @Test
    public void testNpn() throws Exception {
        try {
            // Typical code will not have to check this, but will get a initialization error on class load.
            // Check in this test just in case we have multiple tests that just the class and we already ignored the
            // initialization error.
            if (!JdkNpnSslEngine.isAvailable()) {
                throw new RuntimeException("NPN not on classpath");
            }
            JdkApplicationProtocolNegotiator apn = new JdkNpnApplicationProtocolNegotiator(true, true,
                    PREFERRED_APPLICATION_LEVEL_PROTOCOL);
            mySetup(apn);
            runTest();
        } catch (RuntimeException e) {
            // NPN availability is dependent on the java version. If NPN is not available because of
            // java version incompatibility don't fail the test, but instead just skip the test
            assumeNoException(e);
        }
    }

    @Test
    public void testNpnNoCompatibleProtocolsNoHandshakeFailure() throws Exception {
        try {
            // Typical code will not have to check this, but will get a initialization error on class load.
            // Check in this test just in case we have multiple tests that just the class and we already ignored the
            // initialization error.
            if (!JdkNpnSslEngine.isAvailable()) {
                throw new RuntimeException("NPN not on classpath");
            }
            JdkApplicationProtocolNegotiator clientApn = new JdkNpnApplicationProtocolNegotiator(false, false,
                    PREFERRED_APPLICATION_LEVEL_PROTOCOL);
            JdkApplicationProtocolNegotiator serverApn = new JdkNpnApplicationProtocolNegotiator(false, false,
                    APPLICATION_LEVEL_PROTOCOL_NOT_COMPATIBLE);
            mySetup(serverApn, clientApn);
            runTest(null);
        } catch (Exception e) {
            // ALPN availability is dependent on the java version. If ALPN is not available because of
            // java version incompatibility don't fail the test, but instead just skip the test
            assumeNoException(e);
        }
    }

    @Test
    public void testNpnNoCompatibleProtocolsClientHandshakeFailure() throws Exception {
        try {
            // Typical code will not have to check this, but will get a initialization error on class load.
            // Check in this test just in case we have multiple tests that just the class and we already ignored the
            // initialization error.
            if (!JdkNpnSslEngine.isAvailable()) {
                throw new RuntimeException("NPN not on classpath");
            }
            JdkApplicationProtocolNegotiator clientApn = new JdkNpnApplicationProtocolNegotiator(true, true,
                    PREFERRED_APPLICATION_LEVEL_PROTOCOL);
            JdkApplicationProtocolNegotiator serverApn = new JdkNpnApplicationProtocolNegotiator(false, false,
                    APPLICATION_LEVEL_PROTOCOL_NOT_COMPATIBLE);
            mySetup(serverApn, clientApn);
            assertTrue(clientLatch.await(2, TimeUnit.SECONDS));
            assertTrue(clientException instanceof SSLHandshakeException);
        } catch (RuntimeException e) {
            // NPN availability is dependent on the java version. If NPN is not available because of
            // java version incompatibility don't fail the test, but instead just skip the test
            assumeNoException(e);
        }
    }

    @Test
    public void testNpnNoCompatibleProtocolsServerHandshakeFailure() throws Exception {
        try {
            // Typical code will not have to check this, but will get a initialization error on class load.
            // Check in this test just in case we have multiple tests that just the class and we already ignored the
            // initialization error.
            if (!JdkNpnSslEngine.isAvailable()) {
                throw new RuntimeException("NPN not on classpath");
            }
            JdkApplicationProtocolNegotiator clientApn = new JdkNpnApplicationProtocolNegotiator(false, false,
                    PREFERRED_APPLICATION_LEVEL_PROTOCOL);
            JdkApplicationProtocolNegotiator serverApn = new JdkNpnApplicationProtocolNegotiator(true, true,
                    APPLICATION_LEVEL_PROTOCOL_NOT_COMPATIBLE);
            mySetup(serverApn, clientApn);
            assertTrue(serverLatch.await(2, TimeUnit.SECONDS));
            assertTrue(serverException instanceof SSLHandshakeException);
        } catch (RuntimeException e) {
            // NPN availability is dependent on the java version. If NPN is not available because of
            // java version incompatibility don't fail the test, but instead just skip the test
            assumeNoException(e);
        }
    }

    @Test
    public void testAlpn() throws Exception {
        try {
            // Typical code will not have to check this, but will get a initialization error on class load.
            // Check in this test just in case we have multiple tests that just the class and we already ignored the
            // initialization error.
            if (!JdkAlpnSslEngine.isAvailable()) {
                throw new RuntimeException("ALPN not on classpath");
            }
            JdkApplicationProtocolNegotiator apn = new JdkAlpnApplicationProtocolNegotiator(true, true,
                    PREFERRED_APPLICATION_LEVEL_PROTOCOL);
            mySetup(apn);
            runTest();
        } catch (Exception e) {
            // ALPN availability is dependent on the java version. If ALPN is not available because of
            // java version incompatibility don't fail the test, but instead just skip the test
            assumeNoException(e);
        }
    }

    @Test
    public void testAlpnNoCompatibleProtocolsNoHandshakeFailure() throws Exception {
        try {
            // Typical code will not have to check this, but will get a initialization error on class load.
            // Check in this test just in case we have multiple tests that just the class and we already ignored the
            // initialization error.
            if (!JdkAlpnSslEngine.isAvailable()) {
                throw new RuntimeException("ALPN not on classpath");
            }
            JdkApplicationProtocolNegotiator clientApn = new JdkAlpnApplicationProtocolNegotiator(false, false,
                    PREFERRED_APPLICATION_LEVEL_PROTOCOL);
            JdkApplicationProtocolNegotiator serverApn = new JdkAlpnApplicationProtocolNegotiator(false, false,
                    APPLICATION_LEVEL_PROTOCOL_NOT_COMPATIBLE);
            mySetup(serverApn, clientApn);
            runTest(null);
        } catch (Exception e) {
            // ALPN availability is dependent on the java version. If ALPN is not available because of
            // java version incompatibility don't fail the test, but instead just skip the test
            assumeNoException(e);
        }
    }

    @Test
    public void testAlpnNoCompatibleProtocolsServerHandshakeFailure() throws Exception {
        try {
            // Typical code will not have to check this, but will get a initialization error on class load.
            // Check in this test just in case we have multiple tests that just the class and we already ignored the
            // initialization error.
            if (!JdkAlpnSslEngine.isAvailable()) {
                throw new RuntimeException("ALPN not on classpath");
            }
            JdkApplicationProtocolNegotiator clientApn = new JdkAlpnApplicationProtocolNegotiator(false, false,
                    PREFERRED_APPLICATION_LEVEL_PROTOCOL);
            JdkApplicationProtocolNegotiator serverApn = new JdkAlpnApplicationProtocolNegotiator(true, true,
                    APPLICATION_LEVEL_PROTOCOL_NOT_COMPATIBLE);
            mySetup(serverApn, clientApn);
            assertTrue(serverLatch.await(2, TimeUnit.SECONDS));
            assertTrue(serverException instanceof SSLHandshakeException);
        } catch (Exception e) {
            // ALPN availability is dependent on the java version. If ALPN is not available because of
            // java version incompatibility don't fail the test, but instead just skip the test
            assumeNoException(e);
        }
    }

    @Test
    public void testAlpnCompatibleProtocolsDifferentClientOrder() throws Exception {
        try {
            // Typical code will not have to check this, but will get a initialization error on class load.
            // Check in this test just in case we have multiple tests that just the class and we already ignored the
            // initialization error.
            if (!JdkAlpnSslEngine.isAvailable()) {
                throw new RuntimeException("ALPN not on classpath");
            }
            // Even the preferred application protocol appears second in the client's list, it will be picked
            // because it's the first one on server's list.
            JdkApplicationProtocolNegotiator clientApn = new JdkAlpnApplicationProtocolNegotiator(false, false,
                FALLBACK_APPLICATION_LEVEL_PROTOCOL, PREFERRED_APPLICATION_LEVEL_PROTOCOL);
            JdkApplicationProtocolNegotiator serverApn = new JdkAlpnApplicationProtocolNegotiator(true, true,
                PREFERRED_APPLICATION_LEVEL_PROTOCOL, FALLBACK_APPLICATION_LEVEL_PROTOCOL);
            mySetup(serverApn, clientApn);
            assertNull(serverException);
            runTest(PREFERRED_APPLICATION_LEVEL_PROTOCOL);
        } catch (Exception e) {
            // ALPN availability is dependent on the java version. If ALPN is not available because of
            // java version incompatibility don't fail the test, but instead just skip the test
            assumeNoException(e);
        }
    }

    @Test
    public void testAlpnNoCompatibleProtocolsClientHandshakeFailure() throws Exception {
        try {
            // Typical code will not have to check this, but will get a initialization error on class load.
            // Check in this test just in case we have multiple tests that just the class and we already ignored the
            // initialization error.
            if (!JdkAlpnSslEngine.isAvailable()) {
                throw new RuntimeException("ALPN not on classpath");
            }
            JdkApplicationProtocolNegotiator clientApn = new JdkAlpnApplicationProtocolNegotiator(true, true,
                    PREFERRED_APPLICATION_LEVEL_PROTOCOL);
            JdkApplicationProtocolNegotiator serverApn = new JdkAlpnApplicationProtocolNegotiator(
                    new ProtocolSelectorFactory() {
                        @Override
                        public ProtocolSelector newSelector(SSLEngine engine, Set<String> supportedProtocols) {
                            return new ProtocolSelector() {
                                @Override
                                public void unsupported() {
                                }

                                @Override
                                public String select(List<String> protocols) {
                                    return APPLICATION_LEVEL_PROTOCOL_NOT_COMPATIBLE;
                                }
                            };
                        }
                    }, JdkBaseApplicationProtocolNegotiator.FAIL_SELECTION_LISTENER_FACTORY,
                    APPLICATION_LEVEL_PROTOCOL_NOT_COMPATIBLE);
            mySetup(serverApn, clientApn);
            assertTrue(clientLatch.await(2, TimeUnit.SECONDS));
            assertTrue(clientException instanceof SSLHandshakeException);
        } catch (Exception e) {
            // ALPN availability is dependent on the java version. If ALPN is not available because of
            // java version incompatibility don't fail the test, but instead just skip the test
            assumeNoException(e);
        }
    }

    @Test
    public void testMutualAuthSameCerts() throws Exception {
        mySetupMutualAuth(new File(getClass().getResource("test_unencrypted.pem").getFile()),
                          new File(getClass().getResource("test.crt").getFile()),
                          null);
        runTest(null);
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

    private void mySetup(JdkApplicationProtocolNegotiator apn) throws InterruptedException, SSLException,
            CertificateException {
        mySetup(apn, apn);
    }

    private void mySetup(JdkApplicationProtocolNegotiator serverApn, JdkApplicationProtocolNegotiator clientApn)
            throws InterruptedException, SSLException, CertificateException {
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = new JdkSslServerContext(ssc.certificate(), ssc.privateKey(), null, null,
                IdentityCipherSuiteFilter.INSTANCE, serverApn, 0, 0);
        clientSslCtx = new JdkSslClientContext(null, InsecureTrustManagerFactory.INSTANCE, null,
                IdentityCipherSuiteFilter.INSTANCE, clientApn, 0, 0);

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
                p.addLast(new ChannelHandlerAdapter() {
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
                p.addLast(new ChannelHandlerAdapter() {
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

        serverChannel = sb.bind(new InetSocketAddress(0)).sync().channel();
        int port = ((InetSocketAddress) serverChannel.localAddress()).getPort();

        ChannelFuture ccf = cb.connect(new InetSocketAddress(NetUtil.LOCALHOST, port));
        assertTrue(ccf.awaitUninterruptibly().isSuccess());
        clientChannel = ccf.channel();
    }

    private void mySetupMutualAuth(File keyFile, File crtFile, String keyPassword)
            throws SSLException, CertificateException, InterruptedException {
        mySetupMutualAuth(crtFile, keyFile, crtFile, keyPassword, crtFile, keyFile, crtFile, keyPassword);
    }

    private void mySetupMutualAuth(
            File servertTrustCrtFile, File serverKeyFile, File serverCrtFile, String serverKeyPassword,
            File clientTrustCrtFile, File clientKeyFile, File clientCrtFile, String clientKeyPassword)
            throws InterruptedException, SSLException, CertificateException {
        serverSslCtx = new JdkSslServerContext(servertTrustCrtFile, null,
                serverCrtFile, serverKeyFile, serverKeyPassword, null,
                null, IdentityCipherSuiteFilter.INSTANCE, (ApplicationProtocolConfig) null, 0, 0);
        clientSslCtx = new JdkSslClientContext(clientTrustCrtFile, null,
                clientCrtFile, clientKeyFile, clientKeyPassword, null,
                null, IdentityCipherSuiteFilter.INSTANCE, (ApplicationProtocolConfig) null, 0, 0);

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
                p.addLast(new ChannelHandlerAdapter() {
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
                p.addLast(new ChannelHandlerAdapter() {
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

        serverChannel = sb.bind(new InetSocketAddress(0)).sync().channel();
        int port = ((InetSocketAddress) serverChannel.localAddress()).getPort();

        ChannelFuture ccf = cb.connect(new InetSocketAddress(NetUtil.LOCALHOST, port));
        assertTrue(ccf.awaitUninterruptibly().isSuccess());
        clientChannel = ccf.channel();
    }

    private void runTest() throws Exception {
        runTest(PREFERRED_APPLICATION_LEVEL_PROTOCOL);
    }

    private void runTest(String expectedApplicationProtocol) throws Exception {
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

    private void verifyApplicationLevelProtocol(Channel channel, String expectedApplicationProtocol) {
        SslHandler handler = channel.pipeline().get(SslHandler.class);
        assertNotNull(handler);
        String[] protocol = handler.engine().getSession().getProtocol().split(":");
        assertNotNull(protocol);
        if (expectedApplicationProtocol != null && !expectedApplicationProtocol.isEmpty()) {
            assertTrue("protocol.length must be greater than 1 but is " + protocol.length, protocol.length > 1);
            assertEquals(expectedApplicationProtocol, protocol[1]);
        } else {
            assertEquals(1, protocol.length);
        }
    }

    private static void writeAndVerifyReceived(ByteBuf message, Channel sendChannel, CountDownLatch receiverLatch,
            MessageReciever receiver) throws Exception {
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
}
