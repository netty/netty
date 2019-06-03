/*
 * Copyright 2017 The Netty Project
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
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.handler.ssl.util.SimpleTrustManagerFactory;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.ThrowableUtil;
import org.junit.Assert;

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
import java.util.Collections;
import java.util.List;

/**
 * In extra class to be able to run tests with java7 without trying to load classes that not exists in java7.
 */
final class SniClientJava8TestUtil {

    private SniClientJava8TestUtil() { }

    static void testSniClient(SslProvider sslClientProvider, SslProvider sslServerProvider, final boolean match)
            throws Exception {
        final String sniHost = "sni.netty.io";
        SelfSignedCertificate cert = new SelfSignedCertificate();
        LocalAddress address = new LocalAddress("test");
        EventLoopGroup group = new DefaultEventLoopGroup(1);
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
                    SslHandler handler = finalContext.newHandler(ch.alloc());
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
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
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
            }).bind(address).syncUninterruptibly().channel();

            sslClientContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .sslProvider(sslClientProvider).build();

            SslHandler sslHandler = new SslHandler(
                    sslClientContext.newEngine(ByteBufAllocator.DEFAULT, sniHost, -1));
            Bootstrap cb = new Bootstrap();
            cc = cb.group(group).channel(LocalChannel.class).handler(sslHandler)
                    .connect(address).syncUninterruptibly().channel();

            promise.syncUninterruptibly();
            sslHandler.handshakeFuture().syncUninterruptibly();
        } finally {
            if (cc != null) {
                cc.close().syncUninterruptibly();
            }
            if (sc != null) {
                sc.close().syncUninterruptibly();
            }

            ReferenceCountUtil.release(sslServerContext);
            ReferenceCountUtil.release(sslClientContext);

            cert.delete();

            group.shutdownGracefully();
        }
    }

    static void assertSSLSession(boolean clientSide, SSLSession session, String name) {
        assertSSLSession(clientSide, session, new SNIHostName(name));
    }

    private static void assertSSLSession(boolean clientSide, SSLSession session, SNIServerName name) {
        Assert.assertNotNull(session);
        if (session instanceof ExtendedSSLSession) {
            ExtendedSSLSession extendedSSLSession = (ExtendedSSLSession) session;
            List<SNIServerName> names = extendedSSLSession.getRequestedServerNames();
            Assert.assertEquals(1, names.size());
            Assert.assertEquals(name, names.get(0));
            Assert.assertTrue(extendedSSLSession.getLocalSupportedSignatureAlgorithms().length > 0);
            if (clientSide) {
                Assert.assertEquals(0, extendedSSLSession.getPeerSupportedSignatureAlgorithms().length);
            } else {
                Assert.assertTrue(extendedSSLSession.getPeerSupportedSignatureAlgorithms().length >= 0);
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
                    Assert.fail();
                }

                @Override
                public void checkServerTrusted(X509Certificate[] x509Certificates, String s, Socket socket)
                        throws CertificateException {
                    Assert.fail();
                }

                @Override
                public void checkClientTrusted(X509Certificate[] x509Certificates, String s, SSLEngine sslEngine)
                        throws CertificateException {
                    Assert.fail();
                }

                @Override
                public void checkServerTrusted(X509Certificate[] x509Certificates, String s, SSLEngine sslEngine)
                        throws CertificateException {
                    assertSSLSession(sslEngine.getUseClientMode(), sslEngine.getHandshakeSession(), name);
                }

                @Override
                public void checkClientTrusted(X509Certificate[] x509Certificates, String s)
                        throws CertificateException {
                    Assert.fail();
                }

                @Override
                public void checkServerTrusted(X509Certificate[] x509Certificates, String s)
                        throws CertificateException {
                    Assert.fail();
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
                new X509Certificate[] { cert.cert() }, cert.key(), null, null, null));
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
                    List<KeyManager> managers = new ArrayList<KeyManager>();
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
}
