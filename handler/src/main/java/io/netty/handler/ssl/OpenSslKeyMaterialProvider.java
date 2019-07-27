/*
 * Copyright 2018 The Netty Project
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

import io.netty.buffer.ByteBufAllocator;
import io.netty.internal.tcnative.SSL;

import javax.net.ssl.X509KeyManager;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import static io.netty.handler.ssl.ReferenceCountedOpenSslContext.toBIO;

/**
 * Provides {@link OpenSslKeyMaterial} for a given alias.
 */
class OpenSslKeyMaterialProvider {

    private final X509KeyManager keyManager;
    private final String password;

    OpenSslKeyMaterialProvider(X509KeyManager keyManager, String password) {
        this.keyManager = keyManager;
        this.password = password;
    }

    /**
     * Returns the underlying {@link X509KeyManager} that is used.
     */
    X509KeyManager keyManager() {
        return keyManager;
    }

    /**
     * Returns the {@link OpenSslKeyMaterial} or {@code null} (if none) that should be used during the handshake by
     * OpenSSL.
     */
    OpenSslKeyMaterial chooseKeyMaterial(ByteBufAllocator allocator, String alias) throws Exception {
        X509Certificate[] certificates = keyManager.getCertificateChain(alias);
        if (certificates == null || certificates.length == 0) {
            return null;
        }

        PrivateKey key = keyManager.getPrivateKey(alias);
        PemEncoded encoded = PemX509Certificate.toPEM(allocator, true, certificates);
        long chainBio = 0;
        long pkeyBio = 0;
        long chain = 0;
        long pkey = 0;
        try {
            chainBio = toBIO(allocator, encoded.retain());
            chain = SSL.parseX509Chain(chainBio);

            OpenSslKeyMaterial keyMaterial;
            if (key instanceof OpenSslPrivateKey) {
                keyMaterial = ((OpenSslPrivateKey) key).toKeyMaterial(chain, certificates);
            } else {
                pkeyBio = toBIO(allocator, key);
                pkey = key == null ? 0 : SSL.parsePrivateKey(pkeyBio, password);
                keyMaterial = new DefaultOpenSslKeyMaterial(chain, pkey, certificates);
            }

            // See the chain and pkey to 0 so we will not release it as the ownership was
            // transferred to OpenSslKeyMaterial.
            chain = 0;
            pkey = 0;
            return keyMaterial;
        } finally {
            SSL.freeBIO(chainBio);
            SSL.freeBIO(pkeyBio);
            if (chain != 0) {
                SSL.freeX509Chain(chain);
            }
            if (pkey != 0) {
                SSL.freePrivateKey(pkey);
            }
            encoded.release();
        }
    }

    /**
     * Will be invoked once the provider should be destroyed.
     */
    void destroy() {
        // NOOP.
    }
}
