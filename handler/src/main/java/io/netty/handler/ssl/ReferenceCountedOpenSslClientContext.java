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
package io.netty.handler.ssl;

import io.netty.internal.tcnative.CertificateCallback;
import io.netty.util.internal.SuppressJava6Requirement;
import io.netty.internal.tcnative.SSL;
import io.netty.internal.tcnative.SSLContext;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
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

    private static final Set<String> SUPPORTED_KEY_TYPES = Collections.unmodifiableSet(new LinkedHashSet<String>(
            Arrays.asList(OpenSslKeyMaterialManager.KEY_TYPE_RSA,
                          OpenSslKeyMaterialManager.KEY_TYPE_DH_RSA,
                          OpenSslKeyMaterialManager.KEY_TYPE_EC,
                          OpenSslKeyMaterialManager.KEY_TYPE_EC_RSA,
                          OpenSslKeyMaterialManager.KEY_TYPE_EC_EC)));

    private final OpenSslSessionContext sessionContext;

    ReferenceCountedOpenSslClientContext(X509Certificate[] trustCertCollection, TrustManagerFactory trustManagerFactory,
                                         X509Certificate[] keyCertChain, PrivateKey key, String keyPassword,
                                         KeyManagerFactory keyManagerFactory, Iterable<String> ciphers,
                                         CipherSuiteFilter cipherFilter, ApplicationProtocolConfig apn,
                                         String[] protocols, long sessionCacheSize, long sessionTimeout,
                                         boolean enableOcsp, String keyStore,
                                         Map.Entry<SslContextOption<?>, Object>... options) throws SSLException {
        super(ciphers, cipherFilter, toNegotiator(apn), SSL.SSL_MODE_CLIENT, keyCertChain,
              ClientAuth.NONE, protocols, false, enableOcsp, true, options);
        boolean success = false;
        try {
            sessionContext = newSessionContext(this, ctx, engineMap, trustCertCollection, trustManagerFactory,
                                               keyCertChain, key, keyPassword, keyManagerFactory, keyStore,
                                               sessionCacheSize, sessionTimeout);
            success = true;
        } finally {
            if (!success) {
                release();
            }
        }
    }

    @Override
    public OpenSslSessionContext sessionContext() {
        return sessionContext;
    }

    static OpenSslSessionContext newSessionContext(ReferenceCountedOpenSslContext thiz, long ctx,
                                                   OpenSslEngineMap engineMap,
                                                   X509Certificate[] trustCertCollection,
                                                   TrustManagerFactory trustManagerFactory,
                                                   X509Certificate[] keyCertChain, PrivateKey key,
                                                   String keyPassword, KeyManagerFactory keyManagerFactory,
                                                   String keyStore, long sessionCacheSize, long sessionTimeout)
            throws SSLException {
        if (key == null && keyCertChain != null || key != null && keyCertChain == null) {
            throw new IllegalArgumentException(
                    "Either both keyCertChain and key needs to be null or none of them");
        }
        OpenSslKeyMaterialProvider keyMaterialProvider = null;
        try {
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
                        char[] keyPasswordChars = keyStorePassword(keyPassword);
                        KeyStore ks = buildKeyStore(keyCertChain, key, keyPasswordChars, keyStore);
                        if (ks.aliases().hasMoreElements()) {
                            keyManagerFactory = new OpenSslX509KeyManagerFactory();
                        } else {
                            keyManagerFactory = new OpenSslCachingX509KeyManagerFactory(
                                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()));
                        }
                        keyManagerFactory.init(ks, keyPasswordChars);
                        keyMaterialProvider = providerFor(keyManagerFactory, keyPassword);
                    } else if (keyManagerFactory != null) {
                        keyMaterialProvider = providerFor(keyManagerFactory, keyPassword);
                    }

                    if (keyMaterialProvider != null) {
                        OpenSslKeyMaterialManager materialManager = new OpenSslKeyMaterialManager(keyMaterialProvider);
                        SSLContext.setCertificateCallback(ctx, new OpenSslClientCertificateCallback(
                                engineMap, materialManager));
                    }
                }
            } catch (Exception e) {
                throw new SSLException("failed to set certificate and key", e);
            }

            // On the client side we always need to use SSL_CVERIFY_OPTIONAL (which will translate to SSL_VERIFY_PEER)
            // to ensure that when the TrustManager throws we will produce the correct alert back to the server.
            //
            // See:
            //   - https://www.openssl.org/docs/man1.0.2/man3/SSL_CTX_set_verify.html
            //   - https://github.com/netty/netty/issues/8942
            SSLContext.setVerify(ctx, SSL.SSL_CVERIFY_OPTIONAL, VERIFY_DEPTH);

            try {
                if (trustCertCollection != null) {
                    trustManagerFactory = buildTrustManagerFactory(trustCertCollection, trustManagerFactory, keyStore);
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

                setVerifyCallback(ctx, engineMap, manager);
            } catch (Exception e) {
                if (keyMaterialProvider != null) {
                    keyMaterialProvider.destroy();
                }
                throw new SSLException("unable to setup trustmanager", e);
            }
            OpenSslClientSessionContext context = new OpenSslClientSessionContext(thiz, keyMaterialProvider);
            context.setSessionCacheEnabled(CLIENT_ENABLE_SESSION_CACHE);
            if (sessionCacheSize > 0) {
                context.setSessionCacheSize((int) Math.min(sessionCacheSize, Integer.MAX_VALUE));
            }
            if (sessionTimeout > 0) {
                context.setSessionTimeout((int) Math.min(sessionTimeout, Integer.MAX_VALUE));
            }

            if (CLIENT_ENABLE_SESSION_TICKET) {
                context.setTicketKeys();
            }

            keyMaterialProvider = null;
            return context;
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
            SSLContext.setCertVerifyCallback(ctx,
                    new ExtendedTrustManagerVerifyCallback(engineMap, (X509ExtendedTrustManager) manager));
        } else {
            SSLContext.setCertVerifyCallback(ctx, new TrustManagerVerifyCallback(engineMap, manager));
        }
    }

    static final class OpenSslClientSessionContext extends OpenSslSessionContext {
        OpenSslClientSessionContext(ReferenceCountedOpenSslContext context, OpenSslKeyMaterialProvider provider) {
            super(context, provider, SSL.SSL_SESS_CACHE_CLIENT, new OpenSslClientSessionCache(context.engineMap));
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
            manager.checkServerTrusted(peerCerts, auth, engine);
        }
    }

    private static final class OpenSslClientCertificateCallback implements CertificateCallback {
        private final OpenSslEngineMap engineMap;
        private final OpenSslKeyMaterialManager keyManagerHolder;

        OpenSslClientCertificateCallback(OpenSslEngineMap engineMap, OpenSslKeyMaterialManager keyManagerHolder) {
            this.engineMap = engineMap;
            this.keyManagerHolder = keyManagerHolder;
        }

        @Override
        public void handle(long ssl, byte[] keyTypeBytes, byte[][] asn1DerEncodedPrincipals) throws Exception {
            final ReferenceCountedOpenSslEngine engine = engineMap.get(ssl);
            // May be null if it was destroyed in the meantime.
            if (engine == null) {
                return;
            }
            try {
                final Set<String> keyTypesSet = supportedClientKeyTypes(keyTypeBytes);
                final String[] keyTypes = keyTypesSet.toArray(new String[0]);
                final X500Principal[] issuers;
                if (asn1DerEncodedPrincipals == null) {
                    issuers = null;
                } else {
                    issuers = new X500Principal[asn1DerEncodedPrincipals.length];
                    for (int i = 0; i < asn1DerEncodedPrincipals.length; i++) {
                        issuers[i] = new X500Principal(asn1DerEncodedPrincipals[i]);
                    }
                }
                keyManagerHolder.setKeyMaterialClientSide(engine, keyTypes, issuers);
            } catch (Throwable cause) {
                engine.initHandshakeException(cause);
                if (cause instanceof Exception) {
                    throw (Exception) cause;
                }
                throw new SSLException(cause);
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
            if (clientCertificateTypes == null) {
                // Try all of the supported key types.
                return SUPPORTED_KEY_TYPES;
            }
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
            // See also https://www.ietf.org/assignments/tls-parameters/tls-parameters.xml
            switch (clientCertificateType) {
                case CertificateCallback.TLS_CT_RSA_SIGN:
                    return OpenSslKeyMaterialManager.KEY_TYPE_RSA; // RFC rsa_sign
                case CertificateCallback.TLS_CT_RSA_FIXED_DH:
                    return OpenSslKeyMaterialManager.KEY_TYPE_DH_RSA; // RFC rsa_fixed_dh
                case CertificateCallback.TLS_CT_ECDSA_SIGN:
                    return OpenSslKeyMaterialManager.KEY_TYPE_EC; // RFC ecdsa_sign
                case CertificateCallback.TLS_CT_RSA_FIXED_ECDH:
                    return OpenSslKeyMaterialManager.KEY_TYPE_EC_RSA; // RFC rsa_fixed_ecdh
                case CertificateCallback.TLS_CT_ECDSA_FIXED_ECDH:
                    return OpenSslKeyMaterialManager.KEY_TYPE_EC_EC; // RFC ecdsa_fixed_ecdh
                default:
                    return null;
            }
        }
    }
}
