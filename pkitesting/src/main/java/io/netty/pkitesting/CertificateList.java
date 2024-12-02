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
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.TBSCertList;
import org.bouncycastle.asn1.x509.Time;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

final class CertificateList {
    private final X509Bundle issuer;
    private final Instant thisUpdate;
    private final Instant nextUpdate;
    private final Iterable<Map.Entry<BigInteger, Instant>> revokedCerts;

    CertificateList(X509Bundle issuer, Instant thisUpdate, Instant nextUpdate,
                    Iterable<Map.Entry<BigInteger, Instant>> revokedCerts) {
        this.issuer = issuer;
        this.thisUpdate = thisUpdate;
        this.nextUpdate = nextUpdate;
        this.revokedCerts = revokedCerts;
    }

    byte[] getEncoded() {
        ASN1EncodableVector vec = new ASN1EncodableVector();
        X509Certificate cert = issuer.getCertificate();
        vec.add(new ASN1Integer(1)); // Version 2
        vec.add(new AlgorithmIdentifier(new ASN1ObjectIdentifier(cert.getSigAlgOID())));
        vec.add(X500Name.getInstance(cert.getSubjectX500Principal().getEncoded()));
        vec.add(new Time(Date.from(thisUpdate)));
        if (nextUpdate != null) {
            vec.add(new Time(Date.from(nextUpdate)));
        }
        ASN1EncodableVector revokedVec = new ASN1EncodableVector();
        for (Map.Entry<BigInteger, Instant> revokedCert : revokedCerts) {
            revokedVec.add(TBSCertList.CRLEntry.getInstance(new DERSequence(new ASN1Encodable[]{
                    new ASN1Integer(revokedCert.getKey()),
                    new Time(Date.from(revokedCert.getValue()))
            })));
        }
        vec.add(new DERSequence(revokedVec));
        TBSCertList list = new TBSCertList(new DERSequence(vec));
        try {
            return list.getEncoded("DER");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
