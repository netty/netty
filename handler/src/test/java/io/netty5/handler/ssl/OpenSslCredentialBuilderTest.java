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
package io.netty5.handler.ssl;

import io.netty5.pkitesting.CertificateBuilder;
import io.netty5.pkitesting.X509Bundle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for {@link OpenSslCredentialBuilder}.
 */
public class OpenSslCredentialBuilderTest {

    private static X509Bundle cert;

    @BeforeAll
    public static void setUp() throws Exception {
        OpenSsl.ensureAvailability();
        assumeTrue(OpenSsl.isBoringSSL(), "SSL_CREDENTIAL API is only supported with BoringSSL");

        // Create RSA certificate
        cert = new CertificateBuilder()
                .subject("cn=rsa.localhost")
                .buildSelfSigned();
    }

    @Test
    public void testBuildWithAllOptions() throws Exception {
        byte[] mockOcsp = new byte[]{0x30, 0x03, 0x0a, 0x01, 0x00};
        byte[] mockSct = new byte[]{0x00, 0x01, 0x02, 0x03};
        byte[] mockProps = new byte[]{0x00, 0x00};
        byte[] mockTrustAnchor = new byte[]{0x01, 0x02};
        int[] sigAlgPrefs = new int[]{0x0804, 0x0403};
        byte[] mockDc = new byte[]{0x00, 0x01, 0x02, 0x03}; // Mock delegated credential


        OpenSslCredential credential = OpenSslCredentialBuilder.newX509()
                .privateKey(cert.getKeyPair().getPrivate())
                .certificateChain(cert.getCertificate())
                .ocspResponse(mockOcsp)
                .signedCertificateTimestamps(mockSct)
                .signingAlgorithmPreferences(sigAlgPrefs)
                .certificateProperties(mockProps)
                .trustAnchorId(mockTrustAnchor)
                .delegatedCredential(mockDc)
                .mustMatchIssuer(true)
                .build();

        assertNotNull(credential);
        credential.release();
    }

    @Test
    public void testBuildWithoutPrivateKey() throws Exception {
        // Building without a private key should throw
        Exception exception = assertThrows(
                IllegalStateException.class,
                () -> OpenSslCredentialBuilder.newX509()
                        .certificateChain(cert.getCertificate())
                        .build()
        );
        assertTrue(exception.getMessage().contains("Certificate chain provided without private key"));
    }

    @Test
    public void testBuildWithoutCertChain() throws Exception {
        // Building without a cert chain should throw
        Exception exception = assertThrows(
                IllegalStateException.class,
                () -> OpenSslCredentialBuilder.newX509()
                        .privateKey(cert.getKeyPair().getPrivate())
                        .build()
        );
        assertTrue(exception.getMessage().contains("Private key provided without certificate chain"));
    }
}
