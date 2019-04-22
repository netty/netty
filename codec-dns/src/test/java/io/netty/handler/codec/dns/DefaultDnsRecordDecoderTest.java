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
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.dns.record.DnsAAAARecord;
import io.netty.handler.codec.dns.record.DnsAFSDBRecord;
import io.netty.handler.codec.dns.record.DnsARecord;
import io.netty.handler.codec.dns.record.DnsCNAMERecord;
import io.netty.handler.codec.dns.record.DnsMXRecord;
import io.netty.handler.codec.dns.record.DnsNSRecord;
import io.netty.handler.codec.dns.record.DnsOPTRecord;
import io.netty.handler.codec.dns.record.DnsPTRRecord;
import io.netty.handler.codec.dns.record.DnsRPRecord;
import io.netty.handler.codec.dns.record.DnsSIGRecord;
import io.netty.handler.codec.dns.record.DnsTXTRecord;
import io.netty.handler.codec.dns.record.opt.EDNS0LlqOption;
import io.netty.handler.codec.dns.record.opt.EDNS0SubnetOption;
import org.junit.Test;

import java.util.Arrays;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class DefaultDnsRecordDecoderTest {
    private static final String DEFAULT_NAME = "netty.io.";
    private static final int DEFAULT_DNS_CLASS = DnsRecord.CLASS_IN;
    private static final long DEFAULT_TIME_TO_LIVE = 60L;

    @Test
    public void testDecodeARecord() throws Exception {
        byte[] addressBytes = { 1, 2, 3, 4 };
        ByteBuf rData = Unpooled.wrappedBuffer(addressBytes);
        DnsRecord record =
                testDecodeRecord(DEFAULT_NAME, DnsRecordType.A, DEFAULT_DNS_CLASS, DEFAULT_TIME_TO_LIVE, rData);
        assertThat(record, instanceOf(DnsARecord.class));
        DnsARecord aRecord = (DnsARecord) record;
        assertArrayEquals(addressBytes, aRecord.address().getAddress());
    }

    @Test(expected = CorruptedFrameException.class)
    public void testDecodeIllegalARecord() throws Exception {
        byte[] addressBytes = { 1, 2, 3 };
        ByteBuf rData = Unpooled.wrappedBuffer(addressBytes);
        testDecodeRecord(DEFAULT_NAME, DnsRecordType.A, DEFAULT_DNS_CLASS, DEFAULT_TIME_TO_LIVE, rData);
    }

    @Test
    public void testDecodeNSRecord() throws Exception {
        byte[] nsBytes = { 5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0 };
        ByteBuf rData = Unpooled.wrappedBuffer(nsBytes);
        DnsRecord record =
                testDecodeRecord(DEFAULT_NAME, DnsRecordType.NS, DEFAULT_DNS_CLASS, DEFAULT_TIME_TO_LIVE, rData);
        assertThat(record, instanceOf(DnsNSRecord.class));
        DnsNSRecord nsRecord = (DnsNSRecord) record;
        assertEquals("netty.io.", nsRecord.ns());
    }

    @Test
    public void testDecodeCNAMERecord() throws Exception {
        byte[] nsBytes = { 5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0 };
        ByteBuf rData = Unpooled.wrappedBuffer(nsBytes);
        DnsRecord record =
                testDecodeRecord(DEFAULT_NAME, DnsRecordType.CNAME, DEFAULT_DNS_CLASS, DEFAULT_TIME_TO_LIVE, rData);
        assertThat(record, instanceOf(DnsCNAMERecord.class));
        DnsCNAMERecord cnameRecord = (DnsCNAMERecord) record;
        assertEquals("netty.io.", cnameRecord.target());
    }

    @Test
    public void testDecodePTRecord() throws Exception {
        byte[] nsBytes = { 5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0 };
        ByteBuf rData = Unpooled.wrappedBuffer(nsBytes);
        DnsRecord record =
                testDecodeRecord(DEFAULT_NAME, DnsRecordType.PTR, DEFAULT_DNS_CLASS, DEFAULT_TIME_TO_LIVE, rData);
        assertThat(record, instanceOf(DnsPTRRecord.class));
        DnsPTRRecord ptrRecord = (DnsPTRRecord) record;
        assertEquals("netty.io.", ptrRecord.ptr());
    }

    @Test
    public void testDecodeMXRecord() throws Exception {
        byte[] mxBytes = {
                0, 1, // preference
                5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0  // exchange
        };
        ByteBuf rData = Unpooled.wrappedBuffer(mxBytes);
        DnsRecord record =
                testDecodeRecord(DEFAULT_NAME, DnsRecordType.MX, DEFAULT_DNS_CLASS, DEFAULT_TIME_TO_LIVE, rData);
        assertThat(record, instanceOf(DnsMXRecord.class));
        DnsMXRecord mxRecord = (DnsMXRecord) record;
        assertEquals(1, mxRecord.preference());
        assertEquals("netty.io.", mxRecord.exchange());
    }

    @Test
    public void testDecodeTXTRecord() throws Exception {
        byte[] txtBytes = { 4, 'n', 'y', 'a', 'n', 3, 'a', 'a', 'a' };
        ByteBuf rData = Unpooled.wrappedBuffer(txtBytes);
        DnsRecord record =
                testDecodeRecord(DEFAULT_NAME, DnsRecordType.TXT, DEFAULT_DNS_CLASS, DEFAULT_TIME_TO_LIVE, rData);
        assertThat(record, instanceOf(DnsTXTRecord.class));
        DnsTXTRecord txtRecord = (DnsTXTRecord) record;
        assertEquals(Arrays.asList("nyan", "aaa"), txtRecord.txt());
    }

    @Test
    public void testDecodeRPRecord() throws Exception {
        byte[] rpBytes = {
                5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0, // mbox
                7, 'e', 'x', 'a', 'm', 'p', 'l', 'e', 3, 'c', 'o', 'm', 0// txt
        };
        ByteBuf rData = Unpooled.wrappedBuffer(rpBytes);
        DnsRecord record =
                testDecodeRecord(DEFAULT_NAME, DnsRecordType.RP, DEFAULT_DNS_CLASS, DEFAULT_TIME_TO_LIVE, rData);
        assertThat(record, instanceOf(DnsRPRecord.class));
        DnsRPRecord rpRecord = (DnsRPRecord) record;
        assertEquals("netty.io.", rpRecord.mbox());
        assertEquals("example.com.", rpRecord.txt());
    }

    @Test
    public void testDecodeAFSDBRecord() throws Exception {
        byte[] afsdbBytes = {
                0, 1,  // subtype
                5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0 // hostname
        };
        ByteBuf rData = Unpooled.wrappedBuffer(afsdbBytes);
        DnsRecord record =
                testDecodeRecord(DEFAULT_NAME, DnsRecordType.AFSDB, DEFAULT_DNS_CLASS, DEFAULT_TIME_TO_LIVE, rData);
        assertThat(record, instanceOf(DnsAFSDBRecord.class));
        DnsAFSDBRecord afsdbRecord = (DnsAFSDBRecord) record;
        assertEquals(1, afsdbRecord.subtype());
        assertEquals("netty.io.", afsdbRecord.hostname());
    }

    public void testDecodeSIGRecord() throws Exception {
        byte[] sigBytes = {
                0, 1, // type covered
                1, // algorithem
                2, // lables
                0, 0, 0, 60, // ttl
                0, 0, 0, 10, // expiration
                0, 0, 0, 10, // inception
                0, 11, // key tag
                5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0, // signer's name
                'c', '2', 'l', 'n', 'b', 'm', 'F', '0', 'd', 'X', 'J', 'l', // signature base64 encode -> c2lnbmF0dXJl
        };
        ByteBuf rData = Unpooled.wrappedBuffer(sigBytes);
        DnsRecord record =
                testDecodeRecord(DEFAULT_NAME, DnsRecordType.SIG, DEFAULT_DNS_CLASS, DEFAULT_TIME_TO_LIVE, rData);
        assertThat(record, instanceOf(DnsSIGRecord.class));
        DnsSIGRecord sigRecord = (DnsSIGRecord) record;
        assertEquals(1, sigRecord.typeCovered());
        assertEquals(1, sigRecord.algorithem());
        assertEquals(2, sigRecord.labels());
        assertEquals(60, sigRecord.originalTTL());
        assertEquals(10, sigRecord.expiration());
        assertEquals(10, sigRecord.inception());
        assertEquals(11, sigRecord.keyTag());
        assertEquals("netty.io.", sigRecord.signerName());
        assertEquals("signature", sigRecord.signature());
    }

    @Test
    public void testDecodeAAAARecord() throws Exception {
        byte[] addressBytes = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16 };
        ByteBuf rData = Unpooled.wrappedBuffer(addressBytes);
        DnsRecord record =
                testDecodeRecord(DEFAULT_NAME, DnsRecordType.AAAA, DEFAULT_DNS_CLASS, DEFAULT_TIME_TO_LIVE, rData);
        assertThat(record, instanceOf(DnsAAAARecord.class));
        DnsAAAARecord aaaaRecord = (DnsAAAARecord) record;
        assertArrayEquals(addressBytes, aaaaRecord.address().getAddress());
    }

    @Test(expected = CorruptedFrameException.class)
    public void testDecodeIllegalAAAARecord() throws Exception {
        byte[] addressBytes = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 };
        ByteBuf rData = Unpooled.wrappedBuffer(addressBytes);
        testDecodeRecord(DEFAULT_NAME, DnsRecordType.AAAA, DEFAULT_DNS_CLASS, DEFAULT_TIME_TO_LIVE, rData);
    }

    @Test
    public void testDecodeOPTRecord() throws Exception {
        short dnsClass = 512; // udp size 512
        int ttl = 0x1028000; // extension code 1, version 2, is do true
        byte[] optionData = {
                // option long live queries
                0, 1, // option code
                0, 18, // option length
                0, 1, // version
                0, 1, // op code
                0, 2, // error code
                0, 0, 0, 0, 0, 0, 0, 1, // id
                0, 0, 0, 60, // lease life
                // option client subnet
                0, 8, // option code
                0, 8, // option length
                0, 1, // family ipv4
                8, // source prefix length
                8, // scope prefix length
                1, 2, 3, 4 // address
        };
        ByteBuf rData = Unpooled.wrappedBuffer(optionData);
        DnsRecord record =
                testDecodeRecord(DEFAULT_NAME, DnsRecordType.OPT, dnsClass, ttl, rData);
        assertThat(record, instanceOf(DnsOPTRecord.class));
        DnsOPTRecord optRecord = (DnsOPTRecord) record;
        assertEquals(1, optRecord.extendedRcode());
        assertEquals(2, optRecord.version());
        assertEquals(512, optRecord.udpSize());
        assertTrue(optRecord.isDo());
        assertEquals(2, optRecord.options().size());

        // Check the options
        EDNS0LlqOption llqOption = (EDNS0LlqOption) optRecord.options().get(0);
        assertEquals(1, llqOption.version());
        assertEquals(1, llqOption.opcode());
        assertEquals(2, llqOption.errCode());
        assertEquals(1, llqOption.id());
        assertEquals(60, llqOption.leaseLife());

        EDNS0SubnetOption subnetOption = (EDNS0SubnetOption) optRecord.options().get(1);
        assertEquals(1, subnetOption.family());
        assertEquals(8, subnetOption.sourcePrefixLength());
        assertEquals(8, subnetOption.scopePrefixLength());
        assertArrayEquals(subnetOption.address().getAddress(), new byte[] { 1, 2, 3, 4 });
    }

    public DnsRecord testDecodeRecord(String name, DnsRecordType type, int dnsClass, long timeToLive, ByteBuf rData)
            throws Exception {
        DefaultDnsRecordDecoder decoder = new DefaultDnsRecordDecoder();
        int readerIndex = rData.readerIndex();
        int writerIndex = rData.writerIndex();
        int length = rData.readableBytes();
        try {
            DnsRecord record = decoder.decodeRecord(name, type, dnsClass, timeToLive, rData, readerIndex, length);
            assertEquals(name, record.name());
            assertEquals(dnsClass, record.dnsClass());
            assertEquals(type, record.type());
            assertEquals(timeToLive, record.timeToLive());
            assertEquals(readerIndex, rData.readerIndex());
            assertEquals(writerIndex, rData.writerIndex());
            return record;
        } finally {
            rData.release();
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
