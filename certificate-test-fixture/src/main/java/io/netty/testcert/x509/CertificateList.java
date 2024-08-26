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

import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Map;

public final class CertificateList implements DerWriter.WritableSequence {
    private final X509Bundle issuer;
    private final Instant thisUpdate;
    private final Instant nextUpdate;
    private final Iterable<Map.Entry<BigInteger, Instant>> revokedCerts;

    public CertificateList(X509Bundle issuer, Instant thisUpdate, Instant nextUpdate,
                           Iterable<Map.Entry<BigInteger, Instant>> revokedCerts) {
        this.issuer = issuer;
        this.thisUpdate = thisUpdate;
        this.nextUpdate = nextUpdate;
        this.revokedCerts = revokedCerts;
    }

    public byte[] getEncoded() {
        try (DerWriter der = new DerWriter()) {
            der.writeSequence(this);
            return der.getBytes();
        }
    }

    @Override
    public void writeSequence(DerWriter writer) {
        X509Certificate cert = issuer.getCertificate();
        writer.writeInteger(1); // Version v2
        AlgorithmIdentifier.writeAlgorithmId(cert.getSigAlgName(), writer); // signature
        writer.writeRawDER(cert.getSubjectX500Principal().getEncoded()); // issuer
        writer.writeGeneralizedTime(thisUpdate);
        if (nextUpdate != null) {
            writer.writeGeneralizedTime(nextUpdate);
        }
        writer.writeSequence(outer -> {
            for (Map.Entry<BigInteger, Instant> revokedCert : revokedCerts) {
                outer.writeSequence(inner -> {
                    inner.writeInteger(revokedCert.getKey());
                    inner.writeGeneralizedTime(revokedCert.getValue());
                });
            }
        });
    }
}
