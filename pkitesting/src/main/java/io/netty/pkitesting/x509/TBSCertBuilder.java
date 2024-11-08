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
package io.netty.pkitesting.x509;

import io.netty.util.internal.UnstableApi;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x509.Time;
import org.bouncycastle.asn1.x509.V3TBSCertificateGenerator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.security.auth.x500.X500Principal;

@UnstableApi
public final class TBSCertBuilder {
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
        V3TBSCertificateGenerator generator = new V3TBSCertificateGenerator();
        generator.setIssuer(X500Name.getInstance(issuer.getEncoded()));
        generator.setSubject(X500Name.getInstance(subject.getEncoded()));
        generator.setSerialNumber(new ASN1Integer(serial));
        generator.setSignature(new org.bouncycastle.asn1.x509.AlgorithmIdentifier(new ASN1ObjectIdentifier(
                AlgorithmIdentifier.algorithmNameToOid(signatureAlgorithmIdentifier))));
        generator.setStartDate(new Time(new Date(notBefore.toEpochMilli())));
        generator.setEndDate(new Time(new Date(notAfter.toEpochMilli())));
        generator.setSubjectPublicKeyInfo(SubjectPublicKeyInfo.getInstance(pubKey.getEncoded()));
        if (!extensions.isEmpty()) {
            org.bouncycastle.asn1.x509.Extension[] bcExtensions =
                    new org.bouncycastle.asn1.x509.Extension[extensions.size()];
            for (int i = 0; i < extensions.size(); i++) {
                Extension ex = extensions.get(i);
                bcExtensions[i] = new org.bouncycastle.asn1.x509.Extension(
                        new ASN1ObjectIdentifier(ex.getExtnId()),
                        ex.isCritical(),
                        new DEROctetString(ex.getExtnValue())
                );
            }
            generator.setExtensions(new Extensions(bcExtensions));
        }
        try {
            return generator.generateTBSCertificate().getEncoded("DER");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
