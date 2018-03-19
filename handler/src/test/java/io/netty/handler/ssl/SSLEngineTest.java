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
import io.netty.buffer.CompositeByteBuf;
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
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
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
import java.nio.channels.ClosedChannelException;
import java.security.KeyStore;
import java.security.Provider;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;

import static io.netty.handler.ssl.SslUtils.PROTOCOL_TLS_V1;
import static io.netty.handler.ssl.SslUtils.PROTOCOL_TLS_V1_2;
import static io.netty.handler.ssl.SslUtils.SSL_RECORD_HEADER_LENGTH;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
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
                    "MIIBmTCCAQICAQEwDQYJKoZIhvcNAQELBQAwFDESMBAGA1UEAwwJbG9jYWxob3N0\n" +
                    "MCAXDTE3MDkyMTAzNDUwMVoYDzIxMTcwOTIyMDM0NTAxWjAUMRIwEAYDVQQDEwl0\n" +
                    "bHNjbGllbnQwgZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGBAMHX2Jc5i3I+npww\n" +
                    "mIb0L1A3D+ujpJam/0fTA9w8GFZGs8Em9emlVbEwzFi4kVIoLxwZGqkr6TSH2iaf\n" +
                    "aX5zVF4oUQyLRyxlFkwaORRi/T+iXq2XPQIW9A5TmVHGSHUlYj8/X9vfrMkJO/I0\n" +
                    "RXi6mMBXV4C7bu3BLyEGs8rb6kirAgMBAAEwDQYJKoZIhvcNAQELBQADgYEAYLYI\n" +
                    "5wvUaGRqJn7pA4xR9nEhsNpQbza3bJayQvyiJsB5rn9yBJsk5ch3tBBCfh/MA6PW\n" +
                    "xcy2hS5rhZUTve6FK3Kr2GiUYy+keYmbna1UJPKPgIR3BX66g+Ev5RUinmbieC2J\n" +
                    "eE0EtFfLq3uzj8HjakuxOGJz9h+bnCGBhgWWOBo=\n" +
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

    enum BufferType {
        Direct,
        Heap,
        Mixed
    }

    private final BufferType type;

    protected SSLEngineTest(BufferType type) {
        this.type = type;
    }

    protected ByteBuffer allocateBuffer(int len) {
        switch (type) {
            case Direct:
                return ByteBuffer.allocateDirect(len);
            case Heap:
                return ByteBuffer.allocate(len);
            case Mixed:
                return PlatformDependent.threadLocalRandom().nextBoolean() ?
                        ByteBuffer.allocateDirect(len) : ByteBuffer.allocate(len);
            default:
                throw new Error();
        }
    }

    private static final class TestByteBufAllocator implements ByteBufAllocator {

        private final ByteBufAllocator allocator;
        private final BufferType type;

        TestByteBufAllocator(ByteBufAllocator allocator, BufferType type) {
            this.allocator = allocator;
            this.type = type;
        }

        @Override
        public ByteBuf buffer() {
            switch (type) {
                case Direct:
                    return allocator.directBuffer();
                case Heap:
                    return allocator.heapBuffer();
                case Mixed:
                    return PlatformDependent.threadLocalRandom().nextBoolean() ?
                            allocator.directBuffer() : allocator.heapBuffer();
                default:
                    throw new Error();
            }
        }

        @Override
        public ByteBuf buffer(int initialCapacity) {
            switch (type) {
                case Direct:
                    return allocator.directBuffer(initialCapacity);
                case Heap:
                    return allocator.heapBuffer(initialCapacity);
                case Mixed:
                    return PlatformDependent.threadLocalRandom().nextBoolean() ?
                            allocator.directBuffer(initialCapacity) : allocator.heapBuffer(initialCapacity);
                default:
                    throw new Error();
            }
        }

        @Override
        public ByteBuf buffer(int initialCapacity, int maxCapacity) {
            switch (type) {
                case Direct:
                    return allocator.directBuffer(initialCapacity, maxCapacity);
                case Heap:
                    return allocator.heapBuffer(initialCapacity, maxCapacity);
                case Mixed:
                    return PlatformDependent.threadLocalRandom().nextBoolean() ?
                            allocator.directBuffer(initialCapacity, maxCapacity) :
                            allocator.heapBuffer(initialCapacity, maxCapacity);
                default:
                    throw new Error();
            }
        }

        @Override
        public ByteBuf ioBuffer() {
            return allocator.ioBuffer();
        }

        @Override
        public ByteBuf ioBuffer(int initialCapacity) {
            return allocator.ioBuffer(initialCapacity);
        }

        @Override
        public ByteBuf ioBuffer(int initialCapacity, int maxCapacity) {
            return allocator.ioBuffer(initialCapacity, maxCapacity);
        }

        @Override
        public ByteBuf heapBuffer() {
            return allocator.heapBuffer();
        }

        @Override
        public ByteBuf heapBuffer(int initialCapacity) {
            return allocator.heapBuffer(initialCapacity);
        }

        @Override
        public ByteBuf heapBuffer(int initialCapacity, int maxCapacity) {
            return allocator.heapBuffer(initialCapacity, maxCapacity);
        }

        @Override
        public ByteBuf directBuffer() {
            return allocator.directBuffer();
        }

        @Override
        public ByteBuf directBuffer(int initialCapacity) {
            return allocator.directBuffer(initialCapacity);
        }

        @Override
        public ByteBuf directBuffer(int initialCapacity, int maxCapacity) {
            return allocator.directBuffer(initialCapacity, maxCapacity);
        }

        @Override
        public CompositeByteBuf compositeBuffer() {
            switch (type) {
                case Direct:
                    return allocator.compositeDirectBuffer();
                case Heap:
                    return allocator.compositeHeapBuffer();
                case Mixed:
                    return PlatformDependent.threadLocalRandom().nextBoolean() ?
                            allocator.compositeDirectBuffer() :
                            allocator.compositeHeapBuffer();
                default:
                    throw new Error();
            }
        }

        @Override
        public CompositeByteBuf compositeBuffer(int maxNumComponents) {
            switch (type) {
                case Direct:
                    return allocator.compositeDirectBuffer(maxNumComponents);
                case Heap:
                    return allocator.compositeHeapBuffer(maxNumComponents);
                case Mixed:
                    return PlatformDependent.threadLocalRandom().nextBoolean() ?
                            allocator.compositeDirectBuffer(maxNumComponents) :
                            allocator.compositeHeapBuffer(maxNumComponents);
                default:
                    throw new Error();
            }
        }

        @Override
        public CompositeByteBuf compositeHeapBuffer() {
            return allocator.compositeHeapBuffer();
        }

        @Override
        public CompositeByteBuf compositeHeapBuffer(int maxNumComponents) {
            return allocator.compositeHeapBuffer(maxNumComponents);
        }

        @Override
        public CompositeByteBuf compositeDirectBuffer() {
            return allocator.compositeDirectBuffer();
        }

        @Override
        public CompositeByteBuf compositeDirectBuffer(int maxNumComponents) {
            return allocator.compositeDirectBuffer(maxNumComponents);
        }

        @Override
        public boolean isDirectBufferPooled() {
            return allocator.isDirectBufferPooled();
        }

        @Override
        public int calculateNewCapacity(int minNewCapacity, int maxCapacity) {
            return allocator.calculateNewCapacity(minNewCapacity, maxCapacity);
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

    @Test
    public void testMutualAuthInvalidIntermediateCASucceedWithOptionalClientAuth() throws Exception {
        testMutualAuthInvalidClientCertSucceed(ClientAuth.NONE);
    }

    @Test
    public void testMutualAuthInvalidIntermediateCAFailWithOptionalClientAuth() throws Exception {
        testMutualAuthClientCertFail(ClientAuth.OPTIONAL);
    }

    @Test
    public void testMutualAuthInvalidIntermediateCAFailWithRequiredClientAuth() throws Exception {
        testMutualAuthClientCertFail(ClientAuth.REQUIRE);
    }

    @Test
    public void testMutualAuthValidClientCertChainTooLongFailOptionalClientAuth() throws Exception {
        testMutualAuthClientCertFail(ClientAuth.OPTIONAL, "mutual_auth_client.p12", true);
    }

    @Test
    public void testMutualAuthValidClientCertChainTooLongFailRequireClientAuth() throws Exception {
        testMutualAuthClientCertFail(ClientAuth.REQUIRE, "mutual_auth_client.p12", true);
    }

    private void testMutualAuthInvalidClientCertSucceed(ClientAuth auth) throws Exception {
        char[] password = "example".toCharArray();
        final KeyStore serverKeyStore = KeyStore.getInstance("PKCS12");
        serverKeyStore.load(getClass().getResourceAsStream("mutual_auth_server.p12"), password);
        final KeyStore clientKeyStore = KeyStore.getInstance("PKCS12");
        clientKeyStore.load(getClass().getResourceAsStream("mutual_auth_invalid_client.p12"), password);
        final KeyManagerFactory serverKeyManagerFactory =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        serverKeyManagerFactory.init(serverKeyStore, password);
        final KeyManagerFactory clientKeyManagerFactory =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        clientKeyManagerFactory.init(clientKeyStore, password);
        File commonCertChain = new File(getClass().getResource("mutual_auth_ca.pem").getFile());

        mySetupMutualAuth(serverKeyManagerFactory, commonCertChain, clientKeyManagerFactory, commonCertChain,
                auth, false, false);
        assertTrue(clientLatch.await(5, TimeUnit.SECONDS));
        assertNull(clientException);
        assertTrue(serverLatch.await(5, TimeUnit.SECONDS));
        assertNull(serverException);
    }

    private void testMutualAuthClientCertFail(ClientAuth auth) throws Exception {
        testMutualAuthClientCertFail(auth, "mutual_auth_invalid_client.p12", false);
    }

    private void testMutualAuthClientCertFail(ClientAuth auth, String clientCert, boolean serverInitEngine)
            throws Exception {
        char[] password = "example".toCharArray();
        final KeyStore serverKeyStore = KeyStore.getInstance("PKCS12");
        serverKeyStore.load(getClass().getResourceAsStream("mutual_auth_server.p12"), password);
        final KeyStore clientKeyStore = KeyStore.getInstance("PKCS12");
        clientKeyStore.load(getClass().getResourceAsStream(clientCert), password);
        final KeyManagerFactory serverKeyManagerFactory =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        serverKeyManagerFactory.init(serverKeyStore, password);
        final KeyManagerFactory clientKeyManagerFactory =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        clientKeyManagerFactory.init(clientKeyStore, password);
        File commonCertChain = new File(getClass().getResource("mutual_auth_ca.pem").getFile());

        mySetupMutualAuth(serverKeyManagerFactory, commonCertChain, clientKeyManagerFactory, commonCertChain,
                          auth, true, serverInitEngine);
        assertTrue(clientLatch.await(5, TimeUnit.SECONDS));
        assertTrue("unexpected exception: " + clientException,
                mySetupMutualAuthServerIsValidClientException(clientException));
        assertTrue(serverLatch.await(5, TimeUnit.SECONDS));
        assertTrue("unexpected exception: " + serverException,
                mySetupMutualAuthServerIsValidServerException(serverException));
    }

    protected static boolean causedBySSLException(Throwable cause) {
        Throwable next = cause;
        do {
            if (next instanceof SSLException) {
                return true;
            }
            next = next.getCause();
        } while (next != null);
        return false;
    }

    protected boolean mySetupMutualAuthServerIsValidServerException(Throwable cause) {
        return mySetupMutualAuthServerIsValidException(cause);
    }

    protected boolean mySetupMutualAuthServerIsValidClientException(Throwable cause) {
        return mySetupMutualAuthServerIsValidException(cause);
    }

    protected boolean mySetupMutualAuthServerIsValidException(Throwable cause) {
        return cause instanceof SSLHandshakeException || cause instanceof ClosedChannelException;
    }

    protected void mySetupMutualAuthServerInitSslHandler(SslHandler handler) {
    }

    private void mySetupMutualAuth(KeyManagerFactory serverKMF, final File serverTrustManager,
                                   KeyManagerFactory clientKMF, File clientTrustManager,
                                   ClientAuth clientAuth, final boolean failureExpected,
                                   final boolean serverInitEngine)
            throws SSLException, InterruptedException {
        serverSslCtx = SslContextBuilder.forServer(serverKMF)
                .sslProvider(sslServerProvider())
                .sslContextProvider(serverSslContextProvider())
                .trustManager(serverTrustManager)
                .clientAuth(clientAuth)
                .ciphers(null, IdentityCipherSuiteFilter.INSTANCE)
                .sessionCacheSize(0)
                .sessionTimeout(0)
                .build();

        clientSslCtx = SslContextBuilder.forClient()
                .sslProvider(sslClientProvider())
                .sslContextProvider(clientSslContextProvider())
                .trustManager(clientTrustManager)
                .keyManager(clientKMF)
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
                ch.config().setAllocator(new TestByteBufAllocator(ch.config().getAllocator(), type));

                ChannelPipeline p = ch.pipeline();
                SslHandler handler = serverSslCtx.newHandler(ch.alloc());
                if (serverInitEngine) {
                    mySetupMutualAuthServerInitSslHandler(handler);
                }
                p.addLast(handler);
                p.addLast(new MessageDelegatorChannelHandler(serverReceiver, serverLatch));
                p.addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                        if (evt == SslHandshakeCompletionEvent.SUCCESS) {
                            if (failureExpected) {
                                serverException = new IllegalStateException("handshake complete. expected failure");
                            }
                            serverLatch.countDown();
                        } else if (evt instanceof SslHandshakeCompletionEvent) {
                            serverException = ((SslHandshakeCompletionEvent) evt).cause();
                            serverLatch.countDown();
                        }
                        ctx.fireUserEventTriggered(evt);
                    }

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
                });
                serverConnectedChannel = ch;
            }
        });

        cb.group(new NioEventLoopGroup());
        cb.channel(NioSocketChannel.class);
        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.config().setAllocator(new TestByteBufAllocator(ch.config().getAllocator(), type));
                ChannelPipeline p = ch.pipeline();
                p.addLast(clientSslCtx.newHandler(ch.alloc()));
                p.addLast(new MessageDelegatorChannelHandler(clientReceiver, clientLatch));
                p.addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                        if (evt == SslHandshakeCompletionEvent.SUCCESS) {
                            if (failureExpected) {
                                clientException = new IllegalStateException("handshake complete. expected failure");
                            }
                            clientLatch.countDown();
                        } else if (evt instanceof SslHandshakeCompletionEvent) {
                            clientException = ((SslHandshakeCompletionEvent) evt).cause();
                            clientLatch.countDown();
                        }
                        ctx.fireUserEventTriggered(evt);
                    }

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

    @Test
    public void testClientHostnameValidationSuccess() throws InterruptedException, SSLException {
        mySetupClientHostnameValidation(new File(getClass().getResource("localhost_server.pem").getFile()),
                                        new File(getClass().getResource("localhost_server.key").getFile()),
                                        new File(getClass().getResource("mutual_auth_ca.pem").getFile()),
                                        false);
        assertTrue(clientLatch.await(5, TimeUnit.SECONDS));
        assertNull(clientException);
        assertTrue(serverLatch.await(5, TimeUnit.SECONDS));
        assertNull(serverException);
    }

    @Test
    public void testClientHostnameValidationFail() throws InterruptedException, SSLException {
        mySetupClientHostnameValidation(new File(getClass().getResource("notlocalhost_server.pem").getFile()),
                                        new File(getClass().getResource("notlocalhost_server.key").getFile()),
                                        new File(getClass().getResource("mutual_auth_ca.pem").getFile()),
                                        true);
        assertTrue(clientLatch.await(5, TimeUnit.SECONDS));
        assertTrue("unexpected exception: " + clientException,
                mySetupMutualAuthServerIsValidClientException(clientException));
        assertTrue(serverLatch.await(5, TimeUnit.SECONDS));
        assertTrue("unexpected exception: " + serverException,
                mySetupMutualAuthServerIsValidServerException(serverException));
    }

    private void mySetupClientHostnameValidation(File serverCrtFile, File serverKeyFile,
                                                 File clientTrustCrtFile,
                                                 final boolean failureExpected)
            throws SSLException, InterruptedException {
        final String expectedHost = "localhost";
        serverSslCtx = SslContextBuilder.forServer(serverCrtFile, serverKeyFile, null)
                .sslProvider(sslServerProvider())
                .sslContextProvider(serverSslContextProvider())
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .ciphers(null, IdentityCipherSuiteFilter.INSTANCE)
                .sessionCacheSize(0)
                .sessionTimeout(0)
                .build();

        clientSslCtx = SslContextBuilder.forClient()
                .sslProvider(sslClientProvider())
                .sslContextProvider(clientSslContextProvider())
                .trustManager(clientTrustCrtFile)
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
                ch.config().setAllocator(new TestByteBufAllocator(ch.config().getAllocator(), type));
                ChannelPipeline p = ch.pipeline();
                p.addLast(serverSslCtx.newHandler(ch.alloc()));
                p.addLast(new MessageDelegatorChannelHandler(serverReceiver, serverLatch));
                p.addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                        if (evt == SslHandshakeCompletionEvent.SUCCESS) {
                            if (failureExpected) {
                                serverException = new IllegalStateException("handshake complete. expected failure");
                            }
                            serverLatch.countDown();
                        } else if (evt instanceof SslHandshakeCompletionEvent) {
                            serverException = ((SslHandshakeCompletionEvent) evt).cause();
                            serverLatch.countDown();
                        }
                        ctx.fireUserEventTriggered(evt);
                    }

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
                });
                serverConnectedChannel = ch;
            }
        });

        cb.group(new NioEventLoopGroup());
        cb.channel(NioSocketChannel.class);
        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.config().setAllocator(new TestByteBufAllocator(ch.config().getAllocator(), type));
                ChannelPipeline p = ch.pipeline();
                InetSocketAddress remoteAddress = (InetSocketAddress) serverChannel.localAddress();
                SslHandler sslHandler = clientSslCtx.newHandler(ch.alloc(), expectedHost, 0);
                SSLParameters parameters = sslHandler.engine().getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                sslHandler.engine().setSSLParameters(parameters);
                p.addLast(sslHandler);
                p.addLast(new MessageDelegatorChannelHandler(clientReceiver, clientLatch));
                p.addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                        if (evt == SslHandshakeCompletionEvent.SUCCESS) {
                            if (failureExpected) {
                                clientException = new IllegalStateException("handshake complete. expected failure");
                            }
                            clientLatch.countDown();
                        } else if (evt instanceof SslHandshakeCompletionEvent) {
                            clientException = ((SslHandshakeCompletionEvent) evt).cause();
                            clientLatch.countDown();
                        }
                        ctx.fireUserEventTriggered(evt);
                    }

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

        serverChannel = sb.bind(new InetSocketAddress(expectedHost, 0)).sync().channel();
        final int port = ((InetSocketAddress) serverChannel.localAddress()).getPort();

        ChannelFuture ccf = cb.connect(new InetSocketAddress(expectedHost, port));
        assertTrue(ccf.awaitUninterruptibly().isSuccess());
        clientChannel = ccf.channel();
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
                .sslContextProvider(serverSslContextProvider())
                .trustManager(servertTrustCrtFile)
                .ciphers(null, IdentityCipherSuiteFilter.INSTANCE)
                .sessionCacheSize(0)
                .sessionTimeout(0)
                .build();

        clientSslCtx = SslContextBuilder.forClient()
                .sslProvider(sslClientProvider())
                .sslContextProvider(clientSslContextProvider())
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
                ch.config().setAllocator(new TestByteBufAllocator(ch.config().getAllocator(), type));

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
                ch.config().setAllocator(new TestByteBufAllocator(ch.config().getAllocator(), type));

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
            verifyApplicationLevelProtocol(clientChannel, expectedApplicationProtocol);
            verifyApplicationLevelProtocol(serverConnectedChannel, expectedApplicationProtocol);
        } finally {
            clientMessage.release();
            serverMessage.release();
        }
    }

    private static void verifyApplicationLevelProtocol(Channel channel, String expectedApplicationProtocol) {
        SslHandler handler = channel.pipeline().get(SslHandler.class);
        assertNotNull(handler);
        String appProto = handler.applicationProtocol();
        assertEquals(expectedApplicationProtocol, appProto);

        SSLEngine engine = handler.engine();
        if (engine instanceof Java9SslEngine) {
            // Also verify the Java9 exposed method.
            Java9SslEngine java9SslEngine = (Java9SslEngine) engine;
            assertEquals(expectedApplicationProtocol == null ? StringUtil.EMPTY_STRING : expectedApplicationProtocol,
                    java9SslEngine.getApplicationProtocol());
        }
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
        clientSslCtx = SslContextBuilder.forClient()
                .sslProvider(sslClientProvider())
                .sslContextProvider(clientSslContextProvider()).build();
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
                .sslContextProvider(clientSslContextProvider())
                .build();
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(sslServerProvider())
                .sslContextProvider(serverSslContextProvider())
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
            ssc.delete();
        }
    }

    @Test
    public void testSSLSessionId() throws Exception {
        clientSslCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(sslClientProvider())
                .sslContextProvider(clientSslContextProvider())
                .build();
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(sslServerProvider())
                .sslContextProvider(serverSslContextProvider())
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
            ssc.delete();
        }
    }

    @Test(timeout = 30000)
    public void clientInitiatedRenegotiationWithFatalAlertDoesNotInfiniteLoopServer()
            throws CertificateException, SSLException, InterruptedException, ExecutionException {
        final SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(sslServerProvider())
                .sslContextProvider(serverSslContextProvider()).build();
        sb = new ServerBootstrap()
                .group(new NioEventLoopGroup(1))
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ch.config().setAllocator(new TestByteBufAllocator(ch.config().getAllocator(), type));

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
                        ch.config().setAllocator(new TestByteBufAllocator(ch.config().getAllocator(), type));

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
        ssc.delete();
    }

    protected void testEnablingAnAlreadyDisabledSslProtocol(String[] protocols1, String[] protocols2) throws Exception {
        SSLEngine sslEngine = null;
        try {
            File serverKeyFile = new File(getClass().getResource("test_unencrypted.pem").getFile());
            File serverCrtFile = new File(getClass().getResource("test.crt").getFile());
            serverSslCtx = SslContextBuilder.forServer(serverCrtFile, serverKeyFile)
               .sslProvider(sslServerProvider())
               .sslContextProvider(serverSslContextProvider())
               .build();

            sslEngine = serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);

            // Disable all protocols
            sslEngine.setEnabledProtocols(EmptyArrays.EMPTY_STRINGS);

            // The only protocol that should be enabled is SSLv2Hello
            String[] enabledProtocols = sslEngine.getEnabledProtocols();
            assertArrayEquals(protocols1, enabledProtocols);

            // Enable a protocol that is currently disabled
            sslEngine.setEnabledProtocols(new String[]{ PROTOCOL_TLS_V1_2 });

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

    protected void handshake(SSLEngine clientEngine, SSLEngine serverEngine) throws SSLException {
        ByteBuffer cTOs = allocateBuffer(clientEngine.getSession().getPacketBufferSize());
        ByteBuffer sTOc = allocateBuffer(serverEngine.getSession().getPacketBufferSize());

        ByteBuffer serverAppReadBuffer = allocateBuffer(
                serverEngine.getSession().getApplicationBufferSize());
        ByteBuffer clientAppReadBuffer = allocateBuffer(
                clientEngine.getSession().getApplicationBufferSize());

        clientEngine.beginHandshake();
        serverEngine.beginHandshake();

        ByteBuffer empty = allocateBuffer(0);

        SSLEngineResult clientResult;
        SSLEngineResult serverResult;

        boolean clientHandshakeFinished = false;
        boolean serverHandshakeFinished = false;

        do {
            int cTOsPos = cTOs.position();
            int sTOcPos = sTOc.position();

            if (!clientHandshakeFinished) {
                clientResult = clientEngine.wrap(empty, cTOs);
                runDelegatedTasks(clientResult, clientEngine);
                assertEquals(empty.remaining(), clientResult.bytesConsumed());
                assertEquals(cTOs.position() - cTOsPos,  clientResult.bytesProduced());

                if (isHandshakeFinished(clientResult)) {
                    clientHandshakeFinished = true;
                }
            }

            if (!serverHandshakeFinished) {
                serverResult = serverEngine.wrap(empty, sTOc);
                runDelegatedTasks(serverResult, serverEngine);
                assertEquals(empty.remaining(), serverResult.bytesConsumed());
                assertEquals(sTOc.position() - sTOcPos, serverResult.bytesProduced());

                if (isHandshakeFinished(serverResult)) {
                    serverHandshakeFinished = true;
                }
            }

            cTOs.flip();
            sTOc.flip();

            cTOsPos = cTOs.position();
            sTOcPos = sTOc.position();

            if (!clientHandshakeFinished) {
                int clientAppReadBufferPos = clientAppReadBuffer.position();
                clientResult = clientEngine.unwrap(sTOc, clientAppReadBuffer);

                runDelegatedTasks(clientResult, clientEngine);
                assertEquals(sTOc.position() - sTOcPos, clientResult.bytesConsumed());
                assertEquals(clientAppReadBuffer.position() - clientAppReadBufferPos, clientResult.bytesProduced());

                if (isHandshakeFinished(clientResult)) {
                    clientHandshakeFinished = true;
                }
            } else {
                assertFalse(sTOc.hasRemaining());
            }

            if (!serverHandshakeFinished) {
                int serverAppReadBufferPos = serverAppReadBuffer.position();
                serverResult = serverEngine.unwrap(cTOs, serverAppReadBuffer);
                runDelegatedTasks(serverResult, serverEngine);
                assertEquals(cTOs.position() - cTOsPos, serverResult.bytesConsumed());
                assertEquals(serverAppReadBuffer.position() - serverAppReadBufferPos, serverResult.bytesProduced());

                if (isHandshakeFinished(serverResult)) {
                    serverHandshakeFinished = true;
                }
            } else {
                assertFalse(cTOs.hasRemaining());
            }

            sTOc.compact();
            cTOs.compact();
        } while (!clientHandshakeFinished || !serverHandshakeFinished);
    }

    private static boolean isHandshakeFinished(SSLEngineResult result) {
        return result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED;
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

    protected Provider clientSslContextProvider() {
        return null;
    }
    protected Provider serverSslContextProvider() {
        return null;
    }

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

        try {
          setupHandlers(SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey(), null)
                          .sslProvider(sslServerProvider())
                          .sslContextProvider(serverSslContextProvider())
                          .ciphers(null, IdentityCipherSuiteFilter.INSTANCE)
                          .applicationProtocolConfig(serverApn)
                          .sessionCacheSize(0)
                          .sessionTimeout(0)
                          .build(),

                  SslContextBuilder.forClient()
                          .sslProvider(sslClientProvider())
                          .sslContextProvider(clientSslContextProvider())
                          .applicationProtocolConfig(clientApn)
                          .trustManager(InsecureTrustManagerFactory.INSTANCE)
                          .ciphers(null, IdentityCipherSuiteFilter.INSTANCE)
                          .sessionCacheSize(0)
                          .sessionTimeout(0)
                          .build());
        } finally {
          ssc.delete();
        }
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
                ch.config().setAllocator(new TestByteBufAllocator(ch.config().getAllocator(), type));

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
                ch.config().setAllocator(new TestByteBufAllocator(ch.config().getAllocator(), type));

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

    @Test(timeout = 30000)
    public void testMutualAuthSameCertChain() throws Exception {
        serverSslCtx = SslContextBuilder.forServer(
                new ByteArrayInputStream(X509_CERT_PEM.getBytes(CharsetUtil.UTF_8)),
                new ByteArrayInputStream(PRIVATE_KEY_PEM.getBytes(CharsetUtil.UTF_8)))
                .trustManager(new ByteArrayInputStream(X509_CERT_PEM.getBytes(CharsetUtil.UTF_8)))
                .clientAuth(ClientAuth.REQUIRE).sslProvider(sslServerProvider())
                .sslContextProvider(serverSslContextProvider()).build();

        sb = new ServerBootstrap();
        sb.group(new NioEventLoopGroup(), new NioEventLoopGroup());
        sb.channel(NioServerSocketChannel.class);

        final Promise<String> promise = sb.config().group().next().newPromise();
        serverChannel = sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.config().setAllocator(new TestByteBufAllocator(ch.config().getAllocator(), type));

                ch.pipeline().addFirst(serverSslCtx.newHandler(ch.alloc()));
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                        if (evt instanceof SslHandshakeCompletionEvent) {
                            Throwable cause = ((SslHandshakeCompletionEvent) evt).cause();
                            if (cause == null) {
                                SSLSession session = ((SslHandler) ctx.pipeline().first()).engine().getSession();
                                X509Certificate[] peerCertificateChain = session.getPeerCertificateChain();
                                Certificate[] peerCertificates = session.getPeerCertificates();
                                if (peerCertificateChain == null) {
                                    promise.setFailure(new NullPointerException("peerCertificateChain"));
                                } else if (peerCertificates == null) {
                                    promise.setFailure(new NullPointerException("peerCertificates"));
                                } else if (peerCertificateChain.length + peerCertificates.length != 4) {
                                    String excTxtFmt = "peerCertificateChain.length:%s, peerCertificates.length:%s";
                                    promise.setFailure(new IllegalStateException(String.format(excTxtFmt,
                                            peerCertificateChain.length,
                                            peerCertificates.length)));
                                } else {
                                    for (int i = 0; i < peerCertificateChain.length; i++) {
                                        if (peerCertificateChain[i] == null || peerCertificates[i] == null) {
                                            promise.setFailure(
                                                    new IllegalStateException("Certificate in chain is null"));
                                            return;
                                        }
                                    }
                                    promise.setSuccess(null);
                                }
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
                .sslProvider(sslClientProvider())
                .sslContextProvider(clientSslContextProvider()).build();
        cb = new Bootstrap();
        cb.group(new NioEventLoopGroup());
        cb.channel(NioSocketChannel.class);
        clientChannel = cb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.config().setAllocator(new TestByteBufAllocator(ch.config().getAllocator(), type));
                ch.pipeline().addLast(new SslHandler(clientSslCtx.newEngine(ch.alloc())));
            }

        }).connect(serverChannel.localAddress()).syncUninterruptibly().channel();

        promise.syncUninterruptibly();
    }

    @Test
    public void testUnwrapBehavior() throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();

        clientSslCtx = SslContextBuilder
                .forClient()
                .trustManager(cert.cert())
                .sslProvider(sslClientProvider())
                .build();
        SSLEngine client = clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);

        serverSslCtx = SslContextBuilder
                .forServer(cert.certificate(), cert.privateKey())
                .sslProvider(sslServerProvider())
                .build();
        SSLEngine server = serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);

        byte[] bytes = "Hello World".getBytes(CharsetUtil.US_ASCII);

        try {
            ByteBuffer plainClientOut = allocateBuffer(client.getSession().getApplicationBufferSize());
            ByteBuffer encryptedClientToServer = allocateBuffer(server.getSession().getPacketBufferSize() * 2);
            ByteBuffer plainServerIn = allocateBuffer(server.getSession().getApplicationBufferSize());

            handshake(client, server);

            // create two TLS frames

            // first frame
            plainClientOut.put(bytes, 0, 5);
            plainClientOut.flip();

            SSLEngineResult result = client.wrap(plainClientOut, encryptedClientToServer);
            assertEquals(SSLEngineResult.Status.OK, result.getStatus());
            assertEquals(5, result.bytesConsumed());
            assertTrue(result.bytesProduced() > 0);

            assertFalse(plainClientOut.hasRemaining());

            // second frame
            plainClientOut.clear();
            plainClientOut.put(bytes, 5, 6);
            plainClientOut.flip();

            result = client.wrap(plainClientOut, encryptedClientToServer);
            assertEquals(SSLEngineResult.Status.OK, result.getStatus());
            assertEquals(6, result.bytesConsumed());
            assertTrue(result.bytesProduced() > 0);

            // send over to server
            encryptedClientToServer.flip();

            // try with too small output buffer first (to check BUFFER_OVERFLOW case)
            int remaining = encryptedClientToServer.remaining();
            ByteBuffer small = allocateBuffer(3);
            result = server.unwrap(encryptedClientToServer, small);
            assertEquals(SSLEngineResult.Status.BUFFER_OVERFLOW, result.getStatus());
            assertEquals(remaining, encryptedClientToServer.remaining());

            // now with big enough buffer
            result = server.unwrap(encryptedClientToServer, plainServerIn);
            assertEquals(SSLEngineResult.Status.OK, result.getStatus());

            assertEquals(5, result.bytesProduced());
            assertTrue(encryptedClientToServer.hasRemaining());

            result = server.unwrap(encryptedClientToServer, plainServerIn);
            assertEquals(SSLEngineResult.Status.OK, result.getStatus());
            assertEquals(6, result.bytesProduced());
            assertFalse(encryptedClientToServer.hasRemaining());

            plainServerIn.flip();

            assertEquals(ByteBuffer.wrap(bytes), plainServerIn);
        } finally {
            cleanupClientSslEngine(client);
            cleanupServerSslEngine(server);
            cert.delete();
        }
    }

    @Test
    public void testProtocolMatch() throws Exception {
        testProtocol(new String[] {"TLSv1.2"}, new String[] {"TLSv1", "TLSv1.1", "TLSv1.2"});
    }

    @Test(expected = SSLHandshakeException.class)
    public void testProtocolNoMatch() throws Exception {
        testProtocol(new String[] {"TLSv1.2"}, new String[] {"TLSv1", "TLSv1.1"});
    }

    private void testProtocol(String[] clientProtocols, String[] serverProtocols) throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();

        clientSslCtx = SslContextBuilder
                .forClient()
                .trustManager(cert.cert())
                .sslProvider(sslClientProvider())
                .protocols(clientProtocols)
                .build();
        SSLEngine client = clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);

        serverSslCtx = SslContextBuilder
                .forServer(cert.certificate(), cert.privateKey())
                .sslProvider(sslServerProvider())
                .protocols(serverProtocols)
                .build();
        SSLEngine server = serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);

        try {
            handshake(client, server);
        } finally {
            cleanupClientSslEngine(client);
            cleanupServerSslEngine(server);
            cert.delete();
        }
    }

    @Test
    public void testHandshakeCompletesWithNonContiguousProtocolsTLSv1_2CipherOnly() throws Exception {
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        // Select a mandatory cipher from the TLSv1.2 RFC https://www.ietf.org/rfc/rfc5246.txt so handshakes won't fail
        // due to no shared/supported cipher.
        final String sharedCipher = "TLS_RSA_WITH_AES_128_CBC_SHA";
        clientSslCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .ciphers(Arrays.asList(sharedCipher))
                .protocols(PROTOCOL_TLS_V1_2, PROTOCOL_TLS_V1)
                .sslProvider(sslClientProvider())
                .build();

        serverSslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .ciphers(Arrays.asList(sharedCipher))
                .protocols(PROTOCOL_TLS_V1_2, PROTOCOL_TLS_V1)
                .sslProvider(sslServerProvider())
                .build();
        SSLEngine clientEngine = null;
        SSLEngine serverEngine = null;
        try {
            clientEngine = clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);
            serverEngine = serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);
            handshake(clientEngine, serverEngine);
        } finally {
            cleanupClientSslEngine(clientEngine);
            cleanupServerSslEngine(serverEngine);
            ssc.delete();
        }
    }

    @Test
    public void testHandshakeCompletesWithoutFilteringSupportedCipher() throws Exception {
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        // Select a mandatory cipher from the TLSv1.2 RFC https://www.ietf.org/rfc/rfc5246.txt so handshakes won't fail
        // due to no shared/supported cipher.
        final String sharedCipher = "TLS_RSA_WITH_AES_128_CBC_SHA";
        clientSslCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .ciphers(Arrays.asList(sharedCipher), SupportedCipherSuiteFilter.INSTANCE)
                .protocols(PROTOCOL_TLS_V1_2, PROTOCOL_TLS_V1)
                .sslProvider(sslClientProvider())
                .build();

        serverSslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .ciphers(Arrays.asList(sharedCipher), SupportedCipherSuiteFilter.INSTANCE)
                .protocols(PROTOCOL_TLS_V1_2, PROTOCOL_TLS_V1)
                .sslProvider(sslServerProvider())
                .build();
        SSLEngine clientEngine = null;
        SSLEngine serverEngine = null;
        try {
            clientEngine = clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);
            serverEngine = serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);
            handshake(clientEngine, serverEngine);
        } finally {
            cleanupClientSslEngine(clientEngine);
            cleanupServerSslEngine(serverEngine);
            ssc.delete();
        }
    }

    @Test
    public void testPacketBufferSizeLimit() throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();

        clientSslCtx = SslContextBuilder
                .forClient()
                .trustManager(cert.cert())
                .sslProvider(sslClientProvider())
                .build();
        SSLEngine client = clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);

        serverSslCtx = SslContextBuilder
                .forServer(cert.certificate(), cert.privateKey())
                .sslProvider(sslServerProvider())
                .build();
        SSLEngine server = serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);

        try {
            // Allocate an buffer that is bigger then the max plain record size.
            ByteBuffer plainServerOut = allocateBuffer(server.getSession().getApplicationBufferSize() * 2);

            handshake(client, server);

            // Fill the whole buffer and flip it.
            plainServerOut.position(plainServerOut.capacity());
            plainServerOut.flip();

            ByteBuffer encryptedServerToClient = allocateBuffer(server.getSession().getPacketBufferSize());

            int encryptedServerToClientPos = encryptedServerToClient.position();
            int plainServerOutPos = plainServerOut.position();
            SSLEngineResult result = server.wrap(plainServerOut, encryptedServerToClient);
            assertEquals(SSLEngineResult.Status.OK, result.getStatus());
            assertEquals(plainServerOut.position() - plainServerOutPos, result.bytesConsumed());
            assertEquals(encryptedServerToClient.position() - encryptedServerToClientPos, result.bytesProduced());
        } finally {
            cleanupClientSslEngine(client);
            cleanupServerSslEngine(server);
            cert.delete();
        }
    }

    @Test
    public void testSSLEngineUnwrapNoSslRecord() throws Exception {
        clientSslCtx = SslContextBuilder
                .forClient()
                .sslProvider(sslClientProvider())
                .build();
        SSLEngine client = clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);

        try {
            ByteBuffer src = allocateBuffer(client.getSession().getApplicationBufferSize());
            ByteBuffer dst = allocateBuffer(client.getSession().getPacketBufferSize());
            ByteBuffer empty = allocateBuffer(0);

            SSLEngineResult clientResult = client.wrap(empty, dst);
            assertEquals(SSLEngineResult.Status.OK, clientResult.getStatus());
            assertEquals(SSLEngineResult.HandshakeStatus.NEED_UNWRAP, clientResult.getHandshakeStatus());

            try {
                client.unwrap(src, dst);
                fail();
            } catch (SSLException expected) {
                // expected
            }
        } finally {
            cleanupClientSslEngine(client);
        }
    }

    @Test
    public void testBeginHandshakeAfterEngineClosed() throws SSLException {
        clientSslCtx = SslContextBuilder
                .forClient()
                .sslProvider(sslClientProvider())
                .build();
        SSLEngine client = clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);

        try {
            client.closeInbound();
            client.closeOutbound();
            try {
                client.beginHandshake();
                fail();
            } catch (SSLException expected) {
                // expected
            }
        } finally {
            cleanupClientSslEngine(client);
        }
    }

    @Test
    public void testBeginHandshakeCloseOutbound() throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();

        clientSslCtx = SslContextBuilder
                .forClient()
                .sslProvider(sslClientProvider())
                .build();
        SSLEngine client = clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);

        serverSslCtx = SslContextBuilder
                .forServer(cert.certificate(), cert.privateKey())
                .sslProvider(sslServerProvider())
                .build();
        SSLEngine server = serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);

        try {
            testBeginHandshakeCloseOutbound(client);
            testBeginHandshakeCloseOutbound(server);
        } finally {
            cleanupClientSslEngine(client);
            cleanupServerSslEngine(server);
            cert.delete();
        }
    }

    private void testBeginHandshakeCloseOutbound(SSLEngine engine) throws SSLException {
        ByteBuffer dst = allocateBuffer(engine.getSession().getPacketBufferSize());
        ByteBuffer empty = allocateBuffer(0);
        engine.beginHandshake();
        engine.closeOutbound();

        SSLEngineResult result;
        for (;;) {
            result = engine.wrap(empty, dst);
            dst.flip();

            assertEquals(0, result.bytesConsumed());
            assertEquals(dst.remaining(), result.bytesProduced());
            if (result.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NEED_WRAP) {
                break;
            }
            dst.clear();
        }
        assertEquals(SSLEngineResult.Status.CLOSED, result.getStatus());
    }

    @Test
    public void testCloseInboundAfterBeginHandshake() throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();

        clientSslCtx = SslContextBuilder
                .forClient()
                .sslProvider(sslClientProvider())
                .build();
        SSLEngine client = clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);

        serverSslCtx = SslContextBuilder
                .forServer(cert.certificate(), cert.privateKey())
                .sslProvider(sslServerProvider())
                .build();
        SSLEngine server = serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);

        try {
            testCloseInboundAfterBeginHandshake(client);
            testCloseInboundAfterBeginHandshake(server);
        } finally {
            cleanupClientSslEngine(client);
            cleanupServerSslEngine(server);
            cert.delete();
        }
    }

    private static void testCloseInboundAfterBeginHandshake(SSLEngine engine) throws SSLException {
        engine.beginHandshake();
        try {
            engine.closeInbound();
            fail();
        } catch (SSLException expected) {
            // expected
        }
    }

    @Test
    public void testCloseNotifySequence() throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();

        clientSslCtx = SslContextBuilder
                .forClient()
                .trustManager(cert.cert())
                .sslProvider(sslClientProvider())
                .build();
        SSLEngine client = clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);

        serverSslCtx = SslContextBuilder
                .forServer(cert.certificate(), cert.privateKey())
                .sslProvider(sslServerProvider())
                .build();
        SSLEngine server = serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);

        try {
            ByteBuffer plainClientOut = allocateBuffer(client.getSession().getApplicationBufferSize());
            ByteBuffer plainServerOut = allocateBuffer(server.getSession().getApplicationBufferSize());

            ByteBuffer encryptedClientToServer = allocateBuffer(client.getSession().getPacketBufferSize());
            ByteBuffer encryptedServerToClient = allocateBuffer(server.getSession().getPacketBufferSize());
            ByteBuffer empty = allocateBuffer(0);

            handshake(client, server);

            // This will produce a close_notify
            client.closeOutbound();

            // Something still pending in the outbound buffer.
            assertFalse(client.isOutboundDone());
            assertFalse(client.isInboundDone());

            // Now wrap and so drain the outbound buffer.
            SSLEngineResult result = client.wrap(empty, encryptedClientToServer);
            encryptedClientToServer.flip();

            assertEquals(SSLEngineResult.Status.CLOSED, result.getStatus());
            // Need an UNWRAP to read the response of the close_notify
            assertEquals(SSLEngineResult.HandshakeStatus.NEED_UNWRAP, result.getHandshakeStatus());

            int produced = result.bytesProduced();
            int consumed = result.bytesConsumed();
            int closeNotifyLen = produced;

            assertTrue(produced > 0);
            assertEquals(0, consumed);
            assertEquals(produced, encryptedClientToServer.remaining());
            // Outbound buffer should be drained now.
            assertTrue(client.isOutboundDone());
            assertFalse(client.isInboundDone());

            assertFalse(server.isOutboundDone());
            assertFalse(server.isInboundDone());
            result = server.unwrap(encryptedClientToServer, plainServerOut);
            plainServerOut.flip();

            assertEquals(SSLEngineResult.Status.CLOSED, result.getStatus());
            // Need a WRAP to respond to the close_notify
            assertEquals(SSLEngineResult.HandshakeStatus.NEED_WRAP, result.getHandshakeStatus());

            produced = result.bytesProduced();
            consumed = result.bytesConsumed();
            assertEquals(closeNotifyLen, consumed);
            assertEquals(0, produced);
            // Should have consumed the complete close_notify
            assertEquals(0, encryptedClientToServer.remaining());
            assertEquals(0, plainServerOut.remaining());

            assertFalse(server.isOutboundDone());
            assertTrue(server.isInboundDone());

            result = server.wrap(empty, encryptedServerToClient);
            encryptedServerToClient.flip();
            assertEquals(SSLEngineResult.Status.CLOSED, result.getStatus());
            // UNWRAP/WRAP are not expected after this point
            assertEquals(SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING, result.getHandshakeStatus());

            produced = result.bytesProduced();
            consumed = result.bytesConsumed();
            assertEquals(closeNotifyLen, produced);
            assertEquals(0, consumed);

            assertEquals(produced, encryptedServerToClient.remaining());
            assertTrue(server.isOutboundDone());
            assertTrue(server.isInboundDone());

            result = client.unwrap(encryptedServerToClient, plainClientOut);
            plainClientOut.flip();
            assertEquals(SSLEngineResult.Status.CLOSED, result.getStatus());
            // UNWRAP/WRAP are not expected after this point
            assertEquals(SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING, result.getHandshakeStatus());

            produced = result.bytesProduced();
            consumed = result.bytesConsumed();
            assertEquals(closeNotifyLen, consumed);
            assertEquals(0, produced);
            assertEquals(0, encryptedServerToClient.remaining());

            assertTrue(client.isOutboundDone());
            assertTrue(client.isInboundDone());

            // Ensure that calling wrap or unwrap again will not produce a SSLException
            encryptedServerToClient.clear();
            plainServerOut.clear();

            result = server.wrap(plainServerOut, encryptedServerToClient);
            assertEngineRemainsClosed(result);

            encryptedClientToServer.clear();
            plainServerOut.clear();

            result = server.unwrap(encryptedClientToServer, plainServerOut);
            assertEngineRemainsClosed(result);

            encryptedClientToServer.clear();
            plainClientOut.clear();

            result = client.wrap(plainClientOut, encryptedClientToServer);
            assertEngineRemainsClosed(result);

            encryptedServerToClient.clear();
            plainClientOut.clear();

            result = client.unwrap(encryptedServerToClient, plainClientOut);
            assertEngineRemainsClosed(result);
        } finally {
            cert.delete();
            cleanupClientSslEngine(client);
            cleanupServerSslEngine(server);
        }
    }

    private static void assertEngineRemainsClosed(SSLEngineResult result) {
        assertEquals(SSLEngineResult.Status.CLOSED, result.getStatus());
        assertEquals(SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING, result.getHandshakeStatus());
        assertEquals(0, result.bytesConsumed());
        assertEquals(0, result.bytesProduced());
    }

    @Test
    public void testWrapAfterCloseOutbound() throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();

        clientSslCtx = SslContextBuilder
                .forClient()
                .trustManager(cert.cert())
                .sslProvider(sslClientProvider())
                .build();
        SSLEngine client = clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);

        serverSslCtx = SslContextBuilder
                .forServer(cert.certificate(), cert.privateKey())
                .sslProvider(sslServerProvider())
                .build();
        SSLEngine server = serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);

        try {
            ByteBuffer dst = allocateBuffer(client.getSession().getPacketBufferSize());
            ByteBuffer src = allocateBuffer(1024);

            handshake(client, server);

            // This will produce a close_notify
            client.closeOutbound();
            SSLEngineResult result = client.wrap(src, dst);
            assertEquals(SSLEngineResult.Status.CLOSED, result.getStatus());
            assertEquals(0, result.bytesConsumed());
            assertTrue(result.bytesProduced() > 0);

            assertTrue(client.isOutboundDone());
            assertFalse(client.isInboundDone());
        } finally {
            cert.delete();
            cleanupClientSslEngine(client);
            cleanupServerSslEngine(server);
        }
    }

    @Test
    public void testMultipleRecordsInOneBufferWithNonZeroPosition() throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();

        clientSslCtx = SslContextBuilder
                .forClient()
                .trustManager(cert.cert())
                .sslProvider(sslClientProvider())
                .build();
        SSLEngine client = clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);

        serverSslCtx = SslContextBuilder
                .forServer(cert.certificate(), cert.privateKey())
                .sslProvider(sslServerProvider())
                .build();
        SSLEngine server = serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);

        try {
            // Choose buffer size small enough that we can put multiple buffers into one buffer and pass it into the
            // unwrap call without exceed MAX_ENCRYPTED_PACKET_LENGTH.
            ByteBuffer plainClientOut = allocateBuffer(1024);
            ByteBuffer plainServerOut = allocateBuffer(server.getSession().getApplicationBufferSize());

            ByteBuffer encClientToServer = allocateBuffer(client.getSession().getPacketBufferSize());

            int positionOffset = 1;
            // We need to be able to hold 2 records + positionOffset
            ByteBuffer combinedEncClientToServer = allocateBuffer(
                    encClientToServer.capacity() * 2 + positionOffset);
            combinedEncClientToServer.position(positionOffset);

            handshake(client, server);

            plainClientOut.limit(plainClientOut.capacity());
            SSLEngineResult result = client.wrap(plainClientOut, encClientToServer);
            assertEquals(plainClientOut.capacity(), result.bytesConsumed());
            assertTrue(result.bytesProduced() > 0);

            encClientToServer.flip();

            // Copy the first record into the combined buffer
            combinedEncClientToServer.put(encClientToServer);

            plainClientOut.clear();
            encClientToServer.clear();

            result = client.wrap(plainClientOut, encClientToServer);
            assertEquals(plainClientOut.capacity(), result.bytesConsumed());
            assertTrue(result.bytesProduced() > 0);

            encClientToServer.flip();

            int encClientToServerLen = encClientToServer.remaining();

            // Copy the first record into the combined buffer
            combinedEncClientToServer.put(encClientToServer);

            encClientToServer.clear();

            combinedEncClientToServer.flip();
            combinedEncClientToServer.position(positionOffset);

            // Ensure we have the first record and a tiny amount of the second record in the buffer
            combinedEncClientToServer.limit(
                    combinedEncClientToServer.limit() - (encClientToServerLen - positionOffset));
            result = server.unwrap(combinedEncClientToServer, plainServerOut);
            assertEquals(encClientToServerLen, result.bytesConsumed());
            assertTrue(result.bytesProduced() > 0);
        } finally {
            cert.delete();
            cleanupClientSslEngine(client);
            cleanupServerSslEngine(server);
        }
    }

    @Test
    public void testMultipleRecordsInOneBufferBiggerThenPacketBufferSize() throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();

        clientSslCtx = SslContextBuilder
                .forClient()
                .trustManager(cert.cert())
                .sslProvider(sslClientProvider())
                .build();
        SSLEngine client = clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);

        serverSslCtx = SslContextBuilder
                .forServer(cert.certificate(), cert.privateKey())
                .sslProvider(sslServerProvider())
                .build();
        SSLEngine server = serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);

        try {
            ByteBuffer plainClientOut = allocateBuffer(4096);
            ByteBuffer plainServerOut = allocateBuffer(server.getSession().getApplicationBufferSize());

            ByteBuffer encClientToServer = allocateBuffer(server.getSession().getPacketBufferSize() * 2);

            handshake(client, server);

            int srcLen = plainClientOut.remaining();
            SSLEngineResult result;

            while (encClientToServer.position() <= server.getSession().getPacketBufferSize()) {
                result = client.wrap(plainClientOut, encClientToServer);
                assertEquals(SSLEngineResult.Status.OK, result.getStatus());
                assertEquals(srcLen, result.bytesConsumed());
                assertTrue(result.bytesProduced() > 0);

                plainClientOut.clear();
            }

            encClientToServer.flip();

            result = server.unwrap(encClientToServer, plainServerOut);
            assertEquals(SSLEngineResult.Status.OK, result.getStatus());
            assertTrue(result.bytesConsumed() > 0);
            assertTrue(result.bytesProduced() > 0);
        } finally {
            cert.delete();
            cleanupClientSslEngine(client);
            cleanupServerSslEngine(server);
        }
    }

    @Test
    public void testBufferUnderFlow() throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();

        clientSslCtx = SslContextBuilder
                .forClient()
                .trustManager(cert.cert())
                .sslProvider(sslClientProvider())
                .build();
        SSLEngine client = clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);

        serverSslCtx = SslContextBuilder
                .forServer(cert.certificate(), cert.privateKey())
                .sslProvider(sslServerProvider())
                .build();
        SSLEngine server = serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);

        try {
            ByteBuffer plainClient = allocateBuffer(1024);
            plainClient.limit(plainClient.capacity());

            ByteBuffer encClientToServer = allocateBuffer(client.getSession().getPacketBufferSize());
            ByteBuffer plainServer = allocateBuffer(server.getSession().getApplicationBufferSize());

            handshake(client, server);

            SSLEngineResult result = client.wrap(plainClient, encClientToServer);
            assertEquals(SSLEngineResult.Status.OK, result.getStatus());
            assertEquals(result.bytesConsumed(), plainClient.capacity());

            // Flip so we can read it.
            encClientToServer.flip();
            int remaining = encClientToServer.remaining();

            // We limit the buffer so we have less then the header to read, this should result in an BUFFER_UNDERFLOW.
            encClientToServer.limit(SSL_RECORD_HEADER_LENGTH - 1);
            result = server.unwrap(encClientToServer, plainServer);
            assertResultIsBufferUnderflow(result);

            // We limit the buffer so we can read the header but not the rest, this should result in an
            // BUFFER_UNDERFLOW.
            encClientToServer.limit(SSL_RECORD_HEADER_LENGTH);
            result = server.unwrap(encClientToServer, plainServer);
            assertResultIsBufferUnderflow(result);

            // We limit the buffer so we can read the header and partly the rest, this should result in an
            // BUFFER_UNDERFLOW.
            encClientToServer.limit(SSL_RECORD_HEADER_LENGTH  + remaining - 1 - SSL_RECORD_HEADER_LENGTH);
            result = server.unwrap(encClientToServer, plainServer);
            assertResultIsBufferUnderflow(result);

            // Reset limit so we can read the full record.
            encClientToServer.limit(remaining);

            result = server.unwrap(encClientToServer, plainServer);
            assertEquals(SSLEngineResult.Status.OK, result.getStatus());
            assertEquals(result.bytesConsumed(), remaining);
            assertTrue(result.bytesProduced() > 0);
        } finally {
            cert.delete();
            cleanupClientSslEngine(client);
            cleanupServerSslEngine(server);
        }
    }

    private static void assertResultIsBufferUnderflow(SSLEngineResult result) {
        assertEquals(SSLEngineResult.Status.BUFFER_UNDERFLOW, result.getStatus());
        assertEquals(0, result.bytesConsumed());
        assertEquals(0, result.bytesProduced());
    }
}
