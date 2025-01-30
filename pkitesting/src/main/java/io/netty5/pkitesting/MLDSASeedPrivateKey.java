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
package io.netty5.pkitesting;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.PrivateKey;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;
import javax.security.auth.DestroyFailedException;

/**
 * ML-DSA needs to be represented in a seed-form when serialized, but the current encoding the JDK is using
 * the expanded form, and throws away the seed during the key generation.
 * <p>
 * This class preserves the seed, and keeps a seed-form encoding of the key, which we can use when creating PEM files.
 * It is important to unwrap the key to its inner representation, before interacting with the JDK and 3rd-party
 * libraries, like BouncyCastle, since they may have expectations around the concrete class used.
 */
final class MLDSASeedPrivateKey implements PrivateKey {
    private static final long serialVersionUID = 4206741400099880395L;
    private final PrivateKey key;
    private final byte[] seedFormat;

    MLDSASeedPrivateKey(PrivateKey key, CertificateBuilder.Algorithm algorithm, byte[] seed) {
        this.key = key;
        // See https://www.ietf.org/archive/id/draft-ietf-lamps-dilithium-certificates-06.html#name-private-key-format
        try {
            seedFormat = new DERSequence(new ASN1Encodable[]{
                    new ASN1Integer(0),
                    new DERSequence(new ASN1ObjectIdentifier(Algorithms.oidForAlgorithmName(algorithm.signatureType))),
                    new DEROctetString(seed)
            }).getEncoded("DER");
        } catch (IOException e) {
            throw new UncheckedIOException("Unexpected problem encoding private key DER", e);
        }
    }

    public AlgorithmParameterSpec getParams() {
        try {
            return (AlgorithmParameterSpec) key.getClass().getMethod("getParams").invoke(key);
        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }
    }

    @Override
    public String getAlgorithm() {
        return key.getAlgorithm();
    }

    @Override
    public String getFormat() {
        return key.getFormat();
    }

    @Override
    public byte[] getEncoded() {
        return seedFormat.clone();
    }

    @Override
    public void destroy() throws DestroyFailedException {
        key.destroy();
        Arrays.fill(seedFormat, (byte) 0);
    }

    @Override
    public boolean isDestroyed() {
        return key.isDestroyed();
    }

    static byte[] getEncoded(PrivateKey key) {
        if (key instanceof MLDSASeedPrivateKey) {
            return ((MLDSASeedPrivateKey) key).seedFormat.clone();
        }
        return key.getEncoded();
    }

    static PrivateKey unwrap(PrivateKey key) {
        if (key instanceof MLDSASeedPrivateKey) {
            return ((MLDSASeedPrivateKey) key).key;
        }
        return key;
    }
}
