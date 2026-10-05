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
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.local.LocalServerChannel;
import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.EmptyArrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.Socket;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509ExtendedKeyManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PkiTestingTlsTest {

    static List<Arguments> classicalAlgorithms() {
        List<Arguments> args = new ArrayList<>();
        for (SslProvider provider : tlsv13Providers()) {
            List<CertificateBuilder.Algorithm> algs =  new ArrayList<>();
            algs.add(CertificateBuilder.Algorithm.rsa2048);
            algs.add(CertificateBuilder.Algorithm.ecp256);

            for (CertificateBuilder.Algorithm alg : algs) {
                args.add(Arguments.of(provider, alg));
            }
        }
        return args;
    }

    static List<SslProvider> tlsv13Providers() {
        List<SslProvider> providers = new ArrayList<>();
        if (SslProvider.isTlsv13Supported(SslProvider.JDK)) {
            providers.add(SslProvider.JDK);
        }
        if (OpenSsl.isAvailable() && OpenSsl.supportsKeyManagerFactory() && OpenSsl.isTlsv13Supported()) {
            providers.add(SslProvider.OPENSSL);
        }
        return providers;
    }

    static Stream<Arguments> interoperabilityParams() {
        Stream.Builder<Arguments> builder = Stream.builder();
        for (boolean enableOnClient : new boolean[] {true, false}) {
            for (String[] protocols : new String[][] {{"TLSv1.2"}, {"TLSv1.3"}, {"TLSv1.3", "TLSv1.2"}}) {
                builder.add(Arguments.of(enableOnClient, protocols));
            }
        }
        return builder.build();
    }

    /**
     * A TLS connection with just classical algorithms.
     */
    @ParameterizedTest
    @MethodSource("classicalAlgorithms")
    public void connectWithClassicalAlgorithms(SslProvider provider, CertificateBuilder.Algorithm algorithm)
            throws Exception {
        X509Bundle cert = new CertificateBuilder()
                .algorithm(algorithm)
                .setIsCertificateAuthority(true)
                .subject("CN=localhost")
                .buildSelfSigned();

        final SslContext serverContext = SslContextBuilder.forServer(cert.toKeyManagerFactory())
                .sslProvider(provider)
                .build();

        final SslContext clientContext = SslContextBuilder.forClient()
                .trustManager(cert.toTrustManagerFactory())
                .sslProvider(provider)
                .serverName(new SNIHostName("localhost"))
                .protocols("TLSv1.3")
                .build();

        testTlsConnection(serverContext, clientContext, null);
    }

    static List<SslProvider> ed25519Providers() {
        List<SslProvider> providers = tlsv13Providers();
        if (!OpenSsl.isBoringSSL()) {
            // The OPENSSL provider doesn't include `ed25519` in its peer signature algorithms
            // https://github.com/netty/netty-tcnative/pull/1019
            providers.remove(SslProvider.OPENSSL);
        }
        return providers;
    }

    static List<Arguments> ed25519Params() {
        List<Arguments> args = new ArrayList<>();
        for (SslProvider provider : ed25519Providers()) {
            for (boolean useKeyManagerFactory : new boolean[] {true, false}) {
                args.add(Arguments.of(provider, useKeyManagerFactory));
            }
        }
        return args;
    }

    @EnabledForJreRange(min = JRE.JAVA_15)
    @ParameterizedTest
    @MethodSource("ed25519Params")
    public void connectWithEd25519(SslProvider provider, boolean useKeyManagerFactory)
            throws Exception {
        X509Bundle cert = new CertificateBuilder()
                .algorithm(CertificateBuilder.Algorithm.ed25519)
                .setIsCertificateAuthority(true)
                .subject("CN=localhost")
                .buildSelfSigned();

        SslContextBuilder serverBuilder = useKeyManagerFactory ?
                SslContextBuilder.forServer(cert.toKeyManagerFactory()) :
                SslContextBuilder.forServer(cert.getKeyPair().getPrivate(), cert.getCertificate());
        final SslContext serverContext = serverBuilder
                .sslProvider(provider)
                .build();

        final SslContext clientContext = SslContextBuilder.forClient()
                .trustManager(cert.toTrustManagerFactory())
                .sslProvider(SslProvider.JDK)
                .serverName(new SNIHostName("localhost"))
                .protocols("TLSv1.3")
                .build();

        SSLSession session = testTlsConnection(serverContext, clientContext, null, null);
        assertThat(session.getPeerCertificates()[0]).isEqualTo(cert.getCertificate());
    }

    static List<Arguments> ed25519AndRsaParams() {
        List<Arguments> args = new ArrayList<>();
        for (SslProvider provider : ed25519Providers()) {
            // The JDK client's default signature schemes list ECDSA first, then Ed25519, then RSA.
            args.add(Arguments.of(provider, null, "Ed25519"));
            args.add(Arguments.of(provider, new String[] {"rsa_pss_rsae_sha256"}, "RSA"));
            args.add(Arguments.of(provider, new String[] {"rsa_pss_rsae_sha256", "ed25519"}, "RSA"));
            args.add(Arguments.of(provider, new String[] {"ed25519", "rsa_pss_rsae_sha256"}, "Ed25519"));
        }
        return args;
    }

    /**
     * Requires Java 19 for {@code SSLParameters#setSignatureSchemes(String[])}.
     */
    @EnabledForJreRange(min = JRE.JAVA_19)
    @ParameterizedTest
    @MethodSource("ed25519AndRsaParams")
    public void connectWithEd25519AndRsa(SslProvider provider, String[] clientSignatureSchemes,
                                         String expectedAlgorithm) throws Exception {
        X509Bundle ed25519 = new CertificateBuilder()
                .algorithm(CertificateBuilder.Algorithm.ed25519)
                .setIsCertificateAuthority(true)
                .subject("CN=localhost")
                .buildSelfSigned();
        X509Bundle rsa = new CertificateBuilder()
                .algorithm(CertificateBuilder.Algorithm.rsa2048)
                .setIsCertificateAuthority(true)
                .subject("CN=localhost")
                .buildSelfSigned();

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("ed25519", ed25519.getKeyPair().getPrivate(), EmptyArrays.EMPTY_CHARS,
                ed25519.getCertificatePath());
        keyStore.setKeyEntry("rsa", rsa.getKeyPair().getPrivate(), EmptyArrays.EMPTY_CHARS,
                rsa.getCertificatePath());
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, EmptyArrays.EMPTY_CHARS);

        final SslContext serverContext = SslContextBuilder.forServer(kmf)
                .sslProvider(provider)
                .build();

        final SslContext clientContext = SslContextBuilder.forClient()
                .trustManager(ed25519.getCertificate(), rsa.getCertificate())
                .sslProvider(SslProvider.JDK)
                .serverName(new SNIHostName("localhost"))
                .protocols("TLSv1.3")
                .build();

        SSLSession session = testTlsConnection(serverContext, clientContext, null, clientSignatureSchemes);
        X509Bundle expected = "RSA".equals(expectedAlgorithm) ? rsa : ed25519;
        assertThat(session.getPeerCertificates()[0]).isEqualTo(expected.getCertificate());
    }

    /**
     * Requires Java 19 for {@code SSLParameters#setSignatureSchemes(String[])}.
     */
    @EnabledForJreRange(min = JRE.JAVA_19)
    @ParameterizedTest
    @MethodSource("tlsv13Providers")
    public void connectWithEd25519FailsWithoutCommonSignatureAlgorithm(SslProvider provider) throws Exception {
        X509Bundle cert = new CertificateBuilder()
                .algorithm(CertificateBuilder.Algorithm.ed25519)
                .setIsCertificateAuthority(true)
                .subject("CN=localhost")
                .buildSelfSigned();

        final SslContext serverContext = SslContextBuilder.forServer(cert.toKeyManagerFactory())
                .sslProvider(provider)
                .build();

        final SslContext clientContext = SslContextBuilder.forClient()
                .trustManager(cert.toTrustManagerFactory())
                .sslProvider(SslProvider.JDK)
                .serverName(new SNIHostName("localhost"))
                .protocols("TLSv1.3")
                .build();

        assertThrows(SSLException.class, () -> testTlsConnection(
                serverContext, clientContext, null, new String[] {"ecdsa_secp256r1_sha256"}));
    }

    /**
     * The OPENSSL provider should report Ed25519 in the peer's supported signature algorithms when the client
     * offers it, so key managers can see what the client actually supports.
     * <p>
     * This is currently limited to BoringSSL, because stock OpenSSL has no combined signature-and-hash NID for them.
     */
    @EnabledForJreRange(min = JRE.JAVA_15)
    @EnabledIf("isBoringSSLAvailable")
    @Test
    public void peerSupportedSignatureAlgorithmsContainEd25519() throws Exception {
        X509Bundle cert = new CertificateBuilder()
            .algorithm(CertificateBuilder.Algorithm.ecp256)
            .setIsCertificateAuthority(true)
            .subject("CN=localhost")
            .buildSelfSigned();

        final X509ExtendedKeyManager delegate =
            (X509ExtendedKeyManager) cert.toKeyManagerFactory().getKeyManagers()[0];
        final AtomicReference<String[]> peerAlgorithms = new AtomicReference<>();
        X509ExtendedKeyManager keyManager = new X509ExtendedKeyManager() {
            @Override
            public String[] getClientAliases(String keyType, Principal[] issuers) {
                return delegate.getClientAliases(keyType, issuers);
            }

            @Override
            public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
                return delegate.chooseClientAlias(keyType, issuers, socket);
            }

            @Override
            public String[] getServerAliases(String keyType, Principal[] issuers) {
                return delegate.getServerAliases(keyType, issuers);
            }

            @Override
            public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
                return delegate.chooseServerAlias(keyType, issuers, socket);
            }

            @Override
            public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
                peerAlgorithms.set(((ExtendedSSLSession) engine.getHandshakeSession())
                    .getPeerSupportedSignatureAlgorithms());
                return delegate.chooseEngineServerAlias(keyType, issuers, engine);
            }

            @Override
            public X509Certificate[] getCertificateChain(String alias) {
                return delegate.getCertificateChain(alias);
            }

            @Override
            public PrivateKey getPrivateKey(String alias) {
                return delegate.getPrivateKey(alias);
            }
        };

        final SslContext serverContext = SslContextBuilder.forServer(keyManager)
            .sslProvider(SslProvider.OPENSSL)
            .protocols("TLSv1.3")
            .build();

        // The JDK client offers ed25519 in its signature_algorithms extension by default.
        final SslContext clientContext = SslContextBuilder.forClient()
            .trustManager(cert.toTrustManagerFactory())
            .sslProvider(SslProvider.JDK)
            .serverName(new SNIHostName("localhost"))
            .protocols("TLSv1.3")
            .build();

        testTlsConnection(serverContext, clientContext, null);
        assertThat(peerAlgorithms.get()).contains("Ed25519");
    }

    static boolean isBoringSSLAvailable() {
        return OpenSsl.isBoringSSL() && OpenSsl.isTlsv13Supported();
    }

    /**
     * A TLS connection using the X25519MLKEM768 hybrid classical-and-quantum-safe key exchange.
     * This protects the ephemeral TLS session key from harvest-now-decrypt-later attacks.
     * <p>
     * The ephemeral session key is used for the symmetric encryption algorithm.
     * To make that quantum safe, we just need to double the bit-width, from AES-128 to AES-256.
     */
    @EnabledIf("isBoringSSLAvailable")
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void connectWithX25519MLKEM768(boolean configureViaParameter) throws Exception {
        X509Bundle cert = new CertificateBuilder()
                .algorithm(CertificateBuilder.Algorithm.ecp256)
                .setIsCertificateAuthority(true)
                .subject("CN=localhost")
                .buildSelfSigned();

        String[] groups = new String[]{"X25519MLKEM768"};
        SslContextBuilder serverBuilder = SslContextBuilder.forServer(cert.toKeyManagerFactory())
                .sslProvider(SslProvider.OPENSSL)
                .protocols("TLSv1.3");

        if (!configureViaParameter) {
            serverBuilder.option(OpenSslContextOption.GROUPS, groups.clone());
        }
        final SslContext serverContext = serverBuilder.build();

        SslContextBuilder clientBuilder = SslContextBuilder.forClient()
                .trustManager(cert.toTrustManagerFactory())
                .sslProvider(SslProvider.OPENSSL)
                .serverName(new SNIHostName("localhost"))
                .protocols("TLSv1.3");

        if (!configureViaParameter) {
            clientBuilder.option(OpenSslContextOption.GROUPS, groups.clone());
        }
        final SslContext clientContext = clientBuilder
                .build();

        testTlsConnection(serverContext, clientContext, configureViaParameter ? groups.clone() : null);
    }

    /**
     * It is possible to enable the X25519MLKEM768 hybrid classical-and-quantum-safe key exchange in an interoperable
     * manner.
     * This way, we can allow the classical algorithms to be used as fallback if the peer doesn't support the hybrid
     * quantum safe key exchange.
     * This allows us to gradually introduce quantum safety into complex systems, without forcing all clients and
     * servers to upgrade at the same time.
     */
    @EnabledIf("isBoringSSLAvailable")
    @ParameterizedTest
    @MethodSource("interoperabilityParams")
    void x25519MLKEM768Interoperability(boolean enabledOnClient, String[] protocols) throws Exception {
        X509Bundle cert = new CertificateBuilder()
                .algorithm(CertificateBuilder.Algorithm.ecp256)
                .setIsCertificateAuthority(true)
                .subject("CN=localhost")
                .buildSelfSigned();

        String[] classicalGroups = new String[] {
                "x25519",
                "secp256r1",
                "secp384r1",
                "secp521r1",
        };

        String[] interoperableGroups = new String[] {
                "X25519MLKEM768",
                "x25519",
                "secp256r1",
                "secp384r1",
                "secp521r1",
        };

        SslContextBuilder serverBuilder = SslContextBuilder.forServer(cert.toKeyManagerFactory())
                .sslProvider(SslProvider.OPENSSL)
                .protocols(protocols);
        SslContextBuilder clientBuilder = SslContextBuilder.forClient()
                .trustManager(cert.toTrustManagerFactory())
                .sslProvider(SslProvider.OPENSSL)
                .serverName(new SNIHostName("localhost"))
                .protocols(protocols);

        if (enabledOnClient) {
            clientBuilder.option(OpenSslContextOption.GROUPS, interoperableGroups);
            serverBuilder.option(OpenSslContextOption.GROUPS, classicalGroups);
        } else {
            clientBuilder.option(OpenSslContextOption.GROUPS, classicalGroups);
            serverBuilder.option(OpenSslContextOption.GROUPS, interoperableGroups);
        }

        testTlsConnection(serverBuilder.build(), clientBuilder.build(), null);
    }

    private void testTlsConnection(SslContext serverContext, SslContext clientContext, String[] groups)
            throws Exception {
        testTlsConnection(serverContext, clientContext, groups, null);
    }

    /**
     * @param clientSignatureSchemes sets the preferred client signature schemes. Requires JRE 19+
     * @return the client's session.
     */
    private SSLSession testTlsConnection(SslContext serverContext, SslContext clientContext, String[] groups,
                                         String[] clientSignatureSchemes) throws Exception {
        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, LocalIoHandler.newFactory());
        LocalAddress serverAddress = new LocalAddress(getClass());

        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            serverChannel = new ServerBootstrap()
                    .channel(LocalServerChannel.class)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            SslHandler handler = serverContext.newHandler(ch.alloc());
                            if (groups != null) {
                                SSLParameters parameters = handler.engine().getSSLParameters();
                                OpenSslParametersUtil.setNamesGroups(parameters, groups);
                                handler.engine().setSSLParameters(parameters);
                            }
                            ch.pipeline().addLast(handler);
                        }
                    })
                    .group(group)
                    .bind(serverAddress).sync().channel();

            Promise<SSLSession> promise = group.next().newPromise();

            clientChannel = new Bootstrap()
                    .channel(LocalChannel.class)
                    .group(group)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            SslHandler handler = clientContext.newHandler(ch.alloc(), "localhost", 0);
                            if (groups != null) {
                                SSLParameters parameters = handler.engine().getSSLParameters();
                                OpenSslParametersUtil.setNamesGroups(parameters, groups);
                                handler.engine().setSSLParameters(parameters);
                            }
                            if (clientSignatureSchemes != null) {
                                SSLParameters parameters = handler.engine().getSSLParameters();
                                setSignatureSchemes(parameters, clientSignatureSchemes);
                                handler.engine().setSSLParameters(parameters);
                            }
                            ch.pipeline()
                                    .addLast(handler)
                                    .addLast(new ChannelInboundHandlerAdapter() {
                                        @Override
                                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt)
                                                throws Exception {
                                            if (evt instanceof SslHandshakeCompletionEvent) {
                                                SslHandshakeCompletionEvent shce = (SslHandshakeCompletionEvent) evt;
                                                if (shce.isSuccess()) {
                                                    SSLSession session = handler.engine().getSession();
                                                    if (session instanceof OpenSslSession) {
                                                        String namedGroup = ((OpenSslSession) handler.engine()
                                                                .getSession()).getNamedGroup();
                                                        assertThat(OpenSsl.NAMED_GROUPS).contains(namedGroup);
                                                    }
                                                    promise.setSuccess(session);
                                                } else {
                                                    promise.setFailure(shce.cause());
                                                }
                                                return;
                                            }
                                            super.userEventTriggered(ctx, evt);
                                        }

                                        @Override
                                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                            if (!promise.tryFailure(cause)) {
                                                ctx.fireExceptionCaught(cause);
                                            }
                                        }
                                    });
                        }
                    })
                    .connect(serverAddress)
                    .sync()
                    .channel();

            return promise.sync().getNow();
        } finally {
            if (clientChannel != null) {
                clientChannel.close();
            }
            if (serverChannel != null) {
                serverChannel.close();
            }
            if (clientChannel != null) {
                clientChannel.closeFuture().sync();
            }
            if (serverChannel != null) {
                serverChannel.closeFuture().sync();
            }
            group.shutdownGracefully(10, 1000, TimeUnit.MILLISECONDS)
                    .syncUninterruptibly();
            // Release contexts created for this test to avoid leak failures with OPENSSL_REFCNT.
            ReferenceCountUtil.release(clientContext);
            ReferenceCountUtil.release(serverContext);
        }
    }

    /**
     * Uses reflection to invoke the Java 19+ {@code SSLParameters#setSignatureSchemes(String[])}
     */
    private static void setSignatureSchemes(SSLParameters parameters, String[] signatureSchemes) throws Exception {
        SSLParameters.class.getMethod("setSignatureSchemes", String[].class)
                .invoke(parameters, (Object) signatureSchemes);
    }
}
