/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.dns;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.*;

public class DefaultDnsRecordDecoderTest {

    @Test
    public void testDecodeName() {
        testDecodeName("netty.io.", Unpooled.wrappedBuffer(new byte[] {
                5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0
        }));
    }

    @Test
    public void testDecodeNameWithoutTerminator() {
        testDecodeName("netty.io.", Unpooled.wrappedBuffer(new byte[] {
                5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o'
        }));
    }

    @Test
    public void testDecodeNameWithExtraTerminator() {
        // Should not be decoded as 'netty.io..'
        testDecodeName("netty.io.", Unpooled.wrappedBuffer(new byte[] {
                5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0, 0
        }));
    }

    @Test
    public void testDecodeEmptyName() {
        testDecodeName(".", Unpooled.buffer().writeByte(0));
    }

    @Test
    public void testDecodeEmptyNameFromEmptyBuffer() {
        testDecodeName(".", Unpooled.EMPTY_BUFFER);
    }

    @Test
    public void testDecodeEmptyNameFromExtraZeroes() {
        testDecodeName(".", Unpooled.wrappedBuffer(new byte[] { 0, 0 }));
    }

    private static void testDecodeName(String expected, ByteBuf buffer) {
        try {
            DefaultDnsRecordDecoder decoder = new DefaultDnsRecordDecoder();
            assertEquals(expected, decoder.decodeName0(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testDecodePtrRecord() throws Exception {
        DefaultDnsRecordDecoder decoder = new DefaultDnsRecordDecoder();
        ByteBuf buffer = Unpooled.buffer().writeByte(0);
        int readerIndex = buffer.readerIndex();
        int writerIndex = buffer.writerIndex();
        try {
            DnsPtrRecord record = (DnsPtrRecord) decoder.decodeRecord(
                    "netty.io", DnsRecordType.PTR, DnsRecord.CLASS_IN, 60, buffer, 0, 1);
            assertEquals("netty.io.", record.name());
            assertEquals(DnsRecord.CLASS_IN, record.dnsClass());
            assertEquals(60, record.timeToLive());
            assertEquals(DnsRecordType.PTR, record.type());
            assertEquals(readerIndex, buffer.readerIndex());
            assertEquals(writerIndex, buffer.writerIndex());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testdecompressCompressPointer() {
        byte[] compressionPointer = {
                5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0,
                (byte) 0xC0, 0
        };
        ByteBuf buffer = Unpooled.wrappedBuffer(compressionPointer);
        ByteBuf uncompressed = null;
        try {
            uncompressed = DnsCodecUtil.decompressDomainName(buffer.duplicate().setIndex(10, 12));
            assertEquals(0, ByteBufUtil.compare(buffer.duplicate().setIndex(0, 10), uncompressed));
        } finally {
            buffer.release();
            if (uncompressed != null) {
                uncompressed.release();
            }
        }
    }

    @Test
    public void testdecompressNestedCompressionPointer() {
        byte[] nestedCompressionPointer = {
                6, 'g', 'i', 't', 'h', 'u', 'b', 2, 'i', 'o', 0, // github.io
                5, 'n', 'e', 't', 't', 'y', (byte) 0xC0, 0, // netty.github.io
                (byte) 0xC0, 11, // netty.github.io
        };
        ByteBuf buffer = Unpooled.wrappedBuffer(nestedCompressionPointer);
        ByteBuf uncompressed = null;
        try {
            uncompressed = DnsCodecUtil.decompressDomainName(buffer.duplicate().setIndex(19, 21));
            assertEquals(0, ByteBufUtil.compare(
                    Unpooled.wrappedBuffer(new byte[] {
                            5, 'n', 'e', 't', 't', 'y', 6, 'g', 'i', 't', 'h', 'u', 'b', 2, 'i', 'o', 0
                    }), uncompressed));
        } finally {
            buffer.release();
            if (uncompressed != null) {
                uncompressed.release();
            }
        }
    }

    @Test
    public void testDecodeCompressionRDataPointer() throws Exception {
        DefaultDnsRecordDecoder decoder = new DefaultDnsRecordDecoder();
        byte[] compressionPointer = {
                5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0,
                (byte) 0xC0, 0
        };
        ByteBuf buffer = Unpooled.wrappedBuffer(compressionPointer);
        DefaultDnsRawRecord cnameRecord = null;
        DefaultDnsRawRecord nsRecord = null;
        try {
            cnameRecord = (DefaultDnsRawRecord) decoder.decodeRecord(
                    "netty.github.io", DnsRecordType.CNAME, DnsRecord.CLASS_IN, 60, buffer, 10, 2);
            assertEquals("The rdata of CNAME-type record should be decompressed in advance",
                         0, ByteBufUtil.compare(buffer.duplicate().setIndex(0, 10), cnameRecord.content()));
            assertEquals("netty.io.", DnsCodecUtil.decodeDomainName(cnameRecord.content()));
            nsRecord = (DefaultDnsRawRecord) decoder.decodeRecord(
                    "netty.github.io", DnsRecordType.NS, DnsRecord.CLASS_IN, 60, buffer, 10, 2);
            assertEquals("The rdata of NS-type record should be decompressed in advance",
                         0, ByteBufUtil.compare(buffer.duplicate().setIndex(0, 10), nsRecord.content()));
            assertEquals("netty.io.", DnsCodecUtil.decodeDomainName(nsRecord.content()));
        } finally {
            buffer.release();
            if (cnameRecord != null) {
                cnameRecord.release();
            }

            if (nsRecord != null) {
                nsRecord.release();
            }
        }
    }

    @Test
    public void testDecodeMessageCompression() throws Exception {
        // See https://www.ietf.org/rfc/rfc1035 [4.1.4. Message compression]
        DefaultDnsRecordDecoder decoder = new DefaultDnsRecordDecoder();
        byte[] rfcExample = { 1, 'F', 3, 'I', 'S', 'I', 4, 'A', 'R', 'P', 'A',
                0, 3, 'F', 'O', 'O',
                (byte) 0xC0, 0, // this is 20 in the example
                (byte) 0xC0, 6, // this is 26 in the example
        };
        DefaultDnsRawRecord rawPlainRecord = null;
        DefaultDnsRawRecord rawUncompressedRecord = null;
        DefaultDnsRawRecord rawUncompressedIndexedRecord = null;
        ByteBuf buffer = Unpooled.wrappedBuffer(rfcExample);
        try {
            // First lets test that our utility function can correctly handle index references and decompression.
            String plainName = DefaultDnsRecordDecoder.decodeName(buffer.duplicate());
            assertEquals("F.ISI.ARPA.", plainName);
            String uncompressedPlainName = DefaultDnsRecordDecoder.decodeName(buffer.duplicate().setIndex(16, 20));
            assertEquals(plainName, uncompressedPlainName);
            String uncompressedIndexedName = DefaultDnsRecordDecoder.decodeName(buffer.duplicate().setIndex(12, 20));
            assertEquals("FOO." + plainName, uncompressedIndexedName);

            // Now lets make sure out object parsing produces the same results for non PTR type (just use CNAME).
            rawPlainRecord = (DefaultDnsRawRecord) decoder.decodeRecord(
                    plainName, DnsRecordType.CNAME, DnsRecord.CLASS_IN, 60, buffer, 0, 11);
            assertEquals(plainName, rawPlainRecord.name());
            assertEquals(plainName, DefaultDnsRecordDecoder.decodeName(rawPlainRecord.content()));

            rawUncompressedRecord = (DefaultDnsRawRecord) decoder.decodeRecord(
                    uncompressedPlainName, DnsRecordType.CNAME, DnsRecord.CLASS_IN, 60, buffer, 16, 4);
            assertEquals(uncompressedPlainName, rawUncompressedRecord.name());
            assertEquals(uncompressedPlainName, DefaultDnsRecordDecoder.decodeName(rawUncompressedRecord.content()));

            rawUncompressedIndexedRecord = (DefaultDnsRawRecord) decoder.decodeRecord(
                    uncompressedIndexedName, DnsRecordType.CNAME, DnsRecord.CLASS_IN, 60, buffer, 12, 8);
            assertEquals(uncompressedIndexedName, rawUncompressedIndexedRecord.name());
            assertEquals(uncompressedIndexedName,
                         DefaultDnsRecordDecoder.decodeName(rawUncompressedIndexedRecord.content()));

            // Now lets make sure out object parsing produces the same results for PTR type.
            DnsPtrRecord ptrRecord = (DnsPtrRecord) decoder.decodeRecord(
                    plainName, DnsRecordType.PTR, DnsRecord.CLASS_IN, 60, buffer, 0, 11);
            assertEquals(plainName, ptrRecord.name());
            assertEquals(plainName, ptrRecord.hostname());

            ptrRecord = (DnsPtrRecord) decoder.decodeRecord(
                    uncompressedPlainName, DnsRecordType.PTR, DnsRecord.CLASS_IN, 60, buffer, 16, 4);
            assertEquals(uncompressedPlainName, ptrRecord.name());
            assertEquals(uncompressedPlainName, ptrRecord.hostname());

            ptrRecord = (DnsPtrRecord) decoder.decodeRecord(
                    uncompressedIndexedName, DnsRecordType.PTR, DnsRecord.CLASS_IN, 60, buffer, 12, 8);
            assertEquals(uncompressedIndexedName, ptrRecord.name());
            assertEquals(uncompressedIndexedName, ptrRecord.hostname());
        } finally {
            if (rawPlainRecord != null) {
                rawPlainRecord.release();
            }
            if (rawUncompressedRecord != null) {
                rawUncompressedRecord.release();
            }
            if (rawUncompressedIndexedRecord != null) {
                rawUncompressedIndexedRecord.release();
            }
            buffer.release();
        }
    }

    @Test
    public void testTruncatedPacket() throws Exception {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(0);
        buffer.writeShort(DnsRecordType.A.intValue());
        buffer.writeShort(1);
        buffer.writeInt(32);

        // Write a truncated last value.
        buffer.writeByte(0);
        DefaultDnsRecordDecoder decoder = new DefaultDnsRecordDecoder();
        try {
            int readerIndex = buffer.readerIndex();
            assertNull(decoder.decodeRecord(buffer));
            assertEquals(readerIndex, buffer.readerIndex());
        } finally {
            buffer.release();
        }
    }
}
