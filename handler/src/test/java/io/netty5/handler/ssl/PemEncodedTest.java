/*
 * Copyright 2016 The Netty Project
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
import io.netty5.util.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;

import static io.netty5.util.internal.SilentDispose.autoClosing;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class PemEncodedTest {

    @Test
    public void testPemEncodedOpenSsl() throws Exception {
        testPemEncoded(SslProvider.OPENSSL);
    }

    @Test
    public void testPemEncodedOpenSslRef() throws Exception {
        testPemEncoded(SslProvider.OPENSSL_REFCNT);
    }

    private static void testPemEncoded(SslProvider provider) throws Exception {
        OpenSsl.ensureAvailability();
        assumeFalse(OpenSsl.supportsKeyManagerFactory());

        X509Bundle cert = new CertificateBuilder()
                .subject("cn=localhost")
                .setIsCertificateAuthority(true)
                .buildSelfSigned();
        PemPrivateKey pemKey = PemPrivateKey.valueOf(
                cert.getPrivateKeyPEM().getBytes(StandardCharsets.ISO_8859_1));
        PemX509Certificate pemCert = PemX509Certificate.valueOf(
                cert.getCertificatePEM().getBytes(StandardCharsets.ISO_8859_1));

        assertTrue(pemKey.content().readOnly());
        assertTrue(pemCert.content().readOnly());

        SslContext context = SslContextBuilder.forServer(pemKey, pemCert)
                .sslProvider(provider)
                .build();
        assertFalse(pemKey.isDestroyed());
        assertTrue(pemCert.isAccessible());
        try (AutoCloseable ignore = autoClosing(context)) {
            assertInstanceOf(ReferenceCountedOpenSslContext.class, context);
        }
        assertRelease(pemKey);
        assertRelease(pemCert);
    }

    @Test
    public void testEncodedReturnsNull() throws Exception {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                PemPrivateKey.toPEM(new PrivateKey() {
                    @Override
                    public String getAlgorithm() {
                        return null;
                    }

                    @Override
                    public String getFormat() {
                        return null;
                    }

                    @Override
                    public byte[] getEncoded() {
                        return null;
                    }
                });
            }
        });
    }

    private static void assertRelease(PemEncoded encoded) {
        encoded.close();
        assertFalse(((Resource<?>) encoded).isAccessible());
    }
}
