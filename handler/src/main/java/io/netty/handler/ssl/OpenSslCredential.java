/*
 * Copyright 2026 The Netty Project
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

import io.netty.util.ReferenceCounted;

/**
 * Represents an OpenSSL/BoringSSL {@code SSL_CREDENTIAL} object.
 *
 * <p>SSL credentials provide a more flexible alternative to traditional certificate/key configuration,
 * supporting features like:
 * <ul>
 *   <li>Multiple credentials per context (e.g., RSA + ECDSA)</li>
 *   <li>Delegated credentials</li>
 *   <li>OCSP stapling per credential</li>
 *   <li>Signed Certificate Timestamps (SCT)</li>
 *   <li>Trust anchor identifiers</li>
 *   <li>Per-credential signing algorithm preferences</li>
 * </ul>
 *
 * <p>This is a BoringSSL-specific feature. Use {@link #isAvailable()} to check availability.
 *
 * <p>Instances are reference counted and must be released when no longer needed.
 *
 * @see <a href="https://commondatastorage.googleapis.com/chromium-boringssl-docs/ssl.h.html#SSL_CREDENTIAL_free">
 *      BoringSSL SSL_CREDENTIAL Documentation</a>
 */
public interface OpenSslCredential extends ReferenceCounted {
    /**
     * Check if the credentials API is supported.
     * @return {@code true} if the credentials API is supported, otherwise {@code false}.
     */
    static boolean isAvailable() {
        return OpenSsl.isAvailable() && OpenSsl.isBoringSSL();
    }

    /**
     * Returns the type of this credential.
     *
     * @return the credential type
     */
    CredentialType type();

    @Override
    OpenSslCredential retain();

    @Override
    OpenSslCredential retain(int increment);

    @Override
    OpenSslCredential touch();

    @Override
    OpenSslCredential touch(Object hint);

    /**
     * The type of SSL credential.
     */
    enum CredentialType {
        /**
         * Standard X.509 certificate credential created with {@code SSL_CREDENTIAL_new_x509()}.
         */
        X509,

        /**
         * Delegated credential created with {@code SSL_CREDENTIAL_new_delegated()}.
         *
         * @see <a href="https://datatracker.ietf.org/doc/html/rfc9345">RFC 9345 - Delegated Credentials for TLS</a>
         */
        DELEGATED
    }
}
