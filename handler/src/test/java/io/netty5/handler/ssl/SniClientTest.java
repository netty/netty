/*
 * Copyright 2016 The Netty Project
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
package io.netty5.handler.ssl;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.local.LocalAddress;
import io.netty5.channel.local.LocalChannel;
import io.netty5.channel.local.LocalHandler;
import io.netty5.channel.local.LocalServerChannel;
import io.netty5.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty5.handler.ssl.util.SelfSignedCertificate;
import io.netty5.handler.ssl.util.SimpleTrustManagerFactory;
import io.netty5.util.Resource;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.EmptyArrays;
import io.netty5.util.internal.ThrowableUtil;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.KeyManagerFactorySpi;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIMatcher;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509ExtendedTrustManager;
import java.io.IOException;
import java.net.Socket;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static io.netty5.buffer.api.DefaultBufferAllocators.offHeapAllocator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


public class SniClientTest {
    private static final String PARAMETERIZED_NAME = "{index}: serverSslProvider = {0}, clientSslProvider = {1}";
    static Collection<Object[]> parameters() {
        List<SslProvider> providers = new ArrayList<>(Arrays.asList(SslProvider.values()));
        if (!OpenSsl.isAvailable()) {
            providers.remove(SslProvider.OPENSSL);
            providers.remove(SslProvider.OPENSSL_REFCNT);
        }

        List<Object[]> params = new ArrayList<>();
        for (SslProvider sp: providers) {
            for (SslProvider cp: providers) {
                params.add(new Object[] { sp, cp });
            }
        }
        return params;
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    @MethodSource("parameters")
    public void testSniSNIMatcherMatchesClient(SslProvider serverProvider, SslProvider clientProvider)
            throws Throwable {
        testSniClient(serverProvider, clientProvider, true);
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    @MethodSource("parameters")
    public void testSniSNIMatcherDoesNotMatchClient(
            final SslProvider serverProvider, final SslProvider clientProvider) {
        assertThrows(SSLException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                testSniClient(serverProvider, clientProvider, false);
            }
        });
    }

    private static void testSniClient(SslProvider sslClientProvider, SslProvider sslServerProvider, final boolean match)
            throws Throwable {
        final String sniHost = "sni.netty.io";
        SelfSignedCertificate cert = new SelfSignedCertificate();
        LocalAddress address = new LocalAddress("test");
        EventLoopGroup group = new MultithreadEventLoopGroup(1, LocalHandler.newFactory());
        SslContext sslServerContext = null;
        SslContext sslClientContext = null;

        Channel sc = null;
        Channel cc = null;
        try {
            sslServerContext = SslContextBuilder.forServer(cert.key(), cert.cert())
                    .sslProvider(sslServerProvider).build();
            final Promise<Void> promise = group.next().newPromise();
            ServerBootstrap sb = new ServerBootstrap();

            final SslContext finalContext = sslServerContext;
            sc = sb.group(group).channel(LocalServerChannel.class).childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    SslHandler handler = finalContext.newHandler(ch.bufferAllocator());
                    SSLParameters parameters = handler.engine().getSSLParameters();
                    SNIMatcher matcher = new SNIMatcher(0) {
                        @Override
                        public boolean matches(SNIServerName sniServerName) {
                            return match;
                        }
                    };
                    parameters.setSNIMatchers(Collections.singleton(matcher));
                    handler.engine().setSSLParameters(parameters);

                    ch.pipeline().addFirst(handler);
                    ch.pipeline().addLast(new ChannelHandler() {
                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                            if (evt instanceof SslHandshakeCompletionEvent) {
                                SslHandshakeCompletionEvent event = (SslHandshakeCompletionEvent) evt;
                                if (match) {
                                    if (event.isSuccess()) {
                                        promise.setSuccess(null);
                                    } else {
                                        promise.setFailure(event.cause());
                                    }
                                } else {
                                    if (event.isSuccess()) {
                                        promise.setFailure(new AssertionError("expected SSLException"));
                                    } else {
                                        Throwable cause = event.cause();
                                        if (cause instanceof SSLException) {
                                            promise.setSuccess(null);
                                        } else {
                                            promise.setFailure(
                                                    new AssertionError("cause not of type SSLException: "
                                                            + ThrowableUtil.stackTraceToString(cause)));
                                        }
                                    }
                                }
                            }
                        }
                    });
                }
            }).bind(address).get();

            sslClientContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .sslProvider(sslClientProvider).build();

            SslHandler sslHandler = new SslHandler(
                    sslClientContext.newEngine(offHeapAllocator(), sniHost, -1));
            Bootstrap cb = new Bootstrap();
            cc = cb.group(group).channel(LocalChannel.class).handler(sslHandler).connect(address).get();

            promise.asFuture().syncUninterruptibly();
            sslHandler.handshakeFuture().syncUninterruptibly();
        } catch (CompletionException e) {
            throw e.getCause();
        } finally {
            if (cc != null) {
                cc.close().syncUninterruptibly();
            }
            if (sc != null) {
                sc.close().syncUninterruptibly();
            }

            Resource.dispose(sslServerContext);
            Resource.dispose(sslClientContext);

            cert.delete();

            group.shutdownGracefully();
        }
    }

    static void assertSSLSession(boolean clientSide, SSLSession session, String name) {
        assertSSLSession(clientSide, session, new SNIHostName(name));
    }

    private static void assertSSLSession(boolean clientSide, SSLSession session, SNIServerName name) {
        assertNotNull(session);
        if (session instanceof ExtendedSSLSession) {
            ExtendedSSLSession extendedSSLSession = (ExtendedSSLSession) session;
            List<SNIServerName> names = extendedSSLSession.getRequestedServerNames();
            assertEquals(1, names.size());
            assertEquals(name, names.get(0));
            assertTrue(extendedSSLSession.getLocalSupportedSignatureAlgorithms().length > 0);
            if (clientSide) {
                assertEquals(0, extendedSSLSession.getPeerSupportedSignatureAlgorithms().length);
            } else {
                assertTrue(extendedSSLSession.getPeerSupportedSignatureAlgorithms().length >= 0);
            }
        }
    }

    static TrustManagerFactory newSniX509TrustmanagerFactory(String name) {
        return new SniX509TrustmanagerFactory(new SNIHostName(name));
    }

    private static final class SniX509TrustmanagerFactory extends SimpleTrustManagerFactory {

        private final SNIServerName name;

        SniX509TrustmanagerFactory(SNIServerName name) {
            this.name = name;
        }

        @Override
        protected void engineInit(KeyStore keyStore) throws Exception {
            // NOOP
        }

        @Override
        protected void engineInit(ManagerFactoryParameters managerFactoryParameters) throws Exception {
            // NOOP
        }

        @Override
        protected TrustManager[] engineGetTrustManagers() {
            return new TrustManager[] { new X509ExtendedTrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] x509Certificates, String s, Socket socket)
                        throws CertificateException {
                    fail();
                }

                @Override
                public void checkServerTrusted(X509Certificate[] x509Certificates, String s, Socket socket)
                        throws CertificateException {
                    fail();
                }

                @Override
                public void checkClientTrusted(X509Certificate[] x509Certificates, String s, SSLEngine sslEngine)
                        throws CertificateException {
                    fail();
                }

                @Override
                public void checkServerTrusted(X509Certificate[] x509Certificates, String s, SSLEngine sslEngine)
                        throws CertificateException {
                    assertSSLSession(sslEngine.getUseClientMode(), sslEngine.getHandshakeSession(), name);
                }

                @Override
                public void checkClientTrusted(X509Certificate[] x509Certificates, String s)
                        throws CertificateException {
                    fail();
                }

                @Override
                public void checkServerTrusted(X509Certificate[] x509Certificates, String s)
                        throws CertificateException {
                    fail();
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return EmptyArrays.EMPTY_X509_CERTIFICATES;
                }
            } };
        }
    }

    static KeyManagerFactory newSniX509KeyManagerFactory(SelfSignedCertificate cert, String hostname)
            throws NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException,
            IOException, CertificateException {
        return new SniX509KeyManagerFactory(
                new SNIHostName(hostname), SslContext.buildKeyManagerFactory(
                new X509Certificate[] { cert.cert() }, null,  cert.key(), null, null, null));
    }

    private static final class SniX509KeyManagerFactory extends KeyManagerFactory {

        SniX509KeyManagerFactory(final SNIServerName name, final KeyManagerFactory factory) {
            super(new KeyManagerFactorySpi() {
                @Override
                protected void engineInit(KeyStore keyStore, char[] chars)
                        throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
                    factory.init(keyStore, chars);
                }

                @Override
                protected void engineInit(ManagerFactoryParameters managerFactoryParameters)
                        throws InvalidAlgorithmParameterException {
                    factory.init(managerFactoryParameters);
                }

                @Override
                protected KeyManager[] engineGetKeyManagers() {
                    List<KeyManager> managers = new ArrayList<>();
                    for (final KeyManager km: factory.getKeyManagers()) {
                        if (km instanceof X509ExtendedKeyManager) {
                            managers.add(new X509ExtendedKeyManager() {
                                @Override
                                public String[] getClientAliases(String s, Principal[] principals) {
                                    return ((X509ExtendedKeyManager) km).getClientAliases(s, principals);
                                }

                                @Override
                                public String chooseClientAlias(String[] strings, Principal[] principals,
                                                                Socket socket) {
                                    return ((X509ExtendedKeyManager) km).chooseClientAlias(strings, principals, socket);
                                }

                                @Override
                                public String[] getServerAliases(String s, Principal[] principals) {
                                    return ((X509ExtendedKeyManager) km).getServerAliases(s, principals);
                                }

                                @Override
                                public String chooseServerAlias(String s, Principal[] principals, Socket socket) {
                                    return ((X509ExtendedKeyManager) km).chooseServerAlias(s, principals, socket);
                                }

                                @Override
                                public X509Certificate[] getCertificateChain(String s) {
                                    return ((X509ExtendedKeyManager) km).getCertificateChain(s);
                                }

                                @Override
                                public PrivateKey getPrivateKey(String s) {
                                    return ((X509ExtendedKeyManager) km).getPrivateKey(s);
                                }

                                @Override
                                public String chooseEngineClientAlias(String[] strings, Principal[] principals,
                                                                      SSLEngine sslEngine) {
                                    return ((X509ExtendedKeyManager) km)
                                            .chooseEngineClientAlias(strings, principals, sslEngine);
                                }

                                @Override
                                public String chooseEngineServerAlias(String s, Principal[] principals,
                                                                      SSLEngine sslEngine) {

                                    SSLSession session = sslEngine.getHandshakeSession();
                                    assertSSLSession(sslEngine.getUseClientMode(), session, name);
                                    return ((X509ExtendedKeyManager) km)
                                            .chooseEngineServerAlias(s, principals, sslEngine);
                                }
                            });
                        } else {
                            managers.add(km);
                        }
                    }
                    return managers.toArray(new KeyManager[0]);
                }
            }, factory.getProvider(), factory.getAlgorithm());
        }
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    @MethodSource("parameters")
    public void testSniClient(SslProvider sslServerProvider, SslProvider sslClientProvider) throws Exception {
        String sniHostName = "sni.netty.io";
        LocalAddress address = new LocalAddress("test");
        EventLoopGroup group = new MultithreadEventLoopGroup(1, LocalHandler.newFactory());
        SelfSignedCertificate cert = new SelfSignedCertificate();
        SslContext sslServerContext = null;
        SslContext sslClientContext = null;

        Channel sc = null;
        Channel cc = null;
        try {
            if ((sslServerProvider == SslProvider.OPENSSL || sslServerProvider == SslProvider.OPENSSL_REFCNT)
                && !OpenSsl.supportsKeyManagerFactory()) {
                sslServerContext = SslContextBuilder.forServer(cert.certificate(), cert.privateKey())
                                                    .sslProvider(sslServerProvider)
                                                    .build();
            } else {
                // The used OpenSSL version does support a KeyManagerFactory, so use it.
                KeyManagerFactory kmf = newSniX509KeyManagerFactory(cert, sniHostName);
                sslServerContext = SslContextBuilder.forServer(kmf)
                                                   .sslProvider(sslServerProvider)
                                                   .build();
            }

            final SslContext finalContext = sslServerContext;
            final Promise<String> promise = group.next().newPromise();
            ServerBootstrap sb = new ServerBootstrap();
            sc = sb.group(group).channel(LocalServerChannel.class).childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addFirst(new SniHandler(input -> {
                        promise.setSuccess(input);
                        return finalContext;
                    }));
                }
            }).bind(address).get();

            TrustManagerFactory tmf = newSniX509TrustmanagerFactory(sniHostName);
            sslClientContext = SslContextBuilder.forClient().trustManager(tmf)
                                                     .sslProvider(sslClientProvider).build();
            Bootstrap cb = new Bootstrap();

            SslHandler handler = new SslHandler(
                    sslClientContext.newEngine(offHeapAllocator(), sniHostName, -1));
            cc = cb.group(group).channel(LocalChannel.class).handler(handler).connect(address).get();
            assertEquals(sniHostName, promise.asFuture().syncUninterruptibly().getNow());

            // After we are done with handshaking getHandshakeSession() should return null.
            handler.handshakeFuture().syncUninterruptibly();
            assertNull(handler.engine().getHandshakeSession());

            assertSSLSession(
                    handler.engine().getUseClientMode(), handler.engine().getSession(), sniHostName);
        } finally {
            if (cc != null) {
                cc.close().syncUninterruptibly();
            }
            if (sc != null) {
                sc.close().syncUninterruptibly();
            }
            Resource.dispose(sslServerContext);
            Resource.dispose(sslClientContext);

            cert.delete();

            group.shutdownGracefully();
        }
    }
}
