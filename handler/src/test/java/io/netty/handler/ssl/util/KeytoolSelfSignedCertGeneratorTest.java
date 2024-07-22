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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

class KeytoolSelfSignedCertGeneratorTest {
    @BeforeAll
    static void checkAvailability() {
        Assumptions.assumeTrue(KeytoolSelfSignedCertGenerator.isAvailable());
    }

    @Test
    public void test() throws Exception {
        SelfSignedCertificate.Builder builder = SelfSignedCertificate.builder()
                .fqdn("example.com")
                .algorithm("RSA")
                .bits(2048);
        Assertions.assertTrue(builder.generateKeytool());
        Assertions.assertEquals("RSA", builder.privateKey.getAlgorithm());

        X509Certificate cert;
        try (InputStream certStream = Files.newInputStream(Paths.get(builder.paths[0]))) {
            cert = (X509Certificate) CertificateFactory.getInstance("X509").generateCertificate(certStream);
        }
        cert.checkValidity();
        Assertions.assertEquals("CN=example.com", cert.getSubjectX500Principal().getName());
    }
}
