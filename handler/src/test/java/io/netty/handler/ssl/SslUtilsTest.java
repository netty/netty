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
package io.netty.handler.ssl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.NoSuchAlgorithmException;

import static io.netty.handler.ssl.SslUtils.DTLS_1_0;
import static io.netty.handler.ssl.SslUtils.DTLS_1_2;
import static io.netty.handler.ssl.SslUtils.DTLS_1_3;
import static io.netty.handler.ssl.SslUtils.NOT_ENCRYPTED;
import static io.netty.handler.ssl.SslUtils.NOT_ENOUGH_DATA;
import static io.netty.handler.ssl.SslUtils.getEncryptedPacketLength;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SslUtilsTest {

    @SuppressWarnings("deprecation")
    @Test
    public void testPacketLength() throws SSLException, NoSuchAlgorithmException {
        SSLEngine engineLE = newEngine();
        SSLEngine engineBE = newEngine();

        ByteBuffer empty = ByteBuffer.allocate(0);
        ByteBuffer cTOsLE = ByteBuffer.allocate(17 * 1024).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer cTOsBE = ByteBuffer.allocate(17 * 1024);

        assertTrue(engineLE.wrap(empty, cTOsLE).bytesProduced() > 0);
        cTOsLE.flip();

        assertTrue(engineBE.wrap(empty, cTOsBE).bytesProduced() > 0);
        cTOsBE.flip();

        ByteBuf bufferLE = Unpooled.buffer().order(ByteOrder.LITTLE_ENDIAN).writeBytes(cTOsLE);
        ByteBuf bufferBE = Unpooled.buffer().writeBytes(cTOsBE);

        // Test that the packet-length for BE and LE is the same
        assertEquals(getEncryptedPacketLength(bufferBE, 0, true),
                getEncryptedPacketLength(bufferLE, 0, true));
        assertEquals(getEncryptedPacketLength(new ByteBuffer[] { bufferBE.nioBuffer() }, 0),
                getEncryptedPacketLength(new ByteBuffer[] { bufferLE.nioBuffer().order(ByteOrder.LITTLE_ENDIAN) }, 0));
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
    public void shouldGetPacketLengthOfGmsslProtocolFromByteBuf() {
        int bodyLength = 65;
        ByteBuf buf = Unpooled.buffer()
                              .writeByte(SslUtils.SSL_CONTENT_TYPE_HANDSHAKE)
                              .writeShort(SslUtils.GMSSL_PROTOCOL_VERSION)
                              .writeShort(bodyLength);

        int packetLength = getEncryptedPacketLength(buf, 0, true);
        assertEquals(bodyLength + SslUtils.SSL_RECORD_HEADER_LENGTH, packetLength);
        buf.release();
    }

    @Test
    public void shouldGetPacketLengthOfGmsslProtocolFromByteBuffer() {
        int bodyLength = 65;
        ByteBuf buf = Unpooled.buffer()
                              .writeByte(SslUtils.SSL_CONTENT_TYPE_HANDSHAKE)
                              .writeShort(SslUtils.GMSSL_PROTOCOL_VERSION)
                              .writeShort(bodyLength);

        int packetLength = getEncryptedPacketLength(new ByteBuffer[] { buf.nioBuffer() }, 0);
        assertEquals(bodyLength + SslUtils.SSL_RECORD_HEADER_LENGTH, packetLength);
        buf.release();
    }

    @ParameterizedTest
    @ValueSource(ints = {DTLS_1_0, DTLS_1_2, DTLS_1_3}) // six numbers
    public void shouldGetPacketLengthOfDtlsRecordFromByteBuf(int dtlsVersion) {
        int bodyLength = 65;
        ByteBuf buf = Unpooled.buffer()
                .writeByte(SslUtils.SSL_CONTENT_TYPE_HANDSHAKE)
                .writeShort(dtlsVersion)
                .writeShort(0) // epoch
                .writeBytes(new byte[6]) // sequence number
                .writeShort(bodyLength);

        int packetLength = getEncryptedPacketLength(buf, 0, true);
        // bodyLength + DTLS_RECORD_HEADER_LENGTH = 65 + 13 = 78
        assertEquals(78, packetLength);
        buf.release();
    }

    @ParameterizedTest
    @ValueSource(ints = {DTLS_1_0, DTLS_1_2, DTLS_1_3}) // six numbers
    public void shouldGetPacketLengthOfFirstDtlsRecordFromByteBuf(int dtlsVersion) {
        int bodyLength = 65;
        ByteBuf buf = Unpooled.buffer()
                .writeByte(SslUtils.SSL_CONTENT_TYPE_HANDSHAKE)
                .writeShort(dtlsVersion)
                .writeShort(0) // epoch
                .writeBytes(new byte[6]) // sequence number
                .writeShort(bodyLength)
                .writeBytes(new byte[65])
                .writeByte(SslUtils.SSL_CONTENT_TYPE_HANDSHAKE)
                .writeShort(dtlsVersion)
                .writeShort(0) // epoch
                .writeBytes(new byte[6]) // sequence number
                .writeShort(bodyLength)
                .writeBytes(new byte[65]);

        int packetLength = getEncryptedPacketLength(buf, 0, true);
        assertEquals(78, packetLength);
        buf.release();
    }

    @ParameterizedTest
    @ValueSource(ints = {DTLS_1_0, DTLS_1_2, DTLS_1_3}) // six numbers
    public void shouldSupportIncompletePackets(int dtlsVersion) {
        ByteBuf buf = Unpooled.buffer()
                .writeByte(SslUtils.SSL_CONTENT_TYPE_HANDSHAKE)
                .writeShort(dtlsVersion)
                .writeShort(0) // epoch
                .writeBytes(new byte[6]) // sequence number
                .writeByte(0);
                // Left off the last byte of the length on purpose

        int packetLength = getEncryptedPacketLength(buf, 0, true);
        assertEquals(NOT_ENOUGH_DATA, packetLength);
        buf.release();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    public void probeForSSLv2(boolean probeSSLv2) {
        ByteBuf buf = Unpooled.wrappedBuffer(new byte[] { 0x30, (byte) 0x82, 0x0c, 0x48, 0x02, 0x01, 0x01, 0x01 });
        int packetLength = getEncryptedPacketLength(buf, 0, probeSSLv2);
        if (probeSSLv2) {
            assertNotEquals(NOT_ENCRYPTED, packetLength);
        } else {
            assertEquals(NOT_ENCRYPTED, packetLength);
        }
        buf.release();
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
