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
import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.net.InetAddress;

import static org.junit.Assert.assertEquals;

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
    public void testDecodeARecord() throws Exception {
        DefaultDnsRecordDecoder decoder = new DefaultDnsRecordDecoder();
        InetAddress addr = InetAddress.getByName("1.2.3.4");
        ByteBuf buffer = Unpooled.buffer().writeBytes(addr.getAddress());
        int readerIndex = buffer.readerIndex();
        int writerIndex = buffer.writerIndex();
        try {
            DnsARecord record = (DnsARecord) decoder.decodeRecord(
                    "netty.io", DnsRecordType.A, DnsRecord.CLASS_IN, 60, buffer, 0, buffer.readableBytes());
            assertEquals("netty.io.", record.name());
            assertEquals(DnsRecord.CLASS_IN, record.dnsClass());
            assertEquals(60, record.timeToLive());
            assertEquals(DnsRecordType.A, record.type());
            assertEquals(readerIndex, buffer.readerIndex());
            assertEquals(writerIndex, buffer.writerIndex());
            assertEquals(addr, record.address());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testDecodeAAAARecord() throws Exception {
        DefaultDnsRecordDecoder decoder = new DefaultDnsRecordDecoder();
        InetAddress addr = InetAddress.getByName("1080:0:0:0:8:800:200C:417A");
        ByteBuf buffer = Unpooled.buffer().writeBytes(addr.getAddress());
        int readerIndex = buffer.readerIndex();
        int writerIndex = buffer.writerIndex();
        try {
            DnsAAAARecord record = (DnsAAAARecord) decoder.decodeRecord(
                    "netty.io", DnsRecordType.AAAA, DnsRecord.CLASS_IN, 60, buffer, 0, buffer.readableBytes());
            assertEquals("netty.io.", record.name());
            assertEquals(DnsRecord.CLASS_IN, record.dnsClass());
            assertEquals(60, record.timeToLive());
            assertEquals(DnsRecordType.AAAA, record.type());
            assertEquals(readerIndex, buffer.readerIndex());
            assertEquals(writerIndex, buffer.writerIndex());
            assertEquals(addr, record.address());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testDecodeCNameRecord() throws Exception {
        DefaultDnsRecordDecoder decoder = new DefaultDnsRecordDecoder();
        ByteBuf buffer = Unpooled.buffer();
        DefaultDnsRecordEncoder.encodeName("cname.netty.io.", buffer);
        int readerIndex = buffer.readerIndex();
        int writerIndex = buffer.writerIndex();
        try {
            DnsCNameRecord record = (DnsCNameRecord) decoder.decodeRecord(
                    "netty.io", DnsRecordType.CNAME, DnsRecord.CLASS_IN, 60, buffer, 0, buffer.readableBytes());
            assertEquals("netty.io.", record.name());
            assertEquals(DnsRecord.CLASS_IN, record.dnsClass());
            assertEquals(60, record.timeToLive());
            assertEquals(DnsRecordType.CNAME, record.type());
            assertEquals(readerIndex, buffer.readerIndex());
            assertEquals(writerIndex, buffer.writerIndex());
            assertEquals("cname.netty.io.", record.hostname());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testDecodeMxRecord() throws Exception {
        DefaultDnsRecordDecoder decoder = new DefaultDnsRecordDecoder();
        ByteBuf buffer = Unpooled.buffer().writeShort(0);
        DefaultDnsRecordEncoder.encodeName("smtp.netty.io.", buffer);
        int readerIndex = buffer.readerIndex();
        int writerIndex = buffer.writerIndex();
        try {
            DnsMxRecord record = (DnsMxRecord) decoder.decodeRecord(
                    "netty.io", DnsRecordType.MX, DnsRecord.CLASS_IN, 60, buffer, 0, buffer.readableBytes());
            assertEquals("netty.io.", record.name());
            assertEquals(DnsRecord.CLASS_IN, record.dnsClass());
            assertEquals(60, record.timeToLive());
            assertEquals(DnsRecordType.MX, record.type());
            assertEquals(readerIndex, buffer.readerIndex());
            assertEquals(writerIndex, buffer.writerIndex());
            assertEquals(0, record.preference());
            assertEquals("smtp.netty.io.", record.hostname());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testDecodeNsRecord() throws Exception {
        DefaultDnsRecordDecoder decoder = new DefaultDnsRecordDecoder();
        ByteBuf buffer = Unpooled.buffer();
        DefaultDnsRecordEncoder.encodeName("ns.netty.io.", buffer);
        int readerIndex = buffer.readerIndex();
        int writerIndex = buffer.writerIndex();
        try {
            DnsNsRecord record = (DnsNsRecord) decoder.decodeRecord(
                    "netty.io", DnsRecordType.NS, DnsRecord.CLASS_IN, 60, buffer, 0, buffer.readableBytes());
            assertEquals("netty.io.", record.name());
            assertEquals(DnsRecord.CLASS_IN, record.dnsClass());
            assertEquals(60, record.timeToLive());
            assertEquals(DnsRecordType.NS, record.type());
            assertEquals(readerIndex, buffer.readerIndex());
            assertEquals(writerIndex, buffer.writerIndex());
            assertEquals("ns.netty.io.", record.hostname());
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
    public void testDecodeSoaRecord() throws Exception {
        DefaultDnsRecordDecoder decoder = new DefaultDnsRecordDecoder();
        ByteBuf buffer = Unpooled.buffer();
        DefaultDnsRecordEncoder.encodeName("theo.ns.cloudflare.com.", buffer);
        DefaultDnsRecordEncoder.encodeName("dns.cloudflare.com.", buffer);
        buffer.writeInt(2024173464).writeInt(10000).writeInt(2400).writeInt(604800).writeInt(3600);
        int readerIndex = buffer.readerIndex();
        int writerIndex = buffer.writerIndex();
        try {
            DnsSoaRecord record = (DnsSoaRecord) decoder.decodeRecord(
                    "netty.io", DnsRecordType.SOA, DnsRecord.CLASS_IN, 60, buffer, 0, buffer.readableBytes());
            assertEquals("netty.io.", record.name());
            assertEquals(DnsRecord.CLASS_IN, record.dnsClass());
            assertEquals(60, record.timeToLive());
            assertEquals(DnsRecordType.SOA, record.type());
            assertEquals(readerIndex, buffer.readerIndex());
            assertEquals(writerIndex, buffer.writerIndex());
            assertEquals("theo.ns.cloudflare.com.", record.primaryNameServer());
            assertEquals("dns.cloudflare.com.", record.responsibleAuthorityMailbox());
            assertEquals(2024173464, record.serialNumber());
            assertEquals(10000, record.refreshInterval());
            assertEquals(2400, record.retryInterval());
            assertEquals(604800, record.expireLimit());
            assertEquals(3600, record.minimumTTL());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testDecodeSrvRecord() throws Exception {
        DefaultDnsRecordDecoder decoder = new DefaultDnsRecordDecoder();
        ByteBuf buffer = Unpooled.buffer().writeShort(0).writeShort(1).writeShort(8080);
        DefaultDnsRecordEncoder.encodeName("srv.netty.io.", buffer);
        buffer.writeInt(2024173464).writeInt(10000).writeInt(2400).writeInt(604800).writeInt(3600);
        int readerIndex = buffer.readerIndex();
        int writerIndex = buffer.writerIndex();
        try {
            DnsSrvRecord record = (DnsSrvRecord) decoder.decodeRecord(
                    "netty.io", DnsRecordType.SRV, DnsRecord.CLASS_IN, 60, buffer, 0, buffer.readableBytes());
            assertEquals("netty.io.", record.name());
            assertEquals(DnsRecord.CLASS_IN, record.dnsClass());
            assertEquals(60, record.timeToLive());
            assertEquals(DnsRecordType.SRV, record.type());
            assertEquals(readerIndex, buffer.readerIndex());
            assertEquals(writerIndex, buffer.writerIndex());
            assertEquals(0, record.priority());
            assertEquals(1, record.weight());
            assertEquals(8080, record.port());
            assertEquals("srv.netty.io.", record.target());
        } finally {
            buffer.release();
        }
    }

    static String[][] TXT_TEST_CASES = new String[][]{
        {
            "printer=lpr5", "printer", "lpr5"
        },
        {
            "favorite drink=orange juice", "favorite drink", "orange juice"
        },
        {
            "equation=a=4", "equation", "a=4"
        },
        {
            "a`=a=true", "a=a", "true"
        },
        {
            "a\\`=a=false", "a\\=a", "false"
        },
        {
            "`==\\=", "=", "\\="
        },
        {
            "string=\"Cat\"", "string", "\"Cat\""
        },
        {
            "string2=``abc``", "string2", "`abc`"
        },
        {
            "novalue=", "novalue", ""
        },
        {
            " a b=c d", "a b", "c d"
        },
        {
            "abc` =123\t", "abc ", "123\t"
        }
    };

    @Test
    public void testDecodeDnsTxtRecord() throws Exception {
        DefaultDnsRecordDecoder decoder = new DefaultDnsRecordDecoder();

        for (int i = 0; i < TXT_TEST_CASES.length; i++) {
            String[] testCase = TXT_TEST_CASES[i];
            String text = testCase[0];
            String key = testCase[1];
            String value = testCase[2];

            ByteBuf buffer = Unpooled.buffer().writeBytes(text.getBytes());
            int readerIndex = buffer.readerIndex();
            int writerIndex = buffer.writerIndex();
            try {
                DnsTxtRecord record = (DnsTxtRecord) decoder.decodeRecord(
                        "netty.io", DnsRecordType.TXT, DnsRecord.CLASS_IN, 60, buffer, 0, buffer.readableBytes());
                assertEquals("netty.io.", record.name());
                assertEquals(DnsRecord.CLASS_IN, record.dnsClass());
                assertEquals(60, record.timeToLive());
                assertEquals(DnsRecordType.TXT, record.type());
                assertEquals(readerIndex, buffer.readerIndex());
                assertEquals(writerIndex, buffer.writerIndex());
                assertEquals(key, record.key());
                assertEquals(value, record.value());
            } finally {
                buffer.release();
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
            DnsCNameRecord cnameRecord = (DnsCNameRecord) decoder.decodeRecord(
                    plainName, DnsRecordType.CNAME, DnsRecord.CLASS_IN, 60, buffer, 0, 11);
            assertEquals(plainName, cnameRecord.name());
            assertEquals(plainName, cnameRecord.hostname());

            cnameRecord = (DnsCNameRecord) decoder.decodeRecord(
                    uncompressedPlainName, DnsRecordType.CNAME, DnsRecord.CLASS_IN, 60, buffer, 16, 4);
            assertEquals(uncompressedPlainName, cnameRecord.name());
            assertEquals(uncompressedPlainName, cnameRecord.hostname());

            cnameRecord = (DnsCNameRecord) decoder.decodeRecord(
                    uncompressedIndexedName, DnsRecordType.CNAME, DnsRecord.CLASS_IN, 60, buffer, 12, 8);
            assertEquals(uncompressedIndexedName, cnameRecord.name());
            assertEquals(uncompressedIndexedName, cnameRecord.hostname());

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
}
