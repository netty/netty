/*
 * Copyright 2014 The Netty Project
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

import javax.crypto.NoSuchPaddingException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;

/**
 * A server-side {@link SslContext} which uses JDK's SSL/TLS implementation.
 */
final class JdkSslServerContext extends JdkSslContext {

    private static final boolean WRAP_TRUST_MANAGER;
    static {
        boolean wrapTrustManager = false;
        try {
            checkIfWrappingTrustManagerIsSupported();
            wrapTrustManager = true;
        } catch (Throwable ignore) {
            // Just don't wrap as we might not be able to do so because of FIPS:
            // See https://github.com/netty/netty/issues/13840
        }
        WRAP_TRUST_MANAGER = wrapTrustManager;
    }

    // Package-private for testing.
    static void checkIfWrappingTrustManagerIsSupported() throws CertificateException,
            InvalidAlgorithmParameterException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeySpecException, IOException, KeyException, KeyStoreException, UnrecoverableKeyException {
        X509Certificate[] certs = toX509Certificates(
                new ByteArrayInputStream(SslUtils.PROBING_CERT.getBytes(StandardCharsets.US_ASCII)));
        PrivateKey privateKey = toPrivateKey(new ByteArrayInputStream(
                SslUtils.PROBING_KEY.getBytes(StandardCharsets.US_ASCII)), null);
        char[] keyStorePassword = keyStorePassword(null);
        KeyStore ks = buildKeyStore(certs, privateKey, keyStorePassword, null);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, keyStorePassword);

        SSLContext ctx = SSLContext.getInstance(PROTOCOL);
        TrustManagerFactory tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tm.init((KeyStore) null);
        TrustManager[] managers = tm.getTrustManagers();

        ctx.init(kmf.getKeyManagers(), wrapTrustManagerIfNeeded(managers), null);
    }

    // FIXME test only
    JdkSslServerContext(Provider provider,
                        File certChainFile,
                        File keyFile,
                        String keyPassword,
                        Iterable<String> ciphers,
                        CipherSuiteFilter cipherFilter,
                        JdkApplicationProtocolNegotiator apn,
                        long sessionCacheSize,
                        long sessionTimeout)
      throws Exception {
        super(newSSLContext(provider, null, null,
          toX509CertificatesInternal(certChainFile), toPrivateKeyInternal(keyFile, keyPassword),
          keyPassword, null, sessionCacheSize, sessionTimeout, null, KeyStore.getDefaultType()),
          false, ciphers, cipherFilter, apn, ClientAuth.NONE, null, false, null);
    }

    JdkSslServerContext(Provider provider,
                        X509Certificate[] trustCertCollection,
                        TrustManagerFactory trustManagerFactory,
                        X509Certificate[] keyCertChain,
                        PrivateKey key,
                        String keyPassword,
                        KeyManagerFactory keyManagerFactory,
                        Iterable<String> ciphers,
                        CipherSuiteFilter cipherFilter,
                        ApplicationProtocolConfig apn,
                        long sessionCacheSize,
                        long sessionTimeout,
                        ClientAuth clientAuth,
                        String[] protocols,
                        boolean startTls,
                        SecureRandom secureRandom,
                        String keyStore)
      throws Exception {
        super(newSSLContext(provider, trustCertCollection, trustManagerFactory, keyCertChain, key,
          keyPassword, keyManagerFactory, sessionCacheSize, sessionTimeout, secureRandom, keyStore), false,
          ciphers, cipherFilter, toNegotiator(apn, true), clientAuth, protocols, startTls, null);
    }

    private static SSLContext newSSLContext(Provider sslContextProvider, X509Certificate[] trustCertCollection,
                                     TrustManagerFactory trustManagerFactory, X509Certificate[] keyCertChain,
                                     PrivateKey key, String keyPassword, KeyManagerFactory keyManagerFactory,
                                     long sessionCacheSize, long sessionTimeout, SecureRandom secureRandom,
                                     String keyStore)
            throws SSLException {
        if (key == null && keyManagerFactory == null) {
            throw new NullPointerException("key, keyManagerFactory");
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
            if (key != null) {
                keyManagerFactory = buildKeyManagerFactory(keyCertChain, null,
                        key, keyPassword, keyManagerFactory, null);
            }

            // Initialize the SSLContext to work with our key managers.
            SSLContext ctx = sslContextProvider == null ? SSLContext.getInstance(PROTOCOL)
                : SSLContext.getInstance(PROTOCOL, sslContextProvider);
            ctx.init(keyManagerFactory.getKeyManagers(),
                    wrapTrustManagerIfNeeded(trustManagerFactory.getTrustManagers()), secureRandom);

            SSLSessionContext sessCtx = ctx.getServerSessionContext();
            if (sessionCacheSize > 0) {
                sessCtx.setSessionCacheSize((int) Math.min(sessionCacheSize, Integer.MAX_VALUE));
            }
            if (sessionTimeout > 0) {
                sessCtx.setSessionTimeout((int) Math.min(sessionTimeout, Integer.MAX_VALUE));
            }
            return ctx;
        } catch (Exception e) {
            if (e instanceof SSLException) {
                throw (SSLException) e;
            }
            throw new SSLException("failed to initialize the server-side SSL context", e);
        }
    }

    private static TrustManager[] wrapTrustManagerIfNeeded(TrustManager[] trustManagers) {
        if (WRAP_TRUST_MANAGER) {
            for (int i = 0; i < trustManagers.length; i++) {
                TrustManager tm = trustManagers[i];
                if (tm instanceof X509ExtendedTrustManager) {
                    // Wrap the TrustManager to provide a better exception message for users to debug hostname
                    // validation failures.
                    trustManagers[i] = new EnhancingX509ExtendedTrustManager((X509ExtendedTrustManager) tm);
                }
            }
        }
        return trustManagers;
    }
}
