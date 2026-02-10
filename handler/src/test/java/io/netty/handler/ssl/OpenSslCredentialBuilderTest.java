/*
 * Copyright 2026 The Netty Project
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

import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for {@link OpenSslCredentialBuilder}.
 */
public class OpenSslCredentialBuilderTest {

    private static X509Bundle cert;

    @BeforeAll
    public static void setUp() throws Exception {
        assumeTrue(OpenSslCredential.isAvailable());

        // Create RSA certificate
        cert = new CertificateBuilder()
                .subject("cn=rsa.localhost")
                .setIsCertificateAuthority(true)
                .buildSelfSigned();
    }

    @Test
    public void testBuildWithAllOptions() throws Exception {
        // Create trust anchor ID bytes using BouncyCastle's ASN1ObjectIdentifier for proper DER encoding
        ASN1ObjectIdentifier oid = new ASN1ObjectIdentifier("1.3.6.1.4.1.11129.9.10"); // Google's taiWE1
        byte[] trustAnchorId = oid.getEncoded();

        OpenSslCredential credential = OpenSslCredentialBuilder
                .forX509(cert.getKeyPair().getPrivate(), cert.getCertificate())
                .trustAnchorId(trustAnchorId)
                .mustMatchIssuer(true)
                .build();

        assertNotNull(credential);
        credential.release();
    }
}
