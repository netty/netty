/*
 * Copyright 2016 The Netty Project
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

import io.netty.buffer.ByteBufAllocator;
import io.netty.internal.tcnative.CertificateCallback;
import io.netty.internal.tcnative.SSL;
import io.netty.internal.tcnative.SSLContext;
import io.netty.internal.tcnative.SniHostNameMatcher;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SuppressJava6Requirement;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * A server-side {@link SslContext} which uses OpenSSL's SSL/TLS implementation.
 * <p>Instances of this class must be {@link #release() released} or else native memory will leak!
 *
 * <p>Instances of this class <strong>must not</strong> be released before any {@link ReferenceCountedOpenSslEngine}
 * which depends upon the instance of this class is released. Otherwise if any method of
 * {@link ReferenceCountedOpenSslEngine} is called which uses this class's JNI resources the JVM may crash.
 */
public final class ReferenceCountedOpenSslServerContext extends ReferenceCountedOpenSslContext {
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(ReferenceCountedOpenSslServerContext.class);
    private static final byte[] ID = {'n', 'e', 't', 't', 'y'};
    private final OpenSslServerSessionContext sessionContext;

    private static final boolean ENABLE_SESSION_TICKET =
            SystemPropertyUtil.getBoolean("jdk.tls.server.enableSessionTicketExtension", false);

    ReferenceCountedOpenSslServerContext(
            X509Certificate[] trustCertCollection, TrustManagerFactory trustManagerFactory,
            X509Certificate[] keyCertChain, PrivateKey key, String keyPassword, KeyManagerFactory keyManagerFactory,
            Iterable<String> ciphers, CipherSuiteFilter cipherFilter, ApplicationProtocolConfig apn,
            long sessionCacheSize, long sessionTimeout, ClientAuth clientAuth, String[] protocols, boolean startTls,
            boolean enableOcsp, String keyStore) throws SSLException {
        this(trustCertCollection, trustManagerFactory, keyCertChain, key, keyPassword, keyManagerFactory, ciphers,
                cipherFilter, toNegotiator(apn), sessionCacheSize, sessionTimeout, clientAuth, protocols, startTls,
                enableOcsp, keyStore);
    }

    ReferenceCountedOpenSslServerContext(
            X509Certificate[] trustCertCollection, TrustManagerFactory trustManagerFactory,
            X509Certificate[] keyCertChain, PrivateKey key, String keyPassword, KeyManagerFactory keyManagerFactory,
            Iterable<String> ciphers, CipherSuiteFilter cipherFilter, OpenSslApplicationProtocolNegotiator apn,
            long sessionCacheSize, long sessionTimeout, ClientAuth clientAuth, String[] protocols, boolean startTls,
            boolean enableOcsp, String keyStore) throws SSLException {
        super(ciphers, cipherFilter, apn, SSL.SSL_MODE_SERVER, keyCertChain,
              clientAuth, protocols, startTls, enableOcsp, true);
        // Create a new SSL_CTX and configure it.
        boolean success = false;
        try {
            sessionContext = newSessionContext(this, ctx, engineMap, trustCertCollection, trustManagerFactory,
                    keyCertChain, key, keyPassword, keyManagerFactory, keyStore,
                    sessionCacheSize, sessionTimeout);
            if (ENABLE_SESSION_TICKET) {
                sessionContext.setTicketKeys();
            }
            success = true;
        } finally {
            if (!success) {
                release();
            }
        }
    }

    @Override
    public OpenSslServerSessionContext sessionContext() {
        return sessionContext;
    }

    static OpenSslServerSessionContext newSessionContext(ReferenceCountedOpenSslContext thiz, long ctx,
                                                         OpenSslEngineMap engineMap,
                                                         X509Certificate[] trustCertCollection,
                                                         TrustManagerFactory trustManagerFactory,
                                                         X509Certificate[] keyCertChain, PrivateKey key,
                                                         String keyPassword, KeyManagerFactory keyManagerFactory,
                                                         String keyStore, long sessionCacheSize, long sessionTimeout)
            throws SSLException {
        OpenSslKeyMaterialProvider keyMaterialProvider = null;
        try {
            try {
                SSLContext.setVerify(ctx, SSL.SSL_CVERIFY_NONE, VERIFY_DEPTH);
                if (!OpenSsl.useKeyManagerFactory()) {
                    if (keyManagerFactory != null) {
                        throw new IllegalArgumentException(
                                "KeyManagerFactory not supported");
                    }
                    checkNotNull(keyCertChain, "keyCertChain");

                    setKeyMaterial(ctx, keyCertChain, key, keyPassword);
                } else {
                    // javadocs state that keyManagerFactory has precedent over keyCertChain, and we must have a
                    // keyManagerFactory for the server so build one if it is not specified.
                    if (keyManagerFactory == null) {
                        char[] keyPasswordChars = keyStorePassword(keyPassword);
                        KeyStore ks = buildKeyStore(keyCertChain, key, keyPasswordChars, keyStore);
                        if (ks.aliases().hasMoreElements()) {
                            keyManagerFactory = new OpenSslX509KeyManagerFactory();
                        } else {
                            keyManagerFactory = new OpenSslCachingX509KeyManagerFactory(
                                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()));
                        }
                        keyManagerFactory.init(ks, keyPasswordChars);
                    }
                    keyMaterialProvider = providerFor(keyManagerFactory, keyPassword);

                    SSLContext.setCertificateCallback(ctx, new OpenSslServerCertificateCallback(
                            engineMap, new OpenSslKeyMaterialManager(keyMaterialProvider)));
                }
            } catch (Exception e) {
                throw new SSLException("failed to set certificate and key", e);
            }
            try {
                if (trustCertCollection != null) {
                    trustManagerFactory = buildTrustManagerFactory(trustCertCollection, trustManagerFactory, keyStore);
                } else if (trustManagerFactory == null) {
                    // Mimic the way SSLContext.getInstance(KeyManager[], null, null) works
                    trustManagerFactory = TrustManagerFactory.getInstance(
                            TrustManagerFactory.getDefaultAlgorithm());
                    trustManagerFactory.init((KeyStore) null);
                }

                final X509TrustManager manager = chooseTrustManager(trustManagerFactory.getTrustManagers());

                // IMPORTANT: The callbacks set for verification must be static to prevent memory leak as
                //            otherwise the context can never be collected. This is because the JNI code holds
                //            a global reference to the callbacks.
                //
                //            See https://github.com/netty/netty/issues/5372

                setVerifyCallback(ctx, engineMap, manager);

                X509Certificate[] issuers = manager.getAcceptedIssuers();
                if (issuers != null && issuers.length > 0) {
                    long bio = 0;
                    try {
                        bio = toBIO(ByteBufAllocator.DEFAULT, issuers);
                        if (!SSLContext.setCACertificateBio(ctx, bio)) {
                            throw new SSLException("unable to setup accepted issuers for trustmanager " + manager);
                        }
                    } finally {
                        freeBio(bio);
                    }
                }

                if (PlatformDependent.javaVersion() >= 8) {
                    // Only do on Java8+ as SNIMatcher is not supported in earlier releases.
                    // IMPORTANT: The callbacks set for hostname matching must be static to prevent memory leak as
                    //            otherwise the context can never be collected. This is because the JNI code holds
                    //            a global reference to the matcher.
                    SSLContext.setSniHostnameMatcher(ctx, new OpenSslSniHostnameMatcher(engineMap));
                }
            } catch (SSLException e) {
                throw e;
            } catch (Exception e) {
                throw new SSLException("unable to setup trustmanager", e);
            }

            OpenSslServerSessionContext sessionContext = new OpenSslServerSessionContext(thiz, keyMaterialProvider);
            sessionContext.setSessionIdContext(ID);
            // Enable session caching by default
            sessionContext.setSessionCacheEnabled(true);
            if (sessionCacheSize > 0) {
                sessionContext.setSessionCacheSize((int) Math.min(sessionCacheSize, Integer.MAX_VALUE));
            }
            if (sessionTimeout > 0) {
                sessionContext.setSessionTimeout((int) Math.min(sessionTimeout, Integer.MAX_VALUE));
            }

            keyMaterialProvider = null;

            return sessionContext;
        } finally {
            if (keyMaterialProvider != null) {
                keyMaterialProvider.destroy();
            }
        }
    }

    @SuppressJava6Requirement(reason = "Guarded by java version check")
    private static void setVerifyCallback(long ctx, OpenSslEngineMap engineMap, X509TrustManager manager) {
        // Use this to prevent an error when running on java < 7
        if (useExtendedTrustManager(manager)) {
            SSLContext.setCertVerifyCallback(ctx, new ExtendedTrustManagerVerifyCallback(
                    engineMap, (X509ExtendedTrustManager) manager));
        } else {
            SSLContext.setCertVerifyCallback(ctx, new TrustManagerVerifyCallback(engineMap, manager));
        }
    }

    private static final class OpenSslServerCertificateCallback implements CertificateCallback {
        private final OpenSslEngineMap engineMap;
        private final OpenSslKeyMaterialManager keyManagerHolder;

        OpenSslServerCertificateCallback(OpenSslEngineMap engineMap, OpenSslKeyMaterialManager keyManagerHolder) {
            this.engineMap = engineMap;
            this.keyManagerHolder = keyManagerHolder;
        }

        @Override
        public void handle(long ssl, byte[] keyTypeBytes, byte[][] asn1DerEncodedPrincipals) throws Exception {
            final ReferenceCountedOpenSslEngine engine = engineMap.get(ssl);
            if (engine == null) {
                // Maybe null if destroyed in the meantime.
                return;
            }
            engine.setupHandshakeSession();
            try {
                // For now we just ignore the asn1DerEncodedPrincipals as this is kind of inline with what the
                // OpenJDK SSLEngineImpl does.
                keyManagerHolder.setKeyMaterialServerSide(engine);
            } catch (Throwable cause) {
                logger.debug("Failed to set the server-side key material", cause);
                engine.initHandshakeException(cause);
            }
        }
    }

    private static final class TrustManagerVerifyCallback extends AbstractCertificateVerifier {
        private final X509TrustManager manager;

        TrustManagerVerifyCallback(OpenSslEngineMap engineMap, X509TrustManager manager) {
            super(engineMap);
            this.manager = manager;
        }

        @Override
        void verify(ReferenceCountedOpenSslEngine engine, X509Certificate[] peerCerts, String auth)
                throws Exception {
            manager.checkClientTrusted(peerCerts, auth);
        }
    }

    @SuppressJava6Requirement(reason = "Usage guarded by java version check")
    private static final class ExtendedTrustManagerVerifyCallback extends AbstractCertificateVerifier {
        private final X509ExtendedTrustManager manager;

        ExtendedTrustManagerVerifyCallback(OpenSslEngineMap engineMap, X509ExtendedTrustManager manager) {
            super(engineMap);
            this.manager = OpenSslTlsv13X509ExtendedTrustManager.wrap(manager);
        }

        @Override
        void verify(ReferenceCountedOpenSslEngine engine, X509Certificate[] peerCerts, String auth)
                throws Exception {
            manager.checkClientTrusted(peerCerts, auth, engine);
        }
    }

    private static final class OpenSslSniHostnameMatcher implements SniHostNameMatcher {
        private final OpenSslEngineMap engineMap;

        OpenSslSniHostnameMatcher(OpenSslEngineMap engineMap) {
            this.engineMap = engineMap;
        }

        @Override
        public boolean match(long ssl, String hostname) {
            ReferenceCountedOpenSslEngine engine = engineMap.get(ssl);
            if (engine != null) {
                // TODO: In the next release of tcnative we should pass the byte[] directly in and not use a String.
                return engine.checkSniHostnameMatch(hostname.getBytes(CharsetUtil.UTF_8));
            }
            logger.warn("No ReferenceCountedOpenSslEngine found for SSL pointer: {}", ssl);
            return false;
        }
    }
}
