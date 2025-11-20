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
package io.netty5.handler.ssl;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.Unpooled;
import io.netty5.buffer.UnpooledByteBufAllocator;
import io.netty5.internal.tcnative.Buffer;
import io.netty5.internal.tcnative.SSL;
import io.netty5.internal.tcnative.SSLContext;
import io.netty5.internal.tcnative.SSLCredential;
import io.netty5.util.internal.ObjectUtil;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import static io.netty.handler.ssl.OpenSslCredential.CredentialType;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Builder for creating {@link OpenSslCredential} instances.
 *
 * <p>This builder provides a fluent API for configuring SSL credentials with support for:
 * <ul>
 *   <li>X.509 and delegated credentials</li>
 *   <li>Certificate chains and private keys</li>
 *   <li>OCSP stapling</li>
 *   <li>Signed Certificate Timestamps (SCT)</li>
 *   <li>Signing algorithm preferences</li>
 *   <li>Trust anchor identifiers</li>
 *   <li>Certificate properties</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>
 * OpenSslCredential credential = OpenSslCredentialBuilder.newX509()
 *     .privateKey(privateKey)
 *     .certificateChain(cert1, cert2, cert3)
 *     .ocspResponse(ocspBytes)
 *     .build();
 * </pre>
 *
 * <p>This is a BoringSSL-specific feature.
 */
public final class OpenSslCredentialBuilder {

    private final CredentialType type;
    private PrivateKey privateKey;
    private OpenSslPrivateKey openSslPrivateKey;
    private X509Certificate[] certificateChain;
    private byte[] ocspResponse;
    private byte[] signedCertificateTimestamps;
    private int[] signingAlgorithmPrefs;
    private byte[] certificateProperties;
    private byte[] trustAnchorId;
    private boolean mustMatchIssuer;
    private byte[] delegatedCredential;

    private OpenSslCredentialBuilder(CredentialType type) {
        this.type = type;
    }

    /**
     * Creates a new builder for an X.509 credential.
     *
     * @return a new builder instance
     */
    public static OpenSslCredentialBuilder newX509() {
        return new OpenSslCredentialBuilder(CredentialType.X509);
    }

    /**
     * Creates a new builder for a delegated credential.
     *
     * @return a new builder instance
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc9345">RFC 9345 - Delegated Credentials for TLS</a>
     */
    public static OpenSslCredentialBuilder newDelegated() {
        return new OpenSslCredentialBuilder(CredentialType.DELEGATED);
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
     * Sets the OCSP response to be stapled with this credential.
     *
     * @param ocspResponse the DER-encoded OCSP response
     * @return this builder for chaining
     */
    public OpenSslCredentialBuilder ocspResponse(byte[] ocspResponse) {
        this.ocspResponse = checkNotNull(ocspResponse, "ocspResponse").clone();
        return this;
    }

    /**
     * Sets the Signed Certificate Timestamp list for this credential.
     *
     * @param scts the encoded SCT list
     * @return this builder for chaining
     */
    public OpenSslCredentialBuilder signedCertificateTimestamps(byte[] scts) {
        this.signedCertificateTimestamps = checkNotNull(scts, "scts").clone();
        return this;
    }

    /**
     * Sets the signing algorithm preferences for this credential.
     *
     * @param prefs the signing algorithm identifiers (TLS 1.3 signature scheme values)
     * @return this builder for chaining
     * @see <a href="https://www.iana.org/assignments/tls-parameters/tls-parameters.xhtml#tls-signaturescheme">
     *      TLS SignatureScheme Registry</a>
     */
    public OpenSslCredentialBuilder signingAlgorithmPreferences(int... prefs) {
        this.signingAlgorithmPrefs = checkNotNull(prefs, "prefs").clone();
        return this;
    }

    /**
     * Sets the certificate properties for this credential.
     *
     * @param properties the encoded certificate properties
     * @return this builder for chaining
     */
    public OpenSslCredentialBuilder certificateProperties(byte[] properties) {
        this.certificateProperties = checkNotNull(properties, "properties").clone();
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
     * Sets the delegated credential for this credential.
     *
     * @param delegatedCredential the encoded delegated credential
     * @return this builder for chaining
     */
    public OpenSslCredentialBuilder delegatedCredential(byte[] delegatedCredential) {
        this.delegatedCredential = checkNotNull(delegatedCredential, "delegatedCredential").clone();
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
            if (privateKey != null || openSslPrivateKey != null) {
                privateKeyPtr = getPrivateKeyPointer();
                SSLCredential.setPrivateKey(credentialPtr, privateKeyPtr);
            }

            // Set certificate chain if provided
            if (certificateChain != null && certificateChain.length > 0) {
                certChainPtr = createCertChainPointer();
                SSLCredential.setCertChain(credentialPtr, certChainPtr);
            }

            // Set optional properties
            if (ocspResponse != null) {
                SSLCredential.setOcspResponse(credentialPtr, ocspResponse);
            }

            if (signedCertificateTimestamps != null) {
                SSLCredential.setSignedCertTimestampList(credentialPtr, signedCertificateTimestamps);
            }

            if (signingAlgorithmPrefs != null) {
                SSLCredential.setSigningAlgorithmPrefs(credentialPtr, signingAlgorithmPrefs);
            }

            if (certificateProperties != null) {
                SSLCredential.setCertificateProperties(credentialPtr, certificateProperties);
            }

            if (trustAnchorId != null) {
                SSLCredential.setTrustAnchorId(credentialPtr, trustAnchorId);
            }

            if (mustMatchIssuer) {
                SSLCredential.setMustMatchIssuer(credentialPtr, true);
            }

            if (delegatedCredential != null) {
                SSLCredential.setDelegatedCredential(credentialPtr, delegatedCredential);
            }

            // Success - create the wrapper object
            long finalPtr = credentialPtr;
            credentialPtr = 0; // Don't free on cleanup
            return new DefaultOpenSslCredential(finalPtr, type);

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
        switch (type) {
            case X509:
                return SSLCredential.newX509();
            case DELEGATED:
                return SSLCredential.newDelegated();
            default:
                throw new IllegalStateException("Unknown credential type: " + type);
        }
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
            Buffer.freeBIO(bio);
        }
    }

    private long createCertChainPointer() throws Exception {
        if (certificateChain == null || certificateChain.length == 0) {
            throw new IllegalStateException("No certificate chain specified");
        }

        // Convert certificate chain to PEM format and parse
        try {
            long bio = ReferenceCountedOpenSslContext.toBIO(
                    UnpooledByteBufAllocator.DEFAULT, certificateChain);
            try {
                return SSL.parseX509Chain(bio);
            } finally {
                Buffer.freeBIO(bio);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode certificate chain", e);
        }
    }
}
