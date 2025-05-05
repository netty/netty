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
package io.netty.handler.codec.base64;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class Base64Test {

    @Test
    public void testNotAddNewLineWhenEndOnLimit() {
        ByteBuf src = copiedBuffer("abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcde",
                CharsetUtil.US_ASCII);
        ByteBuf expectedEncoded =
                copiedBuffer("YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXphYmNkZWZnaGlqa2xtbm9wcXJzdHV2d3h5emFiY2Rl",
                        CharsetUtil.US_ASCII);
        testEncode(src, expectedEncoded);
    }

    @Test
    public void testAddNewLine() {
        ByteBuf src = copiedBuffer("abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz12345678",
                CharsetUtil.US_ASCII);
        ByteBuf expectedEncoded =
                copiedBuffer("YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXphYmNkZWZnaGlqa2xtbm9wcXJzdHV2d3h5ejEyMzQ1\nNjc4",
                        CharsetUtil.US_ASCII);
        testEncode(src, expectedEncoded);
    }

    @Test
    public void testEncodeEmpty() {
        ByteBuf src = Unpooled.EMPTY_BUFFER;
        ByteBuf expectedEncoded = Unpooled.EMPTY_BUFFER;
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

        ByteBuf src = Unpooled.wrappedBuffer(certFromString(cert).getEncoded());
        ByteBuf expectedEncoded = copiedBuffer(expected, CharsetUtil.US_ASCII);
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

    private static void testEncode(ByteBuf src, ByteBuf expectedEncoded) {
        ByteBuf encoded = Base64.encode(src, true, Base64Dialect.STANDARD);
        try {
            assertEquals(expectedEncoded, encoded);
        } finally {
            src.release();
            expectedEncoded.release();
            encoded.release();
        }
    }

    @Test
    public void testEncodeDecodeBE() throws IOException {
        testEncodeDecode(ByteOrder.BIG_ENDIAN);
    }

    @Test
    public void testEncodeDecodeLE() throws IOException {
        testEncodeDecode(ByteOrder.LITTLE_ENDIAN);
    }

    private static void testEncodeDecode(ByteOrder order) throws IOException {
        testEncodeDecode(order, Base64Dialect.STANDARD);
        testEncodeDecode(order, Base64Dialect.URL_SAFE);
    }

    private static void testEncodeDecode(ByteOrder order, Base64Dialect dialect) throws IOException {
        testEncodeDecode(order, dialect, true, true);
        testEncodeDecode(order, dialect, true, false);
        testEncodeDecode(order, dialect, false, true);
        testEncodeDecode(order, dialect, false, false);
    }

    private static void testEncodeDecode(ByteOrder order, Base64Dialect dialect, boolean breakLines, boolean padded)
            throws IOException {
        testEncodeDecode(64, order, dialect, breakLines, padded);
        testEncodeDecode(128, order, dialect, breakLines, padded);
        testEncodeDecode(512, order, dialect, breakLines, padded);
        testEncodeDecode(1024, order, dialect, breakLines, padded);
        testEncodeDecode(4096, order, dialect, breakLines, padded);
        testEncodeDecode(8192, order, dialect, breakLines, padded);
        testEncodeDecode(16384, order, dialect, breakLines, padded);
    }

    private static void testEncodeDecode(
            int size, ByteOrder order, Base64Dialect dialect, boolean breakLines, boolean padded) throws IOException {
        byte[] bytes = new byte[size];
        PlatformDependent.threadLocalRandom().nextBytes(bytes);

        // JDK encoder / decoder
        java.util.Base64.Encoder jdkEncoder =
                dialect == Base64Dialect.STANDARD ? java.util.Base64.getEncoder() : java.util.Base64.getUrlEncoder();
        jdkEncoder = padded ? jdkEncoder : jdkEncoder.withoutPadding();
        java.util.Base64.Decoder jdkDecoder =
                dialect == Base64Dialect.STANDARD ? java.util.Base64.getDecoder() : java.util.Base64.getUrlDecoder();

        ByteBuf src = null;
        ByteBuf encoded = null;
        ByteBuf decoded = null;
        ByteBuf expectedBuf = null;
        byte[] jdKEncoded;
        byte[] jdkDecoded;
        try {
            jdKEncoded = breakLines ? insertNewLines(jdkEncoder.encode(bytes)) : jdkEncoder.encode(bytes);
            src = Unpooled.wrappedBuffer(bytes).order(order);
            encoded = Base64.encode(src, breakLines, dialect, padded);

            // Assert JDK encoded equals netty encoded
            assertEquals(Unpooled.wrappedBuffer(jdKEncoded), encoded,
                    StringUtil.NEWLINE + "expected: " + ByteBufUtil.hexDump(jdKEncoded) +
                    StringUtil.NEWLINE + "actual--: " +  ByteBufUtil.hexDump(encoded));

            jdkDecoded = jdkDecoder.decode(stripNewLines(ByteBufUtil.getBytes(encoded)));
            decoded = Base64.decode(encoded, dialect);
            expectedBuf = Unpooled.wrappedBuffer(bytes);

            // Assert JDK decoded equals netty decoded
            assertEquals(Unpooled.wrappedBuffer(jdkDecoded) , decoded,
                    StringUtil.NEWLINE + "expected: " + ByteBufUtil.hexDump(jdkDecoded) +
                    StringUtil.NEWLINE + "actual--: " + ByteBufUtil.hexDump(decoded));

            // Assert netty decoded equals expected
            assertEquals(expectedBuf, decoded,
                    StringUtil.NEWLINE + "expected: " + ByteBufUtil.hexDump(expectedBuf) +
                    StringUtil.NEWLINE + "actual--: " + ByteBufUtil.hexDump(decoded));
        } finally {
            ReferenceCountUtil.safeRelease(src);
            ReferenceCountUtil.safeRelease(encoded);
            ReferenceCountUtil.safeRelease(decoded);
            ReferenceCountUtil.safeRelease(expectedBuf);
        }
    }

    private static byte[] insertNewLines(byte[] src) throws IOException {
        if (src == null || src.length == 0) {
            return src;
        }

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            for (int i = 0; i < src.length; i++) {
                os.write(src[i]);
                if (i + 1 < src.length && (i + 1) % 76 == 0) {
                    os.write('\n');
                }
            }
            return os.toByteArray();
        }
    }

    private static byte[] stripNewLines(byte[] src) throws IOException {
        if (src == null || src.length == 0) {
            return src;
        }

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            for (byte b : src) {
                if (b != '\n') {
                    os.write(b);
                }
            }
            return os.toByteArray();
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
            ByteBuf buf = copiedBuffer("eHh4" + invalidChar, CharsetUtil.ISO_8859_1);
            try {
                Base64.decode(buf);
                fail("Invalid character in not detected: " + invalidChar);
            } catch (IllegalArgumentException ignored) {
                // as expected
            } finally {
                assertTrue(buf.release());
            }
        }
    }
}
