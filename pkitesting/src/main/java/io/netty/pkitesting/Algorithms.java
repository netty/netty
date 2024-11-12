/*
 * Copyright 2024 The Netty Project
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
package io.netty.pkitesting;

import io.netty.util.internal.UnstableApi;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.AlgorithmParameterSpec;

@UnstableApi
final class Algorithms {
    private static Provider bouncyCastle;

    private Algorithms() {
    }

    static String oidForAlgorithmName(String algorithmIdentifier) {
        switch (algorithmIdentifier) {
            case "SHA256withECDSA":
                return "1.2.840.10045.4.3.2";
            case "SHA384withECDSA":
                return "1.2.840.10045.4.3.3";
            case "SHA256withRSA":
                return "1.2.840.113549.1.1.11";
            case "SHA384withRSA":
                return "1.2.840.113549.1.1.12";
            case "Ed25519":
                return "1.3.101.112";
            case "Ed448":
                return "1.3.101.113";
            default:
                throw new UnsupportedOperationException("Algorithm not supported: " + algorithmIdentifier);
        }
    }

    static KeyPairGenerator keyPairGenerator(String keyType, AlgorithmParameterSpec spec, SecureRandom rng)
            throws GeneralSecurityException {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(keyType);
            keyGen.initialize(spec, rng);
            return keyGen;
        } catch (GeneralSecurityException e) {
            try {
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance(keyType, bouncyCastle());
                keyGen.initialize(spec, rng);
                return keyGen;
            } catch (GeneralSecurityException ex) {
                e.addSuppressed(ex);
            }
            throw e;
        }
    }

    static Signature signature(String algorithmIdentifier) throws NoSuchAlgorithmException {
        try {
            return Signature.getInstance(algorithmIdentifier);
        } catch (NoSuchAlgorithmException e) {
            try {
                return Signature.getInstance(algorithmIdentifier, bouncyCastle());
            } catch (NoSuchAlgorithmException ex) {
                e.addSuppressed(ex);
            }
            throw e;
        }
    }

    private static synchronized Provider bouncyCastle() {
        if (bouncyCastle == null) {
            bouncyCastle = new BouncyCastleProvider();
        }
        return bouncyCastle;
    }
}
