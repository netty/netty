/*
 * Copyright 2026 The Netty Project
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
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class AbstractSniHandlerTest {

    @Test
    public void testExtractSniHostnameParsesWellFormedClientHello() {
        ByteBuf buffer = validClientHelloBody("netty.io");
        try {
            assertEquals("netty.io", AbstractSniHandler.extractSniHostname(buffer));
        } finally {
            buffer.release();
        }
    }

    // See https://github.com/netty/netty security report: a ClientHello whose SessionID length
    // field is attacker-controlled and points past the end of the record used to make the
    // subsequent getUnsignedShort(...) call for cipher_suites throw an IndexOutOfBoundsException
    // instead of extractSniHostname() gracefully returning null.
    @Test
    public void testExtractSniHostnameDoesNotThrowOnOversizedSessionIdLength() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeZero(34); // client_version (2) + random (32)
            buffer.writeByte(0xFF); // SessionID length: far larger than the remaining record
            buffer.writeZero(5); // trailing bytes, mirroring a minimal non-fragmented record

            assertNull(AbstractSniHandler.extractSniHostname(buffer));
        } finally {
            buffer.release();
        }
    }

    // Same issue as above but for the cipher_suites length field feeding the unguarded read of
    // compression_methods length.
    @Test
    public void testExtractSniHostnameDoesNotThrowOnOversizedCipherSuitesLength() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeZero(34); // client_version (2) + random (32)
            buffer.writeByte(0); // SessionID length
            buffer.writeShort(0xFFFF); // cipher_suites length: far larger than the remaining record

            assertNull(AbstractSniHandler.extractSniHostname(buffer));
        } finally {
            buffer.release();
        }
    }

    // Same issue as above but for the compression_methods length field feeding the unguarded read
    // of the extensions length.
    @Test
    public void testExtractSniHostnameDoesNotThrowOnOversizedCompressionMethodLength() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeZero(34); // client_version (2) + random (32)
            buffer.writeByte(0); // SessionID length
            buffer.writeShort(0); // cipher_suites length
            buffer.writeByte(0xFF); // compression_methods length: far larger than the remaining record

            assertNull(AbstractSniHandler.extractSniHostname(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testExtractSniHostnameWithFollowingExtension() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            byte[] host = "netty.io".getBytes(CharsetUtil.US_ASCII);
            writeClientHelloPrefix(buffer, 4 + 5 + host.length + 4 + 1);
            buffer.writeShort(0); // extension type: server_name
            buffer.writeShort(5 + host.length);
            buffer.writeShort(3 + host.length); // server_name_list length
            buffer.writeByte(0); // host_name
            buffer.writeShort(host.length);
            buffer.writeBytes(host);
            buffer.writeShort(0xff01); // renegotiation_info
            buffer.writeShort(1);
            buffer.writeByte(0);

            assertEquals("netty.io", AbstractSniHandler.extractSniHostname(buffer));
        } finally {
            buffer.release();
        }
    }

    // The host_name entry must not extend past the server_name extension. Previously the name was only
    // bounded by the end of all extensions, so its bytes could be taken from the following extension.
    @Test
    public void testExtractSniHostnameDoesNotReadPastServerNameExtension() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            writeClientHelloPrefix(buffer, 4 + 9 + 4 + 5);
            buffer.writeShort(0); // extension type: server_name
            buffer.writeShort(9); // extension length
            buffer.writeShort(0xFFFF); // server_name_list length (not validated before)
            buffer.writeByte(0); // host_name
            buffer.writeShort(13); // name length: runs 9 bytes into the next extension
            buffer.writeBytes("beta".getBytes(CharsetUtil.US_ASCII)); // last 4 bytes of the extension
            buffer.writeBytes(".sni.test".getBytes(CharsetUtil.US_ASCII)); // next extension header + data

            assertNull(AbstractSniHandler.extractSniHostname(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testExtractSniHostnameRejectsServerNameListLengthMismatch() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            byte[] host = "netty.io".getBytes(CharsetUtil.US_ASCII);
            writeClientHelloPrefix(buffer, 4 + 5 + host.length);
            buffer.writeShort(0); // extension type: server_name
            buffer.writeShort(5 + host.length);
            buffer.writeShort(0); // server_name_list length: does not cover the entry
            buffer.writeByte(0); // host_name
            buffer.writeShort(host.length);
            buffer.writeBytes(host);

            assertNull(AbstractSniHandler.extractSniHostname(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testExtractSniHostnameRejectsEmptyHostName() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            writeClientHelloPrefix(buffer, 4 + 5);
            buffer.writeShort(0); // extension type: server_name
            buffer.writeShort(5);
            buffer.writeShort(3); // server_name_list length
            buffer.writeByte(0); // host_name
            buffer.writeShort(0); // empty name

            assertNull(AbstractSniHandler.extractSniHostname(buffer));
        } finally {
            buffer.release();
        }
    }

    private static void writeClientHelloPrefix(ByteBuf buffer, int extensionsLength) {
        buffer.writeZero(34); // client_version (2) + random (32)
        buffer.writeByte(0); // SessionID length
        buffer.writeShort(0); // cipher_suites length
        buffer.writeByte(0); // compression_methods length
        buffer.writeShort(extensionsLength);
    }

    private static ByteBuf validClientHelloBody(String hostname) {
        byte[] hostBytes = hostname.getBytes(CharsetUtil.US_ASCII);
        int serverNameListLength = 1 + 2 + hostBytes.length; // name type (1) + name length (2) + name
        int serverNameExtensionDataLength = 2 + serverNameListLength; // server_name_list length (2) + list
        int extensionsLength = 2 + 2 + serverNameExtensionDataLength; // ext type (2) + ext length (2) + data

        ByteBuf buffer = Unpooled.buffer();
        buffer.writeZero(34); // client_version (2) + random (32)
        buffer.writeByte(0); // SessionID length
        buffer.writeShort(0); // cipher_suites length
        buffer.writeByte(0); // compression_methods length
        buffer.writeShort(extensionsLength);
        buffer.writeShort(0); // extension type: server_name
        buffer.writeShort(serverNameExtensionDataLength);
        buffer.writeShort(serverNameListLength);
        buffer.writeByte(0); // server name type: host_name
        buffer.writeShort(hostBytes.length);
        buffer.writeBytes(hostBytes);
        return buffer;
    }
}
