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
import io.netty.buffer.ByteBufAllocator;
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
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.EmptyArrays;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
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

    private static final String X509_CERT_PEM =
            "-----BEGIN CERTIFICATE-----\n" +
                    "MIIB9jCCAV+gAwIBAgIJAO9fzyjyV5BhMA0GCSqGSIb3DQEBCwUAMBQxEjAQBgNV\n" +
                    "BAMMCWxvY2FsaG9zdDAeFw0xNjA5MjAxOTI0MTVaFw00NDEwMDMxOTI0MTVaMBQx\n" +
                    "EjAQBgNVBAMMCWxvY2FsaG9zdDCBnzANBgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEA\n" +
                    "1Kp6DmwRiI+TNs3rZ3WvceDYQ4VTxZQk9jgHVKhHTeA0LptOaazbm9g+aOPiCc6V\n" +
                    "5ysu8T8YRLWjej3by2/1zPBO1k25dQRK8dHr0Grmo20FW7+ES+YxohOfmi7bjOVm\n" +
                    "NrI3NoVZjf3fQjAlrtKCmaxRPgYEwOT0ucGfJiEyV9cCAwEAAaNQME4wHQYDVR0O\n" +
                    "BBYEFIba521hTU1P+1QHcIqAOdAEgd1QMB8GA1UdIwQYMBaAFIba521hTU1P+1QH\n" +
                    "cIqAOdAEgd1QMAwGA1UdEwQFMAMBAf8wDQYJKoZIhvcNAQELBQADgYEAHG5hBy0b\n" +
                    "ysXKJqWQ/3bNId3VCzD9U557oxEYYAuPG0TqyvjvZ3wNQto079Na7lYkTt2kTIYN\n" +
                    "/HPW2eflDyXAwXmdNM1Gre213NECY9VxDBTCYJ1R4f2Ogv9iehwzZ4aJGxEDay69\n" +
                    "wrGrxKIrKL4OMl/E+R4mi+yZ0i6bfQuli5s=\n" +
                    "-----END CERTIFICATE-----\n";

    private static final String PRIVATE_KEY_PEM =
            "-----BEGIN PRIVATE KEY-----\n" +
                    "MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBANSqeg5sEYiPkzbN\n" +
                    "62d1r3Hg2EOFU8WUJPY4B1SoR03gNC6bTmms25vYPmjj4gnOlecrLvE/GES1o3o9\n" +
                    "28tv9czwTtZNuXUESvHR69Bq5qNtBVu/hEvmMaITn5ou24zlZjayNzaFWY3930Iw\n" +
                    "Ja7SgpmsUT4GBMDk9LnBnyYhMlfXAgMBAAECgYAeyc+B5wNi0eZuOMGr6M3Nns+w\n" +
                    "dsz5/cicHOBy0SoBjEQBu1pO0ke4+EWQye0fnlj1brsNEiVhTSqtt+bqPPtIvKtZ\n" +
                    "U4Z2M5euUQL390LnVM+jlaRyKUFVYzFnWfNgciT6SLsrbGRz9EhMH2jM6gi8O/cI\n" +
                    "n8Do9fgHon9dILOPAQJBAO/3xc0/sWP94Cv25ogsvOLRgXY2NqY/PDfWat31MFt4\n" +
                    "pKh9aad7SrqR7oRXIEuJ+16drM0O+KveJEjFnHgcq18CQQDi38CqycxrsL2pzq53\n" +
                    "XtlhbzOBpDaNjySMmdg8wIXVVGmFC7Y2zWq+cEirrI0n2BJOC4LLDNvlT6IjnYqF\n" +
                    "qp6JAkBQfB0Wyz8XF4aBmG0XzVGJDdXLLUHFHr52x+7OBTez5lHrxSyTpPGag+mo\n" +
                    "74QAcgYiZOYZXOUg1//5fHYPfyYnAkANKyenwibXaV7Y6GJAE4VSnn3C3KE9/j0E\n" +
                    "3Dks7Y/XHhsx2cgtziaP/zx4mn9m/KezV/+zgX+SA9lJb++GaqzhAkEAoNfjQ4jd\n" +
                    "3fsY99ZVoC5YFASSKf+DBqcVOkiUtF1pRwBzLDgKW14+nM/v7X+HJzkfnNTj4cW/\n" +
                    "nUq37uAS7oJc4g==\n" +
                    "-----END PRIVATE KEY-----\n";

    private static final String CLIENT_X509_CERT_PEM =
            "-----BEGIN CERTIFICATE-----\n" +
                    "MIIBlzCCAQACAQEwDQYJKoZIhvcNAQELBQAwFDESMBAGA1UEAwwJbG9jYWxob3N0\n" +
                    "MB4XDTE2MDkyMDE5NTIyMFoXDTE3MDkyMDE5NTIyMFowFDESMBAGA1UEAwwJdGxz\n" +
                    "Y2xpZW50MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDB19iXOYtyPp6cMJiG\n" +
                    "9C9QNw/ro6SWpv9H0wPcPBhWRrPBJvXppVWxMMxYuJFSKC8cGRqpK+k0h9omn2l+\n" +
                    "c1ReKFEMi0csZRZMGjkUYv0/ol6tlz0CFvQOU5lRxkh1JWI/P1/b36zJCTvyNEV4\n" +
                    "upjAV1eAu27twS8hBrPK2+pIqwIDAQABMA0GCSqGSIb3DQEBCwUAA4GBAMa35kuy\n" +
                    "WzuHLg7iwnDVX4ZT/4iTJg1fZDms2dTqFWH+RYlxsytePUkY3ksGHl+VJgoDBK3X\n" +
                    "G6/dqNa5BQDyFF0/dDQ2XYTrj5Yd0MsLA00AkFdSow5RyhWXJXzVHgKE48ZFW5Bt\n" +
                    "NW9uXaerRzFR1mG2+0AghxCNMhuWxIblW7L0\n" +
                    "-----END CERTIFICATE-----\n";

    private static final String CLIENT_PRIVATE_KEY_PEM =
            "-----BEGIN PRIVATE KEY-----\n" +
                    "MIICdwIBADANBgkqhkiG9w0BAQEFAASCAmEwggJdAgEAAoGBAMHX2Jc5i3I+npww\n" +
                    "mIb0L1A3D+ujpJam/0fTA9w8GFZGs8Em9emlVbEwzFi4kVIoLxwZGqkr6TSH2iaf\n" +
                    "aX5zVF4oUQyLRyxlFkwaORRi/T+iXq2XPQIW9A5TmVHGSHUlYj8/X9vfrMkJO/I0\n" +
                    "RXi6mMBXV4C7bu3BLyEGs8rb6kirAgMBAAECgYBKPKrzh5NTJo5CDQ5tKNlx5BSR\n" +
                    "zzM6iyxbSoJA9zbu29b90zj8yVgfKywnkk/9Yexg23Btd6axepHeltClH/AgD1GL\n" +
                    "QE9bpeBMm8r+/9v/XR/mn5GjTxspj/q29mqOdg8CrKb8M6r1gtj70r8nI8aqmDwV\n" +
                    "b6/ZTpsei+tN635Y2QJBAP1FHtIK2Z4t2Ro7oNaiv3s3TsYDlj14ladG+DKi2tW+\n" +
                    "9PW7AO8rLAx2LWmrilXDFc7UG6hvhmUVkp7wXRCK0dcCQQDD7r3g8lswdEAVI0tF\n" +
                    "fzJO61vDR12Kxv4flar9GwWdak7EzCp//iYNPWc+S7ONlYRbbI+uKVL/KBlvkU9E\n" +
                    "4M1NAkEAy0ZGzl5W+1XhAeUJ2jsVZFenqdYHJ584veGAI2QCL7vr763/ufX0jKvt\n" +
                    "FvrPNLY3MqGa8T1RqJ//5gEVMMm6UQJAKpBJpX1gu/T1GuJw7qcEKcrNQ23Ub1pt\n" +
                    "SDU+UP+2x4yZkfz8WpO+dm/ZZtoRJnfNqgK6b85AXne6ltcNTlw7nQJBAKnFel18\n" +
                    "Tg2ea308CyM+SJQxpfmU+1yeO2OYHNmimjWFhQPuxIDP9JUzpW39DdCDdTcd++HK\n" +
                    "xJ5gsU/5OLk6ySo=\n" +
                    "-----END PRIVATE KEY-----\n";
    private static final String CLIENT_X509_CERT_CHAIN_PEM = CLIENT_X509_CERT_PEM + X509_CERT_PEM;

    protected static final String PROTOCOL_TLS_V1_2 = "TLSv1.2";
    protected static final String PROTOCOL_SSL_V2_HELLO = "SSLv2Hello";
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
            serverGroupShutdownFuture = sb.group().shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
            serverChildGroupShutdownFuture = sb.childGroup().shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
        }
        if (cb != null) {
            clientGroupShutdownFuture = cb.group().shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
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
            File servertTrustCrtFile, File serverKeyFile, final File serverCrtFile, String serverKeyPassword,
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
                            try {
                                InputStream in = new FileInputStream(serverCrtFile);
                                try {
                                    final byte[] cert = SslContext.X509_CERT_FACTORY
                                            .generateCertificate(in).getEncoded();

                                    // Verify session
                                    SSLSession session = ctx.pipeline().get(SslHandler.class).engine().getSession();
                                    assertEquals(1, session.getPeerCertificates().length);
                                    assertArrayEquals(cert, session.getPeerCertificates()[0].getEncoded());

                                    assertEquals(1, session.getPeerCertificateChain().length);
                                    assertArrayEquals(cert, session.getPeerCertificateChain()[0].getEncoded());

                                    assertEquals(1, session.getLocalCertificates().length);
                                    assertArrayEquals(cert, session.getLocalCertificates()[0].getEncoded());

                                    assertEquals(PRINCIPAL_NAME, session.getLocalPrincipal().getName());
                                    assertEquals(PRINCIPAL_NAME, session.getPeerPrincipal().getName());
                                } finally {
                                    in.close();
                                }
                            } catch (Throwable cause) {
                                serverException = cause;
                            }
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

    @Test(timeout = 5000)
    public void testMutualAuthSameCertChain() throws Exception {
        serverSslCtx = SslContextBuilder.forServer(
                new ByteArrayInputStream(X509_CERT_PEM.getBytes(CharsetUtil.UTF_8)),
                new ByteArrayInputStream(PRIVATE_KEY_PEM.getBytes(CharsetUtil.UTF_8)))
                .trustManager(new ByteArrayInputStream(X509_CERT_PEM.getBytes(CharsetUtil.UTF_8)))
                .clientAuth(ClientAuth.REQUIRE).sslProvider(sslServerProvider()).build();

        sb = new ServerBootstrap();
        sb.group(new NioEventLoopGroup(), new NioEventLoopGroup());
        sb.channel(NioServerSocketChannel.class);

        final Promise<String> promise = sb.group().next().newPromise();
        serverChannel = sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addFirst(serverSslCtx.newHandler(ch.alloc()));
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                        if (evt instanceof SslHandshakeCompletionEvent) {
                            Throwable cause = ((SslHandshakeCompletionEvent) evt).cause();
                            if (cause == null) {
                                promise.setSuccess(null);
                            } else {
                                promise.setFailure(cause);
                            }
                        }
                    }
                });
                serverConnectedChannel = ch;
            }
        }).bind(new InetSocketAddress(0)).syncUninterruptibly().channel();

        clientSslCtx = SslContextBuilder.forClient()
                .keyManager(
                        new ByteArrayInputStream(CLIENT_X509_CERT_CHAIN_PEM.getBytes(CharsetUtil.UTF_8)),
                        new ByteArrayInputStream(CLIENT_PRIVATE_KEY_PEM.getBytes(CharsetUtil.UTF_8)))
                .trustManager(new ByteArrayInputStream(X509_CERT_PEM.getBytes(CharsetUtil.UTF_8)))
                .sslProvider(sslClientProvider()).build();
        cb = new Bootstrap();
        cb.group(new NioEventLoopGroup());
        cb.channel(NioSocketChannel.class);
        clientChannel = cb.handler(
                new SslHandler(clientSslCtx.newEngine(ByteBufAllocator.DEFAULT)))
                .connect(serverChannel.localAddress()).syncUninterruptibly().channel();

        promise.syncUninterruptibly();
    }
}
