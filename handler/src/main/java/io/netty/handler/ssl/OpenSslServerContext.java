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

import io.netty.internal.tcnative.SSL;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;

import static io.netty.handler.ssl.ReferenceCountedOpenSslServerContext.newSessionContext;

/**
 * A server-side {@link SslContext} which uses OpenSSL's SSL/TLS implementation.
 * <p>This class will use a finalizer to ensure native resources are automatically cleaned up. To avoid finalizers
 * and manually release the native memory see {@link ReferenceCountedOpenSslServerContext}.
 */
final class OpenSslServerContext extends OpenSslContext {
    private final OpenSslServerSessionContext sessionContext;

    OpenSslServerContext(X509Certificate[] trustCertCollection,
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
                         boolean enableOcsp)
      throws SSLException {
        super(ciphers, cipherFilter, toNegotiator(apn), sessionCacheSize, sessionTimeout, SSL.SSL_MODE_SERVER,
          keyCertChain, clientAuth, protocols, startTls, enableOcsp);
        // Create a new SSL_CTX and configure it.
        boolean success = false;
        try {
            OpenSslKeyMaterialProvider.validateKeyMaterialSupported(keyCertChain, key, keyPassword);
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
    public OpenSslServerSessionContext sessionContext() {
        return sessionContext;
    }
}
