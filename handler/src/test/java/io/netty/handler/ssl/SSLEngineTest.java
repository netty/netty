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
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.handler.ssl.util.SimpleTrustManagerFactory;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.PromiseNotifier;
import io.netty.util.internal.ResourcesUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.SystemPropertyUtil;
import org.conscrypt.OpenSSLProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opentest4j.AssertionFailedError;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKey;
import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.KeyManagerFactorySpi;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionBindingEvent;
import javax.net.ssl.SSLSessionBindingListener;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;
import javax.security.cert.X509Certificate;

import static io.netty.handler.ssl.SslUtils.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.verify;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class SSLEngineTest {

    private static final String PRINCIPAL_NAME = "CN=e8ac02fa0d65a84219016045db8b05c485b4ecdf.netty.test";
    private final boolean tlsv13Supported;

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

    static final class ProtocolCipherCombo {
        private static final ProtocolCipherCombo TLSV12 = new ProtocolCipherCombo(
                SslProtocols.TLS_v1_2, "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256");
        private static final ProtocolCipherCombo TLSV13 = new ProtocolCipherCombo(
                SslProtocols.TLS_v1_3, "TLS_AES_128_GCM_SHA256");
        final String protocol;
        final String cipher;

        private ProtocolCipherCombo(String protocol, String cipher) {
            this.protocol = protocol;
            this.cipher = cipher;
        }

        static ProtocolCipherCombo tlsv12() {
            return TLSV12;
        }

        static ProtocolCipherCombo tlsv13() {
            return TLSV13;
        }

        @Override
        public String toString() {
            return "ProtocolCipherCombo{" +
                   "protocol='" + protocol + '\'' +
                   ", cipher='" + cipher + '\'' +
                   '}';
        }
    }

    protected SSLEngineTest(boolean tlsv13Supported) {
        this.tlsv13Supported = tlsv13Supported;
    }

    protected static class SSLEngineTestParam {
        private final BufferType type;
        private final ProtocolCipherCombo protocolCipherCombo;
        private final boolean delegate;

        SSLEngineTestParam(BufferType type, ProtocolCipherCombo protocolCipherCombo, boolean delegate) {
            this.type = type;
            this.protocolCipherCombo = protocolCipherCombo;
            this.delegate = delegate;
        }

        final BufferType type() {
            return type;
        }

        final ProtocolCipherCombo combo() {
            return protocolCipherCombo;
        }

        final boolean delegate() {
            return delegate;
        }

        final List<String> protocols() {
            return Collections.singletonList(protocolCipherCombo.protocol);
        }

        final List<String> ciphers() {
            return Collections.singletonList(protocolCipherCombo.cipher);
        }
    }

    protected List<SSLEngineTestParam> newTestParams() {
        List<SSLEngineTestParam> params = new ArrayList<SSLEngineTestParam>();
        for (BufferType type: BufferType.values()) {
            params.add(new SSLEngineTestParam(type, ProtocolCipherCombo.tlsv12(), false));
            params.add(new SSLEngineTestParam(type, ProtocolCipherCombo.tlsv12(), true));

            if (tlsv13Supported) {
                params.add(new SSLEngineTestParam(type, ProtocolCipherCombo.tlsv13(), false));
                params.add(new SSLEngineTestParam(type, ProtocolCipherCombo.tlsv13(), true));
            }
        }
        return params;
    }

    private ExecutorService delegatingExecutor;

    protected ByteBuffer allocateBuffer(BufferType type, int len) {
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

    @BeforeEach
    public void setup() {
        MockitoAnnotations.initMocks(this);
        serverLatch = new CountDownLatch(1);
        clientLatch = new CountDownLatch(1);
        delegatingExecutor = Executors.newCachedThreadPool();
    }

    @AfterEach
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
        delegatingExecutor.shutdown();
        serverException = null;
        clientException = null;
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testMutualAuthSameCerts(SSLEngineTestParam param) throws Throwable {
        mySetupMutualAuth(param, ResourcesUtil.getFile(getClass(), "test_unencrypted.pem"),
                ResourcesUtil.getFile(getClass(), "test.crt"),
                null);
        runTest(null);
        assertTrue(serverLatch.await(2, TimeUnit.SECONDS));
        Throwable cause = serverException;
        if (cause != null) {
            throw cause;
        }
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testSetSupportedCiphers(SSLEngineTestParam param) throws Exception {
        if (param.protocolCipherCombo != ProtocolCipherCombo.tlsv12()) {
            return;
        }
        SelfSignedCertificate cert = new SelfSignedCertificate();
        serverSslCtx = wrapContext(param, SslContextBuilder.forServer(cert.key(), cert.cert())
            .protocols(param.protocols())
            .ciphers(param.ciphers())
            .sslProvider(sslServerProvider()).build());
        final SSLEngine serverEngine =
            wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
        clientSslCtx = wrapContext(param, SslContextBuilder.forClient()
            .trustManager(cert.certificate())
            .protocols(param.protocols())
            .ciphers(param.ciphers())
            .sslProvider(sslClientProvider()).build());
        final SSLEngine clientEngine =
            wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

        final String[] enabledCiphers = new String[]{ param.ciphers().get(0) };

        try {
            clientEngine.setEnabledCipherSuites(enabledCiphers);
            serverEngine.setEnabledCipherSuites(enabledCiphers);

            assertArrayEquals(enabledCiphers, clientEngine.getEnabledCipherSuites());
            assertArrayEquals(enabledCiphers, serverEngine.getEnabledCipherSuites());
        } finally {
            cleanupClientSslEngine(clientEngine);
            cleanupServerSslEngine(serverEngine);
            cert.delete();
        }
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testIncompatibleCiphers(final SSLEngineTestParam param) throws Exception {
        assumeTrue(SslProvider.isTlsv13Supported(sslClientProvider()));
        assumeTrue(SslProvider.isTlsv13Supported(sslServerProvider()));

        SelfSignedCertificate ssc = new SelfSignedCertificate();
        // Select a mandatory cipher from the TLSv1.2 RFC https://www.ietf.org/rfc/rfc5246.txt so handshakes won't fail
        // due to no shared/supported cipher.
        clientSslCtx = wrapContext(param, SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .protocols(SslProtocols.TLS_v1_3, SslProtocols.TLS_v1_2, SslProtocols.TLS_v1)
                .sslContextProvider(clientSslContextProvider())
                .sslProvider(sslClientProvider())
                .build());

        serverSslCtx = wrapContext(param, SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .protocols(SslProtocols.TLS_v1_3, SslProtocols.TLS_v1_2, SslProtocols.TLS_v1)
                .sslContextProvider(serverSslContextProvider())
                .sslProvider(sslServerProvider())
                .build());
        SSLEngine clientEngine = null;
        SSLEngine serverEngine = null;
        try {
            clientEngine = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            serverEngine = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

            // Set the server to only support a single TLSv1.2 cipher
            final String serverCipher = "TLS_RSA_WITH_AES_128_CBC_SHA";
            serverEngine.setEnabledCipherSuites(new String[] { serverCipher });

            // Set the client to only support a single TLSv1.3 cipher
            final String clientCipher = "TLS_AES_256_GCM_SHA384";
            clientEngine.setEnabledCipherSuites(new String[] { clientCipher });

            final SSLEngine client = clientEngine;
            final SSLEngine server = serverEngine;
            assertThrows(SSLHandshakeException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    handshake(param.type(), param.delegate(), client, server);
                }
            });
        } finally {
            cleanupClientSslEngine(clientEngine);
            cleanupServerSslEngine(serverEngine);
            ssc.delete();
        }
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testMutualAuthDiffCerts(SSLEngineTestParam param) throws Exception {
        File serverKeyFile =  ResourcesUtil.getFile(getClass(), "test_encrypted.pem");
        File serverCrtFile = ResourcesUtil.getFile(getClass(), "test.crt");
        String serverKeyPassword = "12345";
        File clientKeyFile = ResourcesUtil.getFile(getClass(), "test2_encrypted.pem");
        File clientCrtFile = ResourcesUtil.getFile(getClass(), "test2.crt");
        String clientKeyPassword = "12345";
        mySetupMutualAuth(param, clientCrtFile, serverKeyFile, serverCrtFile, serverKeyPassword,
                          serverCrtFile, clientKeyFile, clientCrtFile, clientKeyPassword);
        runTest(null);
        assertTrue(serverLatch.await(2, TimeUnit.SECONDS));
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testMutualAuthDiffCertsServerFailure(SSLEngineTestParam param) throws Exception {
        File serverKeyFile = ResourcesUtil.getFile(getClass(), "test_encrypted.pem");
        File serverCrtFile = ResourcesUtil.getFile(getClass(), "test.crt");
        String serverKeyPassword = "12345";
        File clientKeyFile = ResourcesUtil.getFile(getClass(), "test2_encrypted.pem");
        File clientCrtFile = ResourcesUtil.getFile(getClass(), "test2.crt");
        String clientKeyPassword = "12345";
        // Client trusts server but server only trusts itself
        mySetupMutualAuth(param, serverCrtFile, serverKeyFile, serverCrtFile, serverKeyPassword,
                          serverCrtFile, clientKeyFile, clientCrtFile, clientKeyPassword);
        assertTrue(serverLatch.await(10, TimeUnit.SECONDS));
        assertTrue(serverException instanceof SSLHandshakeException);
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testMutualAuthDiffCertsClientFailure(SSLEngineTestParam param) throws Exception {
        File serverKeyFile = ResourcesUtil.getFile(getClass(), "test_unencrypted.pem");
        File serverCrtFile = ResourcesUtil.getFile(getClass(), "test.crt");
        String serverKeyPassword = null;
        File clientKeyFile = ResourcesUtil.getFile(getClass(), "test2_unencrypted.pem");
        File clientCrtFile = ResourcesUtil.getFile(getClass(), "test2.crt");
        String clientKeyPassword = null;
        // Server trusts client but client only trusts itself
        mySetupMutualAuth(param, clientCrtFile, serverKeyFile, serverCrtFile, serverKeyPassword,
                          clientCrtFile, clientKeyFile, clientCrtFile, clientKeyPassword);
        assertTrue(clientLatch.await(10, TimeUnit.SECONDS));
        assertTrue(clientException instanceof SSLHandshakeException);
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testMutualAuthInvalidIntermediateCASucceedWithOptionalClientAuth(SSLEngineTestParam param)
            throws Exception {
        testMutualAuthInvalidClientCertSucceed(param, ClientAuth.NONE);
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testMutualAuthInvalidIntermediateCAFailWithOptionalClientAuth(SSLEngineTestParam param)
            throws Exception {
        testMutualAuthClientCertFail(param, ClientAuth.OPTIONAL);
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testMutualAuthInvalidIntermediateCAFailWithRequiredClientAuth(SSLEngineTestParam param)
            throws Exception {
        testMutualAuthClientCertFail(param, ClientAuth.REQUIRE);
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testMutualAuthValidClientCertChainTooLongFailOptionalClientAuth(SSLEngineTestParam param)
            throws Exception {
        testMutualAuthClientCertFail(param, ClientAuth.OPTIONAL, "mutual_auth_client.p12", true);
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testMutualAuthValidClientCertChainTooLongFailRequireClientAuth(SSLEngineTestParam param)
            throws Exception {
        testMutualAuthClientCertFail(param, ClientAuth.REQUIRE, "mutual_auth_client.p12", true);
    }

    private void testMutualAuthInvalidClientCertSucceed(SSLEngineTestParam param, ClientAuth auth) throws Exception {
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
        File commonCertChain = ResourcesUtil.getFile(getClass(), "mutual_auth_ca.pem");

        mySetupMutualAuth(param, serverKeyManagerFactory, commonCertChain, clientKeyManagerFactory, commonCertChain,
                auth, false, false);
        assertTrue(clientLatch.await(10, TimeUnit.SECONDS));
        rethrowIfNotNull(clientException);
        assertTrue(serverLatch.await(5, TimeUnit.SECONDS));
        rethrowIfNotNull(serverException);
    }

    private void testMutualAuthClientCertFail(SSLEngineTestParam param, ClientAuth auth) throws Exception {
        testMutualAuthClientCertFail(param, auth, "mutual_auth_invalid_client.p12", false);
    }

    private void testMutualAuthClientCertFail(SSLEngineTestParam param, ClientAuth auth, String clientCert,
                                              boolean serverInitEngine)
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
        File commonCertChain = ResourcesUtil.getFile(getClass(), "mutual_auth_ca.pem");

        mySetupMutualAuth(param, serverKeyManagerFactory, commonCertChain, clientKeyManagerFactory, commonCertChain,
                          auth, true, serverInitEngine);
        assertTrue(clientLatch.await(10, TimeUnit.SECONDS));
        assertTrue(mySetupMutualAuthServerIsValidClientException(clientException),
                "unexpected exception: " + clientException);
        assertTrue(serverLatch.await(5, TimeUnit.SECONDS));
        assertTrue(mySetupMutualAuthServerIsValidServerException(serverException),
                "unexpected exception: " + serverException);
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
        // As in TLSv1.3 the handshake is sent without an extra roundtrip an SSLException is valid as well.
        return cause instanceof SSLException || cause instanceof ClosedChannelException;
    }

    protected void mySetupMutualAuthServerInitSslHandler(SslHandler handler) {
    }

    protected void mySetupMutualAuth(final SSLEngineTestParam param, KeyManagerFactory serverKMF,
                                     final File serverTrustManager,
                                     KeyManagerFactory clientKMF, File clientTrustManager,
                                     ClientAuth clientAuth, final boolean failureExpected,
                                     final boolean serverInitEngine)
            throws SSLException, InterruptedException {
        serverSslCtx =
                wrapContext(param, SslContextBuilder.forServer(serverKMF)
                                 .protocols(param.protocols())
                                 .ciphers(param.ciphers())
                                 .sslProvider(sslServerProvider())
                                 .sslContextProvider(serverSslContextProvider())
                                 .trustManager(serverTrustManager)
                                 .clientAuth(clientAuth)
                                 .ciphers(null, IdentityCipherSuiteFilter.INSTANCE)
                                 .sessionCacheSize(0)
                                 .sessionTimeout(0).build());

        clientSslCtx =
                wrapContext(param, SslContextBuilder.forClient()
                                 .protocols(param.protocols())
                                 .ciphers(param.ciphers())
                                 .sslProvider(sslClientProvider())
                                 .sslContextProvider(clientSslContextProvider())
                                 .trustManager(clientTrustManager)
                                 .keyManager(clientKMF)
                                 .ciphers(null, IdentityCipherSuiteFilter.INSTANCE)
                                 .sessionCacheSize(0)
                                 .sessionTimeout(0).build());

        serverConnectedChannel = null;
        sb = new ServerBootstrap();
        cb = new Bootstrap();

        sb.group(new NioEventLoopGroup(), new NioEventLoopGroup());
        sb.channel(NioServerSocketChannel.class);
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.config().setAllocator(new TestByteBufAllocator(ch.config().getAllocator(), param.type()));

                ChannelPipeline p = ch.pipeline();
                SslHandler handler = !param.delegate ? serverSslCtx.newHandler(ch.alloc()) :
                        serverSslCtx.newHandler(ch.alloc(), delegatingExecutor);
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
                ch.config().setAllocator(new TestByteBufAllocator(ch.config().getAllocator(), param.type));
                ChannelPipeline p = ch.pipeline();

                SslHandler handler = !param.delegate ? clientSslCtx.newHandler(ch.alloc()) :
                        clientSslCtx.newHandler(ch.alloc(), delegatingExecutor);
                p.addLast(handler);
                p.addLast(new MessageDelegatorChannelHandler(clientReceiver, clientLatch));
                p.addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                        if (evt == SslHandshakeCompletionEvent.SUCCESS) {
                            // With TLS1.3 a mutal auth error will not be propagated as a handshake error most of the
                            // time as the handshake needs NO extra roundtrip.
                            if (!failureExpected) {
                                clientLatch.countDown();
                            }
                        } else if (evt instanceof SslHandshakeCompletionEvent) {
                            clientException = ((SslHandshakeCompletionEvent) evt).cause();
                            clientLatch.countDown();
                        }
                        ctx.fireUserEventTriggered(evt);
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                        if (cause.getCause() instanceof SSLException) {
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

    protected static void rethrowIfNotNull(Throwable error) {
        if (error != null) {
            throw new AssertionFailedError("Expected no error", error);
        }
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testClientHostnameValidationSuccess(SSLEngineTestParam param) throws Exception {
        mySetupClientHostnameValidation(param, ResourcesUtil.getFile(getClass(),  "localhost_server.pem"),
                                        ResourcesUtil.getFile(getClass(), "localhost_server.key"),
                                        ResourcesUtil.getFile(getClass(), "mutual_auth_ca.pem"),
                                        false);
        assertTrue(clientLatch.await(10, TimeUnit.SECONDS));

        rethrowIfNotNull(clientException);
        assertTrue(serverLatch.await(5, TimeUnit.SECONDS));
        rethrowIfNotNull(serverException);
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testClientHostnameValidationFail(SSLEngineTestParam param) throws Exception {
        Future<Void> clientWriteFuture =
            mySetupClientHostnameValidation(param, ResourcesUtil.getFile(getClass(),  "notlocalhost_server.pem"),
                                            ResourcesUtil.getFile(getClass(), "notlocalhost_server.key"),
                                            ResourcesUtil.getFile(getClass(), "mutual_auth_ca.pem"),
                                            true);
        assertTrue(clientLatch.await(10, TimeUnit.SECONDS));
        assertTrue(mySetupMutualAuthServerIsValidClientException(clientException),
                "unexpected exception: " + clientException);
        assertTrue(serverLatch.await(5, TimeUnit.SECONDS));
        assertTrue(mySetupMutualAuthServerIsValidServerException(serverException),
                "unexpected exception: " + serverException);

        // Verify that any pending writes are failed with the cached handshake exception and not a general SSLException.
        clientWriteFuture.awaitUninterruptibly();
        Throwable actualCause = clientWriteFuture.cause();
        assertSame(clientException, actualCause);
    }

    private Future<Void> mySetupClientHostnameValidation(final SSLEngineTestParam param, File serverCrtFile,
                                                         File serverKeyFile,
                                                         File clientTrustCrtFile,
                                                         final boolean failureExpected)
            throws SSLException, InterruptedException {
        final String expectedHost = "localhost";
        serverSslCtx = wrapContext(param, SslContextBuilder.forServer(serverCrtFile, serverKeyFile, null)
                .sslProvider(sslServerProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .sslContextProvider(serverSslContextProvider())
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .ciphers(null, IdentityCipherSuiteFilter.INSTANCE)
                .sessionCacheSize(0)
                .sessionTimeout(0)
                .build());

        clientSslCtx = wrapContext(param, SslContextBuilder.forClient()
                .sslProvider(sslClientProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .sslContextProvider(clientSslContextProvider())
                .trustManager(clientTrustCrtFile)
                .ciphers(null, IdentityCipherSuiteFilter.INSTANCE)
                .sessionCacheSize(0)
                .sessionTimeout(0)
                .build());

        serverConnectedChannel = null;
        sb = new ServerBootstrap();
        cb = new Bootstrap();

        sb.group(new NioEventLoopGroup(), new NioEventLoopGroup());
        sb.channel(NioServerSocketChannel.class);
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.config().setAllocator(new TestByteBufAllocator(ch.config().getAllocator(), param.type));
                ChannelPipeline p = ch.pipeline();

                SslHandler handler = !param.delegate ? serverSslCtx.newHandler(ch.alloc()) :
                        serverSslCtx.newHandler(ch.alloc(), delegatingExecutor);
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

        final Promise<Void> clientWritePromise = ImmediateEventExecutor.INSTANCE.newPromise();
        cb.group(new NioEventLoopGroup());
        cb.channel(NioSocketChannel.class);
        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.config().setAllocator(new TestByteBufAllocator(ch.config().getAllocator(), param.type));
                ChannelPipeline p = ch.pipeline();
                InetSocketAddress remoteAddress = (InetSocketAddress) serverChannel.localAddress();

                SslHandler sslHandler = !param.delegate ?
                        clientSslCtx.newHandler(ch.alloc(), expectedHost, 0) :
                        clientSslCtx.newHandler(ch.alloc(), expectedHost, 0,  delegatingExecutor);

                SSLParameters parameters = sslHandler.engine().getSSLParameters();
                if (SslUtils.isValidHostNameForSNI(expectedHost)) {
                    assertEquals(1, parameters.getServerNames().size());
                    assertEquals(new SNIHostName(expectedHost), parameters.getServerNames().get(0));
                }
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                sslHandler.engine().setSSLParameters(parameters);
                p.addLast(sslHandler);
                p.addLast(new MessageDelegatorChannelHandler(clientReceiver, clientLatch));
                p.addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void handlerAdded(ChannelHandlerContext ctx) {
                        // Only write if there is a failure expected. We don't actually care about the write going
                        // through we just want to verify the local failure condition. This way we don't have to worry
                        // about verifying the payload and releasing the content on the server side.
                        if (failureExpected) {
                            ChannelFuture f = ctx.write(ctx.alloc().buffer(1).writeByte(1));
                            PromiseNotifier.cascade(f, clientWritePromise);
                        }
                    }

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
        return clientWritePromise;
    }

    private void mySetupMutualAuth(SSLEngineTestParam param, File keyFile, File crtFile, String keyPassword)
            throws SSLException, InterruptedException {
        mySetupMutualAuth(param, crtFile, keyFile, crtFile, keyPassword, crtFile, keyFile, crtFile, keyPassword);
    }

    private void verifySSLSessionForMutualAuth(
            SSLEngineTestParam param, SSLSession session, File certFile, String principalName)
            throws Exception {
        InputStream in = null;
        try {
            assertEquals(principalName, session.getLocalPrincipal().getName());
            assertEquals(principalName, session.getPeerPrincipal().getName());
            assertNotNull(session.getId());
            assertEquals(param.combo().cipher, session.getCipherSuite());
            assertEquals(param.combo().protocol, session.getProtocol());
            assertTrue(session.getApplicationBufferSize() > 0);
            assertTrue(session.getCreationTime() > 0);
            assertTrue(session.isValid());
            assertTrue(session.getLastAccessedTime() > 0);

            in = new FileInputStream(certFile);
            final byte[] certBytes = SslContext.X509_CERT_FACTORY
                    .generateCertificate(in).getEncoded();

            // Verify session
            assertEquals(1, session.getPeerCertificates().length);
            assertArrayEquals(certBytes, session.getPeerCertificates()[0].getEncoded());

            try {
                assertEquals(1, session.getPeerCertificateChain().length);
                assertArrayEquals(certBytes, session.getPeerCertificateChain()[0].getEncoded());
            } catch (UnsupportedOperationException e) {
                // See https://bugs.openjdk.java.net/browse/JDK-8241039
                assertTrue(PlatformDependent.javaVersion() >= 15);
            }

            assertEquals(1, session.getLocalCertificates().length);
            assertArrayEquals(certBytes, session.getLocalCertificates()[0].getEncoded());
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    private void mySetupMutualAuth(final SSLEngineTestParam param,
            File servertTrustCrtFile, File serverKeyFile, final File serverCrtFile, String serverKeyPassword,
            File clientTrustCrtFile, File clientKeyFile, final File clientCrtFile, String clientKeyPassword)
            throws InterruptedException, SSLException {
        serverSslCtx =
                wrapContext(param, SslContextBuilder.forServer(serverCrtFile, serverKeyFile, serverKeyPassword)
                                 .sslProvider(sslServerProvider())
                                 .sslContextProvider(serverSslContextProvider())
                                 .protocols(param.protocols())
                                 .ciphers(param.ciphers())
                                 .trustManager(servertTrustCrtFile)
                                 .ciphers(null, IdentityCipherSuiteFilter.INSTANCE)
                                 .sessionCacheSize(0)
                                 .sessionTimeout(0).build());
        clientSslCtx =
                wrapContext(param, SslContextBuilder.forClient()
                                 .sslProvider(sslClientProvider())
                                 .sslContextProvider(clientSslContextProvider())
                                 .protocols(param.protocols())
                                 .ciphers(param.ciphers())
                                 .trustManager(clientTrustCrtFile)
                                 .keyManager(clientCrtFile, clientKeyFile, clientKeyPassword)
                                 .ciphers(null, IdentityCipherSuiteFilter.INSTANCE)
                                 .sessionCacheSize(0)
                                 .sessionTimeout(0).build());

        serverConnectedChannel = null;
        sb = new ServerBootstrap();
        cb = new Bootstrap();

        sb.group(new NioEventLoopGroup(), new NioEventLoopGroup());
        sb.channel(NioServerSocketChannel.class);
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.config().setAllocator(new TestByteBufAllocator(ch.config().getAllocator(), param.type));

                ChannelPipeline p = ch.pipeline();
                final SSLEngine engine = wrapEngine(serverSslCtx.newEngine(ch.alloc()));
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
                                verifySSLSessionForMutualAuth(
                                        param, engine.getSession(), serverCrtFile, PRINCIPAL_NAME);
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
                ch.config().setAllocator(new TestByteBufAllocator(ch.config().getAllocator(), param.type));

                final SslHandler handler = !param.delegate ?
                        clientSslCtx.newHandler(ch.alloc()) :
                        clientSslCtx.newHandler(ch.alloc(), delegatingExecutor);

                handler.engine().setNeedClientAuth(true);
                ChannelPipeline p = ch.pipeline();
                p.addLast(handler);
                p.addLast(new MessageDelegatorChannelHandler(clientReceiver, clientLatch));
                p.addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                        if (evt == SslHandshakeCompletionEvent.SUCCESS) {
                            try {
                                verifySSLSessionForMutualAuth(
                                        param, handler.engine().getSession(), clientCrtFile, PRINCIPAL_NAME);
                            } catch (Throwable cause) {
                                clientException = cause;
                            }
                        }
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
        if (engine instanceof JdkAlpnSslEngine) {
            // Also verify the Java9 exposed method.
            JdkAlpnSslEngine java9SslEngine = (JdkAlpnSslEngine) engine;
            assertEquals(expectedApplicationProtocol == null ? StringUtil.EMPTY_STRING : expectedApplicationProtocol,
                    java9SslEngine.getApplicationProtocol());
        }
    }

    private static void writeAndVerifyReceived(ByteBuf message, Channel sendChannel, CountDownLatch receiverLatch,
                                               MessageReceiver receiver) throws Exception {
        List<ByteBuf> dataCapture = null;
        try {
            assertTrue(sendChannel.writeAndFlush(message).await(10, TimeUnit.SECONDS));
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
        clientSslCtx = wrapContext(null, SslContextBuilder.forClient()
                .sslProvider(sslClientProvider())
                .sslContextProvider(clientSslContextProvider()).build());
        SSLEngine engine = null;
        try {
            engine = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            assertTrue(engine.getSession().getCreationTime() <= System.currentTimeMillis());
        } finally {
            cleanupClientSslEngine(engine);
        }
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testSessionInvalidate(SSLEngineTestParam param) throws Exception {
        clientSslCtx = wrapContext(param, SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(sslClientProvider())
                .sslContextProvider(clientSslContextProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = wrapContext(param, SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(sslServerProvider())
                .sslContextProvider(serverSslContextProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SSLEngine clientEngine = null;
        SSLEngine serverEngine = null;
        try {
            clientEngine = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            serverEngine = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            handshake(param.type(), param.delegate(), clientEngine, serverEngine);

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

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testSSLSessionId(SSLEngineTestParam param) throws Exception {
        clientSslCtx = wrapContext(param, SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(sslClientProvider())
                // This test only works for non TLSv1.3 for now
                .protocols(param.protocols())
                .sslContextProvider(clientSslContextProvider())
                .build());
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = wrapContext(param, SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(sslServerProvider())
                // This test only works for non TLSv1.3 for now
                .protocols(param.protocols())
                .sslContextProvider(serverSslContextProvider())
                .build());
        SSLEngine clientEngine = null;
        SSLEngine serverEngine = null;
        try {
            clientEngine = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            serverEngine = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

            // Before the handshake the id should have length == 0
            assertEquals(0, clientEngine.getSession().getId().length);
            assertEquals(0, serverEngine.getSession().getId().length);

            handshake(param.type(), param.delegate(), clientEngine, serverEngine);

            if (param.protocolCipherCombo == ProtocolCipherCombo.TLSV13) {
                // Allocate something which is big enough for sure
                ByteBuffer packetBuffer = allocateBuffer(param.type(), 32 * 1024);
                ByteBuffer appBuffer = allocateBuffer(param.type(), 32 * 1024);

                appBuffer.clear().position(4).flip();
                packetBuffer.clear();

                do {
                    SSLEngineResult result;

                    do {
                        result = serverEngine.wrap(appBuffer, packetBuffer);
                    } while (appBuffer.hasRemaining() || result.bytesProduced() > 0);

                    appBuffer.clear();
                    packetBuffer.flip();
                    do {
                        result = clientEngine.unwrap(packetBuffer, appBuffer);
                    } while (packetBuffer.hasRemaining() || result.bytesProduced() > 0);

                    packetBuffer.clear();
                    appBuffer.clear().position(4).flip();

                    do {
                        result = clientEngine.wrap(appBuffer, packetBuffer);
                    } while (appBuffer.hasRemaining() || result.bytesProduced() > 0);

                    appBuffer.clear();
                    packetBuffer.flip();

                    do {
                        result = serverEngine.unwrap(packetBuffer, appBuffer);
                    } while (packetBuffer.hasRemaining() || result.bytesProduced() > 0);

                    packetBuffer.clear();
                    appBuffer.clear().position(4).flip();
                } while (clientEngine.getSession().getId().length == 0);

                // With TLS1.3 we should see pseudo IDs and so these should never match.
                assertFalse(Arrays.equals(clientEngine.getSession().getId(), serverEngine.getSession().getId()));
            } else {
                // After the handshake the id should have length > 0
                assertNotEquals(0, clientEngine.getSession().getId().length);
                assertNotEquals(0, serverEngine.getSession().getId().length);

                assertArrayEquals(clientEngine.getSession().getId(), serverEngine.getSession().getId());
            }
        } finally {
            cleanupClientSslEngine(clientEngine);
            cleanupServerSslEngine(serverEngine);
            ssc.delete();
        }
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    @Timeout(30)
    public void clientInitiatedRenegotiationWithFatalAlertDoesNotInfiniteLoopServer(final SSLEngineTestParam param)
            throws Exception {
        assumeTrue(PlatformDependent.javaVersion() >= 11);
        final SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = wrapContext(param, SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                                        .sslProvider(sslServerProvider())
                                        .sslContextProvider(serverSslContextProvider())
                                        .protocols(param.protocols())
                                        .ciphers(param.ciphers())
                                        .build());
        sb = new ServerBootstrap()
                .group(new NioEventLoopGroup(1))
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ch.config().setAllocator(new TestByteBufAllocator(ch.config().getAllocator(), param.type));

                        ChannelPipeline p = ch.pipeline();

                        SslHandler handler = !param.delegate ?
                                serverSslCtx.newHandler(ch.alloc()) :
                                serverSslCtx.newHandler(ch.alloc(), delegatingExecutor);

                        p.addLast(handler);
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

        clientSslCtx = wrapContext(param, SslContextBuilder.forClient()
                                        // OpenSslEngine doesn't support renegotiation on client side
                                        .sslProvider(SslProvider.JDK)
                                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                        .protocols(param.protocols())
                                        .ciphers(param.ciphers())
                                        .build());

        cb = new Bootstrap();
        cb.group(new NioEventLoopGroup(1))
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ch.config().setAllocator(new TestByteBufAllocator(ch.config().getAllocator(), param.type()));

                        ChannelPipeline p = ch.pipeline();

                        SslHandler sslHandler = !param.delegate ?
                                clientSslCtx.newHandler(ch.alloc()) :
                                clientSslCtx.newHandler(ch.alloc(), delegatingExecutor);

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

    protected void testEnablingAnAlreadyDisabledSslProtocol(SSLEngineTestParam param,
                                                            String[] protocols1, String[] protocols2) throws Exception {
        SSLEngine sslEngine = null;
        try {
            File serverKeyFile = ResourcesUtil.getFile(getClass(), "test_unencrypted.pem");
            File serverCrtFile = ResourcesUtil.getFile(getClass(), "test.crt");
            serverSslCtx = wrapContext(param, SslContextBuilder.forServer(serverCrtFile, serverKeyFile)
                                            .sslProvider(sslServerProvider())
                                            .sslContextProvider(serverSslContextProvider())
                                            .protocols(param.protocols())
                                            .ciphers(param.ciphers())
                                            .build());

            sslEngine = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

            // Disable all protocols
            sslEngine.setEnabledProtocols(EmptyArrays.EMPTY_STRINGS);

            // The only protocol that should be enabled is SSLv2Hello
            String[] enabledProtocols = sslEngine.getEnabledProtocols();
            assertArrayEquals(protocols1, enabledProtocols);

            // Enable a protocol that is currently disabled
            sslEngine.setEnabledProtocols(new String[]{ SslProtocols.TLS_v1_2 });

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

    protected void handshake(BufferType type, boolean delegate, SSLEngine clientEngine, SSLEngine serverEngine)
            throws Exception {
        ByteBuffer cTOs = allocateBuffer(type, clientEngine.getSession().getPacketBufferSize());
        ByteBuffer sTOc = allocateBuffer(type, serverEngine.getSession().getPacketBufferSize());

        ByteBuffer serverAppReadBuffer = allocateBuffer(type, serverEngine.getSession().getApplicationBufferSize());
        ByteBuffer clientAppReadBuffer = allocateBuffer(type, clientEngine.getSession().getApplicationBufferSize());

        clientEngine.beginHandshake();
        serverEngine.beginHandshake();

        ByteBuffer empty = allocateBuffer(type, 0);

        SSLEngineResult clientResult;
        SSLEngineResult serverResult;

        boolean clientHandshakeFinished = false;
        boolean serverHandshakeFinished = false;
        boolean cTOsHasRemaining;
        boolean sTOcHasRemaining;

        do {
            int cTOsPos = cTOs.position();
            int sTOcPos = sTOc.position();

            if (!clientHandshakeFinished) {
                clientResult = clientEngine.wrap(empty, cTOs);
                runDelegatedTasks(delegate, clientResult, clientEngine);
                assertEquals(empty.remaining(), clientResult.bytesConsumed());
                assertEquals(cTOs.position() - cTOsPos,  clientResult.bytesProduced());

                if (isHandshakeFinished(clientResult)) {
                    clientHandshakeFinished = true;
                }
            }

            if (!serverHandshakeFinished) {
                serverResult = serverEngine.wrap(empty, sTOc);
                runDelegatedTasks(delegate, serverResult, serverEngine);
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

            if (!clientHandshakeFinished ||
                // After the handshake completes it is possible we have more data that was send by the server as
                // the server will send session updates after the handshake. In this case continue to unwrap.
                SslProtocols.TLS_v1_3.equals(clientEngine.getSession().getProtocol())) {
                int clientAppReadBufferPos = clientAppReadBuffer.position();
                clientResult = clientEngine.unwrap(sTOc, clientAppReadBuffer);

                runDelegatedTasks(delegate, clientResult, clientEngine);
                assertEquals(sTOc.position() - sTOcPos, clientResult.bytesConsumed());
                assertEquals(clientAppReadBuffer.position() - clientAppReadBufferPos, clientResult.bytesProduced());

                if (isHandshakeFinished(clientResult)) {
                    clientHandshakeFinished = true;
                }
            } else {
                assertEquals(0, sTOc.remaining());
            }

            if (!serverHandshakeFinished) {
                int serverAppReadBufferPos = serverAppReadBuffer.position();
                serverResult = serverEngine.unwrap(cTOs, serverAppReadBuffer);
                runDelegatedTasks(delegate, serverResult, serverEngine);
                assertEquals(cTOs.position() - cTOsPos, serverResult.bytesConsumed());
                assertEquals(serverAppReadBuffer.position() - serverAppReadBufferPos, serverResult.bytesProduced());

                if (isHandshakeFinished(serverResult)) {
                    serverHandshakeFinished = true;
                }
            } else {
                assertFalse(cTOs.hasRemaining());
            }

            cTOsHasRemaining = cTOs.hasRemaining();
            sTOcHasRemaining = sTOc.hasRemaining();

            sTOc.compact();
            cTOs.compact();
        } while (!clientHandshakeFinished || !serverHandshakeFinished ||
                // We need to ensure we feed all the data to the engine to not end up with a corrupted state.
                // This is especially important with TLS1.3 which may produce sessions after the "main handshake" is
                // done
                cTOsHasRemaining || sTOcHasRemaining);
    }

    private static boolean isHandshakeFinished(SSLEngineResult result) {
        return result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED;
    }

    private void runDelegatedTasks(boolean delegate, SSLEngineResult result, SSLEngine engine) throws Exception {
        if (result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK) {
            for (;;) {
                Runnable task = engine.getDelegatedTask();
                if (task == null) {
                    break;
                }
                if (!delegate) {
                    task.run();
                } else {
                    delegatingExecutor.submit(task).get();
                }
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

    protected void setupHandlers(SSLEngineTestParam param, ApplicationProtocolConfig apn)
            throws InterruptedException, SSLException, CertificateException {
        setupHandlers(param, apn, apn);
    }

    protected void setupHandlers(SSLEngineTestParam param,
                                 ApplicationProtocolConfig serverApn, ApplicationProtocolConfig clientApn)
            throws InterruptedException, SSLException, CertificateException {
        SelfSignedCertificate ssc = new SelfSignedCertificate();

        try {
            SslContextBuilder serverCtxBuilder = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey(), null)
                                                         .sslProvider(sslServerProvider())
                                                         .sslContextProvider(serverSslContextProvider())
                                                         .ciphers(null, IdentityCipherSuiteFilter.INSTANCE)
                                                         .applicationProtocolConfig(serverApn)
                                                         .sessionCacheSize(0)
                                                         .sessionTimeout(0);
            if (serverApn.protocol() == Protocol.NPN || serverApn.protocol() == Protocol.NPN_AND_ALPN) {
                // NPN is not really well supported with TLSv1.3 so force to use TLSv1.2
                // See https://github.com/openssl/openssl/issues/3665
                serverCtxBuilder.protocols(SslProtocols.TLS_v1_2);
            }

            SslContextBuilder clientCtxBuilder = SslContextBuilder.forClient()
                             .sslProvider(sslClientProvider())
                             .sslContextProvider(clientSslContextProvider())
                             .applicationProtocolConfig(clientApn)
                             .trustManager(InsecureTrustManagerFactory.INSTANCE)
                             .ciphers(null, IdentityCipherSuiteFilter.INSTANCE)
                             .sessionCacheSize(0)
                             .sessionTimeout(0);

            if (clientApn.protocol() == Protocol.NPN || clientApn.protocol() == Protocol.NPN_AND_ALPN) {
                // NPN is not really well supported with TLSv1.3 so force to use TLSv1.2
                // See https://github.com/openssl/openssl/issues/3665
                clientCtxBuilder.protocols(SslProtocols.TLS_v1_2);
            }

            setupHandlers(param.type(), param.delegate(),
                    wrapContext(param, serverCtxBuilder.build()), wrapContext(param, clientCtxBuilder.build()));
        } finally {
          ssc.delete();
        }
    }

    protected void setupHandlers(final BufferType type, final boolean delegate,
                                 SslContext serverCtx, SslContext clientCtx)
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

                SslHandler sslHandler = !delegate ?
                        serverSslCtx.newHandler(ch.alloc()) :
                        serverSslCtx.newHandler(ch.alloc(), delegatingExecutor);

                p.addLast(sslHandler);
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

                SslHandler sslHandler = !delegate ?
                        clientSslCtx.newHandler(ch.alloc()) :
                        clientSslCtx.newHandler(ch.alloc(), delegatingExecutor);

                p.addLast(sslHandler);
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

                    @Override
                    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                        clientLatch.countDown();
                    }
                });
            }
        });

        serverChannel = sb.bind(new InetSocketAddress(0)).syncUninterruptibly().channel();

        ChannelFuture ccf = cb.connect(serverChannel.localAddress());
        assertTrue(ccf.syncUninterruptibly().isSuccess());
        clientChannel = ccf.channel();
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    @Timeout(30)
    public void testMutualAuthSameCertChain(final SSLEngineTestParam param) throws Exception {
        SelfSignedCertificate serverCert = new SelfSignedCertificate();
        SelfSignedCertificate clientCert = new SelfSignedCertificate();
        serverSslCtx =
                wrapContext(param, SslContextBuilder.forServer(serverCert.certificate(), serverCert.privateKey())
                                .trustManager(clientCert.cert())
                                 .clientAuth(ClientAuth.REQUIRE).sslProvider(sslServerProvider())
                                 .sslContextProvider(serverSslContextProvider())
                                 .protocols(param.protocols())
                                 .ciphers(param.ciphers()).build());

        sb = new ServerBootstrap();
        sb.group(new NioEventLoopGroup(), new NioEventLoopGroup());
        sb.channel(NioServerSocketChannel.class);

        final Promise<String> promise = sb.config().group().next().newPromise();
        serverChannel = sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.config().setAllocator(new TestByteBufAllocator(ch.config().getAllocator(), param.type()));

                SslHandler sslHandler = !param.delegate ?
                        serverSslCtx.newHandler(ch.alloc()) :
                        serverSslCtx.newHandler(ch.alloc(), delegatingExecutor);

                ch.pipeline().addFirst(sslHandler);
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                        if (evt instanceof SslHandshakeCompletionEvent) {
                            Throwable cause = ((SslHandshakeCompletionEvent) evt).cause();
                            if (cause == null) {
                                SSLSession session = ((SslHandler) ctx.pipeline().first()).engine().getSession();
                                Certificate[] peerCertificates = session.getPeerCertificates();
                                if (peerCertificates == null) {
                                    promise.setFailure(new NullPointerException("peerCertificates"));
                                    return;
                                }
                                try {
                                    X509Certificate[] peerCertificateChain = session.getPeerCertificateChain();
                                    if (peerCertificateChain == null) {
                                        promise.setFailure(new NullPointerException("peerCertificateChain"));
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
                                } catch (UnsupportedOperationException e) {
                                    // See https://bugs.openjdk.java.net/browse/JDK-8241039
                                    assertTrue(PlatformDependent.javaVersion() >= 15);
                                    assertEquals(2, peerCertificates.length);
                                    for (int i = 0; i < peerCertificates.length; i++) {
                                        if (peerCertificates[i] == null) {
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

        // We create a new chain for certificates which contains 2 certificates
        ByteArrayOutputStream chainStream = new ByteArrayOutputStream();
        chainStream.write(Files.readAllBytes(clientCert.certificate().toPath()));
        chainStream.write(Files.readAllBytes(serverCert.certificate().toPath()));

        clientSslCtx =
                wrapContext(param, SslContextBuilder.forClient().keyManager(
                        new ByteArrayInputStream(chainStream.toByteArray()),
                        new FileInputStream(clientCert.privateKey()))
                .trustManager(new FileInputStream(serverCert.certificate()))
                .sslProvider(sslClientProvider())
                .sslContextProvider(clientSslContextProvider())
                .protocols(param.protocols()).ciphers(param.ciphers()).build());
        cb = new Bootstrap();
        cb.group(new NioEventLoopGroup());
        cb.channel(NioSocketChannel.class);
        clientChannel = cb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.config().setAllocator(new TestByteBufAllocator(ch.config().getAllocator(), param.type()));
                ch.pipeline().addLast(new SslHandler(wrapEngine(clientSslCtx.newEngine(ch.alloc()))));
            }

        }).connect(serverChannel.localAddress()).syncUninterruptibly().channel();

        promise.syncUninterruptibly();

        serverCert.delete();
        clientCert.delete();
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testUnwrapBehavior(SSLEngineTestParam param) throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();

        clientSslCtx = wrapContext(param, SslContextBuilder
                .forClient()
                .trustManager(cert.cert())
                .sslProvider(sslClientProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SSLEngine client = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

        serverSslCtx = wrapContext(param, SslContextBuilder
                .forServer(cert.certificate(), cert.privateKey())
                .sslProvider(sslServerProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SSLEngine server = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

        byte[] bytes = "Hello World".getBytes(CharsetUtil.US_ASCII);

        try {
            ByteBuffer plainClientOut = allocateBuffer(param.type, client.getSession().getApplicationBufferSize());
            ByteBuffer encryptedClientToServer = allocateBuffer(
                    param.type, server.getSession().getPacketBufferSize() * 2);
            ByteBuffer plainServerIn = allocateBuffer(param.type, server.getSession().getApplicationBufferSize());

            handshake(param.type(), param.delegate(), client, server);

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
            ByteBuffer small = allocateBuffer(param.type, 3);
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

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testProtocolMatch(SSLEngineTestParam param) throws Exception {
        testProtocol(param, false, new String[] {"TLSv1.2"}, new String[] {"TLSv1", "TLSv1.1", "TLSv1.2"});
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testProtocolNoMatch(SSLEngineTestParam param) throws Exception {
        testProtocol(param, true, new String[] {"TLSv1.2"}, new String[] {"TLSv1", "TLSv1.1"});
    }

    private void testProtocol(final SSLEngineTestParam param, boolean handshakeFails,
                              String[] clientProtocols, String[] serverProtocols)
            throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();

        clientSslCtx = wrapContext(param, SslContextBuilder
                .forClient()
                .trustManager(cert.cert())
                .sslProvider(sslClientProvider())
                .protocols(clientProtocols)
                .build());
        SSLEngine client = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

        serverSslCtx = wrapContext(param, SslContextBuilder
                .forServer(cert.certificate(), cert.privateKey())
                .sslProvider(sslServerProvider())
                .protocols(serverProtocols)
                .build());
        SSLEngine server = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

        try {
            if (handshakeFails) {
                final SSLEngine clientEngine = client;
                final SSLEngine serverEngine = server;
                assertThrows(SSLHandshakeException.class, new Executable() {
                    @Override
                    public void execute() throws Throwable {
                        handshake(param.type(), param.delegate(), clientEngine, serverEngine);
                    }
                });
            } else {
                handshake(param.type(), param.delegate(), client, server);
            }
        } finally {
            cleanupClientSslEngine(client);
            cleanupServerSslEngine(server);
            cert.delete();
        }
    }

    private String[] nonContiguousProtocols(SslProvider provider) {
        if (provider != null) {
            // conscrypt not correctly filters out TLSv1 and TLSv1.1 which is required now by the JDK.
            // https://github.com/google/conscrypt/issues/1013
            return new String[] { SslProtocols.TLS_v1_2 };
        }
        return new String[] {SslProtocols.TLS_v1_2, SslProtocols.TLS_v1};
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testHandshakeCompletesWithNonContiguousProtocolsTLSv1_2CipherOnly(SSLEngineTestParam param)
            throws Exception {
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        // Select a mandatory cipher from the TLSv1.2 RFC https://www.ietf.org/rfc/rfc5246.txt so handshakes won't fail
        // due to no shared/supported cipher.
        final String sharedCipher = "TLS_RSA_WITH_AES_128_CBC_SHA";
        clientSslCtx = wrapContext(param, SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .ciphers(Collections.singletonList(sharedCipher))
                .protocols(nonContiguousProtocols(sslClientProvider()))
                .sslContextProvider(clientSslContextProvider())
                .sslProvider(sslClientProvider())
                .build());

        serverSslCtx = wrapContext(param, SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .ciphers(Collections.singletonList(sharedCipher))
                .protocols(nonContiguousProtocols(sslServerProvider()))
                .sslContextProvider(serverSslContextProvider())
                .sslProvider(sslServerProvider())
                .build());
        SSLEngine clientEngine = null;
        SSLEngine serverEngine = null;
        try {
            clientEngine = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            serverEngine = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            handshake(param.type(), param.delegate(), clientEngine, serverEngine);
        } finally {
            cleanupClientSslEngine(clientEngine);
            cleanupServerSslEngine(serverEngine);
            ssc.delete();
        }
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testHandshakeCompletesWithoutFilteringSupportedCipher(SSLEngineTestParam param) throws Exception {
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        // Select a mandatory cipher from the TLSv1.2 RFC https://www.ietf.org/rfc/rfc5246.txt so handshakes won't fail
        // due to no shared/supported cipher.
        final String sharedCipher = "TLS_RSA_WITH_AES_128_CBC_SHA";
        clientSslCtx = wrapContext(param, SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .ciphers(Collections.singletonList(sharedCipher), SupportedCipherSuiteFilter.INSTANCE)
                .protocols(nonContiguousProtocols(sslClientProvider()))
                .sslContextProvider(clientSslContextProvider())
                .sslProvider(sslClientProvider())
                .build());

        serverSslCtx = wrapContext(param, SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .ciphers(Collections.singletonList(sharedCipher), SupportedCipherSuiteFilter.INSTANCE)
                .protocols(nonContiguousProtocols(sslServerProvider()))
                .sslContextProvider(serverSslContextProvider())
                .sslProvider(sslServerProvider())
                .build());
        SSLEngine clientEngine = null;
        SSLEngine serverEngine = null;
        try {
            clientEngine = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            serverEngine = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            handshake(param.type(), param.delegate(), clientEngine, serverEngine);
        } finally {
            cleanupClientSslEngine(clientEngine);
            cleanupServerSslEngine(serverEngine);
            ssc.delete();
        }
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testPacketBufferSizeLimit(SSLEngineTestParam param) throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();

        clientSslCtx = wrapContext(param, SslContextBuilder
                .forClient()
                .trustManager(cert.cert())
                .sslContextProvider(clientSslContextProvider())
                .sslProvider(sslClientProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SSLEngine client = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

        serverSslCtx = wrapContext(param, SslContextBuilder
                .forServer(cert.certificate(), cert.privateKey())
                .sslContextProvider(serverSslContextProvider())
                .sslProvider(sslServerProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SSLEngine server = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

        try {
            // Allocate an buffer that is bigger then the max plain record size.
            ByteBuffer plainServerOut = allocateBuffer(
                    param.type(), server.getSession().getApplicationBufferSize() * 2);

            handshake(param.type(), param.delegate(), client, server);

            // Fill the whole buffer and flip it.
            plainServerOut.position(plainServerOut.capacity());
            plainServerOut.flip();

            ByteBuffer encryptedServerToClient = allocateBuffer(
                    param.type(), server.getSession().getPacketBufferSize());

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

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testSSLEngineUnwrapNoSslRecord(SSLEngineTestParam param) throws Exception {
        clientSslCtx = wrapContext(param, SslContextBuilder
                .forClient()
                .sslContextProvider(clientSslContextProvider())
                .sslProvider(sslClientProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        final SSLEngine client = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

        try {
            final ByteBuffer src = allocateBuffer(param.type(), client.getSession().getApplicationBufferSize());
            final ByteBuffer dst = allocateBuffer(param.type(), client.getSession().getPacketBufferSize());
            ByteBuffer empty = allocateBuffer(param.type(), 0);

            SSLEngineResult clientResult = client.wrap(empty, dst);
            assertEquals(SSLEngineResult.Status.OK, clientResult.getStatus());
            assertEquals(SSLEngineResult.HandshakeStatus.NEED_UNWRAP, clientResult.getHandshakeStatus());

            assertThrows(SSLException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    client.unwrap(src, dst);
                }
            });
        } finally {
            cleanupClientSslEngine(client);
        }
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testBeginHandshakeAfterEngineClosed(SSLEngineTestParam param) throws SSLException {
        clientSslCtx = wrapContext(param, SslContextBuilder
                .forClient()
                .sslContextProvider(clientSslContextProvider())
                .sslProvider(sslClientProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SSLEngine client = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

        try {
            client.closeInbound();
            client.closeOutbound();
            try {
                client.beginHandshake();
                fail();
            } catch (SSLException expected) {
                // expected
            } catch (IllegalStateException e) {
                if (!Conscrypt.isEngineSupported(client)) {
                    throw e;
                }
                // Workaround for conscrypt bug
                // See https://github.com/google/conscrypt/issues/840
            }
        } finally {
            cleanupClientSslEngine(client);
        }
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testBeginHandshakeCloseOutbound(SSLEngineTestParam param) throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();

        clientSslCtx = wrapContext(param, SslContextBuilder
                .forClient()
                .sslContextProvider(clientSslContextProvider())
                .sslProvider(sslClientProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SSLEngine client = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

        serverSslCtx = wrapContext(param, SslContextBuilder
                .forServer(cert.certificate(), cert.privateKey())
                .sslContextProvider(serverSslContextProvider())
                .sslProvider(sslServerProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SSLEngine server = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

        try {
            testBeginHandshakeCloseOutbound(param, client);
            testBeginHandshakeCloseOutbound(param, server);
        } finally {
            cleanupClientSslEngine(client);
            cleanupServerSslEngine(server);
            cert.delete();
        }
    }

    private void testBeginHandshakeCloseOutbound(SSLEngineTestParam param, SSLEngine engine) throws SSLException {
        ByteBuffer dst = allocateBuffer(param.type(), engine.getSession().getPacketBufferSize());
        ByteBuffer empty = allocateBuffer(param.type(), 0);
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

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testCloseInboundAfterBeginHandshake(SSLEngineTestParam param) throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();

        clientSslCtx = wrapContext(param, SslContextBuilder
                .forClient()
                .sslContextProvider(clientSslContextProvider())
                .sslProvider(sslClientProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SSLEngine client = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

        serverSslCtx = wrapContext(param, SslContextBuilder
                .forServer(cert.certificate(), cert.privateKey())
                .sslContextProvider(serverSslContextProvider())
                .sslProvider(sslServerProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SSLEngine server = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

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
            // Workaround for conscrypt bug
            // See https://github.com/google/conscrypt/issues/839
            if (!Conscrypt.isEngineSupported(engine)) {
                fail();
            }
        } catch (SSLException expected) {
            // expected
        }
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testCloseNotifySequence(SSLEngineTestParam param) throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();

        clientSslCtx = wrapContext(param, SslContextBuilder
                .forClient()
                .trustManager(cert.cert())
                .sslContextProvider(clientSslContextProvider())
                .sslProvider(sslClientProvider())
                // This test only works for non TLSv1.3 for now
                .protocols(SslProtocols.TLS_v1_2)
                .build());
        SSLEngine client = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

        serverSslCtx = wrapContext(param, SslContextBuilder
                .forServer(cert.certificate(), cert.privateKey())
                .sslContextProvider(serverSslContextProvider())
                .sslProvider(sslServerProvider())
                // This test only works for non TLSv1.3 for now
                .protocols(SslProtocols.TLS_v1_2)
                .build());
        SSLEngine server = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

        try {
            ByteBuffer plainClientOut = allocateBuffer(param.type(), client.getSession().getApplicationBufferSize());
            ByteBuffer plainServerOut = allocateBuffer(param.type(), server.getSession().getApplicationBufferSize());

            ByteBuffer encryptedClientToServer =
                    allocateBuffer(param.type(), client.getSession().getPacketBufferSize());
            ByteBuffer encryptedServerToClient =
                    allocateBuffer(param.type(), server.getSession().getPacketBufferSize());
            ByteBuffer empty = allocateBuffer(param.type(), 0);

            handshake(param.type(), param.delegate(), client, server);

            // This will produce a close_notify
            client.closeOutbound();

            // Something still pending in the outbound buffer.
            assertFalse(client.isOutboundDone());
            assertFalse(client.isInboundDone());

            // Now wrap and so drain the outbound buffer.
            SSLEngineResult result = client.wrap(empty, encryptedClientToServer);
            encryptedClientToServer.flip();

            assertEquals(SSLEngineResult.Status.CLOSED, result.getStatus());
            SSLEngineResult.HandshakeStatus hs = result.getHandshakeStatus();
            // Need an UNWRAP to read the response of the close_notify
            if (sslClientProvider() == SslProvider.JDK || Conscrypt.isEngineSupported(client)) {
                assertTrue(hs == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING
                        || hs == SSLEngineResult.HandshakeStatus.NEED_UNWRAP);
            } else {
                assertEquals(SSLEngineResult.HandshakeStatus.NEED_UNWRAP, hs);
            }

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

            // Ensure that calling wrap or unwrap again will not produce an SSLException
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

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testWrapAfterCloseOutbound(SSLEngineTestParam param) throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();

        clientSslCtx = wrapContext(param, SslContextBuilder
                .forClient()
                .trustManager(cert.cert())
                .sslProvider(sslClientProvider())
                .sslContextProvider(clientSslContextProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SSLEngine client = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

        serverSslCtx = wrapContext(param, SslContextBuilder
                .forServer(cert.certificate(), cert.privateKey())
                .sslProvider(sslServerProvider())
                .sslContextProvider(serverSslContextProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SSLEngine server = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

        try {
            ByteBuffer dst = allocateBuffer(param.type(), client.getSession().getPacketBufferSize());
            ByteBuffer src = allocateBuffer(param.type(), 1024);

            handshake(param.type(), param.delegate(), client, server);

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

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testMultipleRecordsInOneBufferWithNonZeroPosition(SSLEngineTestParam param) throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();

        clientSslCtx = wrapContext(param, SslContextBuilder
                .forClient()
                .trustManager(cert.cert())
                .sslContextProvider(clientSslContextProvider())
                .sslProvider(sslClientProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SSLEngine client = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

        serverSslCtx = wrapContext(param, SslContextBuilder
                .forServer(cert.certificate(), cert.privateKey())
                .sslContextProvider(serverSslContextProvider())
                .sslProvider(sslServerProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SSLEngine server = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

        try {
            // Choose buffer size small enough that we can put multiple buffers into one buffer and pass it into the
            // unwrap call without exceed MAX_ENCRYPTED_PACKET_LENGTH.
            ByteBuffer plainClientOut = allocateBuffer(param.type(), 1024);
            ByteBuffer plainServerOut = allocateBuffer(param.type(), server.getSession().getApplicationBufferSize());

            ByteBuffer encClientToServer = allocateBuffer(param.type(), client.getSession().getPacketBufferSize());

            int positionOffset = 1;
            // We need to be able to hold 2 records + positionOffset
            ByteBuffer combinedEncClientToServer = allocateBuffer(
                    param.type(), encClientToServer.capacity() * 2 + positionOffset);
            combinedEncClientToServer.position(positionOffset);

            handshake(param.type(), param.delegate(), client, server);

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

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testMultipleRecordsInOneBufferBiggerThenPacketBufferSize(SSLEngineTestParam param) throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();

        clientSslCtx = wrapContext(param, SslContextBuilder
                .forClient()
                .trustManager(cert.cert())
                .sslContextProvider(clientSslContextProvider())
                .sslProvider(sslClientProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SSLEngine client = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

        serverSslCtx = wrapContext(param, SslContextBuilder
                .forServer(cert.certificate(), cert.privateKey())
                .sslContextProvider(serverSslContextProvider())
                .sslProvider(sslServerProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SSLEngine server = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

        try {
            ByteBuffer plainClientOut = allocateBuffer(param.type(), 4096);
            ByteBuffer plainServerOut = allocateBuffer(param.type(), server.getSession().getApplicationBufferSize());

            ByteBuffer encClientToServer = allocateBuffer(param.type(), server.getSession().getPacketBufferSize() * 2);

            handshake(param.type(), param.delegate(), client, server);

            int srcLen = plainClientOut.remaining();
            SSLEngineResult result;

            int count = 0;
            do {
                int plainClientOutPosition = plainClientOut.position();
                int encClientToServerPosition = encClientToServer.position();
                result = client.wrap(plainClientOut, encClientToServer);
                if (result.getStatus() == Status.BUFFER_OVERFLOW) {
                    // We did not have enough room to wrap
                    assertEquals(plainClientOutPosition, plainClientOut.position());
                    assertEquals(encClientToServerPosition, encClientToServer.position());
                    break;
                }
                assertEquals(SSLEngineResult.Status.OK, result.getStatus());
                assertEquals(srcLen, result.bytesConsumed());
                assertTrue(result.bytesProduced() > 0);

                plainClientOut.clear();

                ++count;
            } while (encClientToServer.position() < server.getSession().getPacketBufferSize());

            // Check that we were able to wrap multiple times.
            assertTrue(count >= 2);
            encClientToServer.flip();

            result = server.unwrap(encClientToServer, plainServerOut);
            assertEquals(SSLEngineResult.Status.OK, result.getStatus());
            assertTrue(result.bytesConsumed() > 0);
            assertTrue(result.bytesProduced() > 0);
            assertTrue(encClientToServer.hasRemaining());
        } finally {
            cert.delete();
            cleanupClientSslEngine(client);
            cleanupServerSslEngine(server);
        }
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testBufferUnderFlow(SSLEngineTestParam param) throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();

        clientSslCtx = wrapContext(param, SslContextBuilder
                .forClient()
                .trustManager(cert.cert())
                .sslContextProvider(clientSslContextProvider())
                .sslProvider(sslClientProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SSLEngine client = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

        serverSslCtx = wrapContext(param, SslContextBuilder
                .forServer(cert.certificate(), cert.privateKey())
                .sslContextProvider(serverSslContextProvider())
                .sslProvider(sslServerProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SSLEngine server = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

        try {
            ByteBuffer plainClient = allocateBuffer(param.type(), 1024);
            plainClient.limit(plainClient.capacity());

            ByteBuffer encClientToServer = allocateBuffer(param.type(), client.getSession().getPacketBufferSize());
            ByteBuffer plainServer = allocateBuffer(param.type(), server.getSession().getApplicationBufferSize());

            handshake(param.type(), param.delegate(), client, server);

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

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testWrapDoesNotZeroOutSrc(SSLEngineTestParam param) throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();

        clientSslCtx = wrapContext(param, SslContextBuilder
                .forClient()
                .trustManager(cert.cert())
                .sslContextProvider(clientSslContextProvider())
                .sslProvider(sslClientProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SSLEngine client = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

        serverSslCtx = wrapContext(param, SslContextBuilder
                .forServer(cert.certificate(), cert.privateKey())
                .sslContextProvider(serverSslContextProvider())
                .sslProvider(sslServerProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SSLEngine server = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

        try {
            ByteBuffer plainServerOut =
                    allocateBuffer(param.type(), server.getSession().getApplicationBufferSize() / 2);

            handshake(param.type(), param.delegate(), client, server);

            // Fill the whole buffer and flip it.
            for (int i = 0; i < plainServerOut.capacity(); i++) {
                plainServerOut.put(i, (byte) i);
            }
            plainServerOut.position(plainServerOut.capacity());
            plainServerOut.flip();

            ByteBuffer encryptedServerToClient =
                    allocateBuffer(param.type(), server.getSession().getPacketBufferSize());
            SSLEngineResult result = server.wrap(plainServerOut, encryptedServerToClient);
            assertEquals(SSLEngineResult.Status.OK, result.getStatus());
            assertTrue(result.bytesConsumed() > 0);

            for (int i = 0; i < plainServerOut.capacity(); i++) {
                assertEquals((byte) i, plainServerOut.get(i));
            }
        } finally {
            cleanupClientSslEngine(client);
            cleanupServerSslEngine(server);
            cert.delete();
        }
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testDisableProtocols(SSLEngineTestParam param) throws Exception {
        testDisableProtocols(param, SslProtocols.SSL_v2, SslProtocols.SSL_v2);
        testDisableProtocols(param, SslProtocols.SSL_v3, SslProtocols.SSL_v2, SslProtocols.SSL_v3);
        testDisableProtocols(param, SslProtocols.TLS_v1, SslProtocols.SSL_v2, SslProtocols.SSL_v3, SslProtocols.TLS_v1);
        testDisableProtocols(param,
                SslProtocols.TLS_v1_1, SslProtocols.SSL_v2, SslProtocols.SSL_v3,
                SslProtocols.TLS_v1, SslProtocols.TLS_v1_1);
        testDisableProtocols(param, SslProtocols.TLS_v1_2, SslProtocols.SSL_v2,
                SslProtocols.SSL_v3, SslProtocols.TLS_v1, SslProtocols.TLS_v1_1, SslProtocols.TLS_v1_2);
    }

    private void testDisableProtocols(SSLEngineTestParam param,
                                      String protocol, String... disabledProtocols) throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();

        SslContext ctx = wrapContext(param, SslContextBuilder
                .forServer(cert.certificate(), cert.privateKey())
                .sslContextProvider(serverSslContextProvider())
                .sslProvider(sslServerProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SSLEngine server = wrapEngine(ctx.newEngine(UnpooledByteBufAllocator.DEFAULT));

        try {
            Set<String> supported = new HashSet<String>(Arrays.asList(server.getSupportedProtocols()));
            if (supported.contains(protocol)) {
                server.setEnabledProtocols(server.getSupportedProtocols());
                assertEquals(supported, new HashSet<String>(Arrays.asList(server.getSupportedProtocols())));

                for (String disabled : disabledProtocols) {
                    supported.remove(disabled);
                }
                if (supported.contains(SslProtocols.SSL_v2_HELLO) && supported.size() == 1) {
                    // It's not allowed to set only PROTOCOL_SSL_V2_HELLO if using JDK SSLEngine.
                    return;
                }
                server.setEnabledProtocols(supported.toArray(new String[0]));
                assertEquals(supported, new HashSet<String>(Arrays.asList(server.getEnabledProtocols())));
                server.setEnabledProtocols(server.getSupportedProtocols());
            }
        } finally {
            cleanupServerSslEngine(server);
            cleanupClientSslContext(ctx);
            cert.delete();
        }
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testUsingX509TrustManagerVerifiesHostname(SSLEngineTestParam param) throws Exception {
        if (clientSslContextProvider() != null) {
            // Not supported when using conscrypt
            return;
        }
        SelfSignedCertificate cert = new SelfSignedCertificate();
        clientSslCtx = wrapContext(param, SslContextBuilder
                .forClient()
                .trustManager(new TrustManagerFactory(new TrustManagerFactorySpi() {
                    @Override
                    protected void engineInit(KeyStore keyStore) {
                        // NOOP
                    }
                    @Override
                    protected TrustManager[] engineGetTrustManagers() {
                        // Provide a custom trust manager, this manager trust all certificates
                        return new TrustManager[] {
                                new X509TrustManager() {
                                    @Override
                                    public void checkClientTrusted(
                                            java.security.cert.X509Certificate[] x509Certificates, String s) {
                                        // NOOP
                                    }

                                    @Override
                                    public void checkServerTrusted(
                                            java.security.cert.X509Certificate[] x509Certificates, String s) {
                                        // NOOP
                                    }

                                    @Override
                                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                                        return EmptyArrays.EMPTY_X509_CERTIFICATES;
                                    }
                                }
                        };
                    }

                    @Override
                    protected void engineInit(ManagerFactoryParameters managerFactoryParameters) {
                    }
                }, null, TrustManagerFactory.getDefaultAlgorithm()) {
                })
                .sslContextProvider(clientSslContextProvider())
                .sslProvider(sslClientProvider())
                .build());

        SSLEngine client = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT, "netty.io", 1234));
        SSLParameters sslParameters = client.getSSLParameters();
        sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
        client.setSSLParameters(sslParameters);

        serverSslCtx = wrapContext(param, SslContextBuilder
                .forServer(cert.certificate(), cert.privateKey())
                .sslContextProvider(serverSslContextProvider())
                .sslProvider(sslServerProvider())
                .build());

        SSLEngine server = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
        try {
            handshake(param.type(), param.delegate(), client, server);
            fail();
        } catch (SSLException expected) {
            // expected as the hostname not matches.
        } finally {
            cleanupClientSslEngine(client);
            cleanupServerSslEngine(server);
            cert.delete();
        }
    }

    @Test
    public void testInvalidCipher() throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();
        List<String> cipherList = new ArrayList<String>();
        Collections.addAll(cipherList, ((SSLSocketFactory) SSLSocketFactory.getDefault()).getDefaultCipherSuites());
        cipherList.add("InvalidCipher");
        SSLEngine server = null;
        try {
            serverSslCtx = wrapContext(null, SslContextBuilder.forServer(cert.key(), cert.cert())
                    .sslContextProvider(serverSslContextProvider())
                    .sslProvider(sslServerProvider())
                    .ciphers(cipherList).build());
            server = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            fail();
        } catch (IllegalArgumentException expected) {
            // expected when invalid cipher is used.
        } catch (SSLException expected) {
            // expected when invalid cipher is used.
        } finally {
            cert.delete();
            cleanupServerSslEngine(server);
        }
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testGetCiphersuite(SSLEngineTestParam param) throws Exception {
        clientSslCtx = wrapContext(param, SslContextBuilder.forClient()
                                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                        .sslProvider(sslClientProvider())
                                        .sslContextProvider(clientSslContextProvider())
                                        .protocols(param.protocols())
                                        .ciphers(param.ciphers())
                                        .build());
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = wrapContext(param, SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                                        .sslProvider(sslServerProvider())
                                        .sslContextProvider(serverSslContextProvider())
                                        .protocols(param.protocols())
                                        .ciphers(param.ciphers())
                                        .build());
        SSLEngine clientEngine = null;
        SSLEngine serverEngine = null;
        try {
            clientEngine = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            serverEngine = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            handshake(param.type(), param.delegate(), clientEngine, serverEngine);

            String clientCipher = clientEngine.getSession().getCipherSuite();
            String serverCipher = serverEngine.getSession().getCipherSuite();
            assertEquals(clientCipher, serverCipher);

            assertEquals(param.protocolCipherCombo.cipher, clientCipher);
        } finally {
            cleanupClientSslEngine(clientEngine);
            cleanupServerSslEngine(serverEngine);
            ssc.delete();
        }
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testSessionCache(SSLEngineTestParam param) throws Exception {
        clientSslCtx = wrapContext(param, SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(sslClientProvider())
                .sslContextProvider(clientSslContextProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = wrapContext(param, SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(sslServerProvider())
                .sslContextProvider(serverSslContextProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());

        try {
            doHandshakeVerifyReusedAndClose(param, "a.netty.io", 9999, false);
            doHandshakeVerifyReusedAndClose(param, "a.netty.io", 9999, true);
            doHandshakeVerifyReusedAndClose(param, "b.netty.io", 9999, false);
            invalidateSessionsAndAssert(serverSslCtx.sessionContext());
            invalidateSessionsAndAssert(clientSslCtx.sessionContext());
        } finally {
            ssc.delete();
        }
    }

    protected void invalidateSessionsAndAssert(SSLSessionContext context) {
        Enumeration<byte[]> ids = context.getIds();
        while (ids.hasMoreElements()) {
            byte[] id = ids.nextElement();
            SSLSession session = context.getSession(id);
            if (session != null) {
                session.invalidate();
                assertFalse(session.isValid());
                assertNull(context.getSession(id));
            }
        }
    }

    private static void assertSessionCache(SSLSessionContext sessionContext, int numSessions) {
        Enumeration<byte[]> ids = sessionContext.getIds();
        int numIds = 0;
        while (ids.hasMoreElements()) {
            numIds++;
            byte[] id = ids.nextElement();
            assertNotEquals(0, id.length);
            SSLSession session = sessionContext.getSession(id);
            assertArrayEquals(id, session.getId());
        }
        assertEquals(numSessions, numIds);
    }

    private void doHandshakeVerifyReusedAndClose(SSLEngineTestParam param, String host, int port, boolean reuse)
            throws Exception {
        SSLEngine clientEngine = null;
        SSLEngine serverEngine = null;
        try {
            clientEngine = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT, host, port));
            serverEngine = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            handshake(param.type(), param.delegate(), clientEngine, serverEngine);
            int clientSessions = currentSessionCacheSize(clientSslCtx.sessionContext());
            int serverSessions = currentSessionCacheSize(serverSslCtx.sessionContext());
            int nCSessions = clientSessions;
            int nSSessions = serverSessions;
            boolean clientSessionReused = false;
            boolean serverSessionReused = false;
            if (param.protocolCipherCombo == ProtocolCipherCombo.TLSV13) {
                // Allocate something which is big enough for sure
                ByteBuffer packetBuffer = allocateBuffer(param.type(), 32 * 1024);
                ByteBuffer appBuffer = allocateBuffer(param.type(), 32 * 1024);

                appBuffer.clear().position(4).flip();
                packetBuffer.clear();

                do {
                    SSLEngineResult result;

                    do {
                        result = serverEngine.wrap(appBuffer, packetBuffer);
                    } while (appBuffer.hasRemaining() || result.bytesProduced() > 0);

                    appBuffer.clear();
                    packetBuffer.flip();
                    do {
                        result = clientEngine.unwrap(packetBuffer, appBuffer);
                    } while (packetBuffer.hasRemaining() || result.bytesProduced() > 0);

                    packetBuffer.clear();
                    appBuffer.clear().position(4).flip();

                    do {
                        result = clientEngine.wrap(appBuffer, packetBuffer);
                    } while (appBuffer.hasRemaining() || result.bytesProduced() > 0);

                    appBuffer.clear();
                    packetBuffer.flip();

                    do {
                        result = serverEngine.unwrap(packetBuffer, appBuffer);
                    } while (packetBuffer.hasRemaining() || result.bytesProduced() > 0);

                    packetBuffer.clear();
                    appBuffer.clear().position(4).flip();
                    nCSessions = currentSessionCacheSize(clientSslCtx.sessionContext());
                    nSSessions = currentSessionCacheSize(serverSslCtx.sessionContext());
                    clientSessionReused = isSessionMaybeReused(clientEngine);
                    serverSessionReused = isSessionMaybeReused(serverEngine);
                } while ((reuse && (!clientSessionReused || !serverSessionReused))
                        || (!reuse && (nCSessions < clientSessions ||
                        // server may use multiple sessions
                        nSSessions < serverSessions)));
            }

            assertSessionReusedForEngine(clientEngine, serverEngine, reuse);

            closeOutboundAndInbound(param.type(), clientEngine, serverEngine);
        } finally {
            cleanupClientSslEngine(clientEngine);
            cleanupServerSslEngine(serverEngine);
        }
    }

    protected boolean isSessionMaybeReused(SSLEngine engine) {
        return true;
    }

    private static int currentSessionCacheSize(SSLSessionContext ctx) {
        Enumeration<byte[]> ids = ctx.getIds();
        int i = 0;
        while (ids.hasMoreElements()) {
            i++;
            ids.nextElement();
        }
        return i;
    }

    private void closeOutboundAndInbound(
            BufferType type, SSLEngine clientEngine, SSLEngine serverEngine) throws SSLException {
        assertFalse(clientEngine.isInboundDone());
        assertFalse(clientEngine.isOutboundDone());
        assertFalse(serverEngine.isInboundDone());
        assertFalse(serverEngine.isOutboundDone());

        ByteBuffer empty = allocateBuffer(type, 0);

        // Ensure we allocate a bit more so we can fit in multiple packets. This is needed as we may call multiple
        // time wrap / unwrap in a for loop before we drain the buffer we are writing in.
        ByteBuffer cTOs = allocateBuffer(type, clientEngine.getSession().getPacketBufferSize() * 4);
        ByteBuffer sTOs = allocateBuffer(type, serverEngine.getSession().getPacketBufferSize() * 4);
        ByteBuffer cApps = allocateBuffer(type, clientEngine.getSession().getApplicationBufferSize() * 4);
        ByteBuffer sApps = allocateBuffer(type, serverEngine.getSession().getApplicationBufferSize() * 4);

        clientEngine.closeOutbound();
        for (;;) {
            // call wrap till we produced all data
            SSLEngineResult result = clientEngine.wrap(empty, cTOs);
            if (result.getStatus() == Status.CLOSED && result.bytesProduced() == 0) {
                break;
            }
            assertTrue(cTOs.hasRemaining());
        }
        cTOs.flip();

        for (;;) {
            // call unwrap till we consumed all data
            SSLEngineResult result = serverEngine.unwrap(cTOs, sApps);
            if (result.getStatus() == Status.CLOSED && result.bytesProduced() == 0) {
                break;
            }
            assertTrue(sApps.hasRemaining());
        }

        serverEngine.closeOutbound();
        for (;;) {
            // call wrap till we produced all data
            SSLEngineResult result = serverEngine.wrap(empty, sTOs);
            if (result.getStatus() == Status.CLOSED && result.bytesProduced() == 0) {
                break;
            }
            assertTrue(sTOs.hasRemaining());
        }
        sTOs.flip();

        for (;;) {
            // call unwrap till we consumed all data
            SSLEngineResult result = clientEngine.unwrap(sTOs, cApps);
            if (result.getStatus() == Status.CLOSED && result.bytesProduced() == 0) {
                break;
            }
            assertTrue(cApps.hasRemaining());
        }

        // Now close the inbound as well
        clientEngine.closeInbound();
        serverEngine.closeInbound();
    }

    protected void assertSessionReusedForEngine(SSLEngine clientEngine, SSLEngine serverEngine, boolean reuse) {
        // NOOP
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testSessionCacheTimeout(SSLEngineTestParam param) throws Exception {
        clientSslCtx = wrapContext(param, SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(sslClientProvider())
                .sslContextProvider(clientSslContextProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .sessionTimeout(1)
                .build());
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = wrapContext(param, SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(sslServerProvider())
                .sslContextProvider(serverSslContextProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .sessionTimeout(1)
                .build());

        try {
            doHandshakeVerifyReusedAndClose(param, "a.netty.io", 9999, false);

            // Let's sleep for a bit more then 1 second so the cache should timeout the sessions.
            Thread.sleep(1500);

            assertSessionCache(serverSslCtx.sessionContext(), 0);
            assertSessionCache(clientSslCtx.sessionContext(), 0);
        } finally {
            ssc.delete();
        }
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testSessionCacheSize(SSLEngineTestParam param) throws Exception {
        clientSslCtx = wrapContext(param, SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(sslClientProvider())
                .sslContextProvider(clientSslContextProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .sessionCacheSize(1)
                .build());
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = wrapContext(param, SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(sslServerProvider())
                .sslContextProvider(serverSslContextProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());

        try {
            doHandshakeVerifyReusedAndClose(param, "a.netty.io", 9999, false);
            // As we have a cache size of 1 we should never have more then one session in the cache
            doHandshakeVerifyReusedAndClose(param, "b.netty.io", 9999, false);

            // We should at least reuse b.netty.io
            doHandshakeVerifyReusedAndClose(param, "b.netty.io", 9999, true);
        } finally {
            ssc.delete();
        }
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testSessionBindingEvent(SSLEngineTestParam param) throws Exception {
        clientSslCtx = wrapContext(param, SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(sslClientProvider())
                .sslContextProvider(clientSslContextProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = wrapContext(param, SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(sslServerProvider())
                .sslContextProvider(serverSslContextProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SSLEngine clientEngine = null;
        SSLEngine serverEngine = null;
        try {
            clientEngine = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            serverEngine = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            handshake(param.type(), param.delegate(), clientEngine, serverEngine);
            SSLSession session = clientEngine.getSession();
            assertEquals(0, session.getValueNames().length);

            class SSLSessionBindingEventValue implements SSLSessionBindingListener {
                SSLSessionBindingEvent boundEvent;
                SSLSessionBindingEvent unboundEvent;

                @Override
                public void valueBound(SSLSessionBindingEvent sslSessionBindingEvent) {
                    assertNull(boundEvent);
                    boundEvent = sslSessionBindingEvent;
                }

                @Override
                public void valueUnbound(SSLSessionBindingEvent sslSessionBindingEvent) {
                    assertNull(unboundEvent);
                    unboundEvent = sslSessionBindingEvent;
                }
            }

            String name = "name";
            String name2 = "name2";

            SSLSessionBindingEventValue value1 = new SSLSessionBindingEventValue();
            session.putValue(name, value1);
            assertSSLSessionBindingEventValue(name, session, value1.boundEvent);
            assertNull(value1.unboundEvent);
            assertEquals(1, session.getValueNames().length);

            session.putValue(name2, "value");

            SSLSessionBindingEventValue value2 = new SSLSessionBindingEventValue();
            session.putValue(name, value2);
            assertEquals(2, session.getValueNames().length);

            assertSSLSessionBindingEventValue(name, session, value1.unboundEvent);
            assertSSLSessionBindingEventValue(name, session, value2.boundEvent);
            assertNull(value2.unboundEvent);
            assertEquals(2, session.getValueNames().length);

            session.removeValue(name);
            assertSSLSessionBindingEventValue(name, session, value2.unboundEvent);
            assertEquals(1, session.getValueNames().length);
            session.removeValue(name2);
        } finally {
            cleanupClientSslEngine(clientEngine);
            cleanupServerSslEngine(serverEngine);
            ssc.delete();
        }
    }

    private static void assertSSLSessionBindingEventValue(
            String name, SSLSession session, SSLSessionBindingEvent event) {
        assertEquals(name, event.getName());
        assertEquals(session, event.getSession());
        assertEquals(session, event.getSource());
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testSessionAfterHandshake(SSLEngineTestParam param) throws Exception {
        testSessionAfterHandshake0(param, false, false);
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testSessionAfterHandshakeMutualAuth(SSLEngineTestParam param) throws Exception {
        testSessionAfterHandshake0(param, false, true);
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testSessionAfterHandshakeKeyManagerFactory(SSLEngineTestParam param) throws Exception {
        testSessionAfterHandshake0(param, true, false);
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testSessionAfterHandshakeKeyManagerFactoryMutualAuth(SSLEngineTestParam param) throws Exception {
        testSessionAfterHandshake0(param, true, true);
    }

    private void testSessionAfterHandshake0(
            SSLEngineTestParam param, boolean useKeyManagerFactory, boolean mutualAuth) throws Exception {
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        KeyManagerFactory kmf = useKeyManagerFactory ?
                SslContext.buildKeyManagerFactory(
                        new java.security.cert.X509Certificate[] { ssc.cert()}, null,
                        ssc.key(), null, null, null) : null;

        SslContextBuilder clientContextBuilder = SslContextBuilder.forClient();
        if (mutualAuth) {
            if (kmf != null) {
                clientContextBuilder.keyManager(kmf);
            } else {
                clientContextBuilder.keyManager(ssc.key(), ssc.cert());
            }
        }
        clientSslCtx = wrapContext(param, clientContextBuilder
                                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                        .sslProvider(sslClientProvider())
                                        .sslContextProvider(clientSslContextProvider())
                                        .protocols(param.protocols())
                                        .ciphers(param.ciphers())
                                        .build());

        SslContextBuilder serverContextBuilder = kmf != null ?
                SslContextBuilder.forServer(kmf) :
                SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey());
        if (mutualAuth) {
            serverContextBuilder.clientAuth(ClientAuth.REQUIRE);
        }
        serverSslCtx = wrapContext(param, serverContextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE)
                                     .sslProvider(sslServerProvider())
                                     .sslContextProvider(serverSslContextProvider())
                                     .protocols(param.protocols())
                                     .ciphers(param.ciphers())
                                     .build());
        SSLEngine clientEngine = null;
        SSLEngine serverEngine = null;
        try {
            clientEngine = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            serverEngine = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

            handshake(param.type(), param.delegate(), clientEngine, serverEngine);

            SSLSession clientSession = clientEngine.getSession();
            SSLSession serverSession = serverEngine.getSession();

            assertNull(clientSession.getPeerHost());
            assertNull(serverSession.getPeerHost());
            assertEquals(-1, clientSession.getPeerPort());
            assertEquals(-1, serverSession.getPeerPort());

            assertTrue(clientSession.getCreationTime() > 0);
            assertTrue(serverSession.getCreationTime() > 0);

            assertTrue(clientSession.getLastAccessedTime() > 0);
            assertTrue(serverSession.getLastAccessedTime() > 0);

            assertEquals(param.combo().protocol, clientSession.getProtocol());
            assertEquals(param.combo().protocol, serverSession.getProtocol());

            assertEquals(param.combo().cipher, clientSession.getCipherSuite());
            assertEquals(param.combo().cipher, serverSession.getCipherSuite());

            assertNotNull(clientSession.getId());
            assertNotNull(serverSession.getId());

            assertTrue(clientSession.getApplicationBufferSize() > 0);
            assertTrue(serverSession.getApplicationBufferSize() > 0);

            assertTrue(clientSession.getPacketBufferSize() > 0);
            assertTrue(serverSession.getPacketBufferSize() > 0);

            assertNotNull(clientSession.getSessionContext());

            // Workaround for JDK 14 regression.
            // See https://bugs.openjdk.java.net/browse/JDK-8242008
            if (PlatformDependent.javaVersion() != 14) {
                assertNotNull(serverSession.getSessionContext());
            }

            Object value = new Object();

            assertEquals(0, clientSession.getValueNames().length);
            clientSession.putValue("test", value);
            assertEquals("test", clientSession.getValueNames()[0]);
            assertSame(value, clientSession.getValue("test"));
            clientSession.removeValue("test");
            assertEquals(0, clientSession.getValueNames().length);

            assertEquals(0, serverSession.getValueNames().length);
            serverSession.putValue("test", value);
            assertEquals("test", serverSession.getValueNames()[0]);
            assertSame(value, serverSession.getValue("test"));
            serverSession.removeValue("test");
            assertEquals(0, serverSession.getValueNames().length);

            Certificate[] serverLocalCertificates = serverSession.getLocalCertificates();
            assertEquals(1, serverLocalCertificates.length);
            assertArrayEquals(ssc.cert().getEncoded(), serverLocalCertificates[0].getEncoded());

            Principal serverLocalPrincipal = serverSession.getLocalPrincipal();
            assertNotNull(serverLocalPrincipal);

            if (mutualAuth) {
                Certificate[] clientLocalCertificates = clientSession.getLocalCertificates();
                assertEquals(1, clientLocalCertificates.length);

                Certificate[] serverPeerCertificates = serverSession.getPeerCertificates();
                assertEquals(1, serverPeerCertificates.length);
                assertArrayEquals(clientLocalCertificates[0].getEncoded(), serverPeerCertificates[0].getEncoded());

                try {
                    X509Certificate[] serverPeerX509Certificates = serverSession.getPeerCertificateChain();
                    assertEquals(1, serverPeerX509Certificates.length);
                    assertArrayEquals(clientLocalCertificates[0].getEncoded(),
                            serverPeerX509Certificates[0].getEncoded());
                } catch (UnsupportedOperationException e) {
                    // See https://bugs.openjdk.java.net/browse/JDK-8241039
                    assertTrue(PlatformDependent.javaVersion() >= 15);
                }

                Principal clientLocalPrincipial = clientSession.getLocalPrincipal();
                assertNotNull(clientLocalPrincipial);

                Principal serverPeerPrincipal = serverSession.getPeerPrincipal();
                assertEquals(clientLocalPrincipial, serverPeerPrincipal);
            } else {
                assertNull(clientSession.getLocalCertificates());
                assertNull(clientSession.getLocalPrincipal());

                try {
                    serverSession.getPeerCertificates();
                    fail();
                } catch (SSLPeerUnverifiedException expected) {
                    // As we did not use mutual auth this is expected
                }

                try {
                    serverSession.getPeerCertificateChain();
                    fail();
                } catch (SSLPeerUnverifiedException expected) {
                    // As we did not use mutual auth this is expected
                } catch (UnsupportedOperationException e) {
                    // See https://bugs.openjdk.java.net/browse/JDK-8241039
                    assertTrue(PlatformDependent.javaVersion() >= 15);
                }

                try {
                    serverSession.getPeerPrincipal();
                    fail();
                } catch (SSLPeerUnverifiedException expected) {
                    // As we did not use mutual auth this is expected
                }
            }

            Certificate[] clientPeerCertificates = clientSession.getPeerCertificates();
            assertEquals(1, clientPeerCertificates.length);
            assertArrayEquals(serverLocalCertificates[0].getEncoded(), clientPeerCertificates[0].getEncoded());

            try {
                X509Certificate[] clientPeerX509Certificates = clientSession.getPeerCertificateChain();
                assertEquals(1, clientPeerX509Certificates.length);
                assertArrayEquals(serverLocalCertificates[0].getEncoded(), clientPeerX509Certificates[0].getEncoded());
            } catch (UnsupportedOperationException e) {
                // See https://bugs.openjdk.java.net/browse/JDK-8241039
                assertTrue(PlatformDependent.javaVersion() >= 15);
            }
            Principal clientPeerPrincipal = clientSession.getPeerPrincipal();
            assertEquals(serverLocalPrincipal, clientPeerPrincipal);
        } finally {
            cleanupClientSslEngine(clientEngine);
            cleanupServerSslEngine(serverEngine);
            ssc.delete();
        }
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testSupportedSignatureAlgorithms(SSLEngineTestParam param) throws Exception {
        final SelfSignedCertificate ssc = new SelfSignedCertificate();

        final class TestKeyManagerFactory extends KeyManagerFactory {
            TestKeyManagerFactory(final KeyManagerFactory factory) {
                super(new KeyManagerFactorySpi() {

                    private final KeyManager[] managers = factory.getKeyManagers();

                    @Override
                    protected void engineInit(KeyStore keyStore, char[] chars)  {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    protected void engineInit(ManagerFactoryParameters managerFactoryParameters) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    protected KeyManager[] engineGetKeyManagers() {
                        KeyManager[] array = new KeyManager[managers.length];

                        for (int i = 0 ; i < array.length; i++) {
                            final X509ExtendedKeyManager x509ExtendedKeyManager = (X509ExtendedKeyManager) managers[i];

                            array[i] = new X509ExtendedKeyManager() {
                                @Override
                                public String[] getClientAliases(String s, Principal[] principals) {
                                    fail();
                                    return null;
                                }

                                @Override
                                public String chooseClientAlias(
                                        String[] strings, Principal[] principals, Socket socket) {
                                    fail();
                                    return null;
                                }

                                @Override
                                public String[] getServerAliases(String s, Principal[] principals) {
                                    fail();
                                    return null;
                                }

                                @Override
                                public String chooseServerAlias(String s, Principal[] principals, Socket socket) {
                                    fail();
                                    return null;
                                }

                                @Override
                                public String chooseEngineClientAlias(
                                        String[] strings, Principal[] principals, SSLEngine sslEngine) {
                                    assertNotEquals(0, ((ExtendedSSLSession) sslEngine.getHandshakeSession())
                                            .getPeerSupportedSignatureAlgorithms().length);
                                    assertNotEquals(0, ((ExtendedSSLSession) sslEngine.getHandshakeSession())
                                            .getLocalSupportedSignatureAlgorithms().length);
                                    return x509ExtendedKeyManager.chooseEngineClientAlias(
                                            strings, principals, sslEngine);
                                }

                                @Override
                                public String chooseEngineServerAlias(
                                        String s, Principal[] principals, SSLEngine sslEngine) {
                                    assertNotEquals(0, ((ExtendedSSLSession) sslEngine.getHandshakeSession())
                                            .getPeerSupportedSignatureAlgorithms().length);
                                    assertNotEquals(0, ((ExtendedSSLSession) sslEngine.getHandshakeSession())
                                            .getLocalSupportedSignatureAlgorithms().length);
                                    return x509ExtendedKeyManager.chooseEngineServerAlias(s, principals, sslEngine);
                                }

                                @Override
                                public java.security.cert.X509Certificate[] getCertificateChain(String s) {
                                    return x509ExtendedKeyManager.getCertificateChain(s);
                                }

                                @Override
                                public PrivateKey getPrivateKey(String s) {
                                    return x509ExtendedKeyManager.getPrivateKey(s);
                                }
                            };
                        }
                        return array;
                    }
                }, factory.getProvider(), factory.getAlgorithm());
            }
        }

        clientSslCtx = wrapContext(param, SslContextBuilder.forClient()
                .keyManager(new TestKeyManagerFactory(newKeyManagerFactory(ssc)))
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(sslClientProvider())
                .sslContextProvider(clientSslContextProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());

        serverSslCtx = wrapContext(param, SslContextBuilder.forServer(
                new TestKeyManagerFactory(newKeyManagerFactory(ssc)))
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslContextProvider(serverSslContextProvider())
                .sslProvider(sslServerProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .clientAuth(ClientAuth.REQUIRE)
                .build());
        SSLEngine clientEngine = null;
        SSLEngine serverEngine = null;
        try {
            clientEngine = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            serverEngine = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            handshake(param.type(), param.delegate(), clientEngine, serverEngine);
        } finally {
            cleanupClientSslEngine(clientEngine);
            cleanupServerSslEngine(serverEngine);
            ssc.delete();
        }
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testHandshakeSession(SSLEngineTestParam param) throws Exception {
        final SelfSignedCertificate ssc = new SelfSignedCertificate();

        final TestTrustManagerFactory clientTmf = new TestTrustManagerFactory(ssc.cert());
        final TestTrustManagerFactory serverTmf = new TestTrustManagerFactory(ssc.cert());

        clientSslCtx = wrapContext(param, SslContextBuilder.forClient()
                .trustManager(new SimpleTrustManagerFactory() {
                    @Override
                    protected void engineInit(KeyStore keyStore) {
                        // NOOP
                    }

                    @Override
                    protected void engineInit(ManagerFactoryParameters managerFactoryParameters) {
                        // NOOP
                    }

                    @Override
                    protected TrustManager[] engineGetTrustManagers() {
                        return new TrustManager[] { clientTmf };
                    }
                })
                .keyManager(newKeyManagerFactory(ssc))
                .sslProvider(sslClientProvider())
                .sslContextProvider(clientSslContextProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        serverSslCtx = wrapContext(param, SslContextBuilder.forServer(newKeyManagerFactory(ssc))
                .trustManager(new SimpleTrustManagerFactory() {
                    @Override
                    protected void engineInit(KeyStore keyStore) {
                        // NOOP
                    }

                    @Override
                    protected void engineInit(ManagerFactoryParameters managerFactoryParameters) {
                        // NOOP
                    }

                    @Override
                    protected TrustManager[] engineGetTrustManagers() {
                        return new TrustManager[] { serverTmf };
                    }
                })
                .sslProvider(sslServerProvider())
                .sslContextProvider(serverSslContextProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .clientAuth(ClientAuth.REQUIRE)
                .build());
        SSLEngine clientEngine = null;
        SSLEngine serverEngine = null;
        try {
            clientEngine = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            serverEngine = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            handshake(param.type(), param.delegate(), clientEngine, serverEngine);

            assertTrue(clientTmf.isVerified());
            assertTrue(serverTmf.isVerified());
        } finally {
            cleanupClientSslEngine(clientEngine);
            cleanupServerSslEngine(serverEngine);
            ssc.delete();
        }
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testSessionLocalWhenNonMutualWithKeyManager(SSLEngineTestParam param) throws Exception {
        testSessionLocalWhenNonMutual(param, true);
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testSessionLocalWhenNonMutualWithoutKeyManager(SSLEngineTestParam param) throws Exception {
        testSessionLocalWhenNonMutual(param, false);
    }

    private void testSessionLocalWhenNonMutual(SSLEngineTestParam param, boolean useKeyManager) throws Exception {
        final SelfSignedCertificate ssc = new SelfSignedCertificate();

        SslContextBuilder clientSslCtxBuilder = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(sslClientProvider())
                .sslContextProvider(clientSslContextProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers());

        if (useKeyManager) {
            clientSslCtxBuilder.keyManager(newKeyManagerFactory(ssc));
        } else {
            clientSslCtxBuilder.keyManager(ssc.certificate(), ssc.privateKey());
        }
        clientSslCtx = wrapContext(param, clientSslCtxBuilder.build());

        final SslContextBuilder serverSslCtxBuilder;
        if (useKeyManager) {
            serverSslCtxBuilder = SslContextBuilder.forServer(newKeyManagerFactory(ssc));
        } else {
            serverSslCtxBuilder = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey());
        }
        serverSslCtxBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(sslServerProvider())
                .sslContextProvider(serverSslContextProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .clientAuth(ClientAuth.NONE);

        serverSslCtx = wrapContext(param, serverSslCtxBuilder.build());
        SSLEngine clientEngine = null;
        SSLEngine serverEngine = null;
        try {
            clientEngine = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            serverEngine = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            handshake(param.type(), param.delegate(), clientEngine, serverEngine);

            SSLSession clientSession = clientEngine.getSession();
            assertNull(clientSession.getLocalCertificates());
            assertNull(clientSession.getLocalPrincipal());

            SSLSession serverSession = serverEngine.getSession();
            assertNotNull(serverSession.getLocalCertificates());
            assertNotNull(serverSession.getLocalPrincipal());
        } finally {
            cleanupClientSslEngine(clientEngine);
            cleanupServerSslEngine(serverEngine);
            ssc.delete();
        }
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testEnabledProtocolsAndCiphers(SSLEngineTestParam param) throws Exception {
        clientSslCtx = wrapContext(param, SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(sslClientProvider())
                .sslContextProvider(clientSslContextProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = wrapContext(param, SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(sslServerProvider())
                .sslContextProvider(serverSslContextProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SSLEngine clientEngine = null;
        SSLEngine serverEngine = null;
        try {
            clientEngine = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            serverEngine = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            handshake(param.type(), param.delegate(), clientEngine, serverEngine);
            assertEnabledProtocolsAndCipherSuites(clientEngine);
            assertEnabledProtocolsAndCipherSuites(serverEngine);
        } finally {
            cleanupClientSslEngine(clientEngine);
            cleanupServerSslEngine(serverEngine);
            ssc.delete();
        }
    }

    private static void assertEnabledProtocolsAndCipherSuites(SSLEngine engine) {
        String protocol = engine.getSession().getProtocol();
        String cipherSuite = engine.getSession().getCipherSuite();

        assertArrayContains(protocol, engine.getEnabledProtocols());
        assertArrayContains(cipherSuite, engine.getEnabledCipherSuites());
    }

    private static void assertArrayContains(String expected, String[] array) {
        for (String value: array) {
            if (expected.equals(value)) {
                return;
            }
        }
        fail("Array did not contain '" + expected + "':" + Arrays.toString(array));
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testMasterKeyLogging(final SSLEngineTestParam param) throws Exception {
        if (param.combo() != ProtocolCipherCombo.tlsv12()) {
            return;
        }
        /*
         * At the moment master key logging is not supported for conscrypt
         */
        assumeFalse(serverSslContextProvider() instanceof OpenSSLProvider);

        /*
         * The JDK SSL engine master key retrieval relies on being able to set field access to true.
         * That is not available in JDK9+
         */
        assumeFalse(sslServerProvider() == SslProvider.JDK && PlatformDependent.javaVersion() > 8);

        String originalSystemPropertyValue = SystemPropertyUtil.get(SslMasterKeyHandler.SYSTEM_PROP_KEY);
        System.setProperty(SslMasterKeyHandler.SYSTEM_PROP_KEY, Boolean.TRUE.toString());

        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = wrapContext(param, SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(sslServerProvider())
                .sslContextProvider(serverSslContextProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        Socket socket = null;

        try {
            sb = new ServerBootstrap();
            sb.group(new NioEventLoopGroup(), new NioEventLoopGroup());
            sb.channel(NioServerSocketChannel.class);

            final Promise<SecretKey> promise = sb.config().group().next().newPromise();
            serverChannel = sb.childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.config().setAllocator(new TestByteBufAllocator(ch.config().getAllocator(), param.type()));

                    SslHandler sslHandler = !param.delegate() ?
                            serverSslCtx.newHandler(ch.alloc()) :
                            serverSslCtx.newHandler(ch.alloc(), delegatingExecutor);

                    ch.pipeline().addLast(sslHandler);
                    ch.pipeline().addLast(new SslMasterKeyHandler() {
                        @Override
                        protected void accept(SecretKey masterKey, SSLSession session) {
                            promise.setSuccess(masterKey);
                        }
                    });
                    serverConnectedChannel = ch;
                }
            }).bind(new InetSocketAddress(0)).sync().channel();

            int port = ((InetSocketAddress) serverChannel.localAddress()).getPort();

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, InsecureTrustManagerFactory.INSTANCE.getTrustManagers(), null);
            socket = sslContext.getSocketFactory().createSocket(NetUtil.LOCALHOST, port);
            OutputStream out = socket.getOutputStream();
            out.write(1);
            out.flush();

            assertTrue(promise.await(10, TimeUnit.SECONDS));
            SecretKey key = promise.get();
            assertEquals(48, key.getEncoded().length, "AES secret key must be 48 bytes");
        } finally {
            closeQuietly(socket);
            if (originalSystemPropertyValue != null) {
                System.setProperty(SslMasterKeyHandler.SYSTEM_PROP_KEY, originalSystemPropertyValue);
            } else {
                System.clearProperty(SslMasterKeyHandler.SYSTEM_PROP_KEY);
            }
            ssc.delete();
        }
    }

    private static void closeQuietly(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException ignore) {
                // ignore
            }
        }
    }

    private static KeyManagerFactory newKeyManagerFactory(SelfSignedCertificate ssc)
            throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException,
            CertificateException, IOException {
        return SslContext.buildKeyManagerFactory(
                new java.security.cert.X509Certificate[] { ssc.cert() }, null, ssc.key(), null, null, null);
    }

    private static final class TestTrustManagerFactory extends X509ExtendedTrustManager {
        private final Certificate localCert;
        private volatile boolean verified;

        TestTrustManagerFactory(Certificate localCert) {
            this.localCert = localCert;
        }

        boolean isVerified() {
            return verified;
        }

        @Override
        public void checkClientTrusted(
                java.security.cert.X509Certificate[] x509Certificates, String s, Socket socket) {
            fail();
        }

        @Override
        public void checkServerTrusted(
                java.security.cert.X509Certificate[] x509Certificates, String s, Socket socket) {
            fail();
        }

        @Override
        public void checkClientTrusted(
                java.security.cert.X509Certificate[] x509Certificates, String s, SSLEngine sslEngine) {
            verified = true;
            assertFalse(sslEngine.getUseClientMode());
            SSLSession session = sslEngine.getHandshakeSession();
            assertNotNull(session);
            Certificate[] localCertificates = session.getLocalCertificates();
            assertNotNull(localCertificates);
            assertEquals(1, localCertificates.length);
            assertEquals(localCert, localCertificates[0]);
            assertNotNull(session.getLocalPrincipal());
        }

        @Override
        public void checkServerTrusted(
                java.security.cert.X509Certificate[] x509Certificates, String s, SSLEngine sslEngine) {
            verified = true;
            assertTrue(sslEngine.getUseClientMode());
            SSLSession session = sslEngine.getHandshakeSession();
            assertNotNull(session);
            assertNull(session.getLocalCertificates());
            assertNull(session.getLocalPrincipal());
        }

        @Override
        public void checkClientTrusted(
                java.security.cert.X509Certificate[] x509Certificates, String s) {
            fail();
        }

        @Override
        public void checkServerTrusted(
                java.security.cert.X509Certificate[] x509Certificates, String s) {
            fail();
        }

        @Override
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return EmptyArrays.EMPTY_X509_CERTIFICATES;
        }
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testDefaultProtocolsIncludeTLSv13(SSLEngineTestParam param) throws Exception {
        // Don't specify the protocols as we want to test the default selection
        clientSslCtx = wrapContext(param, SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(sslClientProvider())
                .sslContextProvider(clientSslContextProvider())
                .ciphers(param.ciphers())
                .build());
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = wrapContext(param, SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(sslServerProvider())
                .sslContextProvider(serverSslContextProvider())
                .ciphers(param.ciphers())
                .build());
        SSLEngine clientEngine = null;
        SSLEngine serverEngine = null;
        String[] clientProtocols;
        String[] serverProtocols;
        try {
            clientEngine = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            serverEngine = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            clientProtocols = clientEngine.getEnabledProtocols();
            serverProtocols = serverEngine.getEnabledProtocols();
        } finally {
            cleanupClientSslEngine(clientEngine);
            cleanupServerSslEngine(serverEngine);
            ssc.delete();
        }

        assertEquals(SslProvider.isTlsv13EnabledByDefault(sslClientProvider(), clientSslContextProvider()),
                arrayContains(clientProtocols, SslProtocols.TLS_v1_3));
        assertEquals(SslProvider.isTlsv13EnabledByDefault(sslServerProvider(), serverSslContextProvider()),
                arrayContains(serverProtocols, SslProtocols.TLS_v1_3));
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testRSASSAPSS(SSLEngineTestParam param) throws Exception {
        char[] password = "password".toCharArray();

        final KeyStore serverKeyStore = KeyStore.getInstance("PKCS12");
        serverKeyStore.load(getClass().getResourceAsStream("rsaValidations-server-keystore.p12"), password);

        final KeyStore clientKeyStore = KeyStore.getInstance("PKCS12");
        clientKeyStore.load(getClass().getResourceAsStream("rsaValidation-user-certs.p12"), password);

        final KeyManagerFactory serverKeyManagerFactory =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        serverKeyManagerFactory.init(serverKeyStore, password);
        final KeyManagerFactory clientKeyManagerFactory =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        clientKeyManagerFactory.init(clientKeyStore, password);

        File commonChain = ResourcesUtil.getFile(getClass(), "rsapss-ca-cert.cert");
        ClientAuth auth = ClientAuth.REQUIRE;

        mySetupMutualAuth(param, serverKeyManagerFactory, commonChain, clientKeyManagerFactory, commonChain,
                auth, false, true);

        assertTrue(clientLatch.await(10, TimeUnit.SECONDS));
        rethrowIfNotNull(clientException);
        assertTrue(serverLatch.await(5, TimeUnit.SECONDS));
        rethrowIfNotNull(serverException);
    }

    protected SSLEngine wrapEngine(SSLEngine engine) {
        return engine;
    }

    protected SslContext wrapContext(SSLEngineTestParam param, SslContext context) {
        return context;
    }
}
