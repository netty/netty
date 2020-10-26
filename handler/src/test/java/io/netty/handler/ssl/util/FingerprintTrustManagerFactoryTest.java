/*
 * Copyright 2020 The Netty Project
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

package io.netty.handler.ssl.util;

import org.junit.Test;

import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import static io.netty.handler.ssl.Java8SslTestUtils.loadCertCollection;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FingerprintTrustManagerFactoryTest {

    private static final String FIRST_CERT_SHA1_FINGERPRINT
            = "18:C7:C2:76:1F:DF:72:3B:2A:A7:BB:2C:B0:30:D4:C0:C0:72:AD:84";

    private static final String FIRST_CERT_SHA256_FINGERPRINT
            = "1C:53:0E:6B:FF:93:F0:DE:C2:E6:E7:9D:10:53:58:FF:" +
              "DD:8E:68:CD:82:D9:C9:36:9B:43:EE:B3:DC:13:68:FB";

    private static final X509Certificate[] FIRST_CHAIN;

    private static final X509Certificate[] SECOND_CHAIN;

    static {
        try {
            FIRST_CHAIN = loadCertCollection("test.crt");
            SECOND_CHAIN = loadCertCollection("test2.crt");
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFingerprintWithInvalidLength() {
        FingerprintTrustManagerFactory.builder("SHA-256").fingerprints("00:00:00").build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFingerprintWithUnexpectedCharacters() {
        FingerprintTrustManagerFactory.builder("SHA-256").fingerprints("00:00:00\n").build();
    }

    @Test(expected = IllegalStateException.class)
    public void testWithNoFingerprints() {
        FingerprintTrustManagerFactory.builder("SHA-256").build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithNullFingerprint() {
        FingerprintTrustManagerFactory
                .builder("SHA-256")
                .fingerprints(FIRST_CERT_SHA256_FINGERPRINT, null)
                .build();
    }

    @Test
    public void testValidSHA1Fingerprint() throws Exception {
        FingerprintTrustManagerFactory factory = new FingerprintTrustManagerFactory(FIRST_CERT_SHA1_FINGERPRINT);

        assertTrue(factory.engineGetTrustManagers().length > 0);
        assertTrue(factory.engineGetTrustManagers()[0] instanceof X509TrustManager);
        X509TrustManager tm = (X509TrustManager) factory.engineGetTrustManagers()[0];
        tm.checkClientTrusted(FIRST_CHAIN, "test");
    }

    @Test
    public void testTrustedCertificateWithSHA256Fingerprint() throws Exception {
        FingerprintTrustManagerFactory factory = FingerprintTrustManagerFactory
                .builder("SHA-256")
                .fingerprints(FIRST_CERT_SHA256_FINGERPRINT)
                .build();

        X509Certificate[] keyCertChain = loadCertCollection("test.crt");
        assertNotNull(keyCertChain);
        assertTrue(factory.engineGetTrustManagers().length > 0);
        assertTrue(factory.engineGetTrustManagers()[0] instanceof X509TrustManager);
        X509TrustManager tm = (X509TrustManager) factory.engineGetTrustManagers()[0];
        tm.checkClientTrusted(keyCertChain, "test");
    }

    @Test(expected = CertificateException.class)
    public void testUntrustedCertificateWithSHA256Fingerprint() throws Exception {
        FingerprintTrustManagerFactory factory = FingerprintTrustManagerFactory
                .builder("SHA-256")
                .fingerprints(FIRST_CERT_SHA256_FINGERPRINT)
                .build();

        assertTrue(factory.engineGetTrustManagers().length > 0);
        assertTrue(factory.engineGetTrustManagers()[0] instanceof X509TrustManager);
        X509TrustManager tm = (X509TrustManager) factory.engineGetTrustManagers()[0];
        tm.checkClientTrusted(SECOND_CHAIN, "test");
    }

}
