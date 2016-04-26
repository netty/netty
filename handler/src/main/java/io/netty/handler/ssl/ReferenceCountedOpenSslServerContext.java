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

import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.jni.SSLContext;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509KeyManager;
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
    private static final byte[] ID = {'n', 'e', 't', 't', 'y'};
    private final OpenSslServerSessionContext sessionContext;
    private final OpenSslKeyMaterialManager keyMaterialManager;

    ReferenceCountedOpenSslServerContext(
            X509Certificate[] trustCertCollection, TrustManagerFactory trustManagerFactory,
            X509Certificate[] keyCertChain, PrivateKey key, String keyPassword, KeyManagerFactory keyManagerFactory,
            Iterable<String> ciphers, CipherSuiteFilter cipherFilter, ApplicationProtocolConfig apn,
            long sessionCacheSize, long sessionTimeout, ClientAuth clientAuth, boolean startTls) throws SSLException {
        this(trustCertCollection, trustManagerFactory, keyCertChain, key, keyPassword, keyManagerFactory, ciphers,
                cipherFilter, toNegotiator(apn), sessionCacheSize, sessionTimeout, clientAuth, startTls);
    }

    private ReferenceCountedOpenSslServerContext(
            X509Certificate[] trustCertCollection, TrustManagerFactory trustManagerFactory,
            X509Certificate[] keyCertChain, PrivateKey key, String keyPassword, KeyManagerFactory keyManagerFactory,
            Iterable<String> ciphers, CipherSuiteFilter cipherFilter, OpenSslApplicationProtocolNegotiator apn,
            long sessionCacheSize, long sessionTimeout, ClientAuth clientAuth, boolean startTls) throws SSLException {
        super(ciphers, cipherFilter, apn, sessionCacheSize, sessionTimeout, SSL.SSL_MODE_SERVER, keyCertChain,
              clientAuth, startTls, true);
        // Create a new SSL_CTX and configure it.
        boolean success = false;
        try {
            ServerContext context = newSessionContext(this, ctx, engineMap, trustCertCollection, trustManagerFactory,
                                                      keyCertChain, key, keyPassword, keyManagerFactory);
            sessionContext = context.sessionContext;
            keyMaterialManager = context.keyMaterialManager;
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

    @Override
    OpenSslKeyMaterialManager keyMaterialManager() {
        return keyMaterialManager;
    }

    static final class ServerContext {
        OpenSslServerSessionContext sessionContext;
        OpenSslKeyMaterialManager keyMaterialManager;
    }

    static ServerContext newSessionContext(ReferenceCountedOpenSslContext thiz, long ctx, OpenSslEngineMap engineMap,
                                           X509Certificate[] trustCertCollection,
                                           TrustManagerFactory trustManagerFactory,
                                           X509Certificate[] keyCertChain, PrivateKey key,
                                           String keyPassword, KeyManagerFactory keyManagerFactory)
            throws SSLException {
        ServerContext result = new ServerContext();
        synchronized (ReferenceCountedOpenSslContext.class) {
            try {
                SSLContext.setVerify(ctx, SSL.SSL_CVERIFY_NONE, VERIFY_DEPTH);
                if (!OpenSsl.useKeyManagerFactory()) {
                    if (keyManagerFactory != null) {
                        throw new IllegalArgumentException(
                                "KeyManagerFactory not supported");
                    }
                    checkNotNull(keyCertChain, "keyCertChain");

                        /* Set certificate verification policy. */
                    SSLContext.setVerify(ctx, SSL.SSL_CVERIFY_NONE, VERIFY_DEPTH);

                    setKeyMaterial(ctx, keyCertChain, key, keyPassword);
                } else {
                    // javadocs state that keyManagerFactory has precedent over keyCertChain, and we must have a
                    // keyManagerFactory for the server so build one if it is not specified.
                    if (keyManagerFactory == null) {
                        keyManagerFactory = buildKeyManagerFactory(
                                keyCertChain, key, keyPassword, keyManagerFactory);
                    }
                    X509KeyManager keyManager = chooseX509KeyManager(keyManagerFactory.getKeyManagers());
                    result.keyMaterialManager = useExtendedKeyManager(keyManager) ?
                            new OpenSslExtendedKeyMaterialManager(
                                    (X509ExtendedKeyManager) keyManager, keyPassword) :
                            new OpenSslKeyMaterialManager(keyManager, keyPassword);
                }
            } catch (Exception e) {
                throw new SSLException("failed to set certificate and key", e);
            }
            try {
                if (trustCertCollection != null) {
                    trustManagerFactory = buildTrustManagerFactory(trustCertCollection, trustManagerFactory);
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

                // Use this to prevent an error when running on java < 7
                if (useExtendedTrustManager(manager)) {
                    SSLContext.setCertVerifyCallback(ctx,
                            new ExtendedTrustManagerVerifyCallback(engineMap, (X509ExtendedTrustManager) manager));
                } else {
                    SSLContext.setCertVerifyCallback(ctx, new TrustManagerVerifyCallback(engineMap, manager));
                }
            } catch (Exception e) {
                throw new SSLException("unable to setup trustmanager", e);
            }
        }

        result.sessionContext = new OpenSslServerSessionContext(thiz);
        result.sessionContext.setSessionIdContext(ID);
        return result;
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

    private static final class ExtendedTrustManagerVerifyCallback extends AbstractCertificateVerifier {
        private final X509ExtendedTrustManager manager;

        ExtendedTrustManagerVerifyCallback(OpenSslEngineMap engineMap, X509ExtendedTrustManager manager) {
            super(engineMap);
            this.manager = manager;
        }

        @Override
        void verify(ReferenceCountedOpenSslEngine engine, X509Certificate[] peerCerts, String auth)
                throws Exception {
            manager.checkClientTrusted(peerCerts, auth, engine);
        }
    }
}
