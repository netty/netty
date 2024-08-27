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
package io.netty.handler.ssl.util;

import io.netty.testcert.CertificateBuilder;
import io.netty.testcert.X509Bundle;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;

import static io.netty.handler.ssl.util.SelfSignedCertificate.newSelfSignedCertificate;

final class CertificateBuilderCertGenerator {
    private CertificateBuilderCertGenerator() {
    }

    static boolean isAvailable() {
        try {
            new CertificateBuilder();
            return true;
        } catch (Throwable ignore) {
            return false;
        }
    }

    static void generate(SelfSignedCertificate.Builder config) throws Exception {
        String fqdn = config.fqdn;
        Date notBefore = config.notBefore;
        Date notAfter = config.notAfter;
        String algorithm = config.algorithm;
        SecureRandom random = config.random;
        int bits = config.bits;
        CertificateBuilder builder = new CertificateBuilder();
        builder.setIsCertificateAuthority(true);
        if (fqdn.contains("=")) {
            builder.subject(fqdn);
        } else {
            builder.subject("CN=" + fqdn);
        }
        builder.notBefore(Instant.ofEpochMilli(notBefore.getTime()));
        builder.notAfter(Instant.ofEpochMilli(notAfter.getTime()));
        if (random != null) {
            builder.secureRandom(random);
        }
        if ("RSA".equals(algorithm)) {
            CertificateBuilder.Algorithm alg;
            switch (bits) {
                case 2048: alg = CertificateBuilder.Algorithm.rsa2048; break;
                case 3072: alg = CertificateBuilder.Algorithm.rsa3072; break;
                case 4096: alg = CertificateBuilder.Algorithm.rsa4096; break;
                case 8192: alg = CertificateBuilder.Algorithm.rsa8192; break;
                default:
                    throw new IllegalArgumentException("Unsupported RSA bit-width: " + bits);
            }
            builder.algorithm(alg);
        } else if ("EC".equals(algorithm)) {
            if (bits == 256) {
                builder.algorithm(CertificateBuilder.Algorithm.ecp256);
            } else if (bits == 384) {
                builder.algorithm(CertificateBuilder.Algorithm.ecp384);
            } else {
                throw new IllegalArgumentException("Unsupported EC-P bit-width: " + bits);
            }
        }
        X509Bundle bundle = builder.buildSelfSigned();
        config.paths = newSelfSignedCertificate(fqdn, bundle.getKeyPair().getPrivate(), bundle.getCertificate());
        config.keypair = bundle.getKeyPair();
        config.privateKey = bundle.getKeyPair().getPrivate();
    }
}
