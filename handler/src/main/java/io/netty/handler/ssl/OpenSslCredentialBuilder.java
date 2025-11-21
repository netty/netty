/*
 * Copyright 2025 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.internal.tcnative.SSL;
import io.netty.internal.tcnative.SSLContext;
import io.netty.internal.tcnative.SSLCredential;
import io.netty.util.internal.ObjectUtil;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import static io.netty.handler.ssl.OpenSslCredential.CredentialType;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Builder for creating {@link OpenSslCredential} instances.
 *
 * <p>This builder provides a fluent API for configuring SSL credentials with support for:
 * <ul>
 *   <li>X.509 credentials</li>
 *   <li>Certificate chains and private keys</li>
 *   <li>Trust anchor identifiers (optional)</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>
 * OpenSslCredential credential = OpenSslCredentialBuilder.forX509()
 *     .privateKey(privateKey)
 *     .certificateChain(cert1, cert2, cert3)
 *     .build();
 * </pre>
 *
 * <p>This is a BoringSSL-specific feature.
 */
public final class OpenSslCredentialBuilder {

    private PrivateKey privateKey;
    private OpenSslPrivateKey openSslPrivateKey;
    private X509Certificate[] certificateChain;
    private byte[] trustAnchorId;
    private boolean mustMatchIssuer;

    private OpenSslCredentialBuilder() {
    }

    /**
     * Creates a new builder for an X.509 credential.
     *
     * @return a new builder instance
     */
    public static OpenSslCredentialBuilder forX509() {
        return new OpenSslCredentialBuilder();
    }

    /**
     * Sets the private key for this credential.
     *
     * @param privateKey the private key
     * @return this builder for chaining
     */
    public OpenSslCredentialBuilder privateKey(PrivateKey privateKey) {
        this.privateKey = checkNotNull(privateKey, "privateKey");
        this.openSslPrivateKey = null;
        return this;
    }

    /**
     * Sets the private key for this credential using an OpenSSL-specific key.
     *
     * @param privateKey the OpenSSL private key
     * @return this builder for chaining
     */
    public OpenSslCredentialBuilder privateKey(OpenSslPrivateKey privateKey) {
        this.openSslPrivateKey = checkNotNull(privateKey, "privateKey");
        this.privateKey = null;
        return this;
    }

    /**
     * Sets the certificate chain for this credential.
     *
     * @param certificateChain the certificate chain, starting with the leaf certificate
     * @return this builder for chaining
     */
    public OpenSslCredentialBuilder certificateChain(X509Certificate... certificateChain) {
        this.certificateChain = checkNotNull(certificateChain, "certificateChain").clone();
        ObjectUtil.checkNonEmpty(this.certificateChain, "certificateChain");
        return this;
    }

    /**
     * Sets the trust anchor identifier for this credential.
     *
     * @param trustAnchorId the trust anchor identifier
     * @return this builder for chaining
     */
    public OpenSslCredentialBuilder trustAnchorId(byte[] trustAnchorId) {
        this.trustAnchorId = checkNotNull(trustAnchorId, "trustAnchorId").clone();
        return this;
    }

    /**
     * Sets whether the issuer must match for this credential.
     *
     * @param mustMatchIssuer {@code true} if issuer must match
     * @return this builder for chaining
     */
    public OpenSslCredentialBuilder mustMatchIssuer(boolean mustMatchIssuer) {
        this.mustMatchIssuer = mustMatchIssuer;
        return this;
    }

    /**
     * Builds the {@link OpenSslCredential} instance.
     *
     * @return a new credential instance
     * @throws IllegalStateException if required parameters are missing or if an error occurs
     */
    public OpenSslCredential build() {
        OpenSsl.ensureAvailability();

        if (!OpenSsl.isBoringSSL()) {
            throw new UnsupportedOperationException("SSL_CREDENTIAL API is only supported with BoringSSL");
        }

        // Validate that cert and key are both present or both absent
        boolean hasPrivateKey = privateKey != null || openSslPrivateKey != null;
        boolean hasCertChain = certificateChain != null && certificateChain.length > 0;

        if (hasCertChain && !hasPrivateKey) {
            throw new IllegalStateException(
                    "Certificate chain provided without private key. " +
                    "SSL credentials require both certificate and private key to be set together.");
        }

        if (hasPrivateKey && !hasCertChain) {
            throw new IllegalStateException(
                    "Private key provided without certificate chain. " +
                    "SSL credentials require both certificate and private key to be set together.");
        }

        long credentialPtr = 0;
        long certChainPtr = 0;
        long privateKeyPtr = 0;

        try {
            // Create the credential
            credentialPtr = createCredential();

            // Set private key if provided
            if (hasPrivateKey) {
                privateKeyPtr = getPrivateKeyPointer();
                SSLCredential.setPrivateKey(credentialPtr, privateKeyPtr);
            }

            // Set certificate chain if provided
            if (hasCertChain) {
                certChainPtr = createCertChainPointer();
                SSLCredential.setCertChain(credentialPtr, certChainPtr);
            }

            // Set optional properties
            if (trustAnchorId != null) {
                SSLCredential.setTrustAnchorId(credentialPtr, trustAnchorId);
            }

            if (mustMatchIssuer) {
                SSLCredential.setMustMatchIssuer(credentialPtr, true);
            }

            // Success - create the wrapper object
            long finalPtr = credentialPtr;
            credentialPtr = 0; // Don't free on cleanup
            return new DefaultOpenSslCredential(finalPtr, CredentialType.X509);

        } catch (Exception e) {
            throw new IllegalStateException("Failed to build SSL credential", e);
        } finally {
            // Cleanup on error
            if (credentialPtr != 0) {
                try {
                    SSLCredential.free(credentialPtr);
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
            if (certChainPtr != 0) {
                SSL.freeX509Chain(certChainPtr);
            }
            if (privateKeyPtr != 0 && privateKey != null) {
                // Only free if we created it from a Java PrivateKey
                SSL.freePrivateKey(privateKeyPtr);
            }
        }
    }

    private long createCredential() throws Exception {
        return SSLCredential.newX509();
    }

    private long getPrivateKeyPointer() throws Exception {
        if (openSslPrivateKey != null) {
            return openSslPrivateKey.privateKeyAddress();
        }

        if (privateKey == null) {
            throw new IllegalStateException("No private key specified");
        }

        // Convert Java PrivateKey to OpenSSL EVP_PKEY
        long bio = ReferenceCountedOpenSslContext.toBIO(
                UnpooledByteBufAllocator.DEFAULT, privateKey);
        try {
            return SSL.parsePrivateKey(bio, null);
        } finally {
            SSL.freeBIO(bio);
        }
    }

    private long createCertChainPointer() throws Exception {
        // Convert certificate chain to PEM format and parse
        try {
            long bio = ReferenceCountedOpenSslContext.toBIO(
                    UnpooledByteBufAllocator.DEFAULT, certificateChain);
            try {
                return SSL.parseX509Chain(bio);
            } finally {
                SSL.freeBIO(bio);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode certificate chain", e);
        }
    }
}
