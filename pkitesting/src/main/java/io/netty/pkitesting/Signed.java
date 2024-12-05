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

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Objects;

final class Signed {
    private final byte[] toBeSigned;
    private final String algorithmIdentifier;
    private final PrivateKey privateKey;

    Signed(byte[] toBeSigned, X509Bundle signer) {
        this(toBeSigned, signer.getCertificate().getSigAlgName(), signer.getKeyPair().getPrivate());
    }

    Signed(byte[] toBeSigned, String algorithmIdentifier, PrivateKey privateKey) {
        this.toBeSigned = Objects.requireNonNull(toBeSigned, "toBeSigned");
        this.algorithmIdentifier = Objects.requireNonNull(algorithmIdentifier, "algorithmIdentifier");
        this.privateKey = privateKey;
    }

    byte[] getEncoded() throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature signature = Algorithms.signature(algorithmIdentifier);
        signature.initSign(privateKey);
        signature.update(toBeSigned);
        byte[] signatureBytes = signature.sign();
        try {
            return new DERSequence(new ASN1Encodable[]{
                    ASN1Primitive.fromByteArray(toBeSigned),
                    new AlgorithmIdentifier(new ASN1ObjectIdentifier(
                            Algorithms.oidForAlgorithmName(algorithmIdentifier))),
                    new DERBitString(signatureBytes)
            }).getEncoded("DER");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    InputStream toInputStream() throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        return new ByteArrayInputStream(getEncoded());
    }
}
