/*
 * Copyright 2018 The Netty Project
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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SignatureAlgorithmConverterTest {

    @Test
    public void testWithEncryption() {
        assertEquals("SHA512withRSA", SignatureAlgorithmConverter.toJavaName("sha512WithRSAEncryption"));
    }

    @Test
    public void testWithDash() {
        assertEquals("SHA256withECDSA", SignatureAlgorithmConverter.toJavaName("ecdsa-with-SHA256"));
    }

    @Test
    public void testWithUnderscore() {
        assertEquals("SHA256withDSA", SignatureAlgorithmConverter.toJavaName("dsa_with_SHA256"));
    }

    @Test
    public void testBoringSSLOneUnderscore() {
        assertEquals("SHA256withECDSA", SignatureAlgorithmConverter.toJavaName("ecdsa_sha256"));
    }

    @Test
    public void testBoringSSLPkcs1() {
        assertEquals("SHA256withRSA", SignatureAlgorithmConverter.toJavaName("rsa_pkcs1_sha256"));
    }

    @Test
    public void testBoringSSLPSS() {
        assertEquals("SHA256withRSA", SignatureAlgorithmConverter.toJavaName("rsa_pss_rsae_sha256"));
    }

    @Test
    public void testEd25519() {
        assertEquals("Ed25519", SignatureAlgorithmConverter.toJavaName("ed25519"));
    }

    @Test
    public void testEd448() {
        assertEquals("Ed448", SignatureAlgorithmConverter.toJavaName("ed448"));
    }

    @Test
    public void testOpenSslEd25519() {
        assertEquals("Ed25519", SignatureAlgorithmConverter.toJavaName("ED25519"));
    }

    @Test
    public void testOpenSslEd448() {
        assertEquals("Ed448", SignatureAlgorithmConverter.toJavaName("ED448"));
    }

    @Test
    public void testNamesWithoutJavaName() {
        assertNull(SignatureAlgorithmConverter.toJavaName("rsassaPss"));
        assertNull(SignatureAlgorithmConverter.toJavaName("dsaEncryption"));
        assertNull(SignatureAlgorithmConverter.toJavaName("undefined"));
    }

    @Test
    public void testKnownNamesMatchPattern() {
        String[] knownNames = {
                // BoringSSL / AWS-LC
                "ecdsa_sha1", "ecdsa_sha256", "ecdsa_sha384", "ecdsa_sha512",
                "rsa_pkcs1_sha1", "rsa_pkcs1_sha256", "rsa_pkcs1_sha384", "rsa_pkcs1_sha512",
                "rsa_pss_rsae_sha256", "rsa_pss_rsae_sha384", "rsa_pss_rsae_sha512",
                "ed25519",
                // OpenSSL
                "ecdsa-with-SHA1", "ecdsa-with-SHA224", "ecdsa-with-SHA256", "ecdsa-with-SHA384",
                "ecdsa-with-SHA512",
                "sha1WithRSAEncryption", "sha224WithRSAEncryption", "sha256WithRSAEncryption",
                "sha384WithRSAEncryption", "sha512WithRSAEncryption",
                "dsa_with_SHA256", "ED25519", "ED448", "ed448",
                "rsassaPss", "dsaEncryption", "undefined"
        };
        for (String name : knownNames) {
            assertEquals(SignatureAlgorithmConverter.toJavaNameWithPattern(name),
                    SignatureAlgorithmConverter.toJavaName(name), name);
        }
    }

    @Test
    public void testUnlistedNameUsesPattern() {
        assertEquals("SHA224withDSA", SignatureAlgorithmConverter.toJavaName("dsa_with_SHA224"));
        assertEquals("SHA3-256withECDSA", SignatureAlgorithmConverter.toJavaName("ecdsa_with_SHA3-256"));
    }

    @Test
    public void testInvalid() {
        assertNull(SignatureAlgorithmConverter.toJavaName("ThisIsSomethingInvalid"));
    }
}
