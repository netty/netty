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
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AbstractSniHandlerTest {

    @Test
    public void testExtractSniHostnameParsesWellFormedClientHello() throws Exception {
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
    public void testExtractSniHostnameDoesNotThrowOnOversizedSessionIdLength() throws Exception {
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
    public void testExtractSniHostnameDoesNotThrowOnOversizedCipherSuitesLength() throws Exception {
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
    public void testExtractSniHostnameDoesNotThrowOnOversizedCompressionMethodLength() throws Exception {
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
    public void testExtractSniHostnameWithFollowingExtension() throws Exception {
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
    public void testExtractSniHostnameDoesNotReadPastServerNameExtension() throws Exception {
        ByteBuf buffer = Unpooled.buffer();
        try {
            writeClientHelloPrefix(buffer, 4 + 9 + 4 + 5);
            buffer.writeShort(0); // extension type: server_name
            buffer.writeShort(9); // extension length
            buffer.writeShort(7); // server_name_list length: covers exactly the rest of the extension
            buffer.writeByte(0); // host_name
            buffer.writeShort(13); // name length: runs 9 bytes into the next extension
            buffer.writeBytes("beta".getBytes(CharsetUtil.US_ASCII)); // last 4 bytes of the extension
            buffer.writeBytes(".sni.test".getBytes(CharsetUtil.US_ASCII)); // next extension header + data

            assertMalformedServerName(buffer);
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testExtractSniHostnameRejectsServerNameListLengthMismatch() throws Exception {
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

            assertMalformedServerName(buffer);
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testExtractSniHostnameRejectsEmptyHostName() throws Exception {
        ByteBuf buffer = Unpooled.buffer();
        try {
            writeClientHelloPrefix(buffer, 4 + 5);
            buffer.writeShort(0); // extension type: server_name
            buffer.writeShort(5);
            buffer.writeShort(3); // server_name_list length
            buffer.writeByte(0); // host_name
            buffer.writeShort(0); // empty name

            assertMalformedServerName(buffer);
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testExtractSniHostnameAfterOtherExtension() throws Exception {
        ByteBuf buffer = Unpooled.buffer();
        try {
            byte[] host = "netty.io".getBytes(CharsetUtil.US_ASCII);
            writeClientHelloPrefix(buffer, 4 + 1 + 4 + 5 + host.length);
            buffer.writeShort(0xff01); // renegotiation_info
            buffer.writeShort(1);
            buffer.writeByte(0);
            writeServerNameExtension(buffer, 0, host);

            assertEquals("netty.io", AbstractSniHandler.extractSniHostname(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testExtractSniHostnameRejectsOversizedServerNameListLength() throws Exception {
        ByteBuf buffer = Unpooled.buffer();
        try {
            writeClientHelloPrefix(buffer, 4 + 9 + 4 + 5);
            buffer.writeShort(0); // extension type: server_name
            buffer.writeShort(9); // extension length
            buffer.writeShort(0xFFFF); // server_name_list length: far larger than the extension
            buffer.writeByte(0); // host_name
            buffer.writeShort(13); // name length: runs 9 bytes into the next extension
            buffer.writeBytes("beta".getBytes(CharsetUtil.US_ASCII));
            buffer.writeBytes(".sni.test".getBytes(CharsetUtil.US_ASCII));

            assertMalformedServerName(buffer);
        } finally {
            buffer.release();
        }
    }

    // The list length is one byte larger than the data that is actually left in the extension.
    @Test
    public void testExtractSniHostnameRejectsServerNameListLengthOneTooLarge() throws Exception {
        ByteBuf buffer = Unpooled.buffer();
        try {
            byte[] host = "netty.io".getBytes(CharsetUtil.US_ASCII);
            writeClientHelloPrefix(buffer, 4 + 5 + host.length + 4 + 1);
            buffer.writeShort(0); // extension type: server_name
            buffer.writeShort(5 + host.length);
            buffer.writeShort(3 + host.length + 1); // server_name_list length: one too large
            buffer.writeByte(0); // host_name
            buffer.writeShort(host.length);
            buffer.writeBytes(host);
            buffer.writeShort(0xff01); // renegotiation_info
            buffer.writeShort(1);
            buffer.writeByte(0);

            assertMalformedServerName(buffer);
        } finally {
            buffer.release();
        }
    }

    // The server_name_list length is 2 bytes, so an extension shorter than that can not hold it. The following
    // extension must not be interpreted as the list length.
    @Test
    public void testExtractSniHostnameRejectsTooShortServerNameExtension() throws Exception {
        for (int extensionLength = 0; extensionLength < 2; extensionLength++) {
            ByteBuf buffer = Unpooled.buffer();
            try {
                writeClientHelloPrefix(buffer, 4 + extensionLength + 4 + 7);
                buffer.writeShort(0); // extension type: server_name
                buffer.writeShort(extensionLength);
                buffer.writeZero(extensionLength);
                // Following extension that looks like a server_name_list holding a host name.
                buffer.writeShort(0xff01);
                buffer.writeShort(7);
                buffer.writeShort(5); // would be a server_name_list length
                buffer.writeByte(0); // would be host_name
                buffer.writeShort(2); // would be the name length
                buffer.writeBytes("ab".getBytes(CharsetUtil.US_ASCII));

                assertMalformedServerName(buffer, "extensionLength=" + extensionLength);
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    public void testExtractSniHostnameRejectsEmptyServerNameList() throws Exception {
        ByteBuf buffer = Unpooled.buffer();
        try {
            writeClientHelloPrefix(buffer, 4 + 2);
            buffer.writeShort(0); // extension type: server_name
            buffer.writeShort(2);
            buffer.writeShort(0); // server_name_list length: empty list

            assertMalformedServerName(buffer);
        } finally {
            buffer.release();
        }
    }

    // Unknown name types are skipped like the JDK does, as RFC 6066, Section 3 allows future name types.
    @Test
    public void testExtractSniHostnameIgnoresUnknownNameType() throws Exception {
        ByteBuf buffer = Unpooled.buffer();
        try {
            byte[] host = "netty.io".getBytes(CharsetUtil.US_ASCII);
            writeClientHelloPrefix(buffer, 4 + 5 + host.length);
            writeServerNameExtension(buffer, 1, host);

            assertNull(AbstractSniHandler.extractSniHostname(buffer));
        } finally {
            buffer.release();
        }
    }

    // The JDK walks the whole server_name_list, so a host_name after an unknown name type must be found too.
    // Otherwise we would select the default while the SSLEngine reports the host_name.
    @Test
    public void testExtractSniHostnameAfterUnknownNameType() throws Exception {
        ByteBuf list = Unpooled.buffer();
        writeServerNameEntry(list, 1, "other".getBytes(CharsetUtil.US_ASCII));
        writeServerNameEntry(list, 0, "netty.io".getBytes(CharsetUtil.US_ASCII));
        ByteBuf buffer = clientHelloWithServerNameList(list);
        try {
            assertEquals("netty.io", AbstractSniHandler.extractSniHostname(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testExtractSniHostnameBeforeUnknownNameType() throws Exception {
        ByteBuf list = Unpooled.buffer();
        writeServerNameEntry(list, 0, "netty.io".getBytes(CharsetUtil.US_ASCII));
        writeServerNameEntry(list, 1, "other".getBytes(CharsetUtil.US_ASCII));
        ByteBuf buffer = clientHelloWithServerNameList(list);
        try {
            assertEquals("netty.io", AbstractSniHandler.extractSniHostname(buffer));
        } finally {
            buffer.release();
        }
    }

    // RFC 6066, Section 3: The ServerNameList MUST NOT contain more than one name of the same name_type.
    @Test
    public void testExtractSniHostnameRejectsDuplicateHostName() {
        ByteBuf list = Unpooled.buffer();
        writeServerNameEntry(list, 0, "netty.io".getBytes(CharsetUtil.US_ASCII));
        writeServerNameEntry(list, 0, "other.io".getBytes(CharsetUtil.US_ASCII));
        ByteBuf buffer = clientHelloWithServerNameList(list);
        try {
            assertMalformedServerName(buffer);
        } finally {
            buffer.release();
        }
    }

    // Bytes after the last ServerName that are too short to hold another entry.
    @Test
    public void testExtractSniHostnameRejectsTrailingBytesInServerNameList() {
        for (int trailing = 1; trailing < 3; trailing++) {
            ByteBuf list = Unpooled.buffer();
            writeServerNameEntry(list, 0, "netty.io".getBytes(CharsetUtil.US_ASCII));
            list.writeZero(trailing);
            ByteBuf buffer = clientHelloWithServerNameList(list);
            try {
                assertMalformedServerName(buffer, "trailing=" + trailing);
            } finally {
                buffer.release();
            }
        }
    }

    // An entry of an unknown name type must still fit in the server_name_list.
    @Test
    public void testExtractSniHostnameRejectsUnknownNameTypeExceedingServerNameList() {
        ByteBuf list = Unpooled.buffer();
        list.writeByte(1); // unknown name type
        list.writeShort(10); // but only 2 bytes follow
        list.writeZero(2);
        ByteBuf buffer = clientHelloWithServerNameList(list);
        try {
            assertMalformedServerName(buffer);
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testExtractSniHostnameRejectsEmptyHostNameAfterUnknownNameType() {
        ByteBuf list = Unpooled.buffer();
        writeServerNameEntry(list, 1, "other".getBytes(CharsetUtil.US_ASCII));
        writeServerNameEntry(list, 0, new byte[0]);
        ByteBuf buffer = clientHelloWithServerNameList(list);
        try {
            assertMalformedServerName(buffer);
        } finally {
            buffer.release();
        }
    }

    // A server_name extension that is present but malformed must be reported, so the handler can fail closed
    // instead of selecting the default.
    private static void assertMalformedServerName(ByteBuf buffer) {
        assertMalformedServerName(buffer, null);
    }

    private static void assertMalformedServerName(ByteBuf buffer, String message) {
        assertThrows(NotSslRecordException.class, () -> AbstractSniHandler.extractSniHostname(buffer), message);
    }

    // The server_name extension claims more data than the extensions block holds.
    @Test
    public void testExtractSniHostnameRejectsServerNameExtensionExceedingExtensions() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            writeClientHelloPrefix(buffer, 4 + 3);
            buffer.writeShort(0); // extension type: server_name
            buffer.writeShort(20); // extension length: more than what is left
            buffer.writeZero(3);

            assertMalformedServerName(buffer);
        } finally {
            buffer.release();
        }
    }

    // No server_name extension at all is not an error, only a ClientHello without SNI.
    @Test
    public void testExtractSniHostnameWithoutServerNameExtension() throws Exception {
        ByteBuf buffer = Unpooled.buffer();
        try {
            writeClientHelloPrefix(buffer, 4 + 1);
            buffer.writeShort(0xff01); // renegotiation_info
            buffer.writeShort(1);
            buffer.writeByte(0);

            assertNull(AbstractSniHandler.extractSniHostname(buffer));
        } finally {
            buffer.release();
        }
    }

    private static void writeServerNameExtension(ByteBuf buffer, int nameType, byte[] host) {
        buffer.writeShort(0); // extension type: server_name
        buffer.writeShort(5 + host.length);
        buffer.writeShort(3 + host.length); // server_name_list length
        buffer.writeByte(nameType);
        buffer.writeShort(host.length);
        buffer.writeBytes(host);
    }

    private static void writeServerNameEntry(ByteBuf list, int nameType, byte[] name) {
        list.writeByte(nameType);
        list.writeShort(name.length);
        list.writeBytes(name);
    }

    // Builds a ClientHello holding only a server_name extension with the given server_name_list, and releases
    // the list.
    private static ByteBuf clientHelloWithServerNameList(ByteBuf list) {
        try {
            int listLength = list.readableBytes();
            ByteBuf buffer = Unpooled.buffer();
            writeClientHelloPrefix(buffer, 4 + 2 + listLength);
            buffer.writeShort(0); // extension type: server_name
            buffer.writeShort(2 + listLength);
            buffer.writeShort(listLength); // server_name_list length
            buffer.writeBytes(list);
            return buffer;
        } finally {
            list.release();
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
