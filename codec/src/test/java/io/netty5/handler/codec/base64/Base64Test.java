/*
 * Copyright 2015 The Netty Project
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
package io.netty5.handler.codec.base64;

import io.netty5.buffer.BufferUtil;
import io.netty5.buffer.api.Buffer;
import io.netty5.util.CharsetUtil;
import io.netty5.util.internal.StringUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.concurrent.ThreadLocalRandom;

import static io.netty5.buffer.api.DefaultBufferAllocators.onHeapAllocator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class Base64Test {

    @Test
    public void testNotAddNewLineWhenEndOnLimit() {
        Buffer src = copiedBuffer("abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcde",
                                  CharsetUtil.US_ASCII);
        Buffer expectedEncoded =
                copiedBuffer("YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXphYmNkZWZnaGlqa2xtbm9wcXJzdHV2d3h5emFiY2Rl",
                        CharsetUtil.US_ASCII);
        testEncode(src, expectedEncoded);
    }

    @Test
    public void testAddNewLine() {
        Buffer src = copiedBuffer("abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz12345678",
                CharsetUtil.US_ASCII);
        Buffer expectedEncoded =
                copiedBuffer("YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXphYmNkZWZnaGlqa2xtbm9wcXJzdHV2d3h5ejEyMzQ1\nNjc4",
                        CharsetUtil.US_ASCII);
        testEncode(src, expectedEncoded);
    }

    @Test
    public void testEncodeEmpty() {
        Buffer src = onHeapAllocator().allocate(0);
        Buffer expectedEncoded = onHeapAllocator().allocate(0);
        testEncode(src, expectedEncoded);
    }

    @Test
    public void testPaddingNewline() throws Exception {
        String cert = "-----BEGIN CERTIFICATE-----\n" +
                "MIICqjCCAjGgAwIBAgICI1YwCQYHKoZIzj0EATAmMSQwIgYDVQQDDBtUcnVzdGVk\n" +
                "IFRoaW4gQ2xpZW50IFJvb3QgQ0EwIhcRMTYwMTI0MTU0OTQ1LTA2MDAXDTE2MDQy\n" +
                "NTIyNDk0NVowYzEwMC4GA1UEAwwnREMgMGRlYzI0MGYtOTI2OS00MDY5LWE2MTYt\n" +
                "YjJmNTI0ZjA2ZGE0MREwDwYDVQQLDAhEQyBJUFNFQzEcMBoGA1UECgwTVHJ1c3Rl\n" +
                "ZCBUaGluIENsaWVudDB2MBAGByqGSM49AgEGBSuBBAAiA2IABOB7pZYC24sF5gJm\n" +
                "OHXhasxmrNYebdtSAiQRgz0M0pIsogsFeTU/W0HTlTOqwDDckphHESAKHVxa6EBL\n" +
                "d+/8HYZ1AaCmXtG73XpaOyaRr3TipJl2IaJzwuehgDHs0L+qcqOB8TCB7jAwBgYr\n" +
                "BgEBEAQEJgwkMGRlYzI0MGYtOTI2OS00MDY5LWE2MTYtYjJmNTI0ZjA2ZGE0MCMG\n" +
                "CisGAQQBjCHbZwEEFQwTNDkwNzUyMjc1NjM3MTE3Mjg5NjAUBgorBgEEAYwh22cC\n" +
                "BAYMBDIwNTkwCwYDVR0PBAQDAgXgMAkGA1UdEwQCMAAwHQYDVR0OBBYEFGWljaKj\n" +
                "wiGqW61PgLL/zLxj4iirMB8GA1UdIwQYMBaAFA2FRBtG/dGnl0iXP2uKFwJHmEQI\n" +
                "MCcGA1UdJQQgMB4GCCsGAQUFBwMCBggrBgEFBQcDAQYIKwYBBQUHAwkwCQYHKoZI\n" +
                "zj0EAQNoADBlAjAQFP8rMLUxl36u8610LsSCiRG8pP3gjuLaaJMm3tjbVue/TI4C\n" +
                "z3iL8i96YWK0VxcCMQC7pf6Wk3RhUU2Sg6S9e6CiirFLDyzLkaWxuCnXcOwTvuXT\n" +
                "HUQSeUCp2Q6ygS5qKyc=\n" +
                "-----END CERTIFICATE-----";

        String expected = "MIICqjCCAjGgAwIBAgICI1YwCQYHKoZIzj0EATAmMSQwIgYDVQQDDBtUcnVzdGVkIFRoaW4gQ2xp\n" +
                "ZW50IFJvb3QgQ0EwIhcRMTYwMTI0MTU0OTQ1LTA2MDAXDTE2MDQyNTIyNDk0NVowYzEwMC4GA1UE\n" +
                "AwwnREMgMGRlYzI0MGYtOTI2OS00MDY5LWE2MTYtYjJmNTI0ZjA2ZGE0MREwDwYDVQQLDAhEQyBJ\n" +
                "UFNFQzEcMBoGA1UECgwTVHJ1c3RlZCBUaGluIENsaWVudDB2MBAGByqGSM49AgEGBSuBBAAiA2IA\n" +
                "BOB7pZYC24sF5gJmOHXhasxmrNYebdtSAiQRgz0M0pIsogsFeTU/W0HTlTOqwDDckphHESAKHVxa\n" +
                "6EBLd+/8HYZ1AaCmXtG73XpaOyaRr3TipJl2IaJzwuehgDHs0L+qcqOB8TCB7jAwBgYrBgEBEAQE\n" +
                "JgwkMGRlYzI0MGYtOTI2OS00MDY5LWE2MTYtYjJmNTI0ZjA2ZGE0MCMGCisGAQQBjCHbZwEEFQwT\n" +
                "NDkwNzUyMjc1NjM3MTE3Mjg5NjAUBgorBgEEAYwh22cCBAYMBDIwNTkwCwYDVR0PBAQDAgXgMAkG\n" +
                "A1UdEwQCMAAwHQYDVR0OBBYEFGWljaKjwiGqW61PgLL/zLxj4iirMB8GA1UdIwQYMBaAFA2FRBtG\n" +
                "/dGnl0iXP2uKFwJHmEQIMCcGA1UdJQQgMB4GCCsGAQUFBwMCBggrBgEFBQcDAQYIKwYBBQUHAwkw\n" +
                "CQYHKoZIzj0EAQNoADBlAjAQFP8rMLUxl36u8610LsSCiRG8pP3gjuLaaJMm3tjbVue/TI4Cz3iL\n" +
                "8i96YWK0VxcCMQC7pf6Wk3RhUU2Sg6S9e6CiirFLDyzLkaWxuCnXcOwTvuXTHUQSeUCp2Q6ygS5q\n" +
                "Kyc=";

        Buffer src = onHeapAllocator().copyOf(certFromString(cert).getEncoded());
        Buffer expectedEncoded = copiedBuffer(expected, CharsetUtil.US_ASCII);
        testEncode(src, expectedEncoded);
    }

    private static X509Certificate certFromString(String string) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        ByteArrayInputStream bin = new ByteArrayInputStream(string.getBytes(CharsetUtil.US_ASCII));
        try {
            return (X509Certificate) factory.generateCertificate(bin);
        } finally {
            bin.close();
        }
    }

    private static void testEncode(Buffer src, Buffer expectedEncoded) {
        try (Buffer encoded = Base64.encode(src, true, Base64Dialect.STANDARD)) {
            assertEquals(expectedEncoded, encoded);
        } finally {
            src.close();
            expectedEncoded.close();
        }
    }

    @Test
    public void testEncodeDecode() {
        testEncodeDecode(64);
        testEncodeDecode(128);
        testEncodeDecode(512);
        testEncodeDecode(1024);
        testEncodeDecode(4096);
        testEncodeDecode(8192);
        testEncodeDecode(16384);
    }

    private static void testEncodeDecode(int size) {
        byte[] bytes = new byte[size];
        ThreadLocalRandom.current().nextBytes(bytes);

        try (Buffer src = onHeapAllocator().copyOf(bytes);
             Buffer encoded = Base64.encode(src);
             Buffer decoded = Base64.decode(encoded);
             Buffer expectedBuf = onHeapAllocator().copyOf(bytes)) {
            assertEquals(expectedBuf, decoded,
                         StringUtil.NEWLINE + "expected: " + BufferUtil.hexDump(expectedBuf) +
                         StringUtil.NEWLINE + "actual--: " + BufferUtil.hexDump(decoded));
        }
    }

    @Test
    public void testOverflowEncodedBufferSize() {
        assertEquals(Integer.MAX_VALUE, Base64.encodedBufferSize(Integer.MAX_VALUE, true));
        assertEquals(Integer.MAX_VALUE, Base64.encodedBufferSize(Integer.MAX_VALUE, false));
    }

    @Test
    public void testOverflowDecodedBufferSize() {
        assertEquals(1610612736, Base64.decodedBufferSize(Integer.MAX_VALUE));
    }

    @Test
    public void decodingFailsOnInvalidInputByte() {
        char[] invalidChars = {'\u007F', '\u0080', '\u00BD', '\u00FF'};
        for (char invalidChar : invalidChars) {
            try (Buffer buf = copiedBuffer("eHh4" + invalidChar, CharsetUtil.ISO_8859_1)) {
                Base64.decode(buf);
                fail("Invalid character in not detected: " + invalidChar);
            } catch (IllegalArgumentException ignored) {
                // as expected
            }
        }
    }

    private static Buffer copiedBuffer(String str, Charset charset) {
        return onHeapAllocator().copyOf(str, charset);
    }
}
