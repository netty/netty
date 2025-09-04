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

import io.netty.internal.tcnative.SSLPrivateKeyMethod;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementation of {@link OpenSslAsyncPrivateKeyMethod} that delegates cryptographic operations
 * to JDK signature providers for keys that cannot be accessed directly by OpenSSL.
 */
final class JdkDelegatingPrivateKeyMethod implements SSLPrivateKeyMethod {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(JdkDelegatingPrivateKeyMethod.class);

    private static final Map<Integer, String> SSL_TO_JDK_SIGNATURE_ALGORITHM;
    private static final ConcurrentMap<CacheKey, String> PROVIDER_CACHE = new ConcurrentHashMap<>();

    static {
        Map<Integer, String> algorithmMap = new HashMap<>();

        // RSA PKCS#1 signatures
        algorithmMap.put(OpenSslAsyncPrivateKeyMethod.SSL_SIGN_RSA_PKCS1_SHA1, "SHA1withRSA");
        algorithmMap.put(OpenSslAsyncPrivateKeyMethod.SSL_SIGN_RSA_PKCS1_SHA256, "SHA256withRSA");
        algorithmMap.put(OpenSslAsyncPrivateKeyMethod.SSL_SIGN_RSA_PKCS1_SHA384, "SHA384withRSA");
        algorithmMap.put(OpenSslAsyncPrivateKeyMethod.SSL_SIGN_RSA_PKCS1_SHA512, "SHA512withRSA");
        algorithmMap.put(OpenSslAsyncPrivateKeyMethod.SSL_SIGN_RSA_PKCS1_MD5_SHA1, "MD5andSHA1withRSA");

        // ECDSA signatures
        algorithmMap.put(OpenSslAsyncPrivateKeyMethod.SSL_SIGN_ECDSA_SHA1, "SHA1withECDSA");
        algorithmMap.put(OpenSslAsyncPrivateKeyMethod.SSL_SIGN_ECDSA_SECP256R1_SHA256, "SHA256withECDSA");
        algorithmMap.put(OpenSslAsyncPrivateKeyMethod.SSL_SIGN_ECDSA_SECP384R1_SHA384, "SHA384withECDSA");
        algorithmMap.put(OpenSslAsyncPrivateKeyMethod.SSL_SIGN_ECDSA_SECP521R1_SHA512, "SHA512withECDSA");

        // RSA-PSS signatures
        algorithmMap.put(OpenSslAsyncPrivateKeyMethod.SSL_SIGN_RSA_PSS_RSAE_SHA256, "RSASSA-PSS");
        algorithmMap.put(OpenSslAsyncPrivateKeyMethod.SSL_SIGN_RSA_PSS_RSAE_SHA384, "RSASSA-PSS");
        algorithmMap.put(OpenSslAsyncPrivateKeyMethod.SSL_SIGN_RSA_PSS_RSAE_SHA512, "RSASSA-PSS");

        // EdDSA signatures
        algorithmMap.put(OpenSslAsyncPrivateKeyMethod.SSL_SIGN_ED25519, "EdDSA");

        SSL_TO_JDK_SIGNATURE_ALGORITHM = Collections.unmodifiableMap(algorithmMap);
    }

    private final PrivateKey privateKey;

    /**
     * Creates a new JDK delegating async private key method.
     *
     * @param privateKey the private key to use for cryptographic operations
     */
    JdkDelegatingPrivateKeyMethod(PrivateKey privateKey) {
        this.privateKey = ObjectUtil.checkNotNull(privateKey, "privateKey");
    }

    @Override
    public byte[] sign(long ssl, int signatureAlgorithm, byte[] input) throws Exception {
        Signature signature = createSignature(signatureAlgorithm);
        signature.update(input);
        byte[] result = signature.sign();

        if (logger.isDebugEnabled()) {
            logger.debug("Signing operation completed successfully, result length: {}", result.length);
        }
        return result;
    }

    @Override
    public byte[] decrypt(long ssl, byte[] input) {
        // Modern handshake techniques don't use the private key to decrypt, only to sign in order to verify
        // identity. As such, we don't currently support decrypting using the private key.
        throw new UnsupportedOperationException("Direct decryption is not supported");
    }

    private Signature createSignature(Integer opensslAlgorithm)
            throws NoSuchAlgorithmException {
        String jdkAlgorithm = SSL_TO_JDK_SIGNATURE_ALGORITHM.get(opensslAlgorithm);
        if (jdkAlgorithm == null) {
            String errorMsg = "Unsupported signature algorithm: " + opensslAlgorithm;
            throw new NoSuchAlgorithmException(errorMsg);
        }

        String keyType = privateKey.getClass().getName();
        CacheKey cacheKey = new CacheKey(jdkAlgorithm, keyType);

        // Try cached provider first
        String cachedProviderName = PROVIDER_CACHE.get(cacheKey);
        if (cachedProviderName != null) {
            try {
                Signature signature = Signature.getInstance(jdkAlgorithm, cachedProviderName);
                configureOpenSslAlgorithmParameters(signature, opensslAlgorithm);
                signature.initSign(privateKey);
                if (logger.isDebugEnabled()) {
                    logger.debug("Using cached provider {} for OpenSSL algorithm {} ({}) with key type {}",
                            cachedProviderName, opensslAlgorithm, jdkAlgorithm, keyType);
                }
                return signature;
            } catch (Exception e) {
                // Cache is stale, remove it and try full discovery
                PROVIDER_CACHE.remove(cacheKey);
                if (logger.isDebugEnabled()) {
                    logger.debug("Cached provider {} failed for key type {}, removing from cache: {}",
                            cachedProviderName, keyType, e.getMessage());
                }
            }
        }

        // Do full discovery and cache result
        Signature signature = findCompatibleSignature(opensslAlgorithm, jdkAlgorithm);
        PROVIDER_CACHE.put(cacheKey, signature.getProvider().getName());

        if (logger.isDebugEnabled()) {
            logger.debug("Discovered and cached provider {} for OpenSSL algorithm {} ({}) with key type {}",
                    signature.getProvider().getName(), opensslAlgorithm, jdkAlgorithm, keyType);
        }

        return signature;
    }

    private Signature findCompatibleSignature(Integer opensslAlgorithm,
                                              String jdkAlgorithm) throws NoSuchAlgorithmException {

        // 1. Try default provider first (optimization)
        try {
            Signature signature = Signature.getInstance(jdkAlgorithm);
            configureOpenSslAlgorithmParameters(signature, opensslAlgorithm);
            signature.initSign(privateKey);
            if (logger.isDebugEnabled()) {
                logger.debug("Default provider {} handles key type {} for OpenSSL algorithm {} ({})",
                        signature.getProvider().getName(), privateKey.getClass().getName(),
                        opensslAlgorithm, jdkAlgorithm);
            }
            return signature; // Success!
        } catch (InvalidKeyException e) {
            // Default provider can't handle this key type, continue with full search
            if (logger.isDebugEnabled()) {
                logger.debug("Default provider cannot handle key type {} for OpenSSL algorithm {} ({}): {}",
                        privateKey.getClass().getName(), opensslAlgorithm, jdkAlgorithm, e.getMessage());
            }
        } catch (Exception e) {
            // Other issues with default provider, continue searching
            if (logger.isDebugEnabled()) {
                logger.debug("Default provider failed for OpenSSL algorithm {} ({}): {}",
                        opensslAlgorithm, jdkAlgorithm, e.getMessage());
            }
        }

        // 2. Iterate through all providers. Note this iteration goes in order of priority.
        Provider[] providers = Security.getProviders();
        for (Provider provider : providers) {
            try {
                Signature signature = Signature.getInstance(jdkAlgorithm, provider);
                configureOpenSslAlgorithmParameters(signature, opensslAlgorithm);
                signature.initSign(privateKey); // Test compatibility

                if (logger.isDebugEnabled()) {
                    logger.debug("Found compatible provider {} for key type {} with OpenSSL algorithm {} ({})",
                            provider.getName(), privateKey.getClass().getName(), opensslAlgorithm, jdkAlgorithm);
                }
                return signature; // Found compatible provider!
            } catch (InvalidKeyException e) {
                // This provider can't handle this key type, try next
                if (logger.isTraceEnabled()) {
                    logger.trace("Provider {} cannot handle key type {}: {}",
                            provider.getName(), privateKey.getClass().getName(), e.getMessage());
                }
            } catch (Exception e) {
                // Other issues, try next provider
                if (logger.isTraceEnabled()) {
                    logger.trace("Provider {} failed for OpenSSL algorithm {} ({}): {}",
                            provider.getName(), opensslAlgorithm, jdkAlgorithm, e.getMessage());
                }
            }
        }

        throw new NoSuchAlgorithmException("No provider found for OpenSSL algorithm " + opensslAlgorithm +
                " (" + jdkAlgorithm + ") with private key type: " + privateKey.getClass().getName());
    }

    private static void configureOpenSslAlgorithmParameters(Signature signature, Integer opensslAlgorithm)
            throws InvalidAlgorithmParameterException {
        // Use the OpenSSL algorithm constants for precise parameter configuration
        if (opensslAlgorithm == OpenSslAsyncPrivateKeyMethod.SSL_SIGN_RSA_PSS_RSAE_SHA256) {
            // SHA-256 based PSS with MGF1-SHA256, salt length 32
            configurePssParameters(signature, MGF1ParameterSpec.SHA256, 32);
        } else if (opensslAlgorithm == OpenSslAsyncPrivateKeyMethod.SSL_SIGN_RSA_PSS_RSAE_SHA384) {
            // SHA-384 based PSS with MGF1-SHA384, salt length 48
            configurePssParameters(signature, MGF1ParameterSpec.SHA384, 48);
        } else if (opensslAlgorithm == OpenSslAsyncPrivateKeyMethod.SSL_SIGN_RSA_PSS_RSAE_SHA512) {
            // SHA-512 based PSS with MGF1-SHA512, salt length 64
            configurePssParameters(signature, MGF1ParameterSpec.SHA512, 64);
        } else if (SSL_TO_JDK_SIGNATURE_ALGORITHM.containsKey(opensslAlgorithm)) {
            // All other supported algorithms don't require special parameter configuration
            if (logger.isTraceEnabled()) {
                logger.trace("No parameter configuration needed for OpenSSL algorithm {}", opensslAlgorithm);
            }
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("Unknown OpenSSL algorithm {}, using default configuration", opensslAlgorithm);
            }
        }
    }

    private static void configurePssParameters(Signature signature,
                                               MGF1ParameterSpec mgfSpec, int saltLength)
            throws InvalidAlgorithmParameterException {
        PSSParameterSpec pssSpec = new PSSParameterSpec(mgfSpec.getDigestAlgorithm(), "MGF1", mgfSpec, saltLength, 1);
        signature.setParameter(pssSpec);

        if (logger.isDebugEnabled()) {
            logger.debug("Configured PSS parameters: hash={}, saltLength={}", mgfSpec.getDigestAlgorithm(), saltLength);
        }
    }

    private static final class CacheKey {
        private final String jdkAlgorithm;
        private final String keyTypeName;
        private final int hashCode;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            CacheKey cacheKey = (CacheKey) o;
            return Objects.equals(cacheKey.jdkAlgorithm, jdkAlgorithm)
                    && Objects.equals(cacheKey.keyTypeName, keyTypeName);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        CacheKey(String jdkAlgorithm, String keyTypeName) {
            this.jdkAlgorithm = jdkAlgorithm;
            this.keyTypeName = keyTypeName;
            this.hashCode = 31 * jdkAlgorithm.hashCode() + keyTypeName.hashCode();
        }
    }
}
