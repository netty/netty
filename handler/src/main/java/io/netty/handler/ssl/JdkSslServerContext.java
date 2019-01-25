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
import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * A server-side {@link SslContext} which uses JDK's SSL/TLS implementation.
 */
final class JdkSslServerContext extends JdkSslContext {

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
      throws SSLException {
        super(newSSLContext(provider, null, null,
          toX509CertificatesInternal(certChainFile), toPrivateKeyInternal(keyFile, keyPassword),
          keyPassword, null, sessionCacheSize, sessionTimeout), false,
          ciphers, cipherFilter, apn, ClientAuth.NONE, null, false);
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
                        boolean startTls)
      throws SSLException {
        super(newSSLContext(provider, trustCertCollection, trustManagerFactory, keyCertChain, key,
          keyPassword, keyManagerFactory, sessionCacheSize, sessionTimeout), false,
          ciphers, cipherFilter, toNegotiator(apn, true), clientAuth, protocols, startTls);
    }

    private static SSLContext newSSLContext(Provider sslContextProvider, X509Certificate[] trustCertCollection,
                                     TrustManagerFactory trustManagerFactory, X509Certificate[] keyCertChain,
                                     PrivateKey key, String keyPassword, KeyManagerFactory keyManagerFactory,
                                     long sessionCacheSize, long sessionTimeout)
            throws SSLException {
        if (key == null && keyManagerFactory == null) {
            throw new NullPointerException("key, keyManagerFactory");
        }

        try {
            if (trustCertCollection != null) {
                trustManagerFactory = buildTrustManagerFactory(trustCertCollection, trustManagerFactory);
            }
            if (key != null) {
                keyManagerFactory = buildKeyManagerFactory(keyCertChain, key, keyPassword, keyManagerFactory);
            }

            // Initialize the SSLContext to work with our key managers.
            SSLContext ctx = sslContextProvider == null ? SSLContext.getInstance(PROTOCOL)
                : SSLContext.getInstance(PROTOCOL, sslContextProvider);
            ctx.init(keyManagerFactory.getKeyManagers(),
                     trustManagerFactory == null ? null : trustManagerFactory.getTrustManagers(),
                     null);

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

}
