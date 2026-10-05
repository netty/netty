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

import io.netty.handler.ssl.util.CachedSelfSignedCertificate;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

// Tests for the session_id_context derivation.
// See https://github.com/netty/netty/security/advisories/GHSA-6xv6-9mgg-mfvc
public class SslContextSessionIdContextTest {

    private static final SelfSignedCertificate SELF_SIGNED_CERT_B;

    static {
        try {
            SELF_SIGNED_CERT_B = new SelfSignedCertificate();
        } catch (CertificateException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static X509Certificate[] chainA;
    private static X509Certificate[] chainB;

    @BeforeAll
    public static void setUp() throws Exception {
        chainA = new X509Certificate[] { CachedSelfSignedCertificate.getCachedCertificate().cert() };
        chainB = new X509Certificate[] { SELF_SIGNED_CERT_B.cert() };
    }

    @AfterAll
    public static void tearDown() {
        SELF_SIGNED_CERT_B.delete();
    }

    private static byte[] derive(ClientAuth clientAuth, X509Certificate[] keyCertChain,
                                 X509Certificate[] trustCerts, boolean opaque) {
        return SslContext.deriveSessionIdContext(
                clientAuth, keyCertChain, trustCerts, opaque);
    }

    @Test
    public void identicalConfigurationsDeriveTheSameId() {
        byte[] id = derive(ClientAuth.REQUIRE, chainA, chainB, false);
        // Needs to fit into SSL_MAX_SID_CTX_LENGTH.
        assertEquals(32, id.length);
        assertArrayEquals(id, derive(ClientAuth.REQUIRE, chainA.clone(), chainB.clone(), false));
    }

    @Test
    public void differentClientAuthDerivesDifferentIds() {
        byte[] none = derive(ClientAuth.NONE, chainA, null, false);
        byte[] optional = derive(ClientAuth.OPTIONAL, chainA, null, false);
        byte[] require = derive(ClientAuth.REQUIRE, chainA, null, false);
        assertFalse(Arrays.equals(none, optional));
        assertFalse(Arrays.equals(none, require));
        assertFalse(Arrays.equals(optional, require));
    }

    @Test
    public void differentServerCertificatesDeriveDifferentIds() {
        assertFalse(Arrays.equals(
                derive(ClientAuth.REQUIRE, chainA, chainA, false),
                derive(ClientAuth.REQUIRE, chainB, chainA, false)));
    }

    @Test
    public void differentTrustConfigurationsDeriveDifferentIds() {
        byte[] trustA = derive(ClientAuth.REQUIRE, chainA, chainA, false);
        byte[] trustB = derive(ClientAuth.REQUIRE, chainA, chainB, false);
        byte[] trustDefault = derive(ClientAuth.REQUIRE, chainA, null, false);
        assertFalse(Arrays.equals(trustA, trustB));
        assertFalse(Arrays.equals(trustA, trustDefault));
        assertFalse(Arrays.equals(trustB, trustDefault));
    }

    @Test
    public void certificatesInDifferentFieldsDoNotCollide() {
        // Swapping the key chain and the trust certs must not result in the same digest.
        assertFalse(Arrays.equals(
                derive(ClientAuth.REQUIRE, chainA, chainB, false),
                derive(ClientAuth.REQUIRE, chainB, chainA, false)));
    }

    @Test
    public void opaqueConfigurationsDeriveUniqueIds() {
        // We can not tell if two contexts with a custom TrustManagerFactory behave the same, so they must never
        // be treated as equivalent.
        assertFalse(Arrays.equals(
                derive(ClientAuth.REQUIRE, chainA, null, true),
                derive(ClientAuth.REQUIRE, chainA, null, true)));
        assertFalse(Arrays.equals(
                derive(ClientAuth.REQUIRE, chainA, null, true),
                derive(ClientAuth.REQUIRE, chainA, null, false)));
    }
}
