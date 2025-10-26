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
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.SignatureSpi;
import java.security.spec.AlgorithmParameterSpec;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A mock security provider that simulates alternative key providers (HSMs, smart cards, etc.)
 * by wrapping a real RSA key but returning null from getEncoded() while delegating all
 * cryptographic operations to the underlying JDK implementation.
 */
public final class MockAlternativeKeyProvider extends Provider {

    private static final String PROVIDER_NAME = "MockAlternativeKeyProvider";
    private static final double PROVIDER_VERSION = 1.0;
    private static final String PROVIDER_INFO = "Mock provider simulating alternative key providers";

    // Track signature operations for test verification
    private static final AtomicInteger signatureOperations = new AtomicInteger(0);

    MockAlternativeKeyProvider() {
        super(PROVIDER_NAME, PROVIDER_VERSION, PROVIDER_INFO);

        // Register RSA signature algorithms
        put("Signature.RSASSA-PSS", MockRsaPssSignature.class.getName());
        put("Signature.SHA1withRSA", MockSha1WithRsaSignature.class.getName());
        put("Signature.SHA256withRSA", MockSha256WithRsaSignature.class.getName());
        put("Signature.SHA384withRSA", MockSha384WithRsaSignature.class.getName());
        put("Signature.SHA512withRSA", MockSha512WithRsaSignature.class.getName());
        put("Signature.MD5withRSA", MockMd5WithRsaSignature.class.getName());

        // Register ECDSA signature algorithms
        put("Signature.SHA1withECDSA", MockSha1WithEcdsaSignature.class.getName());
        put("Signature.SHA256withECDSA", MockSha256WithEcdsaSignature.class.getName());
        put("Signature.SHA384withECDSA", MockSha384WithEcdsaSignature.class.getName());
        put("Signature.SHA512withECDSA", MockSha512WithEcdsaSignature.class.getName());
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

    // Abstract base class for all mock signatures
    public abstract static class MockSignature extends SignatureSpi {
        protected final String algorithm;
        protected final String providerName;
        protected final Signature realSignature;

        protected MockSignature(String algorithm, String providerName)
                throws NoSuchProviderException, NoSuchAlgorithmException {
            this.algorithm = algorithm;
            this.providerName = providerName;
            this.realSignature = Signature.getInstance(algorithm, providerName);
        }

        @Override
        protected void engineInitVerify(PublicKey publicKey) throws InvalidKeyException {
            try {
                realSignature.initVerify(publicKey);
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
                    realSignature.initSign(privateKey, random);
                } else {
                    throw new InvalidKeyException("Unrecognized key type: " + privateKey.getClass().getName());
                }
            } catch (Exception e) {
                throw new InvalidKeyException("Failed to initialize signature", e);
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
            try {
                realSignature.setParameter(param, value);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        protected void engineSetParameter(AlgorithmParameterSpec params)
                throws InvalidAlgorithmParameterException {
            try {
                realSignature.setParameter(params);
            } catch (InvalidAlgorithmParameterException e) {
                throw e;
            } catch (Exception e) {
                throw new InvalidAlgorithmParameterException("Failed to set parameter", e);
            }
        }

        @Override
        protected Object engineGetParameter(String param) {
            try {
                return realSignature.getParameter(param);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    // Concrete RSA signature implementations
    public static final class MockRsaPssSignature extends MockSignature {
        public MockRsaPssSignature() throws NoSuchAlgorithmException, NoSuchProviderException {
            super("RSASSA-PSS", "SunRsaSign");
        }
    }

    public static final class MockSha1WithRsaSignature extends MockSignature {
        public MockSha1WithRsaSignature() throws NoSuchAlgorithmException, NoSuchProviderException {
            super("SHA1withRSA", "SunRsaSign");
        }
    }

    public static final class MockSha256WithRsaSignature extends MockSignature {
        public MockSha256WithRsaSignature() throws NoSuchAlgorithmException, NoSuchProviderException {
            super("SHA256withRSA", "SunRsaSign");
        }
    }

    public static final class MockSha384WithRsaSignature extends MockSignature {
        public MockSha384WithRsaSignature() throws NoSuchAlgorithmException, NoSuchProviderException {
            super("SHA384withRSA", "SunRsaSign");
        }
    }

    public static final class MockSha512WithRsaSignature extends MockSignature {
       public  MockSha512WithRsaSignature() throws NoSuchAlgorithmException, NoSuchProviderException {
            super("SHA512withRSA", "SunRsaSign");
        }
    }

    public static final class MockMd5WithRsaSignature extends MockSignature {
        public MockMd5WithRsaSignature() throws NoSuchAlgorithmException, NoSuchProviderException {
            super("MD5withRSA", "SunRsaSign");
        }
    }

    // Concrete ECDSA signature implementations
    public static final class MockSha1WithEcdsaSignature extends MockSignature {
        public MockSha1WithEcdsaSignature() throws NoSuchAlgorithmException, NoSuchProviderException {
            super("SHA1withECDSA", "SunEC");
        }
    }

    public static final class MockSha256WithEcdsaSignature extends MockSignature {
        public MockSha256WithEcdsaSignature() throws NoSuchAlgorithmException, NoSuchProviderException {
            super("SHA256withECDSA", "SunEC");
        }
    }

    public static final class MockSha384WithEcdsaSignature extends MockSignature {
        public MockSha384WithEcdsaSignature() throws NoSuchAlgorithmException, NoSuchProviderException {
            super("SHA384withECDSA", "SunEC");
        }
    }

    public static final class MockSha512WithEcdsaSignature extends MockSignature {
        public MockSha512WithEcdsaSignature() throws NoSuchAlgorithmException, NoSuchProviderException {
            super("SHA512withECDSA", "SunEC");
        }
    }
}
