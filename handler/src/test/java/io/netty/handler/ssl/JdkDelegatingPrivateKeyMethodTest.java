/*
 * Copyright 2025 The Netty Project
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
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static io.netty.handler.ssl.MockAlternativeKeyProvider.wrapPrivateKey;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for alternative key provider integration in Netty OpenSSL.
 * Combines algorithm-specific testing with end-to-end scenarios using reliable
 * single-byte message handlers to avoid partial message issues.
 */
@EnabledIf("isSignatureDelegationSupported")
@Timeout(30)
public class JdkDelegatingPrivateKeyMethodTest {

    private static MockAlternativeKeyProvider mockProvider;
    private static EventLoopGroup GROUP;
    private static X509Bundle RSA_BUNDLE;

    @BeforeAll
    static void setUp() throws Exception {
        Assumptions.assumeTrue(OpenSsl.isAvailable(), "OpenSSL is not available");

        mockProvider = new MockAlternativeKeyProvider();
        Security.addProvider(mockProvider);
        GROUP = new NioEventLoopGroup();

        // Create server certificate bundle
        RSA_BUNDLE = new CertificateBuilder()
            .subject("CN=localhost, O=Netty, C=US")
            .algorithm(CertificateBuilder.Algorithm.rsa2048)
            .setIsCertificateAuthority(true)
            .setKeyUsage(true, CertificateBuilder.KeyUsage.digitalSignature)
            .addExtendedKeyUsageServerAuth()
            .buildSelfSigned();
    }

    @AfterAll
    static void tearDown() {
        if (GROUP != null) {
            GROUP.shutdownGracefully();
        }
        if (mockProvider != null) {
            Security.removeProvider(mockProvider.getName());
        }
        RSA_BUNDLE = null;
    }

    /**
     * Test scenarios for client/server alternative key combinations
     */
    public enum TestScenario {
        SERVER_ALTERNATIVE_CLIENT_STANDARD("Server Alternative + Client Standard", true, false),
        SERVER_STANDARD_CLIENT_ALTERNATIVE("Server Standard + Client Alternative", false, true),
        BOTH_ALTERNATIVE("Both Alternative", true, true);

        final String description;
        final boolean serverUsesAlternativeKey;
        final boolean clientUsesAlternativeKey;

        TestScenario(String description, boolean serverUsesAlternativeKey, boolean clientUsesAlternativeKey) {
            this.description = description;
            this.serverUsesAlternativeKey = serverUsesAlternativeKey;
            this.clientUsesAlternativeKey = clientUsesAlternativeKey;
        }

        @Override
        public String toString() {
            return description;
        }
    }

    /**
     * Test data for supported signature algorithms
     */
    static Stream<Arguments> supportedAlgorithms() {
        return Stream.of(
            // RSA PKCS#1 algorithms
            Arguments.of("RSA PKCS#1 SHA1", OpenSslAsyncPrivateKeyMethod.SSL_SIGN_RSA_PKCS1_SHA1,
                    CertificateBuilder.Algorithm.rsa2048),
            Arguments.of("RSA PKCS#1 SHA256", OpenSslAsyncPrivateKeyMethod.SSL_SIGN_RSA_PKCS1_SHA256,
                    CertificateBuilder.Algorithm.rsa2048),
            Arguments.of("RSA PKCS#1 SHA384", OpenSslAsyncPrivateKeyMethod.SSL_SIGN_RSA_PKCS1_SHA384,
                    CertificateBuilder.Algorithm.rsa2048),
            Arguments.of("RSA PKCS#1 SHA512", OpenSslAsyncPrivateKeyMethod.SSL_SIGN_RSA_PKCS1_SHA512,
                    CertificateBuilder.Algorithm.rsa2048),
            Arguments.of("RSA PKCS#1 MD5+SHA1", OpenSslAsyncPrivateKeyMethod.SSL_SIGN_RSA_PKCS1_MD5_SHA1,
                    CertificateBuilder.Algorithm.rsa2048),

            // RSA-PSS algorithms
            Arguments.of("RSA-PSS SHA256", OpenSslAsyncPrivateKeyMethod.SSL_SIGN_RSA_PSS_RSAE_SHA256,
                    CertificateBuilder.Algorithm.rsa2048),
            Arguments.of("RSA-PSS SHA384", OpenSslAsyncPrivateKeyMethod.SSL_SIGN_RSA_PSS_RSAE_SHA384,
                    CertificateBuilder.Algorithm.rsa2048),
            Arguments.of("RSA-PSS SHA512", OpenSslAsyncPrivateKeyMethod.SSL_SIGN_RSA_PSS_RSAE_SHA512,
                    CertificateBuilder.Algorithm.rsa2048),

            // ECDSA algorithms with different curves
            Arguments.of("ECDSA SHA1 P-256", OpenSslAsyncPrivateKeyMethod.SSL_SIGN_ECDSA_SHA1,
                    CertificateBuilder.Algorithm.ecp256),
            Arguments.of("ECDSA SHA256 P-256", OpenSslAsyncPrivateKeyMethod.SSL_SIGN_ECDSA_SECP256R1_SHA256,
                    CertificateBuilder.Algorithm.ecp256),
            Arguments.of("ECDSA SHA384 P-384", OpenSslAsyncPrivateKeyMethod.SSL_SIGN_ECDSA_SECP384R1_SHA384,
                    CertificateBuilder.Algorithm.ecp384)
//            Arguments.of("ECDSA SHA512 P-521",
//                    OpenSslAsyncPrivateKeyMethod.SSL_SIGN_ECDSA_SECP521R1_SHA512, "EC", "secp521r1")
        );
    }

    @ParameterizedTest(name = "{index}: scenario = {0}")
    @EnumSource(TestScenario.class)
    void testClientServerScenarios(TestScenario scenario) throws Exception {
        // Reset signature operation counter for test isolation
        MockAlternativeKeyProvider.resetSignatureOperationCount();

        // Prepare server key (alternative or standard based on scenario)
        PrivateKey serverPrivateKey = scenario.serverUsesAlternativeKey
            ? wrapPrivateKey(RSA_BUNDLE.getKeyPair().getPrivate())
            : RSA_BUNDLE.getKeyPair().getPrivate();
        X509Certificate serverCertificate = RSA_BUNDLE.getCertificate();

        // Prepare client key (alternative or standard based on scenario)
        PrivateKey clientPrivateKey = scenario.clientUsesAlternativeKey
            ? wrapPrivateKey(RSA_BUNDLE.getKeyPair().getPrivate())
            : RSA_BUNDLE.getKeyPair().getPrivate();
        X509Certificate clientCertificate = RSA_BUNDLE.getCertificate();

        // Verify alternative keys behave correctly
        if (scenario.serverUsesAlternativeKey) {
            assertNull(serverPrivateKey.getEncoded(),
                "Server alternative key should return null from getEncoded()");
        }
        if (scenario.clientUsesAlternativeKey) {
            assertNull(clientPrivateKey.getEncoded(),
                "Client alternative key should return null from getEncoded()");
        }

        // Set up server context
        SslContext serverSslContext = SslContextBuilder.forServer(serverPrivateKey, serverCertificate)
                .sslProvider(SslProvider.OPENSSL)
                .trustManager(clientCertificate)
                .option(OpenSslContextOption.USE_JDK_PROVIDER_SIGNATURES, true)
                .clientAuth(ClientAuth.REQUIRE)
                .build();

        // Set up client context
        SslContext clientSslContext = SslContextBuilder.forClient()
                .sslProvider(SslProvider.OPENSSL)
                .trustManager(serverCertificate)
                .option(OpenSslContextOption.USE_JDK_PROVIDER_SIGNATURES, true)
                .keyManager(clientPrivateKey, clientCertificate)
                .build();

        // Run handshake test
        String result = performHandshakeTest(serverSslContext, clientSslContext);
        assertEquals("R", result, "Handshake should complete successfully for " + scenario.description);

        // Verify that alternative key provider was actually used for signature operations
        int expectedSignatureOperations = 0;
        if (scenario.serverUsesAlternativeKey) {
            expectedSignatureOperations++;
        }
        if (scenario.clientUsesAlternativeKey) {
            expectedSignatureOperations++;
        }

        int actualSignatureOperations = MockAlternativeKeyProvider.getSignatureOperationCount();
        assertEquals(expectedSignatureOperations, actualSignatureOperations,
            "Unexpected number of signature operations for scenario: " + scenario.description);
    }

    @ParameterizedTest
    @MethodSource("supportedAlgorithms")
    void testAlgorithmSupport(String description, int opensslAlgorithm,
                              CertificateBuilder.Algorithm algorithm) throws Exception {
        // Generate certificate with matching key type
        X509Bundle certKeyPair = generateCertificateForAlgorithm(algorithm);
        PrivateKey alternativeKey = wrapPrivateKey(certKeyPair.getKeyPair().getPrivate());
        assertNull(alternativeKey.getEncoded(), "Alternative key should return null from getEncoded()");

        // Reset signature operation counter
        MockAlternativeKeyProvider.resetSignatureOperationCount();

        // Configure SSL contexts with algorithm-specific settings
        SslContextBuilder serverBuilder = SslContextBuilder.forServer(alternativeKey, certKeyPair.getCertificate())
            .sslProvider(SslProvider.OPENSSL)
            .option(OpenSslContextOption.USE_JDK_PROVIDER_SIGNATURES, true);

        SslContextBuilder clientBuilder = SslContextBuilder.forClient()
            .sslProvider(SslProvider.OPENSSL)
            .trustManager(certKeyPair.getCertificate());

        configureCipherSuitesForAlgorithm(serverBuilder, clientBuilder, opensslAlgorithm);

        SslContext serverContext = serverBuilder.build();
        SslContext clientContext = clientBuilder.build();

        String result = performHandshakeTest(serverContext, clientContext);
        assertEquals("R", result, "Handshake should complete successfully for " + description);

        int signatureOperations = MockAlternativeKeyProvider.getSignatureOperationCount();
        assertTrue(signatureOperations > 0,
            "Expected signature operations to be recorded for " + description + ", got: " + signatureOperations);
    }

    @Test
    void testMultipleHandshakes() throws Exception {
        // Create certificate and wrap the private key
        PrivateKey wrappedPrivateKey = wrapPrivateKey(RSA_BUNDLE.getKeyPair().getPrivate());
        X509Certificate certificate = RSA_BUNDLE.getCertificate();

        // Set up contexts
        SslContext serverSslContext = SslContextBuilder.forServer(wrappedPrivateKey, certificate)
                .sslProvider(SslProvider.OPENSSL)
                .option(OpenSslContextOption.USE_JDK_PROVIDER_SIGNATURES, true)
                .build();

        SslContext clientSslContext = SslContextBuilder.forClient()
                .sslProvider(SslProvider.OPENSSL)
                .trustManager(certificate)
                .build();

        // Test multiple connections to ensure provider caching works correctly
        int numberOfConnections = 3;
        List<Future<String>> results = new ArrayList<>();

        // Set up server
        ServerBootstrap serverBootstrap = new ServerBootstrap()
                .group(GROUP)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        SslHandler sslHandler = serverSslContext.newHandler(ch.alloc());
                        pipeline.addLast(sslHandler);
                        pipeline.addLast(new ServerHandler());
                    }
                });

        ChannelFuture serverChannelFuture = serverBootstrap.bind(0).sync();
        Channel serverChannel = serverChannelFuture.channel();
        int serverPort = ((java.net.InetSocketAddress) serverChannel.localAddress()).getPort();

        try {
            // Create multiple client connections
            for (int i = 0; i < numberOfConnections; i++) {
                results.add(runClient(serverPort, clientSslContext));
            }

            // Wait for all connections to complete
            for (Future<String> result : results) {
                assertEquals("R", result.get(20, TimeUnit.SECONDS),
                          "All connections should complete within timeout");
            }
        } finally {
            serverChannel.close().sync();
        }
    }

    @Test
    void testAlgorithmCaching() throws Exception {
        // Create a key for testing
        PrivateKey alternativeKey = wrapPrivateKey(RSA_BUNDLE.getKeyPair().getPrivate());

        MockAlternativeKeyProvider.resetSignatureOperationCount();

        // Create SSL contexts multiple times - provider caching should work
        SslContext context1 = SslContextBuilder.forServer(alternativeKey, RSA_BUNDLE.getCertificate())
            .sslProvider(SslProvider.OPENSSL)
            .option(OpenSslContextOption.USE_JDK_PROVIDER_SIGNATURES, true)
            .build();

        SslContext context2 = SslContextBuilder.forServer(alternativeKey, RSA_BUNDLE.getCertificate())
            .sslProvider(SslProvider.OPENSSL)
            .option(OpenSslContextOption.USE_JDK_PROVIDER_SIGNATURES, true)
            .build();

        assertNotNull(context1, "First context should be created");
        assertNotNull(context2, "Second context should be created");
    }

    private static void configureCipherSuitesForAlgorithm(SslContextBuilder serverBuilder,
                                                          SslContextBuilder clientBuilder,
                                                          int opensslAlgorithm) {
        // Map each OpenSSL algorithm to a single specific cipher suite
        String cipherSuite;
        String protocol = null;

        // RSA-PSS algorithms - require TLS 1.3
        if (opensslAlgorithm == OpenSslAsyncPrivateKeyMethod.SSL_SIGN_RSA_PSS_RSAE_SHA256) {
            cipherSuite = "TLS_AES_128_GCM_SHA256";  // Forces SHA256 signatures
            protocol = "TLSv1.3";
        } else if (opensslAlgorithm == OpenSslAsyncPrivateKeyMethod.SSL_SIGN_RSA_PSS_RSAE_SHA384) {
            cipherSuite = "TLS_AES_256_GCM_SHA384";  // Forces SHA384 signatures
            protocol = "TLSv1.3";
        } else if (opensslAlgorithm == OpenSslAsyncPrivateKeyMethod.SSL_SIGN_RSA_PSS_RSAE_SHA512) {
            cipherSuite = "TLS_AES_256_GCM_SHA384";  // Best available for SHA512 signatures
            protocol = "TLSv1.3";

        // ECDSA algorithms
        } else if (opensslAlgorithm == OpenSslAsyncPrivateKeyMethod.SSL_SIGN_ECDSA_SHA1) {
            cipherSuite = "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA";  // Forces SHA1 signatures
        } else if (opensslAlgorithm == OpenSslAsyncPrivateKeyMethod.SSL_SIGN_ECDSA_SECP256R1_SHA256) {
            cipherSuite = "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256";  // Forces SHA256 signatures
        } else if (opensslAlgorithm == OpenSslAsyncPrivateKeyMethod.SSL_SIGN_ECDSA_SECP384R1_SHA384) {
            cipherSuite = "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384";  // Forces SHA384 signatures
        } else if (opensslAlgorithm == OpenSslAsyncPrivateKeyMethod.SSL_SIGN_ECDSA_SECP521R1_SHA512) {
            cipherSuite = "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384";  // Use GCM instead of CBC for SHA512

        // RSA PKCS#1 algorithms
        } else if (opensslAlgorithm == OpenSslAsyncPrivateKeyMethod.SSL_SIGN_RSA_PKCS1_SHA1) {
            cipherSuite = "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA";  // Forces SHA1 signatures
        } else if (opensslAlgorithm == OpenSslAsyncPrivateKeyMethod.SSL_SIGN_RSA_PKCS1_SHA256) {
            cipherSuite = "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256";  // Forces SHA256 signatures
        } else if (opensslAlgorithm == OpenSslAsyncPrivateKeyMethod.SSL_SIGN_RSA_PKCS1_SHA384) {
            cipherSuite = "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384";  // Forces SHA384 signatures
        } else if (opensslAlgorithm == OpenSslAsyncPrivateKeyMethod.SSL_SIGN_RSA_PKCS1_SHA512) {
            cipherSuite = "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384";  // Use GCM instead of CBC for SHA512
        } else if (opensslAlgorithm == OpenSslAsyncPrivateKeyMethod.SSL_SIGN_RSA_PKCS1_MD5_SHA1) {
            cipherSuite = "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA";  // Forces MD5+SHA1 signatures
        } else {
            throw new IllegalArgumentException("Unsupported OpenSSL algorithm: " + opensslAlgorithm);
        }

        // Configure both server and client with the same single cipher suite
        List<String> singleCipherSuite = Arrays.asList(cipherSuite);
        serverBuilder.ciphers(singleCipherSuite);
        clientBuilder.ciphers(singleCipherSuite);

        // Set protocol if specified (for TLS 1.3)
        if (protocol != null) {
            serverBuilder.protocols(protocol);
            clientBuilder.protocols(protocol);
        }
    }

    private static X509Bundle generateCertificateForAlgorithm(CertificateBuilder.Algorithm algorithm) throws Exception {
        CertificateBuilder builder = new CertificateBuilder()
            .subject("CN=localhost, O=Netty, C=US")
            .setIsCertificateAuthority(true)
            .setKeyUsage(true, CertificateBuilder.KeyUsage.digitalSignature)
            .addExtendedKeyUsageServerAuth()
            .algorithm(algorithm);
        return builder.buildSelfSigned();
    }

    /**
     * Perform a handshake test using the reliable single-byte message handlers
     */
    private static String performHandshakeTest(SslContext serverContext, SslContext clientContext) throws Exception {
        // Set up server
        ServerBootstrap serverBootstrap = new ServerBootstrap()
                .group(GROUP)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        SslHandler serverSslHandler = serverContext.newHandler(ch.alloc());
                        pipeline.addLast(serverSslHandler);
                        pipeline.addLast(new ServerHandler());
                    }
                });

        ChannelFuture serverChannelFuture = serverBootstrap.bind(0).sync();
        Channel serverChannel = serverChannelFuture.channel();
        int serverPort = ((java.net.InetSocketAddress) serverChannel.localAddress()).getPort();

        try {
            // Run client and verify handshake
            return runClient(serverPort, clientContext).get(10, TimeUnit.SECONDS);
        } finally {
            serverChannel.close().sync();
        }
    }

    private static Future<String> runClient(int serverPort, SslContext clientSslContext) {
        Promise<String> resultPromise = GROUP.next().newPromise();
        Bootstrap clientBootstrap = new Bootstrap()
                .group(GROUP)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        SslHandler sslHandler = clientSslContext.newHandler(ch.alloc(),
                                "localhost", serverPort);
                        pipeline.addLast(sslHandler);
                        pipeline.addLast(new ClientHandler(resultPromise));
                    }
                });

        clientBootstrap.connect("localhost", serverPort);
        return resultPromise;
    }

    /**
     * Simple client handler that sends a single byte 'R' and expects echo back
     */
    private static final class ClientHandler extends ChannelInboundHandlerAdapter {
        Promise<String> resultPromise;

        ClientHandler(Promise<String> resultPromise) {
            this.resultPromise = resultPromise;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            // Send single byte message once connection is established
            ByteBuf message = Unpooled.copiedBuffer("R", CharsetUtil.UTF_8);
            ctx.writeAndFlush(message);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            // Close connection after receiving echo
            if (msg instanceof ByteBuf) {
                ByteBuf bytes = (ByteBuf) msg;
                resultPromise.setSuccess(bytes.toString(CharsetUtil.UTF_8));
                bytes.release();
                ctx.close();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            resultPromise.tryFailure(cause);
            ctx.close();
        }
    }

    /**
     * Simple server handler that echoes messages back
     */
    private static final class ServerHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            // Echo the message back and close
            ctx.writeAndFlush(msg).addListener(future -> {
                ctx.close();
            });
        }
    }

    private static boolean isSignatureDelegationSupported() {
        return OpenSsl.isBoringSSL() || OpenSsl.isAWSLC();
    }
}
