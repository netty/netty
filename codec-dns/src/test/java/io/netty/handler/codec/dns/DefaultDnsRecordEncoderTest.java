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
import io.netty.handler.codec.dns.record.opt.EDNS0Option;
import io.netty.handler.codec.dns.record.opt.EDNS0SubnetOption;
import io.netty.util.internal.SocketUtils;
import org.junit.Test;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;

public class DefaultDnsRecordEncoderTest {
    private static final String NAME = "netty.io.";
    private static final int DNS_CLASS = DnsRecord.CLASS_IN;
    private static final long TTL = 60L;
    private static final ByteBuf ENCODE_HEADER = Unpooled.wrappedBuffer(
            new byte[] {                                         // field     offset  length
                    5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0,  // name      0       10
                    0, 0,                                        // type      10      2
                    0, 1,                                        // class     12      2
                    0, 0, 0, 60,                                 // ttl       14      4
                    0, 0,                                        // rdlength  18      2
                    0, 0, 0, 0                                   // rdata     20      <rdlength>
            });

    @Test
    public void testEncodeARecord() throws Exception {
        byte[] addressBytes = { 1, 2, 3, 4 };
        InetAddress address = InetAddress.getByAddress(addressBytes);
        DnsARecord aRecord = new DnsARecord(NAME, DNS_CLASS, TTL, address);
        ByteBuf out = Unpooled.buffer(64);
        testEncodeRecord(aRecord, out);

        byte[] actualAddressBytes = new byte[addressBytes.length];
        out.readBytes(actualAddressBytes);
        assertArrayEquals(addressBytes, actualAddressBytes);
        out.release();
    }

    @Test
    public void testEncodeNSRecord() throws Exception {
        String ns = "example.com";
        byte[] nsBytes = { 7, 'e', 'x', 'a', 'm', 'p', 'l', 'e', 3, 'c', 'o', 'm', 0 };
        DnsNSRecord record = new DnsNSRecord(NAME, DNS_CLASS, TTL, ns);
        ByteBuf out = Unpooled.buffer(64);
        testEncodeRecord(record, out);
        byte[] actualNsBytes = new byte[nsBytes.length];
        out.readBytes(actualNsBytes);
        assertArrayEquals(nsBytes, actualNsBytes);
        out.release();
    }

    @Test
    public void testEncodeCNAMERecord() throws Exception {
        String cname = "example.com";
        byte[] cnameBytes = { 7, 'e', 'x', 'a', 'm', 'p', 'l', 'e', 3, 'c', 'o', 'm', 0 };
        DnsCNAMERecord record = new DnsCNAMERecord(NAME, DNS_CLASS, TTL, cname);
        ByteBuf out = Unpooled.buffer(64);
        testEncodeRecord(record, out);
        byte[] actualCnameBytes = new byte[cnameBytes.length];
        out.readBytes(actualCnameBytes);
        assertArrayEquals(cnameBytes, actualCnameBytes);
        out.release();
    }

    @Test
    public void testEncodePTRRecord() throws Exception {
        String ptr = "example.com";
        byte[] ptrBytes = { 7, 'e', 'x', 'a', 'm', 'p', 'l', 'e', 3, 'c', 'o', 'm', 0 };
        DnsPTRRecord record = new DnsPTRRecord(NAME, DNS_CLASS, TTL, ptr);
        ByteBuf out = Unpooled.buffer(64);
        testEncodeRecord(record, out);
        byte[] actualPtrBytes = new byte[ptrBytes.length];
        out.readBytes(actualPtrBytes);
        assertArrayEquals(ptrBytes, actualPtrBytes);
        out.release();
    }

    @Test
    public void testEncodeMXRecord() throws Exception {
        short preference = 1;
        String exchange = "example.com";
        DnsMXRecord record = new DnsMXRecord(NAME, DNS_CLASS, TTL, preference, exchange);
        ByteBuf out = Unpooled.buffer(64);
        testEncodeRecord(record, out);
        byte[] expectedMXBytes = { 0, 1, 7, 'e', 'x', 'a', 'm', 'p', 'l', 'e', 3, 'c', 'o', 'm', 0 };
        byte[] actualMXBytes = new byte[expectedMXBytes.length];
        out.readBytes(actualMXBytes);
        assertArrayEquals(expectedMXBytes, actualMXBytes);
        out.release();
    }

    @Test
    public void testEncodeTXTRecord() throws Exception {
        List<String> txt = Arrays.asList("nyan", "aaa");
        DnsTXTRecord record = new DnsTXTRecord(NAME, DNS_CLASS, TTL, txt);
        ByteBuf out = Unpooled.buffer(64);
        testEncodeRecord(record, out);
        byte[] expectedTxtBytes = { 4, 'n', 'y', 'a', 'n', 3, 'a', 'a', 'a' };
        byte[] actualTxtBytes = new byte[expectedTxtBytes.length];
        out.readBytes(actualTxtBytes);
        assertArrayEquals(expectedTxtBytes, actualTxtBytes);
        out.release();
    }

    @Test
    public void testEncodeRPRecord() throws Exception {
        String mBox = "netty.io";
        String txt = "example.com";
        DnsRPRecord record = new DnsRPRecord(NAME, DNS_CLASS, TTL, mBox, txt);
        ByteBuf out = Unpooled.buffer(64);
        testEncodeRecord(record, out);
        byte[] expectedRPBytes = {
                5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0, // mbox
                7, 'e', 'x', 'a', 'm', 'p', 'l', 'e', 3, 'c', 'o', 'm', 0// txt
        };
        byte[] actualRPBytes = new byte[expectedRPBytes.length];
        out.readBytes(actualRPBytes);
        assertArrayEquals(expectedRPBytes, actualRPBytes);
        out.release();
    }

    @Test
    public void testEncodeAFSDBRecord() throws Exception {
        short subtype = 1;
        String hostname = "netty.io";
        DnsAFSDBRecord afsdbRecord = new DnsAFSDBRecord(NAME, DNS_CLASS, TTL, subtype, hostname);
        ByteBuf out = Unpooled.buffer(64);
        testEncodeRecord(afsdbRecord, out);
        byte[] expectedAFSDBBytes = {
                0, 1,  // subtype
                5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0 // hostname
        };
        byte[] actualAFSDBBytes = new byte[expectedAFSDBBytes.length];
        out.readBytes(actualAFSDBBytes);
        assertArrayEquals(expectedAFSDBBytes, actualAFSDBBytes);
        out.release();
    }

    @Test
    public void testEncodeSIGRecord() throws Exception {
        short typeCovered = 1;
        byte algorithem = 1;
        byte lables = 2;
        int originalTTL = 60;
        int expiration = 10;
        int inception = 10;
        short keyTag = 11;
        String signerName = "netty.io";
        String signature = "signature";

        DnsSIGRecord sigRecord =
                new DnsSIGRecord(NAME, DNS_CLASS, TTL, typeCovered, algorithem, lables, originalTTL, expiration,
                                 inception, keyTag, signerName, signature);
        ByteBuf out = Unpooled.buffer(64);
        testEncodeRecord(sigRecord, out);
        byte[] expectedSigBytes = {
                0, 1, // type covered
                1, // algorithem
                2, // lables
                0, 0, 0, 60, // ttl
                0, 0, 0, 10, // expiration
                0, 0, 0, 10, // inception
                0, 11,// key tag
                5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0, // signer's name
                'c', '2', 'l', 'n', 'b', 'm', 'F', '0', 'd', 'X', 'J', 'l'// signature base64 encode -> c2lnbmF0dXJl
        };
        byte[] actualSigBytes = new byte[expectedSigBytes.length];
        out.readBytes(actualSigBytes);
        assertArrayEquals(expectedSigBytes, actualSigBytes);
        out.release();
    }


    @Test
    public void testEncodeAAAARecord() throws Exception {
        byte[] addressBytes = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16 };
        InetAddress address = InetAddress.getByAddress(addressBytes);
        DnsARecord record = new DnsARecord(NAME, DNS_CLASS, TTL, address);
        ByteBuf out = Unpooled.buffer(64);
        testEncodeRecord(record, out);
        byte[] actualAddressBytes = new byte[addressBytes.length];
        out.readBytes(actualAddressBytes);
        assertArrayEquals(addressBytes, actualAddressBytes);
        out.release();
    }

    @Test
    public void testEncodeOPTRecord() throws Exception {
        short dnsClass = 512; // udp size 512
        int ttl = 0x1028000; // extension code 1, version 2, is do true
        List<EDNS0Option> options = new LinkedList<EDNS0Option>();
        options.add(new EDNS0LlqOption((short) 1, (short) 1, (short) 2, 1, 60));
        options.add(new EDNS0SubnetOption(1, 24, 0,
                                          SocketUtils.addressByName("1.2.3.4")));
        DnsOPTRecord record = new DnsOPTRecord(NAME, dnsClass, ttl, options);
        ByteBuf out = Unpooled.buffer(64);
        testEncodeRecord(record, out);
        byte[] expectedBytes = {
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
                0, 7, // option length
                0, 1, // family ipv4
                24, // source prefix length
                0, // scope prefix length
                1, 2, 3, // address 1.2.3.4/24
        };
        byte[] actualBytes = new byte[expectedBytes.length];
        out.readBytes(actualBytes);
        assertArrayEquals(expectedBytes, actualBytes);
        out.release();
    }

    private static void testEncodeRecord(DnsRecord record, ByteBuf out) throws Exception {
        DnsRecordEncoder encoder = new DefaultDnsRecordEncoder();
        encoder.encodeRecord(record, out);
        DnsRecordType type = record.type();
        // Compare name field
        for (int i = 0; i < 10; i++) {
            assertEquals(ENCODE_HEADER.getByte(i), out.getByte(i));
        }
        // Compare type
        assertEquals(type.intValue(), out.getShort(10));
        // Compare dns class
        assertEquals(record.dnsClass(), out.getShort(12));
        // Compare ttl
        assertEquals(record.timeToLive(), out.getInt(14));
        // Compare length
        int rdLength = out.writerIndex() - 20;
        assertEquals(rdLength, out.getShort(18));
        out.readerIndex(20);
    }
}
