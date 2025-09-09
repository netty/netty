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

package io.netty.handler.ssl;

import java.nio.file.Files;
import java.security.PrivateKey;

import io.netty.buffer.UnpooledByteBufAllocator;

import io.netty.handler.ssl.util.CachedSelfSignedCertificate;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class PemEncodedTest {

    @Test
    public void testPemEncodedOpenSsl() throws Exception {
        testPemEncoded(SslProvider.OPENSSL_REFCNT);
    }

    @Test
    public void testPemEncodedOpenSslRef() throws Exception {
        testPemEncoded(SslProvider.OPENSSL_REFCNT);
    }

    private static void testPemEncoded(SslProvider provider) throws Exception {
        OpenSsl.ensureAvailability();
        assumeFalse(OpenSsl.useKeyManagerFactory());
        SelfSignedCertificate ssc = CachedSelfSignedCertificate.getCachedCertificate();
        PemPrivateKey pemKey = PemPrivateKey.valueOf(Files.readAllBytes(ssc.privateKey().toPath()));
        PemX509Certificate pemCert = PemX509Certificate.valueOf(Files.readAllBytes(ssc.certificate().toPath()));

        SslContext context = SslContextBuilder.forServer(pemKey, pemCert)
                .sslProvider(provider)
                .build();
        assertEquals(1, pemKey.refCnt());
        assertEquals(1, pemCert.refCnt());
        try {
            assertInstanceOf(ReferenceCountedOpenSslContext.class, context);
        } finally {
            ReferenceCountUtil.release(context);
            assertRelease(pemKey);
            assertRelease(pemCert);
        }
    }

    @Test
    public void testEncodedReturnsNull() throws Exception {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                PemPrivateKey.toPEM(UnpooledByteBufAllocator.DEFAULT, true, new PrivateKey() {
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
        assertTrue(encoded.release());
    }

}
