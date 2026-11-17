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
import io.netty.util.SecureObjectIdentityHash;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

// Tests for the session_id_context derivation.
// See https://github.com/netty/netty/security/advisories/GHSA-6xv6-9mgg-mfvc
public class SslContextSessionIdContextTest {

    private static final SelfSignedCertificate SELF_SIGNED_CERT_B;
    private static final byte[] SEED = "test-seed".getBytes(StandardCharsets.US_ASCII);
    private static final SecureObjectIdentityHash HASHES = new SecureObjectIdentityHash();

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

    private static SessionIdContextBuilder newBuilder(ClientAuth clientAuth) {
        return new SessionIdContextBuilder(HASHES).setSeed(SEED).setClientAuth(clientAuth);
    }

    private static byte[] derive(ClientAuth clientAuth, X509Certificate[] keyCertChain,
                                 X509Certificate[] trustCerts) {
        return newBuilder(clientAuth)
                .setKeyCertificateChain(keyCertChain)
                .setTrustedCertificates(trustCerts)
                .build();
    }

    @Test
    public void identicalConfigurationsDeriveTheSameId() {
        byte[] id = derive(ClientAuth.REQUIRE, chainA, chainB);
        // Needs to fit into SSL_MAX_SID_CTX_LENGTH.
        assertEquals(32, id.length);
        assertArrayEquals(id, derive(ClientAuth.REQUIRE, chainA.clone(), chainB.clone()));
    }

    @Test
    public void differentClientAuthDerivesDifferentIds() {
        byte[] none = derive(ClientAuth.NONE, chainA, null);
        byte[] optional = derive(ClientAuth.OPTIONAL, chainA, null);
        byte[] require = derive(ClientAuth.REQUIRE, chainA, null);
        assertFalse(Arrays.equals(none, optional));
        assertFalse(Arrays.equals(none, require));
        assertFalse(Arrays.equals(optional, require));
    }

    @Test
    public void differentServerCertificatesDeriveDifferentIds() {
        assertFalse(Arrays.equals(
                derive(ClientAuth.REQUIRE, chainA, chainA),
                derive(ClientAuth.REQUIRE, chainB, chainA)));
    }

    @Test
    public void differentTrustConfigurationsDeriveDifferentIds() {
        byte[] trustA = derive(ClientAuth.REQUIRE, chainA, chainA);
        byte[] trustB = derive(ClientAuth.REQUIRE, chainA, chainB);
        byte[] trustDefault = derive(ClientAuth.REQUIRE, chainA, null);
        assertFalse(Arrays.equals(trustA, trustB));
        assertFalse(Arrays.equals(trustA, trustDefault));
        assertFalse(Arrays.equals(trustB, trustDefault));
    }

    @Test
    public void certificatesInDifferentFieldsDoNotCollide() {
        // Swapping the key chain and the trust certs must not result in the same digest.
        assertFalse(Arrays.equals(
                derive(ClientAuth.REQUIRE, chainA, chainB),
                derive(ClientAuth.REQUIRE, chainB, chainA)));
    }

    @Test
    public void sameTrustManagerDerivesTheSameId() {
        TrustManager tm = new TrustManager() { };
        assertArrayEquals(
                newBuilder(ClientAuth.REQUIRE).setKeyCertificateChain(chainA).setTrustManager(tm).build(),
                newBuilder(ClientAuth.REQUIRE).setKeyCertificateChain(chainA).setTrustManager(tm).build());
    }

    @Test
    public void differentTrustManagersDeriveDifferentIds() {
        // We can not tell if two different TrustManager instances behave the same, so they must never
        // be treated as equivalent.
        assertFalse(Arrays.equals(
                newBuilder(ClientAuth.REQUIRE).setKeyCertificateChain(chainA)
                        .setTrustManager(new TrustManager() { }).build(),
                newBuilder(ClientAuth.REQUIRE).setKeyCertificateChain(chainA)
                        .setTrustManager(new TrustManager() { }).build()));
        assertFalse(Arrays.equals(
                newBuilder(ClientAuth.REQUIRE).setKeyCertificateChain(chainA)
                        .setTrustManager(new TrustManager() { }).build(),
                derive(ClientAuth.REQUIRE, chainA, null)));
    }

    @Test
    public void sameKeyManagerDerivesTheSameIdAndDifferentOnesDoNot() {
        KeyManager km = new KeyManager() { };
        assertArrayEquals(
                newBuilder(ClientAuth.NONE).setKeyManager(km).build(),
                newBuilder(ClientAuth.NONE).setKeyManager(km).build());
        assertFalse(Arrays.equals(
                newBuilder(ClientAuth.NONE).setKeyManager(km).build(),
                newBuilder(ClientAuth.NONE).setKeyManager(new KeyManager() { }).build()));
    }

    @Test
    public void nullTrustSettersClearTrustMaterial() {
        byte[] noTrust = newBuilder(ClientAuth.REQUIRE).setKeyCertificateChain(chainA).build();
        assertArrayEquals(noTrust, newBuilder(ClientAuth.REQUIRE).setKeyCertificateChain(chainA)
                .setTrustManager(new TrustManager() { }).setTrustManager(null).build());
        assertArrayEquals(noTrust, newBuilder(ClientAuth.REQUIRE).setKeyCertificateChain(chainA)
                .setTrustManager(new TrustManager() { }).setTrustManagerFactory(null).build());
        assertArrayEquals(noTrust, newBuilder(ClientAuth.REQUIRE).setKeyCertificateChain(chainA)
                .setTrustedCertificates(chainB).setTrustedCertificates(null).build());
        assertArrayEquals(noTrust, newBuilder(ClientAuth.REQUIRE).setKeyCertificateChain(chainA)
                .setTrustedCertificates(chainB).setTrustManager(null).build());
    }

    @Test
    public void nullKeySettersClearLocalIdentityMaterial() {
        byte[] noKey = newBuilder(ClientAuth.NONE).setTrustedCertificates(chainB).build();
        assertArrayEquals(noKey, newBuilder(ClientAuth.NONE).setTrustedCertificates(chainB)
                .setKeyManager(new KeyManager() { }).setKeyManager(null).build());
        assertArrayEquals(noKey, newBuilder(ClientAuth.NONE).setTrustedCertificates(chainB)
                .setKeyManager(new KeyManager() { }).setKeyManagerFactory(null).build());
        assertArrayEquals(noKey, newBuilder(ClientAuth.NONE).setTrustedCertificates(chainB)
                .setKeyCertificateChain(chainA).setKeyCertificateChain(null).build());
        assertArrayEquals(noKey, newBuilder(ClientAuth.NONE).setTrustedCertificates(chainB)
                .setKeyCertificateChain(chainA).setKeyManager(null).build());
    }

    @Test
    public void sslContextBuilderAcceptsNullMaterial() {
        assertDoesNotThrow(() -> SslContextBuilder.forClient()
                .keyManager((PrivateKey) null, (X509Certificate[]) null));
        assertDoesNotThrow(() -> SslContextBuilder.forClient()
                .keyManager((PrivateKey) null, (String) null, (X509Certificate[]) null));
        assertDoesNotThrow(() -> SslContextBuilder.forClient().keyManager((KeyManagerFactory) null));
        assertDoesNotThrow(() -> SslContextBuilder.forClient().keyManager((KeyManager) null));
        assertDoesNotThrow(() -> SslContextBuilder.forClient().trustManager((X509Certificate[]) null));
        assertDoesNotThrow(() -> SslContextBuilder.forClient().trustManager((TrustManagerFactory) null));
        assertDoesNotThrow(() -> SslContextBuilder.forClient().trustManager((TrustManager) null));
    }

    @Test
    public void sslContextBuilderResetToNullClearsTrustMaterial() {
        byte[] fresh = SslContextBuilder.forClient().sessionIdContextBuilder().build();
        assertArrayEquals(fresh, SslContextBuilder.forClient()
                .trustManager(new TrustManager() { }).trustManager((TrustManager) null)
                .sessionIdContextBuilder().build());
        assertArrayEquals(fresh, SslContextBuilder.forClient()
                .trustManager(chainB).trustManager((TrustManagerFactory) null)
                .sessionIdContextBuilder().build());
        assertArrayEquals(fresh, SslContextBuilder.forClient()
                .trustManager(chainB).trustManager((X509Certificate[]) null)
                .sessionIdContextBuilder().build());
    }

    @Test
    public void sslContextBuilderResetToNullClearsKeyMaterial() {
        byte[] fresh = SslContextBuilder.forClient().sessionIdContextBuilder().build();
        PrivateKey key = SELF_SIGNED_CERT_B.key();
        assertArrayEquals(fresh, SslContextBuilder.forClient()
                .keyManager(new KeyManager() { }).keyManager((KeyManager) null)
                .sessionIdContextBuilder().build());
        assertArrayEquals(fresh, SslContextBuilder.forClient()
                .keyManager(key, chainB).keyManager((KeyManagerFactory) null)
                .sessionIdContextBuilder().build());
        assertArrayEquals(fresh, SslContextBuilder.forClient()
                .keyManager(key, chainB).keyManager((PrivateKey) null, (X509Certificate[]) null)
                .sessionIdContextBuilder().build());
    }

    @Test
    public void sslContextBuilderCopiesKeyCertChain() {
        X509Certificate[] chain = chainB.clone();
        SslContextBuilder builder = SslContextBuilder.forServer(SELF_SIGNED_CERT_B.key(), chain);
        byte[] before = builder.sessionIdContextBuilder().build();
        chain[0] = chainA[0];
        assertArrayEquals(before, builder.sessionIdContextBuilder().build());
    }
}
