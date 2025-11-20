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
package io.netty.handler.ssl;

import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;
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
        byte[] mockTrustAnchor = new byte[]{0x01, 0x02};

        OpenSslCredential credential = OpenSslCredentialBuilder.forX509()
                .privateKey(cert.getKeyPair().getPrivate())
                .certificateChain(cert.getCertificate())
                .trustAnchorId(mockTrustAnchor)
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
                () -> OpenSslCredentialBuilder.forX509()
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
                () -> OpenSslCredentialBuilder.forX509()
                        .privateKey(cert.getKeyPair().getPrivate())
                        .build()
        );
        assertTrue(exception.getMessage().contains("Private key provided without certificate chain"));
    }
}
