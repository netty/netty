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

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;

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
      throws Exception {
        super(newSSLContext(provider, toX509CertificatesInternal(trustCertCollectionFile),
          trustManagerFactory, null, null,
          null, null, sessionCacheSize, sessionTimeout, null, KeyStore.getDefaultType(), null), true,
          ciphers, cipherFilter, apn, ClientAuth.NONE, null, false, null, null, null);
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
                        SecureRandom secureRandom,
                        String keyStore,
                        String endpointIdentificationAlgorithm,
                        List<SNIServerName> serverNames,
                        ResumptionController resumptionController)
      throws Exception {
        super(newSSLContext(sslContextProvider, trustCertCollection, trustManagerFactory,
          keyCertChain, key, keyPassword, keyManagerFactory, sessionCacheSize, sessionTimeout, secureRandom, keyStore,
                        resumptionController),
          true, ciphers, cipherFilter, toNegotiator(apn, false), ClientAuth.NONE, protocols, false,
                endpointIdentificationAlgorithm, serverNames, resumptionController);
    }

    private static SSLContext newSSLContext(Provider sslContextProvider,
                                            X509Certificate[] trustCertCollection,
                                            TrustManagerFactory trustManagerFactory, X509Certificate[] keyCertChain,
                                            PrivateKey key, String keyPassword, KeyManagerFactory keyManagerFactory,
                                            long sessionCacheSize, long sessionTimeout,
                                            SecureRandom secureRandom, String keyStore,
                                            ResumptionController resumptionController) throws SSLException {
        try {
            if (trustCertCollection != null) {
                trustManagerFactory = buildTrustManagerFactory(trustCertCollection, trustManagerFactory, keyStore);
            }
            if (keyCertChain != null) {
                keyManagerFactory = buildKeyManagerFactory(keyCertChain, null,
                        key, keyPassword, keyManagerFactory, keyStore);
            }
            SSLContext ctx = sslContextProvider == null ? SSLContext.getInstance(PROTOCOL)
                : SSLContext.getInstance(PROTOCOL, sslContextProvider);
            ctx.init(keyManagerFactory == null ? null : keyManagerFactory.getKeyManagers(),
                     trustManagerFactory == null ? null :
                             wrapIfNeeded(trustManagerFactory.getTrustManagers(), resumptionController),
                     secureRandom);

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

    private static TrustManager[] wrapIfNeeded(TrustManager[] tms, ResumptionController resumptionController) {
        if (resumptionController != null) {
            for (int i = 0; i < tms.length; i++) {
                tms[i] = resumptionController.wrapIfNeeded(tms[i]);
            }
        }
        return tms;
    }
}
