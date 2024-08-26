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
package io.netty.testcert.x509;

import io.netty.testcert.X509Bundle;
import io.netty.testcert.der.DerWriter;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Objects;
import java.util.function.Supplier;

public final class Signed {
    private final byte[] toBeSigned;
    private final String algorithmIdentifier;
    private final PrivateKey privateKey;

    public Signed(Supplier<byte[]> toBeSigned, X509Bundle signer) {
        this(toBeSigned, signer.getCertificate().getSigAlgName(), signer.getKeyPair().getPrivate());
    }

    public Signed(Supplier<byte[]> toBeSigned, String algorithmIdentifier, PrivateKey privateKey) {
        this.toBeSigned = toBeSigned.get();
        this.algorithmIdentifier = Objects.requireNonNull(algorithmIdentifier, "algorithmIdentifier");
        this.privateKey = privateKey;
    }

    public byte[] getEncoded() throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature signature = Signature.getInstance(algorithmIdentifier);
        signature.initSign(privateKey);
        signature.update(toBeSigned);
        byte[] signatureBytes = signature.sign();
        try (DerWriter der = new DerWriter()) {
            der.writeSequence(writer -> {
                writer.writeRawDER(toBeSigned);
                AlgorithmIdentifier.writeAlgorithmId(algorithmIdentifier, writer);
                writer.writeBitString(signatureBytes, 0);
            });
            return der.getBytes();
        }
    }

    public InputStream toInputStream() throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        return new ByteArrayInputStream(getEncoded());
    }
}
