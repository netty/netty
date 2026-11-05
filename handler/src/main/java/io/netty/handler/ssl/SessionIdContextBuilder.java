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

import io.netty.util.SecureObjectIdentityHash;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Objects;

/**
 * Derives the {@code session_id_context} for a server context from the material that determines which clients
 * it accepts.
 * The session discriminating material is the {@link ClientAuth} mode, the certificate chain it presents,
 * the certificates it trusts, and any installed credentials.
 * <p>
 * The {@code session_id_context} scopes session resumption: a session (and therefore its client-authentication
 * state) can only be resumed by a context that uses the same {@code session_id_context}. It must hence differ
 * between contexts which, when sharing a session cache or session-ticket keys, must not accept each others
 * sessions (for example because they use a different {@link ClientAuth} mode or trust configuration), but it
 * should be the same for contexts that are configured identically (for example on different nodes of a cluster
 * that share session-ticket keys), so resumption keeps working between them.
 * See <a href="https://www.openssl.org/docs/man1.1.1/man3/SSL_CTX_set_session_id_context.html">
 * SSL_CTX_set_session_id_context</a> and
 * <a href="https://github.com/netty/netty/security/advisories/GHSA-6xv6-9mgg-mfvc">GHSA-6xv6-9mgg-mfvc</a>.
 * <p>
 * This class securely computes distinct session context identifiers from the discriminating material,
 * such that resumption is allowed for connections to the same JVM, with optional support for deterministic cross-JVM
 * session context ids.
 */
public final class SessionIdContextBuilder {
    private final SecureObjectIdentityHash identityHashes;

    // When set, use this explicitly and directly, instead of computing a value.
    private byte[] sessionIdContextOverride;

    // Common fields:
    private byte[] seed;
    private ClientAuth clientAuth;
    private OpenSslCredential[] credentials;

    // At most one will be set:
    private KeyManager keyManagerInstance;
    private KeyManagerFactory keyManagerFactoryInstance;
    private X509Certificate[] keyCertChainDecoded;

    // At most one will be set:
    private TrustManager trustManagerInstance;
    private TrustManagerFactory trustManagerFactoryInstance;
    private X509Certificate[] trustedCertsDecoded;

    public SessionIdContextBuilder(SecureObjectIdentityHash identityHashes) {
        this.identityHashes = Objects.requireNonNull(identityHashes, "identityHashes");
    }

    private SessionIdContextBuilder() {
        identityHashes = null;
    }

    public static SessionIdContextBuilder randomSessionIdContext() {
        return new SessionIdContextBuilder()
            .setSessionIdContextOverride(RandomHolder.getRandomSessionIdContext());
    }

    public SessionIdContextBuilder setSessionIdContextOverride(byte[] override) {
        if (override != null && override.length > 32) {
            throw new IllegalArgumentException("Session Id Context can be at most 32 bytes");
        }
        this.sessionIdContextOverride = override;
        return this;
    }

    public SessionIdContextBuilder setSeed(byte[] seed) {
        this.seed = seed;
        return this;
    }

    public SessionIdContextBuilder setClientAuth(ClientAuth clientAuth) {
        this.clientAuth = clientAuth;
        return this;
    }

    public SessionIdContextBuilder setCredentials(OpenSslCredential[] credentials) {
        this.credentials = credentials;
        return this;
    }

    /**
     * Use the identity of the given {@link KeyManager} as the local identity material.
     * {@code null} clears all local identity material.
     */
    public SessionIdContextBuilder setKeyManager(KeyManager keyManager) {
        keyManagerInstance = keyManager;
        keyManagerFactoryInstance = null;
        keyCertChainDecoded = null;
        return this;
    }

    /**
     * Use the identity of the given {@link KeyManagerFactory} as the local identity material.
     * {@code null} clears all local identity material.
     */
    public SessionIdContextBuilder setKeyManagerFactory(KeyManagerFactory keyManagerFactory) {
        keyManagerFactoryInstance = keyManagerFactory;
        keyManagerInstance = null;
        keyCertChainDecoded = null;
        return this;
    }

    /**
     * Use the given certificate chain as the local identity material.
     * The private key is deliberately not part of the digest: the chain already identifies it, and digesting
     * secret material would put a stable fingerprint of it into every session.
     * {@code null} clears all local identity material.
     * <p>
     * The array is not copied, so the caller must not modify it afterwards.
     */
    public SessionIdContextBuilder setKeyCertificateChain(X509Certificate[] chain) {
        keyCertChainDecoded = chain;
        keyManagerInstance = null;
        keyManagerFactoryInstance = null;
        return this;
    }

    /**
     * Use the identity of the given {@link TrustManager} as the trust material.
     * {@code null} clears all trust material.
     */
    public SessionIdContextBuilder setTrustManager(TrustManager trustManager) {
        trustManagerInstance = trustManager;
        trustManagerFactoryInstance = null;
        trustedCertsDecoded = null;
        return this;
    }

    /**
     * Use the identity of the given {@link TrustManagerFactory} as the trust material.
     * {@code null} clears all trust material.
     */
    public SessionIdContextBuilder setTrustManagerFactory(TrustManagerFactory trustManagerFactory) {
        trustManagerFactoryInstance = trustManagerFactory;
        trustManagerInstance = null;
        trustedCertsDecoded = null;
        return this;
    }

    /**
     * Use the given certificates as the trust material.
     * {@code null} clears all trust material.
     * <p>
     * The array is not copied, so the caller must not modify it afterwards.
     */
    public SessionIdContextBuilder setTrustedCertificates(X509Certificate[] trustedCertificates) {
        trustedCertsDecoded = trustedCertificates;
        trustManagerInstance = null;
        trustManagerFactoryInstance = null;
        return this;
    }

    public byte[] build() {
        if (sessionIdContextOverride != null) {
            return sessionIdContextOverride;
        }

        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // Every Java implementation is required to support SHA-256.
            throw new IllegalStateException(e);
        }

        ByteBuffer intCarry = ByteBuffer.allocate(Integer.BYTES);
        update(digest, intCarry, seed);
        update(digest, intCarry, clientAuth == null ? -1 : clientAuth.ordinal());

        // Credentials domain
        if (credentials != null) {
            update(digest, intCarry, credentials.length);
            for (OpenSslCredential credential : credentials) {
                // All identity hashes are fixed-length, so length prefix is not needed.
                identityHashes.updateWithIdentityHash(digest, credential);
            }
        } else {
            update(digest, intCarry, 0); // Empty array and null are equivalent.
        }

        // Local identity domain
        update(digest, intCarry, -2);
        if (keyManagerFactoryInstance != null) {
            identityHashes.updateWithIdentityHash(digest, keyManagerFactoryInstance);
        } else if (keyManagerInstance != null) {
            identityHashes.updateWithIdentityHash(digest, keyManagerInstance);
        } else if (keyCertChainDecoded != null) {
            update(digest, intCarry, keyCertChainDecoded);
        }

        // Trust domain
        update(digest, intCarry, -3);
        if (trustManagerFactoryInstance != null) {
            identityHashes.updateWithIdentityHash(digest, trustManagerFactoryInstance);
        } else if (trustManagerInstance != null) {
            identityHashes.updateWithIdentityHash(digest, trustManagerInstance);
        } else if (trustedCertsDecoded != null) {
            update(digest, intCarry, trustedCertsDecoded);
        }

        return digest.digest();
    }

    private void update(MessageDigest digest, ByteBuffer size, byte[] value) {
        if (value == null) {
            size.putInt(0, -1);
            digest.update(size.array());
        } else {
            size.putInt(0, value.length);
            digest.update(size.array());
            digest.update(value);
        }
    }

    private void update(MessageDigest digest, ByteBuffer intBuf, int intValue) {
        intBuf.putInt(0, intValue);
        digest.update(intBuf.array());
    }

    private void update(MessageDigest digest, ByteBuffer intCarry, X509Certificate[] certs) {
        if (certs == null) {
            update(digest, intCarry, -1);
        } else {
            update(digest, intCarry, certs.length);
            for (X509Certificate cert : certs) {
                if (cert == null) {
                    update(digest, intCarry, -1);
                } else {
                    try {
                        update(digest, intCarry, cert.getEncoded());
                    } catch (CertificateEncodingException e) {
                        identityHashes.updateWithIdentityHash(digest, cert);
                    }
                }
            }
        }
    }

    private static final class RandomHolder {
        private static final SecureRandom RNG = new SecureRandom();

        static byte[] getRandomSessionIdContext() {
            byte[] ctx = new byte[32];
            RNG.nextBytes(ctx);
            return ctx;
        }
    }
}
