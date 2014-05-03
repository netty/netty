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
package org.jboss.netty.handler.ssl;

import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.jni.SSLContext;

/**
 * Encapsulates an OpenSSL SSL_CTX object.
 */
public class OpenSslContextHolder {

    private long sslContext;

    /**
     * Create an SSLContext from the given SSLConfiguration
     * @param cfg the SSLConfiguration
     */
    public OpenSslContextHolder(long pool, OpenSslConfig cfg) throws Exception {
        synchronized (OpenSslContextHolder.class) {
            sslContext = SSLContext.make(pool, SSL.SSL_PROTOCOL_ALL, SSL.SSL_MODE_SERVER);

            SSLContext.setOptions(sslContext, SSL.SSL_OP_ALL);
            SSLContext.setOptions(sslContext, SSL.SSL_OP_NO_SSLv2);
            SSLContext.setOptions(sslContext, SSL.SSL_OP_CIPHER_SERVER_PREFERENCE);
            SSLContext.setOptions(sslContext, SSL.SSL_OP_SINGLE_ECDH_USE);
            SSLContext.setOptions(sslContext, SSL.SSL_OP_SINGLE_DH_USE);
            SSLContext.setOptions(sslContext, SSL.SSL_OP_NO_SESSION_RESUMPTION_ON_RENEGOTIATION);

            /* List the ciphers that the client is permitted to negotiate. */
            SSLContext.setCipherSuite(sslContext, cfg.getCipherSpec());

            /* Set certificate verification policy. */
            SSLContext.setVerify(sslContext, SSL.SSL_CVERIFY_NONE, 10);

            final String certFilename = cfg.getCertPath();
            final String certChainFilename = cfg.getCaPath();

            /* Load the certificate file and private key. */
            if (!SSLContext.setCertificate(
                    sslContext, certFilename, cfg.getKeyPath(), cfg.getKeyPassword(), SSL.SSL_AIDX_RSA)) {
                throw new Exception("Failed to set certificate file '" + certFilename + "': " + SSL.getLastError());
            }

            /* Load certificate chain file, if specified */
            if (certChainFilename != null && certChainFilename.length() > 0) {
                /* If named same as cert file, we must skip the first cert since it was loaded above. */
                boolean skipFirstCert = certFilename.equals(certChainFilename);

                if (!SSLContext.setCertificateChainFile(sslContext, certChainFilename, skipFirstCert)) {
                    throw new Exception(
                            "Failed to set certificate chain file '" + certChainFilename + "': " + SSL.getLastError());
                }
            }

            /* Set next protocols for next protocol negotiation extension, if specified */
            String nextProtos = cfg.getNextProtos();
            if (nextProtos != null && nextProtos.length() > 0) {
                SSLContext.setNextProtos(sslContext, nextProtos);
            }
        }
    }

    protected long getSslContext() {
        return sslContext;
    }
}
