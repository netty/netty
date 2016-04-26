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

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.apache.tomcat.jni.CertificateRequestedCallback;
import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.jni.SSLContext;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.Set;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

/**
 * A client-side {@link SslContext} which uses OpenSSL's SSL/TLS implementation.
 * <p>Instances of this class must be {@link #release() released} or else native memory will leak!
 *
 * <p>Instances of this class <strong>must not</strong> be released before any {@link ReferenceCountedOpenSslEngine}
 * which depends upon the instance of this class is released. Otherwise if any method of
 * {@link ReferenceCountedOpenSslEngine} is called which uses this class's JNI resources the JVM may crash.
 */
public final class ReferenceCountedOpenSslClientContext extends ReferenceCountedOpenSslContext {
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(ReferenceCountedOpenSslClientContext.class);
    private final OpenSslSessionContext sessionContext;

    ReferenceCountedOpenSslClientContext(X509Certificate[] trustCertCollection, TrustManagerFactory trustManagerFactory,
                                         X509Certificate[] keyCertChain, PrivateKey key, String keyPassword,
                                         KeyManagerFactory keyManagerFactory, Iterable<String> ciphers,
                                         CipherSuiteFilter cipherFilter, ApplicationProtocolConfig apn,
                                         long sessionCacheSize, long sessionTimeout)
            throws SSLException {
        super(ciphers, cipherFilter, apn, sessionCacheSize, sessionTimeout, SSL.SSL_MODE_CLIENT, keyCertChain,
              ClientAuth.NONE, false, true);
        boolean success = false;
        try {
            sessionContext = newSessionContext(this, ctx, engineMap, trustCertCollection, trustManagerFactory,
                                               keyCertChain, key, keyPassword, keyManagerFactory);
            success = true;
        } finally {
            if (!success) {
                release();
            }
        }
    }

    @Override
    OpenSslKeyMaterialManager keyMaterialManager() {
        return null;
    }

    @Override
    public OpenSslSessionContext sessionContext() {
        return sessionContext;
    }

    static OpenSslSessionContext newSessionContext(ReferenceCountedOpenSslContext thiz, long ctx,
                                                   OpenSslEngineMap engineMap,
                                                   X509Certificate[] trustCertCollection,
                                                   TrustManagerFactory trustManagerFactory,
                                                   X509Certificate[] keyCertChain, PrivateKey key, String keyPassword,
                                                   KeyManagerFactory keyManagerFactory) throws SSLException {
        if (key == null && keyCertChain != null || key != null && keyCertChain == null) {
            throw new IllegalArgumentException(
                    "Either both keyCertChain and key needs to be null or none of them");
        }
        synchronized (ReferenceCountedOpenSslContext.class) {
            try {
                if (!OpenSsl.useKeyManagerFactory()) {
                    if (keyManagerFactory != null) {
                        throw new IllegalArgumentException(
                                "KeyManagerFactory not supported");
                    }
                    if (keyCertChain != null/* && key != null*/) {
                        setKeyMaterial(ctx, keyCertChain, key, keyPassword);
                    }
                } else {
                    // javadocs state that keyManagerFactory has precedent over keyCertChain
                    if (keyManagerFactory == null && keyCertChain != null) {
                        keyManagerFactory = buildKeyManagerFactory(
                                keyCertChain, key, keyPassword, keyManagerFactory);
                    }

                    if (keyManagerFactory != null) {
                        X509KeyManager keyManager = chooseX509KeyManager(keyManagerFactory.getKeyManagers());
                        OpenSslKeyMaterialManager materialManager = useExtendedKeyManager(keyManager) ?
                                new OpenSslExtendedKeyMaterialManager(
                                        (X509ExtendedKeyManager) keyManager, keyPassword) :
                                new OpenSslKeyMaterialManager(keyManager, keyPassword);
                        SSLContext.setCertRequestedCallback(ctx, new OpenSslCertificateRequestedCallback(
                                engineMap, materialManager));
                    }
                }
            } catch (Exception e) {
                throw new SSLException("failed to set certificate and key", e);
            }

            SSLContext.setVerify(ctx, SSL.SSL_VERIFY_NONE, VERIFY_DEPTH);

            try {
                if (trustCertCollection != null) {
                    trustManagerFactory = buildTrustManagerFactory(trustCertCollection, trustManagerFactory);
                } else if (trustManagerFactory == null) {
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
        return new OpenSslClientSessionContext(thiz);
    }

    // No cache is currently supported for client side mode.
    static final class OpenSslClientSessionContext extends OpenSslSessionContext {
        OpenSslClientSessionContext(ReferenceCountedOpenSslContext context) {
            super(context);
        }

        @Override
        public void setSessionTimeout(int seconds) {
            if (seconds < 0) {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public int getSessionTimeout() {
            return 0;
        }

        @Override
        public void setSessionCacheSize(int size)  {
            if (size < 0) {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public int getSessionCacheSize() {
            return 0;
        }

        @Override
        public void setSessionCacheEnabled(boolean enabled) {
            // ignored
        }

        @Override
        public boolean isSessionCacheEnabled() {
            return false;
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
            manager.checkServerTrusted(peerCerts, auth);
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
            manager.checkServerTrusted(peerCerts, auth, engine);
        }
    }

    private static final class OpenSslCertificateRequestedCallback implements CertificateRequestedCallback {
        private final OpenSslEngineMap engineMap;
        private final OpenSslKeyMaterialManager keyManagerHolder;

        OpenSslCertificateRequestedCallback(OpenSslEngineMap engineMap, OpenSslKeyMaterialManager keyManagerHolder) {
            this.engineMap = engineMap;
            this.keyManagerHolder = keyManagerHolder;
        }

        @Override
        public KeyMaterial requested(long ssl, byte[] keyTypeBytes, byte[][] asn1DerEncodedPrincipals) {
            final ReferenceCountedOpenSslEngine engine = engineMap.get(ssl);
            try {
                final Set<String> keyTypesSet = supportedClientKeyTypes(keyTypeBytes);
                final String[] keyTypes = keyTypesSet.toArray(new String[keyTypesSet.size()]);
                final X500Principal[] issuers;
                if (asn1DerEncodedPrincipals == null) {
                    issuers = null;
                } else {
                    issuers = new X500Principal[asn1DerEncodedPrincipals.length];
                    for (int i = 0; i < asn1DerEncodedPrincipals.length; i++) {
                        issuers[i] = new X500Principal(asn1DerEncodedPrincipals[i]);
                    }
                }
                return keyManagerHolder.keyMaterial(engine, keyTypes, issuers);
            } catch (Throwable cause) {
                logger.debug("request of key failed", cause);
                SSLHandshakeException e = new SSLHandshakeException("General OpenSslEngine problem");
                e.initCause(cause);
                engine.handshakeException = e;
                return null;
            }
        }

        /**
         * Gets the supported key types for client certificates.
         *
         * @param clientCertificateTypes {@code ClientCertificateType} values provided by the server.
         *        See https://www.ietf.org/assignments/tls-parameters/tls-parameters.xml.
         * @return supported key types that can be used in {@code X509KeyManager.chooseClientAlias} and
         *         {@code X509ExtendedKeyManager.chooseEngineClientAlias}.
         */
        private static Set<String> supportedClientKeyTypes(byte[] clientCertificateTypes) {
            Set<String> result = new HashSet<String>(clientCertificateTypes.length);
            for (byte keyTypeCode : clientCertificateTypes) {
                String keyType = clientKeyType(keyTypeCode);
                if (keyType == null) {
                    // Unsupported client key type -- ignore
                    continue;
                }
                result.add(keyType);
            }
            return result;
        }

        private static String clientKeyType(byte clientCertificateType) {
            // See also http://www.ietf.org/assignments/tls-parameters/tls-parameters.xml
            switch (clientCertificateType) {
                case CertificateRequestedCallback.TLS_CT_RSA_SIGN:
                    return OpenSslKeyMaterialManager.KEY_TYPE_RSA; // RFC rsa_sign
                case CertificateRequestedCallback.TLS_CT_RSA_FIXED_DH:
                    return OpenSslKeyMaterialManager.KEY_TYPE_DH_RSA; // RFC rsa_fixed_dh
                case CertificateRequestedCallback.TLS_CT_ECDSA_SIGN:
                    return OpenSslKeyMaterialManager.KEY_TYPE_EC; // RFC ecdsa_sign
                case CertificateRequestedCallback.TLS_CT_RSA_FIXED_ECDH:
                    return OpenSslKeyMaterialManager.KEY_TYPE_EC_RSA; // RFC rsa_fixed_ecdh
                case CertificateRequestedCallback.TLS_CT_ECDSA_FIXED_ECDH:
                    return OpenSslKeyMaterialManager.KEY_TYPE_EC_EC; // RFC ecdsa_fixed_ecdh
                default:
                    return null;
            }
        }
    }
}
