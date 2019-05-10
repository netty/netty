/*
 * Copyright 2014 The Netty Project
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

import java.io.File;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * A client-side {@link SslContext} which uses JDK's SSL/TLS implementation.
 */
final class JdkSslClientContext extends JdkSslContext {

    // FIXME test only
    JdkSslClientContext(Provider provider,
                        File trustCertCollectionFile,
                        TrustManagerFactory trustManagerFactory,
                        Iterable<String> ciphers,
                        CipherSuiteFilter cipherFilter,
                        JdkApplicationProtocolNegotiator apn,
                        long sessionCacheSize,
                        long sessionTimeout)
      throws SSLException {
        super(newSSLContext(provider, toX509CertificatesInternal(trustCertCollectionFile),
          trustManagerFactory, null, null,
          null, null, sessionCacheSize, sessionTimeout, KeyStore.getDefaultType()), true,
          ciphers, cipherFilter, apn, ClientAuth.NONE, null, false);
    }

    JdkSslClientContext(Provider sslContextProvider,
                        X509Certificate[] trustCertCollection,
                        TrustManagerFactory trustManagerFactory,
                        X509Certificate[] keyCertChain,
                        PrivateKey key,
                        String keyPassword,
                        KeyManagerFactory keyManagerFactory,
                        Iterable<String> ciphers,
                        CipherSuiteFilter cipherFilter,
                        ApplicationProtocolConfig apn,
                        String[] protocols,
                        long sessionCacheSize,
                        long sessionTimeout,
                        String keyStore)
      throws SSLException {
        super(newSSLContext(sslContextProvider, trustCertCollection, trustManagerFactory,
          keyCertChain, key, keyPassword, keyManagerFactory, sessionCacheSize, sessionTimeout, keyStore),
          true, ciphers, cipherFilter, toNegotiator(apn, false), ClientAuth.NONE, protocols, false);
    }

    private static SSLContext newSSLContext(Provider sslContextProvider,
                                            X509Certificate[] trustCertCollection,
                                            TrustManagerFactory trustManagerFactory, X509Certificate[] keyCertChain,
                                            PrivateKey key, String keyPassword, KeyManagerFactory keyManagerFactory,
                                            long sessionCacheSize, long sessionTimeout,
                                            String keyStore) throws SSLException {
        try {
            if (trustCertCollection != null) {
                trustManagerFactory = buildTrustManagerFactory(trustCertCollection, trustManagerFactory, keyStore);
            }
            if (keyCertChain != null) {
                keyManagerFactory = buildKeyManagerFactory(keyCertChain, key, keyPassword, keyManagerFactory, keyStore);
            }
            SSLContext ctx = sslContextProvider == null ? SSLContext.getInstance(PROTOCOL)
                : SSLContext.getInstance(PROTOCOL, sslContextProvider);
            ctx.init(keyManagerFactory == null ? null : keyManagerFactory.getKeyManagers(),
                     trustManagerFactory == null ? null : trustManagerFactory.getTrustManagers(),
                     null);

            SSLSessionContext sessCtx = ctx.getClientSessionContext();
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
            throw new SSLException("failed to initialize the client-side SSL context", e);
        }
    }
}
