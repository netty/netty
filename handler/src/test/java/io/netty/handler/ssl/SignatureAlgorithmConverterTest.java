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
    public void testInvalid() {
        assertNull(SignatureAlgorithmConverter.toJavaName("ThisIsSomethingInvalid"));
    }
}
