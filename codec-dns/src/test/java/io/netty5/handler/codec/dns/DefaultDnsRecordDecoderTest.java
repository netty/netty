/*
 * Copyright 2016 The Netty Project
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
package io.netty5.handler.codec.dns;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.Resource;
import org.junit.jupiter.api.Test;

import static io.netty5.buffer.api.DefaultBufferAllocators.onHeapAllocator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DefaultDnsRecordDecoderTest {

    @Test
    public void testDecodeName() {
        testDecodeName("netty.io.", onHeapAllocator().copyOf(new byte[] {
                5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0
        }));
    }

    @Test
    public void testDecodeNameWithoutTerminator() {
        testDecodeName("netty.io.", onHeapAllocator().copyOf(new byte[] {
                5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o'
        }));
    }

    @Test
    public void testDecodeNameWithExtraTerminator() {
        // Should not be decoded as 'netty.io..'
        testDecodeName("netty.io.", onHeapAllocator().copyOf(new byte[] {
                5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0, 0
        }));
    }

    @Test
    public void testDecodeEmptyName() {
        testDecodeName(".", onHeapAllocator().allocate(1).writeByte((byte) 0));
    }

    @Test
    public void testDecodeEmptyNameFromEmptyBuffer() {
        testDecodeName(".", onHeapAllocator().allocate(0));
    }

    @Test
    public void testDecodeEmptyNameFromExtraZeroes() {
        testDecodeName(".", onHeapAllocator().copyOf(new byte[] { 0, 0 }));
    }

    private static void testDecodeName(String expected, Buffer buffer) {
        try {
            DefaultDnsRecordDecoder decoder = new DefaultDnsRecordDecoder();
            assertEquals(expected, decoder.decodeName0(buffer));
        } finally {
            buffer.close();
        }
    }

    @Test
    public void testDecodePtrRecord() throws Exception {
        DefaultDnsRecordDecoder decoder = new DefaultDnsRecordDecoder();
        BufferAllocator allocator = onHeapAllocator();
        try (Buffer buffer = allocator.allocate(1).writeByte((byte) 0)) {
            int readerOffset = buffer.readerOffset();
            int writerOffset = buffer.writerOffset();
            DnsPtrRecord record = (DnsPtrRecord) decoder.decodeRecord(
                    "netty.io", DnsRecordType.PTR, DnsRecord.CLASS_IN, 60, allocator, buffer, 0, 1);
            assertEquals("netty.io.", record.name());
            assertEquals(DnsRecord.CLASS_IN, record.dnsClass());
            assertEquals(60, record.timeToLive());
            assertEquals(DnsRecordType.PTR, record.type());
            assertEquals(readerOffset, buffer.readerOffset());
            assertEquals(writerOffset, buffer.writerOffset());
        }
    }

    @Test
    public void testDecompressCompressPointer() {
        byte[] compressionPointer = {
                5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0,
                (byte) 0xC0, 0
        };
        Buffer uncompressed = null;
        BufferAllocator allocator = onHeapAllocator();
        try (Buffer buffer = allocator.copyOf(compressionPointer)) {
            buffer.writerOffset(12).readerOffset(10);
            uncompressed = DnsCodecUtil.decompressDomainName(allocator, buffer);
            buffer.readerOffset(0).writerOffset(10);
            assertEquals(buffer, uncompressed);
        } finally {
            Resource.dispose(uncompressed);
        }
    }

    @Test
    public void testDecompressNestedCompressionPointer() {
        byte[] nestedCompressionPointer = {
                6, 'g', 'i', 't', 'h', 'u', 'b', 2, 'i', 'o', 0, // github.io
                5, 'n', 'e', 't', 't', 'y', (byte) 0xC0, 0, // netty.github.io
                (byte) 0xC0, 11, // netty.github.io
        };
        Buffer uncompressed = null;
        BufferAllocator allocator = onHeapAllocator();
        try (Buffer buffer = allocator.copyOf(nestedCompressionPointer).readerOffset(19).writerOffset(21);
             Buffer expected = allocator.copyOf(new byte[] {
                     5, 'n', 'e', 't', 't', 'y', 6, 'g', 'i', 't', 'h', 'u', 'b', 2, 'i', 'o', 0
             })) {
            uncompressed = DnsCodecUtil.decompressDomainName(allocator, buffer);
            assertEquals(expected, uncompressed);
        } finally {
            Resource.dispose(uncompressed);
        }
    }

    @Test
    public void testDecodeCompressionRDataPointer() throws Exception {
        DefaultDnsRecordDecoder decoder = new DefaultDnsRecordDecoder();
        byte[] compressionPointer = {
                5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0,
                (byte) 0xC0, 0
        };
        DefaultDnsRawRecord cnameRecord = null;
        DefaultDnsRawRecord nsRecord = null;
        BufferAllocator allocator = onHeapAllocator();
        try (Buffer buffer = allocator.copyOf(compressionPointer)) {
            cnameRecord = (DefaultDnsRawRecord) decoder.decodeRecord(
                    "netty.github.io", DnsRecordType.CNAME, DnsRecord.CLASS_IN, 60, allocator, buffer, 10, 2);
            buffer.writerOffset(10).readerOffset(0);
            assertEquals(buffer, cnameRecord.content(),
                "The rdata of CNAME-type record should be decompressed in advance");
            assertEquals("netty.io.", DnsCodecUtil.decodeDomainName(cnameRecord.content()));
            nsRecord = (DefaultDnsRawRecord) decoder.decodeRecord(
                    "netty.github.io", DnsRecordType.NS, DnsRecord.CLASS_IN, 60, allocator, buffer, 10, 2);
            buffer.writerOffset(10).readerOffset(0);
            assertEquals(buffer, nsRecord.content(),
                        "The rdata of NS-type record should be decompressed in advance");
            assertEquals("netty.io.", DnsCodecUtil.decodeDomainName(nsRecord.content()));
        } finally {
            Resource.dispose(cnameRecord);
            Resource.dispose(nsRecord);
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
        BufferAllocator allocator = onHeapAllocator();
        try (Buffer buffer = allocator.copyOf(rfcExample)) {
            // First lets test that our utility function can correctly handle index references and decompression.
            String plainName = DefaultDnsRecordDecoder.decodeName(buffer);
            assertEquals("F.ISI.ARPA.", plainName);
            buffer.writerOffset(20).readerOffset(16);
            String uncompressedPlainName = DefaultDnsRecordDecoder.decodeName(buffer);
            assertEquals(plainName, uncompressedPlainName);
            buffer.writerOffset(20).readerOffset(12);
            String uncompressedIndexedName = DefaultDnsRecordDecoder.decodeName(buffer);
            assertEquals("FOO." + plainName, uncompressedIndexedName);

            // Now lets make sure out object parsing produces the same results for non PTR type (just use CNAME).
            rawPlainRecord = (DefaultDnsRawRecord) decoder.decodeRecord(
                    plainName, DnsRecordType.CNAME, DnsRecord.CLASS_IN, 60, allocator, buffer, 0, 11);
            assertEquals(plainName, rawPlainRecord.name());
            assertEquals(plainName, DefaultDnsRecordDecoder.decodeName(rawPlainRecord.content()));

            rawUncompressedRecord = (DefaultDnsRawRecord) decoder.decodeRecord(
                    uncompressedPlainName, DnsRecordType.CNAME, DnsRecord.CLASS_IN, 60, allocator, buffer, 16, 4);
            assertEquals(uncompressedPlainName, rawUncompressedRecord.name());
            assertEquals(uncompressedPlainName, DefaultDnsRecordDecoder.decodeName(rawUncompressedRecord.content()));

            rawUncompressedIndexedRecord = (DefaultDnsRawRecord) decoder.decodeRecord(
                    uncompressedIndexedName, DnsRecordType.CNAME, DnsRecord.CLASS_IN, 60, allocator, buffer, 12, 8);
            assertEquals(uncompressedIndexedName, rawUncompressedIndexedRecord.name());
            assertEquals(uncompressedIndexedName,
                         DefaultDnsRecordDecoder.decodeName(rawUncompressedIndexedRecord.content()));

            // Now lets make sure out object parsing produces the same results for PTR type.
            DnsPtrRecord ptrRecord = (DnsPtrRecord) decoder.decodeRecord(
                    plainName, DnsRecordType.PTR, DnsRecord.CLASS_IN, 60, allocator, buffer, 0, 11);
            assertEquals(plainName, ptrRecord.name());
            assertEquals(plainName, ptrRecord.hostname());

            ptrRecord = (DnsPtrRecord) decoder.decodeRecord(
                    uncompressedPlainName, DnsRecordType.PTR, DnsRecord.CLASS_IN, 60, allocator, buffer, 16, 4);
            assertEquals(uncompressedPlainName, ptrRecord.name());
            assertEquals(uncompressedPlainName, ptrRecord.hostname());

            ptrRecord = (DnsPtrRecord) decoder.decodeRecord(
                    uncompressedIndexedName, DnsRecordType.PTR, DnsRecord.CLASS_IN, 60, allocator, buffer, 12, 8);
            assertEquals(uncompressedIndexedName, ptrRecord.name());
            assertEquals(uncompressedIndexedName, ptrRecord.hostname());
        } finally {
            Resource.dispose(rawPlainRecord);
            Resource.dispose(rawUncompressedRecord);
            Resource.dispose(rawUncompressedIndexedRecord);
        }
    }

    @Test
    public void testTruncatedPacket() throws Exception {
        BufferAllocator allocator = onHeapAllocator();
        try (Buffer buffer = allocator.allocate(64)) {
            buffer.writeByte((byte) 0);
            buffer.writeShort((short) DnsRecordType.A.intValue());
            buffer.writeShort((short) 1);
            buffer.writeInt(32);

            // Write a truncated last value.
            buffer.writeByte((byte) 0);
            DefaultDnsRecordDecoder decoder = new DefaultDnsRecordDecoder();
            int readerOffset = buffer.readerOffset();
            assertNull(decoder.decodeRecord(allocator, buffer));
            assertEquals(readerOffset, buffer.readerOffset());
        }
    }
}
