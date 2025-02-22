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

import io.netty.internal.tcnative.SSL;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

import static io.netty5.handler.ssl.ReferenceCountedOpenSslClientContext.newSessionContext;

/**
 * A client-side {@link SslContext} which uses OpenSSL's SSL/TLS implementation.
 * <p>This class will use a finalizer to ensure native resources are automatically cleaned up. To avoid finalizers
 * and manually release the native memory see {@link ReferenceCountedOpenSslClientContext}.
 */
final class OpenSslClientContext extends OpenSslContext {
    private final OpenSslSessionContext sessionContext;

    OpenSslClientContext(X509Certificate[] trustCertCollection, TrustManagerFactory trustManagerFactory,
                         X509Certificate[] keyCertChain, PrivateKey key, String keyPassword,
                         KeyManagerFactory keyManagerFactory, Iterable<String> ciphers,
                         CipherSuiteFilter cipherFilter, ApplicationProtocolConfig apn, String[] protocols,
                         long sessionCacheSize, long sessionTimeout, boolean enableOcsp, String keyStore,
                         String endpointIdentificationAlgorithm, List<SNIServerName> serverName,
                         ResumptionController resumptionController,
                         Map.Entry<SslContextOption<?>, Object>... options)
            throws SSLException {
        super(ciphers, cipherFilter, apn, SSL.SSL_MODE_CLIENT, keyCertChain, ClientAuth.NONE, protocols, false,
                enableOcsp, endpointIdentificationAlgorithm, serverName, resumptionController, options);
        boolean success = false;
        try {
            OpenSslKeyMaterialProvider.validateKeyMaterialSupported(keyCertChain, key, keyPassword);
            sessionContext = newSessionContext(this, ctx, engineMap, trustCertCollection, trustManagerFactory,
                                               keyCertChain, key, keyPassword, keyManagerFactory, keyStore,
                                               sessionCacheSize, sessionTimeout, resumptionController);
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
}
