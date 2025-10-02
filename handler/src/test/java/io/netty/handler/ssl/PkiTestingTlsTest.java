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
import io.netty.util.concurrent.Promise;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;

public class PkiTestingTlsTest {

    static List<Arguments> classicalAlgorithms() {
        List<SslProvider> providers = new ArrayList<>();
        if (SslProvider.isTlsv13Supported(SslProvider.JDK)) {
            providers.add(SslProvider.JDK);
        }
        if (OpenSsl.isAvailable() && OpenSsl.supportsKeyManagerFactory() && OpenSsl.isTlsv13Supported()) {
            providers.add(SslProvider.OPENSSL);
        }

        List<Arguments> args = new ArrayList<>();
        for (SslProvider provider : providers) {
            List<CertificateBuilder.Algorithm> algs =  new ArrayList<>();
            algs.add(CertificateBuilder.Algorithm.rsa2048);
            algs.add(CertificateBuilder.Algorithm.ecp256);

            for (CertificateBuilder.Algorithm alg : algs) {
                args.add(Arguments.of(provider, alg));
            }
        }
        return args;
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

    @EnabledForJreRange(min = JRE.JAVA_15)
    @Test
    public void connectWithEd25519()
            throws Exception {
        X509Bundle cert = new CertificateBuilder()
                .algorithm(CertificateBuilder.Algorithm.ed25519)
                .setIsCertificateAuthority(true)
                .subject("CN=localhost")
                .buildSelfSigned();

        // We currently don't support Ed25519 or Ed448 with the OPENSSL provider,
        // so to use those algorithms we have to use the JDK provider.
        SslProvider provider = SslProvider.JDK;

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
    @EnabledForJreRange(min = JRE.JAVA_24)
    @EnabledIf("isBoringSSLAvailable")
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void connectWithX25519MLKEM768(boolean configureViaParameter) throws Exception {
        X509Bundle cert = new CertificateBuilder()
                .algorithm(CertificateBuilder.Algorithm.ecp256)
                .setIsCertificateAuthority(true)
                .subject("CN=localhost")
                .buildSelfSigned();

        // Disable 128-bit ciphers so only 256-bit ciphers remain.
        String[] ciphers = {"TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
                            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
                            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
                            "TLS_RSA_WITH_AES_256_CBC_SHA",
                            "TLS_AES_256_GCM_SHA384",
                            "TLS_CHACHA20_POLY1305_SHA256"};

        String[] groups = new String[]{"X25519MLKEM768"};
        SslContextBuilder serverBuilder = SslContextBuilder.forServer(cert.toKeyManagerFactory())
                .sslProvider(SslProvider.OPENSSL)
                .protocols("TLSv1.3")
                .ciphers(Arrays.asList(ciphers));

        if (!configureViaParameter) {
            serverBuilder.option(OpenSslContextOption.GROUPS, groups.clone());
        }
        final SslContext serverContext = serverBuilder.build();

        SslContextBuilder clientBuilder = SslContextBuilder.forClient()
                .trustManager(cert.toTrustManagerFactory())
                .sslProvider(SslProvider.OPENSSL)
                .serverName(new SNIHostName("localhost"))
                .protocols("TLSv1.3")
                .ciphers(Arrays.asList(ciphers));

        if (!configureViaParameter) {
            clientBuilder.option(OpenSslContextOption.GROUPS, groups.clone());
        }
        final SslContext clientContext = clientBuilder
                .build();

        testTlsConnection(serverContext, clientContext, configureViaParameter ? groups.clone() : null);
    }

    private void testTlsConnection(SslContext serverContext, SslContext clientContext, String[] groups)
            throws InterruptedException {
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

            Promise<SslHandshakeCompletionEvent> promise = group.next().newPromise();

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
                            ch.pipeline()
                                    .addLast(handler)
                                    .addLast(new ChannelInboundHandlerAdapter() {
                                        @Override
                                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt)
                                                throws Exception {
                                            if (evt instanceof SslHandshakeCompletionEvent) {
                                                SslHandshakeCompletionEvent shce = (SslHandshakeCompletionEvent) evt;
                                                if (shce.isSuccess()) {
                                                    promise.setSuccess(shce);
                                                } else {
                                                    promise.setFailure(shce.cause());
                                                }
                                                return;
                                            }
                                            super.userEventTriggered(ctx, evt);
                                        }

                                        @Override
                                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                                                throws Exception {
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

            promise.sync();
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
}
