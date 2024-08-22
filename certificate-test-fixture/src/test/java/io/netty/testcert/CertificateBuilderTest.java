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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import javax.security.auth.x500.X500Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CertificateBuilderTest {
    private static final Instant NOW = Instant.now();

    @ParameterizedTest
    @EnumSource
    void createCertOfEveryKeyType(Algorithm algorithm) throws Exception {
        // Ed25519 and Ed448 are only supported on Java 15 and newer.
        assumeTrue(algorithm != Algorithm.ed25519 && algorithm != Algorithm.ed448 ||
                PlatformDependent.javaVersion() >= 15);
        // Assume that RSA 4096 and RSA 8192 work if the other RSA bit-widths work.
        // These big keys just take too long to test with.
        assumeTrue(algorithm != Algorithm.rsa4096 && algorithm != Algorithm.rsa8192);

        CertificateBuilder builder = new CertificateBuilder();

        String fqdn = "CN=netty.io, O=Netty";
        X509Bundle bundle = builder.subject(fqdn)
                .algorithm(algorithm)
                .notBefore(NOW.minus(1, ChronoUnit.DAYS))
                .notAfter(NOW.plus(1, ChronoUnit.DAYS))
                .setIsCertificateAuthority(true)
                .buildSelfSigned();
        X509Certificate cert = bundle.getCertificate();
        assertTrue(bundle.isCertificateAuthority());
        assertTrue(bundle.isSelfSigned());
        assertThat(cert.getSubjectX500Principal()).isEqualTo(new X500Principal(fqdn));
    }

    @Test
    void createCertIssuedBySameAlgorithm() throws Exception {
        CertificateBuilder builder = new CertificateBuilder()
                .ecp256()
                .notBefore(NOW.minus(1, ChronoUnit.DAYS))
                .notAfter(NOW.plus(1, ChronoUnit.DAYS));
        X509Bundle root = builder.copy()
                .subject("CN=netty.io, O=Netty")
                .setKeyUsage(true, KeyUsage.digitalSignature, KeyUsage.keyCertSign)
                .setIsCertificateAuthority(true)
                .buildSelfSigned();
        X509Bundle leaf = builder.copy()
                .subject("CN=leaf.netty.io, O=Netty")
                .buildIssuedBy(root);
        assertThat(leaf.getCertificate().getSubjectX500Principal()).isEqualTo(
                new X500Principal("CN=leaf.netty.io, O=Netty"));
        assertThat(leaf.getCertificate().getIssuerX500Principal()).isEqualTo(
                new X500Principal("CN=netty.io, O=Netty"));

        Signature signature = Signature.getInstance(leaf.getCertificate().getSigAlgName());
        signature.initVerify(root.getCertificate());
        signature.update(leaf.getCertificate().getTBSCertificate());
        assertTrue(signature.verify(leaf.getCertificate().getSignature()));
    }

    @Test
    void createCertIssuedByDifferentAlgorithmEcp384vsEcp256() throws Exception {
        CertificateBuilder builder = new CertificateBuilder()
                .notBefore(NOW.minus(1, ChronoUnit.DAYS))
                .notAfter(NOW.plus(1, ChronoUnit.DAYS));
        X509Bundle root = builder.copy()
                .algorithm(Algorithm.ecp384)
                .subject("CN=netty.io, O=Netty")
                .setKeyUsage(true, KeyUsage.digitalSignature, KeyUsage.keyCertSign)
                .setIsCertificateAuthority(true)
                .buildSelfSigned();
        X509Bundle leaf = builder.copy()
                .ecp256()
                .subject("CN=leaf.netty.io, O=Netty")
                .buildIssuedBy(root);
        assertThat(leaf.getCertificate().getSubjectX500Principal()).isEqualTo(
                new X500Principal("CN=leaf.netty.io, O=Netty"));
        assertThat(leaf.getCertificate().getIssuerX500Principal()).isEqualTo(
                new X500Principal("CN=netty.io, O=Netty"));

        Signature signature = Signature.getInstance(leaf.getCertificate().getSigAlgName());
        signature.initVerify(root.getCertificate());
        signature.update(leaf.getCertificate().getTBSCertificate());
        assertTrue(signature.verify(leaf.getCertificate().getSignature()));
    }

    @Test
    void createCertIssuedByDifferentAlgorithmEcp256vsRsa2048() throws Exception {
        CertificateBuilder builder = new CertificateBuilder()
                .notBefore(NOW.minus(1, ChronoUnit.DAYS))
                .notAfter(NOW.plus(1, ChronoUnit.DAYS));
        X509Bundle root = builder.copy()
                .ecp256()
                .subject("CN=netty.io, O=Netty")
                .setKeyUsage(true, KeyUsage.digitalSignature, KeyUsage.keyCertSign)
                .setIsCertificateAuthority(true)
                .buildSelfSigned();
        X509Bundle leaf = builder.copy()
                .rsa2048()
                .subject("CN=leaf.netty.io, O=Netty")
                .buildIssuedBy(root);
        assertThat(leaf.getCertificate().getSubjectX500Principal()).isEqualTo(
                new X500Principal("CN=leaf.netty.io, O=Netty"));
        assertThat(leaf.getCertificate().getIssuerX500Principal()).isEqualTo(
                new X500Principal("CN=netty.io, O=Netty"));

        Signature signature = Signature.getInstance(leaf.getCertificate().getSigAlgName());
        signature.initVerify(root.getCertificate());
        signature.update(leaf.getCertificate().getTBSCertificate());
        assertTrue(signature.verify(leaf.getCertificate().getSignature()));
    }

    @EnabledForJreRange(min = JRE.JAVA_15)
    @Test
    void createCertIssuedByDifferentAlgorithmEd25519vEcp256() throws Exception {
        CertificateBuilder builder = new CertificateBuilder()
                .notBefore(NOW.minus(1, ChronoUnit.DAYS))
                .notAfter(NOW.plus(1, ChronoUnit.DAYS));
        X509Bundle root = builder.copy()
                .algorithm(Algorithm.ed25519)
                .subject("CN=netty.io, O=Netty")
                .setKeyUsage(true, KeyUsage.digitalSignature, KeyUsage.keyCertSign)
                .setIsCertificateAuthority(true)
                .buildSelfSigned();
        X509Bundle leaf = builder.copy()
                .ecp256()
                .subject("CN=leaf.netty.io, O=Netty")
                .buildIssuedBy(root);
        assertThat(leaf.getCertificate().getSubjectX500Principal()).isEqualTo(
                new X500Principal("CN=leaf.netty.io, O=Netty"));
        assertThat(leaf.getCertificate().getIssuerX500Principal()).isEqualTo(
                new X500Principal("CN=netty.io, O=Netty"));

        Signature signature = Signature.getInstance(leaf.getCertificate().getSigAlgName());
        signature.initVerify(root.getCertificate());
        signature.update(leaf.getCertificate().getTBSCertificate());
        assertTrue(signature.verify(leaf.getCertificate().getSignature()));
    }

    @EnabledForJreRange(min = JRE.JAVA_15)
    @Test
    void createCertIssuedByDifferentAlgorithmEd448vEcp256() throws Exception {
        CertificateBuilder builder = new CertificateBuilder()
                .notBefore(NOW.minus(1, ChronoUnit.DAYS))
                .notAfter(NOW.plus(1, ChronoUnit.DAYS));
        X509Bundle root = builder.copy()
                .algorithm(Algorithm.ed448)
                .subject("CN=netty.io, O=Netty")
                .setKeyUsage(true, KeyUsage.digitalSignature, KeyUsage.keyCertSign)
                .setIsCertificateAuthority(true)
                .buildSelfSigned();
        X509Bundle leaf = builder.copy()
                .ecp256()
                .subject("CN=leaf.netty.io, O=Netty")
                .buildIssuedBy(root);
        assertThat(leaf.getCertificate().getSubjectX500Principal()).isEqualTo(
                new X500Principal("CN=leaf.netty.io, O=Netty"));
        assertThat(leaf.getCertificate().getIssuerX500Principal()).isEqualTo(
                new X500Principal("CN=netty.io, O=Netty"));

        Signature signature = Signature.getInstance(leaf.getCertificate().getSigAlgName());
        signature.initVerify(root.getCertificate());
        signature.update(leaf.getCertificate().getTBSCertificate());
        assertTrue(signature.verify(leaf.getCertificate().getSignature()));
    }
}
