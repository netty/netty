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
package io.netty.testcert;

import io.netty.testcert.CertificateBuilder.Algorithm;
import io.netty.testcert.CertificateBuilder.KeyUsage;
import io.netty.util.internal.PlatformDependent;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigInteger;
import java.security.Signature;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertificateException;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import java.util.TreeSet;
import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CertificateBuilderTest {
    private static final Instant NOW = Instant.now();
    private static final String SUBJECT = "CN=netty.io, O=Netty";
    private static final CertificateBuilder BASE = new CertificateBuilder()
            .notBefore(NOW.minus(1, DAYS))
            .notAfter(NOW.plus(1, DAYS))
            .subject(SUBJECT);

    @ParameterizedTest
    @EnumSource
    void createCertOfEveryKeyType(Algorithm algorithm) throws Exception {
        // Ed25519 and Ed448 are only supported on Java 15 and newer.
        assumeTrue(algorithm != Algorithm.ed25519 && algorithm != Algorithm.ed448 ||
                PlatformDependent.javaVersion() >= 15);
        // Assume that RSA 4096 and RSA 8192 work if the other RSA bit-widths work.
        // These big keys just take too long to test with.
        assumeTrue(algorithm != Algorithm.rsa4096 && algorithm != Algorithm.rsa8192);

        X509Bundle bundle = BASE.copy()
                .algorithm(algorithm)
                .setIsCertificateAuthority(true)
                .buildSelfSigned();
        X509Certificate cert = bundle.getCertificate();
        assertTrue(bundle.isCertificateAuthority());
        assertTrue(bundle.isSelfSigned());
        assertThat(cert.getSubjectX500Principal()).isEqualTo(new X500Principal(SUBJECT));
    }

    @Test
    void createCertIssuedBySameAlgorithm() throws Exception {
        CertificateBuilder builder = BASE.copy().ecp256();
        X509Bundle root = builder.copy()
                .setKeyUsage(true, KeyUsage.digitalSignature, KeyUsage.keyCertSign)
                .setIsCertificateAuthority(true)
                .buildSelfSigned();
        X509Bundle leaf = builder.copy()
                .subject("CN=leaf.netty.io, O=Netty")
                .buildIssuedBy(root);
        assertThat(leaf.getCertificate().getSubjectX500Principal()).isEqualTo(
                new X500Principal("CN=leaf.netty.io, O=Netty"));
        assertThat(leaf.getCertificate().getIssuerX500Principal()).isEqualTo(
                new X500Principal(SUBJECT));

        Signature signature = Signature.getInstance(leaf.getCertificate().getSigAlgName());
        signature.initVerify(root.getCertificate());
        signature.update(leaf.getCertificate().getTBSCertificate());
        assertTrue(signature.verify(leaf.getCertificate().getSignature()));
    }

    @Test
    void createCertIssuedByDifferentAlgorithmEcp384vsEcp256() throws Exception {
        X509Bundle root = BASE.copy()
                .algorithm(Algorithm.ecp384)
                .setKeyUsage(true, KeyUsage.digitalSignature, KeyUsage.keyCertSign)
                .setIsCertificateAuthority(true)
                .buildSelfSigned();
        X509Bundle leaf = BASE.copy()
                .ecp256()
                .subject("CN=leaf.netty.io, O=Netty")
                .buildIssuedBy(root);
        assertThat(leaf.getCertificate().getSubjectX500Principal()).isEqualTo(
                new X500Principal("CN=leaf.netty.io, O=Netty"));
        assertThat(leaf.getCertificate().getIssuerX500Principal()).isEqualTo(
                new X500Principal(SUBJECT));

        Signature signature = Signature.getInstance(leaf.getCertificate().getSigAlgName());
        signature.initVerify(root.getCertificate());
        signature.update(leaf.getCertificate().getTBSCertificate());
        assertTrue(signature.verify(leaf.getCertificate().getSignature()));
    }

    @Test
    void createCertIssuedByDifferentAlgorithmEcp256vsRsa2048() throws Exception {
        X509Bundle root = BASE.copy()
                .ecp256()
                .setKeyUsage(true, KeyUsage.digitalSignature, KeyUsage.keyCertSign)
                .setIsCertificateAuthority(true)
                .buildSelfSigned();
        X509Bundle leaf = BASE.copy()
                .rsa2048()
                .subject("CN=leaf.netty.io, O=Netty")
                .buildIssuedBy(root);
        assertThat(leaf.getCertificate().getSubjectX500Principal()).isEqualTo(
                new X500Principal("CN=leaf.netty.io, O=Netty"));
        assertThat(leaf.getCertificate().getIssuerX500Principal()).isEqualTo(
                new X500Principal(SUBJECT));

        Signature signature = Signature.getInstance(leaf.getCertificate().getSigAlgName());
        signature.initVerify(root.getCertificate());
        signature.update(leaf.getCertificate().getTBSCertificate());
        assertTrue(signature.verify(leaf.getCertificate().getSignature()));
    }

    @EnabledForJreRange(min = JRE.JAVA_15)
    @Test
    void createCertIssuedByDifferentAlgorithmEd25519vEcp256() throws Exception {
        X509Bundle root = BASE.copy()
                .algorithm(Algorithm.ed25519)
                .setKeyUsage(true, KeyUsage.digitalSignature, KeyUsage.keyCertSign)
                .setIsCertificateAuthority(true)
                .buildSelfSigned();
        X509Bundle leaf = BASE.copy()
                .ecp256()
                .subject("CN=leaf.netty.io, O=Netty")
                .buildIssuedBy(root);
        assertThat(leaf.getCertificate().getSubjectX500Principal()).isEqualTo(
                new X500Principal("CN=leaf.netty.io, O=Netty"));
        assertThat(leaf.getCertificate().getIssuerX500Principal()).isEqualTo(
                new X500Principal(SUBJECT));

        Signature signature = Signature.getInstance(leaf.getCertificate().getSigAlgName());
        signature.initVerify(root.getCertificate());
        signature.update(leaf.getCertificate().getTBSCertificate());
        assertTrue(signature.verify(leaf.getCertificate().getSignature()));
    }

    @EnabledForJreRange(min = JRE.JAVA_15)
    @Test
    void createCertIssuedByDifferentAlgorithmEd448vEcp256() throws Exception {
        X509Bundle root = BASE.copy()
                .algorithm(Algorithm.ed448)
                .setKeyUsage(true, KeyUsage.digitalSignature, KeyUsage.keyCertSign)
                .setIsCertificateAuthority(true)
                .buildSelfSigned();
        X509Bundle leaf = BASE.copy()
                .ecp256()
                .subject("CN=leaf.netty.io, O=Netty")
                .buildIssuedBy(root);
        assertThat(leaf.getCertificate().getSubjectX500Principal()).isEqualTo(
                new X500Principal("CN=leaf.netty.io, O=Netty"));
        assertThat(leaf.getCertificate().getIssuerX500Principal()).isEqualTo(
                new X500Principal(SUBJECT));

        Signature signature = Signature.getInstance(leaf.getCertificate().getSigAlgName());
        signature.initVerify(root.getCertificate());
        signature.update(leaf.getCertificate().getTBSCertificate());
        assertTrue(signature.verify(leaf.getCertificate().getSignature()));
    }

    @Test
    void createCertificateWithSans() throws Exception {
        X509Bundle root = BASE.copy()
                .setIsCertificateAuthority(true)
                .setKeyUsage(true, KeyUsage.digitalSignature, KeyUsage.keyCertSign)
                .buildSelfSigned();
        X509Bundle leaf = BASE.copy()
                .subject("CN=leaf.netty.io")
                .addSanDirectoryName("CN=san.leaf.netty.io")
                .addSanDnsName("san-1.leaf.netty.io")
                .addSanDnsName("san-2.leaf.netty.io")
                .addSanIpAddress("192.0.2.1") // RFC 5737 example IP
                .addSanRfc822Name("san@netty.io")
                .addSanRegisteredId("1.2.840.113635.100.1.2.42")
                .addSanUriName("spiffe://netty.io/example/san")
                .addSanOtherName("1.2.840.113635.100.1.2.42", new byte[] {0x01, 0x01, 0x00})
                .buildIssuedBy(root);
        X509Certificate cert = leaf.getCertificate();
        assertEquals(new X500Principal("CN=leaf.netty.io"), cert.getSubjectX500Principal());
        List<List<?>> sans = List.copyOf(cert.getSubjectAlternativeNames());
        assertThat(sans).hasSize(8);
        assertThat(sans.get(0)).isEqualTo(List.of(4, "CN=san.leaf.netty.io"));
        assertThat(sans.get(1)).isEqualTo(List.of(2, "san-1.leaf.netty.io"));
        assertThat(sans.get(2)).isEqualTo(List.of(2, "san-2.leaf.netty.io"));
        assertThat(sans.get(3)).isEqualTo(List.of(7, "192.0.2.1"));
        assertThat(sans.get(4)).isEqualTo(List.of(1, "san@netty.io"));
        assertThat(sans.get(5)).isEqualTo(List.of(8, "1.2.840.113635.100.1.2.42"));
        assertThat(sans.get(6)).isEqualTo(List.of(6, "spiffe://netty.io/example/san"));
        assertThat(sans.get(7).get(2)).isEqualTo("1.2.840.113635.100.1.2.42");
        assertThat(sans.get(7).get(3)).isEqualTo(new byte[] {0x01, 0x01, 0x00});
    }

    @Test
    void createCertificteWithExtendedKeyUsage() throws Exception {
        CertificateBuilder builder = BASE.copy()
                .setIsCertificateAuthority(true)
                .setKeyUsage(true, KeyUsage.digitalSignature, KeyUsage.keyCertSign)
                .addExtendedKeyUsage("1.2.840.113635.100.1.2.42");
        for (CertificateBuilder.ExtendedKeyUsage extendedKeyUsage : CertificateBuilder.ExtendedKeyUsage.values()) {
            builder.addExtendedKeyUsage(extendedKeyUsage);
        }
        X509Bundle bundle = builder.buildSelfSigned();
        TreeSet<String> expectedExtendedKeyUsage = new TreeSet<>();
        expectedExtendedKeyUsage.add("1.2.840.113635.100.1.2.42");
        for (CertificateBuilder.ExtendedKeyUsage extendedKeyUsage : CertificateBuilder.ExtendedKeyUsage.values()) {
            expectedExtendedKeyUsage.add(extendedKeyUsage.getOid());
        }
        List<String> actualExtendedKeyUsage = bundle.getCertificate().getExtendedKeyUsage();
        assertThat(actualExtendedKeyUsage).containsExactlyInAnyOrderElementsOf(expectedExtendedKeyUsage);
    }

    @Test
    void createCertificateWithOtherFields() throws Exception {
        X509Bundle root = BASE.copy()
                .setIsCertificateAuthority(true)
                .setKeyUsage(true, KeyUsage.digitalSignature, KeyUsage.keyCertSign)
                .setPathLengthConstraint(OptionalInt.of(42))
                .serial(BigInteger.TEN)
                .buildSelfSigned();
        X509Certificate cert = root.getCertificate();
        assertThat(cert.getBasicConstraints()).isEqualTo(42);
        assertThat(cert.getSerialNumber()).isEqualTo(10);
        assertEquals(3, cert.getVersion());
        assertFalse(cert.hasUnsupportedCriticalExtension());
        assertThat(cert.getKeyUsage()).isEqualTo(
                new boolean[] {true, false, false, false, false, true, false, false, false});
        cert.checkValidity();
        assertThat(NOW.minus(1, DAYS).toEpochMilli()).isCloseTo(cert.getNotBefore().getTime(), Offset.offset(200L));
        assertThat(NOW.plus(1, DAYS).toEpochMilli()).isCloseTo(cert.getNotAfter().getTime(), Offset.offset(200L));
    }

    @Test
    void validCertificatesWithCrlMustPassValidation() throws Exception {
        X509Bundle root = BASE.copy()
                .setIsCertificateAuthority(true)
                .setKeyUsage(true, KeyUsage.digitalSignature, KeyUsage.keyCertSign, KeyUsage.cRLSign)
                .buildSelfSigned();
        RevocationServer server = new RevocationServer();
        server.start();
        try {
            server.registerPath("/crl.crl", root);
            X509Bundle cert = BASE.copy()
                    .subject("CN=leaf.netty.io")
                    .addCrlDistributionPoint(server.getCrlUri(root))
                    .addExtendedKeyUsageClientAuth()
                    .buildIssuedBy(root);

            X509TrustManager tm = getX509TrustManager(root);

            // Assert that this does not throw:
            tm.checkClientTrusted(cert.getCertificatePath(), "EC");
        } finally {
            server.stop(1);
        }
    }

    @Test
    void revokedCertificatesWithCrlMustFailValidation() throws Exception {
        X509Bundle root = BASE.copy()
                .setIsCertificateAuthority(true)
                .setKeyUsage(true, KeyUsage.digitalSignature, KeyUsage.keyCertSign, KeyUsage.cRLSign)
                .buildSelfSigned();
        RevocationServer server = new RevocationServer();
        server.start();
        try {
            server.registerPath("/crl.crl", root);
            X509Bundle cert = BASE.copy()
                    .subject("CN=leaf.netty.io")
                    .addCrlDistributionPoint(server.getCrlUri(root))
                    .addExtendedKeyUsageClientAuth()
                    .buildIssuedBy(root);
            server.revoke(cert, NOW);

            X509TrustManager tm = getX509TrustManager(root);

            // Assert that this does not throw:
            CertificateException ce = assertThrows(CertificateException.class,
                    () -> tm.checkClientTrusted(cert.getCertificatePath(), "EC"));
            assertThat(ce).hasMessageContaining("Certificate has been revoked");
        } finally {
            server.stop(1);
        }
    }

    private static X509TrustManager getX509TrustManager(X509Bundle root) throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        PKIXBuilderParameters params = new PKIXBuilderParameters(Collections.singleton(
                new TrustAnchor(root.getCertificate(), null)), null);

        // Explicitly add the revocation checker. We cannot use params.setRevocationEnabled(true) because
        // it will not do any online revocation checking, i.e. it will not do any network access.
        CertPathBuilder cpb = CertPathBuilder.getInstance("PKIX");
        PKIXRevocationChecker rc = (PKIXRevocationChecker) cpb.getRevocationChecker();
        params.addCertPathChecker(rc);

        tmf.init(new CertPathTrustManagerParameters(params));
        return (X509TrustManager) tmf.getTrustManagers()[0];
    }
}
