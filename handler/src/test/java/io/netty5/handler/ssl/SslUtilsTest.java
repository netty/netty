/*
 * Copyright 2017 The Netty Project
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

import io.netty5.buffer.Buffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;

import static io.netty5.buffer.DefaultBufferAllocators.offHeapAllocator;
import static io.netty5.handler.ssl.SslUtils.getEncryptedPacketLength;
import static io.netty5.handler.ssl.SslUtils.DTLS_1_0;
import static io.netty5.handler.ssl.SslUtils.DTLS_1_2;
import static io.netty5.handler.ssl.SslUtils.DTLS_1_3;
import static io.netty5.handler.ssl.SslUtils.NOT_ENOUGH_DATA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SslUtilsTest {

    @Test
    public void testPacketLength() throws Exception {
        SSLEngine engineBE = newEngine();

        ByteBuffer empty = ByteBuffer.allocate(0);
        ByteBuffer cTOsBE = ByteBuffer.allocate(17 * 1024);

        assertTrue(engineBE.wrap(empty, cTOsBE).bytesProduced() > 0);
        cTOsBE.flip();

        try (Buffer buffer = offHeapAllocator().copyOf(cTOsBE);
             Buffer copy = buffer.copy()) {
            try (var iterator = buffer.forEachComponent()) {
                var component = iterator.firstReadable();
                ByteBuffer[] byteBuffers = { component.readableBuffer() };
                assertEquals(getEncryptedPacketLength(byteBuffers, 0),
                             getEncryptedPacketLength(copy, 0));
                assertNull(component.nextReadable()); // Only one component expected here.
            }
        }
    }

    private static SSLEngine newEngine() throws SSLException, NoSuchAlgorithmException  {
        SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        engine.setUseClientMode(true);
        engine.beginHandshake();
        return engine;
    }

    @Test
    public void testIsTLSv13Cipher() {
        assertTrue(SslUtils.isTLSv13Cipher("TLS_AES_128_GCM_SHA256"));
        assertTrue(SslUtils.isTLSv13Cipher("TLS_AES_256_GCM_SHA384"));
        assertTrue(SslUtils.isTLSv13Cipher("TLS_CHACHA20_POLY1305_SHA256"));
        assertTrue(SslUtils.isTLSv13Cipher("TLS_AES_128_CCM_SHA256"));
        assertTrue(SslUtils.isTLSv13Cipher("TLS_AES_128_CCM_8_SHA256"));
        assertFalse(SslUtils.isTLSv13Cipher("TLS_DHE_RSA_WITH_AES_128_GCM_SHA256"));
    }

    @Test
    public void shouldGetPacketLengthOfGmsslProtocolFromBuffer() {
        int bodyLength = 65;
        try (Buffer buf = offHeapAllocator().allocate(bodyLength + SslUtils.SSL_RECORD_HEADER_LENGTH)
                              .writeByte((byte) SslUtils.SSL_CONTENT_TYPE_HANDSHAKE)
                              .writeShort((short) SslUtils.GMSSL_PROTOCOL_VERSION)
                              .writeShort((short) bodyLength)) {
            int packetLength = getEncryptedPacketLength(buf, 0);
            assertEquals(bodyLength + SslUtils.SSL_RECORD_HEADER_LENGTH, packetLength);
        }
    }

    @Test
    public void shouldGetPacketLengthOfGmsslProtocolFromByteBuffer() {
        int bodyLength = 65;
        try (Buffer buf = offHeapAllocator().allocate(bodyLength + SslUtils.SSL_RECORD_HEADER_LENGTH)
                                            .writeByte((byte) SslUtils.SSL_CONTENT_TYPE_HANDSHAKE)
                                            .writeShort((short) SslUtils.GMSSL_PROTOCOL_VERSION)
                                            .writeShort((short) bodyLength)) {
            try (var iterable = buf.forEachComponent()) {
                var component = iterable.firstReadable();
                assertNotNull(component);
                int packetLength = getEncryptedPacketLength(new ByteBuffer[]{ component.readableBuffer() }, 0);
                assertEquals(bodyLength + SslUtils.SSL_RECORD_HEADER_LENGTH, packetLength);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(shorts = {DTLS_1_0, DTLS_1_2, DTLS_1_3}) // six numbers
    public void shouldGetPacketLengthOfDtlsRecordFromByteBuf(short dtlsVersion) {
        short bodyLength = 65;
        try (Buffer buf = offHeapAllocator().allocate(bodyLength + SslUtils.DTLS_RECORD_HEADER_LENGTH)
                .writeByte((byte) SslUtils.SSL_CONTENT_TYPE_HANDSHAKE)
                .writeShort(dtlsVersion)
                .writeShort((short) 0) // epoch
                .writeBytes(new byte[6]) // sequence number
                .writeShort(bodyLength)) {

            int packetLength = getEncryptedPacketLength(buf, 0);
            // bodyLength + DTLS_RECORD_HEADER_LENGTH = 65 + 13 = 78
            assertEquals(78, packetLength);
        }
    }

    @ParameterizedTest
    @ValueSource(shorts = {DTLS_1_0, DTLS_1_2, DTLS_1_3}) // six numbers
    public void shouldGetPacketLengthOfFirstDtlsRecordFromByteBuf(short dtlsVersion) {
        short bodyLength = 65;
        try (Buffer buf = offHeapAllocator().allocate(bodyLength + SslUtils.DTLS_RECORD_HEADER_LENGTH)
                .writeByte((byte) SslUtils.SSL_CONTENT_TYPE_HANDSHAKE)
                .writeShort(dtlsVersion)
                .writeShort((short) 0) // epoch
                .writeBytes(new byte[6]) // sequence number
                .writeShort(bodyLength)
                .writeBytes(new byte[65])
                .writeByte((byte) SslUtils.SSL_CONTENT_TYPE_HANDSHAKE)
                .writeShort(dtlsVersion)
                .writeShort((short) 0) // epoch
                .writeBytes(new byte[6]) // sequence number
                .writeShort(bodyLength)
                .writeBytes(new byte[65])) {
            int packetLength = getEncryptedPacketLength(buf, 0);
            assertEquals(78, packetLength);
        }
    }

    @ParameterizedTest
    @ValueSource(shorts = {DTLS_1_0, DTLS_1_2, DTLS_1_3}) // six numbers
    public void shouldSupportIncompletePackets(short dtlsVersion) {
        // Left off the last byte of the length on purpose
        try (Buffer buf = offHeapAllocator().allocate(SslUtils.DTLS_RECORD_HEADER_LENGTH)
                .writeByte((byte) SslUtils.SSL_CONTENT_TYPE_HANDSHAKE)
                .writeShort(dtlsVersion)
                .writeShort((short) 0) // epoch
                .writeBytes(new byte[6]) // sequence number
                .writeByte((byte) 0)) {
            int packetLength = getEncryptedPacketLength(buf, 0);
            assertEquals(NOT_ENOUGH_DATA, packetLength);
        }
    }

    @Test
    public void testValidHostNameForSni() {
        assertFalse(SslUtils.isValidHostNameForSNI("/test.de"), "SNI domain can't start with /");
        assertFalse(SslUtils.isValidHostNameForSNI("test.de."), "SNI domain can't end with a dot/");
        assertTrue(SslUtils.isValidHostNameForSNI("test.de"));
        // see https://datatracker.ietf.org/doc/html/rfc6066#section-3
        // it has to be test.local to qualify as SNI
        assertFalse(SslUtils.isValidHostNameForSNI("test"), "SNI has to be FQDN");
    }
}
