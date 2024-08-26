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

import io.netty.testcert.der.DerWriter;

import java.math.BigInteger;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.security.auth.x500.X500Principal;

public final class TBSCertBuilder implements DerWriter.WritableSequence {
    private final X500Principal issuer;
    private final X500Principal subject;
    private final BigInteger serial;
    private final Instant notBefore;
    private final Instant notAfter;
    private final PublicKey pubKey;
    private final String signatureAlgorithmIdentifier;
    private final List<Extension> extensions;

    public TBSCertBuilder(
            X500Principal issuer, X500Principal subject,
            BigInteger serial,
            Instant notBefore, Instant notAfter,
            PublicKey pubKey,
            String signatureAlgorithmIdentifier) {
        this.issuer = issuer;
        this.subject = subject;
        this.serial = serial;
        this.notBefore = notBefore;
        this.notAfter = notAfter;
        this.pubKey = pubKey;
        this.signatureAlgorithmIdentifier = signatureAlgorithmIdentifier;
        extensions = new ArrayList<>();
    }

    public void addExtension(Extension extension) {
        extensions.add(extension);
    }

    public byte[] getEncoded() {
        try (DerWriter der = new DerWriter()) {
            return der.writeSequence(this).getBytes();
        }
    }

    @Override
    public void writeSequence(DerWriter writer) {
        writer.writeExplicit(DerWriter.TAG_CONTEXT|DerWriter.TAG_CONSTRUCTED, w -> w.writeInteger(2));
        writer.writeInteger(serial);
        AlgorithmIdentifier.writeAlgorithmId(signatureAlgorithmIdentifier, writer);
        writer.writeRawDER(issuer.getEncoded());
        // Validity
        writer.writeSequence(w -> w.writeGeneralizedTime(notBefore).writeGeneralizedTime(notAfter));
        writer.writeRawDER(subject.getEncoded());
        // SubjectPublicKeyInfo - we assume this is the public keys "primary encoding"
        // BouncyCastle likewise makes this assumption, so it's not unheard of.
        writer.writeRawDER(pubKey.getEncoded());
        if (!extensions.isEmpty()) {
            writer.writeExplicit(DerWriter.TAG_CONTEXT|DerWriter.TAG_CONSTRUCTED|3, extensionWriter -> {
                extensionWriter.writeSequence(w -> extensions.forEach(extension -> extension.encode(w)));
            });
        }
    }
}
