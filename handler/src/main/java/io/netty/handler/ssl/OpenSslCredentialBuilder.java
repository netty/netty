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

import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.internal.tcnative.SSL;
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
 * // Create credential with trust anchor (optional)
 * ASN1ObjectIdentifier oid = new ASN1ObjectIdentifier("1.3.6.1.4.1.11129.9.10"); // Google's taiWE1
 * byte[] trustAnchorBytes = oid.getEncoded();
 *
 * OpenSslCredential credential = OpenSslCredentialBuilder.forX509(privateKey, cert1, cert2, cert3)
 *     .trustAnchorId(trustAnchorBytes)  // optional
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

    private OpenSslCredentialBuilder(PrivateKey privateKey, X509Certificate[] certificateChain) {
        this.privateKey = checkNotNull(privateKey, "privateKey");
        this.certificateChain = checkNotNull(certificateChain, "certificateChain").clone();
        ObjectUtil.checkNonEmpty(this.certificateChain, "certificateChain");
    }

    private OpenSslCredentialBuilder(OpenSslPrivateKey openSslPrivateKey, X509Certificate[] certificateChain) {
        this.openSslPrivateKey = checkNotNull(openSslPrivateKey, "privateKey");
        this.certificateChain = checkNotNull(certificateChain, "certificateChain").clone();
        ObjectUtil.checkNonEmpty(this.certificateChain, "certificateChain");
    }

    /**
     * Creates a new builder for an X.509 credential with a Java PrivateKey.
     *
     * @param privateKey the private key (required)
     * @param certificateChain the certificate chain, starting with the leaf certificate (required)
     * @return a new builder instance
     */
    public static OpenSslCredentialBuilder forX509(PrivateKey privateKey, X509Certificate... certificateChain) {
        return new OpenSslCredentialBuilder(privateKey, certificateChain);
    }

    /**
     * Sets the trust anchor identifier for this credential.
     *
     * <p>The trust anchor identifier should be ASN.1 DER encoded bytes.
     * To convert from an OID string, use BouncyCastle's ASN1Encodable:
     * <pre>
     * // Example: Google's taiWE1 OID from https://pki.goog/oids/index.html
     * ASN1ObjectIdentifier oid = new ASN1ObjectIdentifier("1.3.6.1.4.1.11129.9.10");
     * byte[] encoded = oid.getEncoded();
     * credential.trustAnchorId(encoded);
     * </pre>
     *
     * @param trustAnchorId the trust anchor identifier as ASN.1 DER encoded bytes
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
     * @throws IllegalStateException if an error occurs during credential creation
     */
    public OpenSslCredential build() {
        OpenSsl.ensureAvailability();

        if (!OpenSslCredential.isAvailable()) {
            throw new UnsupportedOperationException("SSL_CREDENTIAL API is not supported");
        }

        long credentialPtr = 0;
        long certChainPtr = 0;
        long privateKeyPtr = 0;

        try {
            // Create the credential
            credentialPtr = createCredential();

            // Set private key (guaranteed to be present via constructor)
            privateKeyPtr = getPrivateKeyPointer();
            SSLCredential.setPrivateKey(credentialPtr, privateKeyPtr);

            // Set certificate chain (guaranteed to be present via constructor)
            certChainPtr = createCertChainPointer();
            SSLCredential.setCertChain(credentialPtr, certChainPtr);

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
