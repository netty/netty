/*
 * Copyright 2022 The Netty Project
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
package io.netty.handler.codec.quic;

/**
 * Execute {@link BoringSSLCertificateCallback#handle(long, byte[], byte[][], String[])}.
 */
final class BoringSSLCertificateCallbackTask extends BoringSSLTask {
    private final byte[] keyTypeBytes;
    private final byte[][] asn1DerEncodedPrincipals;
    private final String[] authMethods;
    private final BoringSSLCertificateCallback callback;

    // Accessed via JNI.
    private long key;
    private long chain;

    BoringSSLCertificateCallbackTask(long ssl, byte[] keyTypeBytes, byte[][] asn1DerEncodedPrincipals,
                                     String[] authMethods, BoringSSLCertificateCallback callback) {
        // It is important that this constructor never throws. Be sure to not change this!
        super(ssl);
        // It's ok to not clone the arrays as we create these in JNI and not-reuse.
        this.keyTypeBytes = keyTypeBytes;
        this.asn1DerEncodedPrincipals = asn1DerEncodedPrincipals;
        this.authMethods = authMethods;
        this.callback = callback;
    }

    // See https://www.openssl.org/docs/man1.0.2/man3/SSL_set_cert_cb.html.
    @Override
    protected void runTask(long ssl, TaskCallback taskCallback) {
        try {
            long[] result = callback.handle(ssl, keyTypeBytes, asn1DerEncodedPrincipals, authMethods);
            if (result == null) {
                taskCallback.onResult(ssl, 0);
            } else {
                this.key = result[0];
                this.chain = result[1];
                taskCallback.onResult(ssl, 1);
            }
        } catch (Exception e) {
            // Just catch the exception and return 0 to fail the handshake.
            // The problem is that rethrowing here is really "useless" as we will process it as part of an openssl
            // c callback which needs to return 0 for an error to abort the handshake.
            taskCallback.onResult(ssl, 0);
        }
    }

    @Override
    protected void destroy() {
        if (key != 0) {
            BoringSSL.EVP_PKEY_free(key);
            key = 0;
        }
        if (chain != 0) {
            BoringSSL.CRYPTO_BUFFER_stack_free(chain);
            chain = 0;
        }
    }
}
