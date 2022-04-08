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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty5.buffer.api.Buffer;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;

import static io.netty5.buffer.api.DefaultBufferAllocators.offHeapAllocator;
import static io.netty5.handler.ssl.SslUtils.getEncryptedPacketLength;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
            buffer.forEachReadable(0, (index, component) -> {
                assertEquals(0, index); // Only one component expected here.
                ByteBuffer[] byteBuffers = { component.readableBuffer() };
                assertEquals(getEncryptedPacketLength(byteBuffers, 0),
                             getEncryptedPacketLength(copy, 0));
                return true;
            });
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
    public void shouldGetPacketLengthOfGmsslProtocolFromByteBuf() {
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
        ByteBuf buf = Unpooled.buffer()
                              .writeByte(SslUtils.SSL_CONTENT_TYPE_HANDSHAKE)
                              .writeShort(SslUtils.GMSSL_PROTOCOL_VERSION)
                              .writeShort(bodyLength);

        int packetLength = getEncryptedPacketLength(new ByteBuffer[] { buf.nioBuffer() }, 0);
        assertEquals(bodyLength + SslUtils.SSL_RECORD_HEADER_LENGTH, packetLength);
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
