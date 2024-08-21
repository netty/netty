package io.netty.testcert;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import javax.security.auth.x500.X500Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class CertificateBuilderTest {
    private static final Instant NOW = Instant.now();

    @ParameterizedTest
    @EnumSource
    void createCertOfEveryKeyType(CertificateBuilder.Algorithm algorithm) throws Exception {
        CertificateBuilder builder = new CertificateBuilder();

        String fqdn = "CN=netty.io, O=Netty";
        X509Bundle bundle = builder.subject(fqdn)
                .algorithm(algorithm)
                .notBefore(NOW.minus(1, ChronoUnit.DAYS))
                .notAfter(NOW.plus(1, ChronoUnit.DAYS))
                .setIsCertificateAuthority(true)
                .buildSelfSigned();
        X509Certificate cert = bundle.getCertificate();
        assertThat(cert.getSubjectX500Principal()).isEqualTo(new X500Principal(fqdn));
    }
}
