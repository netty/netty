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

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.SignatureSpi;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A mock security provider that simulates alternative key providers (HSMs, smart cards, etc.)
 * by wrapping a real RSA key but returning null from getEncoded() while delegating all
 * cryptographic operations to the underlying JDK implementation.
 */
final class MockAlternativeKeyProvider extends Provider {

    private static final String PROVIDER_NAME = "MockAlternativeKeyProvider";
    private static final double PROVIDER_VERSION = 1.0;
    private static final String PROVIDER_INFO = "Mock provider simulating alternative key providers";

    // Track signature operations for test verification
    private static final AtomicInteger signatureOperations = new AtomicInteger(0);

    MockAlternativeKeyProvider() {
        super(PROVIDER_NAME, PROVIDER_VERSION, PROVIDER_INFO);

        Provider prototype = Security.getProvider("SunRsaSign");
        assert prototype != null;
        for (Map.Entry<Object, Object> entry : prototype.entrySet()) {
            String key = (String) entry.getKey();
            if (key.startsWith("Signature.")) {
                put(entry.getKey(), MockRSASignature.class.getName());
            }
        }
    }

    static PrivateKey wrapPrivateKey(PrivateKey realKey) {
        return new AlternativePrivateKeyWrapper(realKey);
    }

    /**
     * Reset the signature operation counter for test isolation.
     */
    static void resetSignatureOperationCount() {
        signatureOperations.set(0);
    }

    /**
     * Get the number of signature operations performed by this provider.
     */
    static int getSignatureOperationCount() {
        return signatureOperations.get();
    }

    private static final class AlternativePrivateKeyWrapper implements PrivateKey {
        private final PrivateKey delegate;

        AlternativePrivateKeyWrapper(PrivateKey delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getAlgorithm() {
            return delegate.getAlgorithm();
        }

        @Override
        public String getFormat() {
            // Alternative key providers typically don't support standard formats
            return null;
        }

        @Override
        public byte[] getEncoded() {
            // This is the key behavior: alternative key providers return null
            // because the private key material is not directly accessible
            return null;
        }

        // Provide access to the real key for signature operations
        PrivateKey getDelegate() {
            return delegate;
        }
    }

    // Needs to be public for the SPI structure to work
    public static final class MockRSASignature extends SignatureSpi {
        // TODO: is there a more robust way to do this? The typical pattern is class-per-algorithm
        private static final String algorithm = "RSASSA-PSS";

        private Signature realSignature;

        @Override
        protected void engineInitVerify(PublicKey publicKey) throws InvalidKeyException {
            try {
                getRealSignature().initVerify(publicKey);
            } catch (Exception e) {
                throw new InvalidKeyException("Failed to initialize signature", e);
            }
        }

        @Override
        protected void engineInitSign(PrivateKey privateKey) throws InvalidKeyException {
            engineInitSign(privateKey, null);
        }

        @Override
        protected void engineInitSign(PrivateKey privateKey, SecureRandom random) throws InvalidKeyException {
            try {
                // Extract the real key if it's wrapped
                if (privateKey instanceof AlternativePrivateKeyWrapper) {
                    privateKey = ((AlternativePrivateKeyWrapper) privateKey).getDelegate();
                    getRealSignature().initSign(privateKey, random);
                } else {
                    throw new InvalidKeyException("Unrecognized key type: " + privateKey.getClass().getName());
                }
            } catch (Exception e) {
                throw new InvalidKeyException("Failed to initialize signature", e);
            }
        }

        private Signature getRealSignature() throws Exception {
            if (realSignature == null) {
                realSignature = Signature.getInstance(algorithm, "SunRsaSign");
                configureParametersIfNeeded(realSignature);
            }
            return realSignature;
        }

        private static void configureParametersIfNeeded(Signature sig) throws Exception {
            if ("RSASSA-PSS".equals(algorithm)) {
                // Configure PSS parameters following the same pattern as JdkSignatureProviderDiscovery
                java.security.spec.PSSParameterSpec pssSpec =
                    new java.security.spec.PSSParameterSpec("SHA-256", "MGF1",
                        java.security.spec.MGF1ParameterSpec.SHA256, 32, 1);
                sig.setParameter(pssSpec);
            }
        }

        @Override
        protected void engineUpdate(byte b) throws SignatureException {
            realSignature.update(b);
        }

        @Override
        protected void engineUpdate(byte[] b, int off, int len) throws SignatureException {
            realSignature.update(b, off, len);
        }

        @Override
        protected byte[] engineSign() throws SignatureException {
            // Track signature operations
            signatureOperations.incrementAndGet();
            return realSignature.sign();
        }

        @Override
        protected boolean engineVerify(byte[] sigBytes) throws SignatureException {
            return realSignature.verify(sigBytes);
        }

        @Override
        protected void engineSetParameter(String param, Object value) {
            if (realSignature != null) {
                try {
                    realSignature.setParameter(param, value);
                } catch (Exception e) {
                    // Ignore parameter setting errors for compatibility
                }
            }
        }

        @Override
        protected void engineSetParameter(AlgorithmParameterSpec params)
                throws InvalidAlgorithmParameterException {
            if (realSignature != null) {
                try {
                    realSignature.setParameter(params);
                } catch (InvalidAlgorithmParameterException e) {
                    throw e;
                } catch (Exception e) {
                    throw new InvalidAlgorithmParameterException("Failed to set parameter", e);
                }
            }
        }

        @Override
        protected Object engineGetParameter(String param) {
            if (realSignature != null) {
                try {
                    return realSignature.getParameter(param);
                } catch (Exception e) {
                    // Ignore parameter retrieval errors for compatibility
                    return null;
                }
            }
            return null;
        }
    }
}
