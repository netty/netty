/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.dns;

import io.netty.channel.embedded.EmbeddedChannel;

import io.netty.channel.socket.DatagramPacket;
import io.netty.util.internal.SocketUtils;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DnsQueryTest {

    private static final DnsOpCode[] OP_CODES = {
            DnsOpCode.QUERY, DnsOpCode.IQUERY, DnsOpCode.STATUS, DnsOpCode.NOTIFY, DnsOpCode.UPDATE };

    @Test
    public void testEncodeAndDecodeQuery() {
        InetSocketAddress addr = SocketUtils.socketAddress("8.8.8.8", 53);
        EmbeddedChannel writeChannel = new EmbeddedChannel(new DatagramDnsQueryEncoder());
        EmbeddedChannel readChannel = new EmbeddedChannel(new DatagramDnsQueryDecoder());

        List<DnsQuery> queries = new ArrayList<DnsQuery>(5);
        queries.add(new DatagramDnsQuery(null, addr, 1).setRecord(
                DnsSection.QUESTION,
                new DefaultDnsQuestion("1.0.0.127.in-addr.arpa", DnsRecordType.PTR)));
        queries.add(new DatagramDnsQuery(null, addr, 1).setRecord(
                DnsSection.QUESTION,
                new DefaultDnsQuestion("www.example.com", DnsRecordType.A)));
        queries.add(new DatagramDnsQuery(null, addr, 1).setRecord(
                DnsSection.QUESTION,
                new DefaultDnsQuestion("example.com", DnsRecordType.AAAA)));
        queries.add(new DatagramDnsQuery(null, addr, 1).setRecord(
                DnsSection.QUESTION,
                new DefaultDnsQuestion("example.com", DnsRecordType.MX)));
        queries.add(new DatagramDnsQuery(null, addr, 1).setRecord(
                DnsSection.QUESTION,
                new DefaultDnsQuestion("example.com", DnsRecordType.CNAME)));

        for (DnsQuery query: queries) {
            assertEquals(1, query.count(DnsSection.QUESTION));
            assertEquals(0, query.count(DnsSection.ANSWER));
            assertEquals(0, query.count(DnsSection.AUTHORITY));
            assertEquals(0, query.count(DnsSection.ADDITIONAL));

            assertTrue(writeChannel.writeOutbound(query));

            DatagramPacket packet = writeChannel.readOutbound();
            assertTrue(packet.content().isReadable());
            assertTrue(readChannel.writeInbound(packet));

            DnsQuery decodedDnsQuery = readChannel.readInbound();
            assertEquals(query, decodedDnsQuery);
            assertTrue(decodedDnsQuery.release());

            assertNull(writeChannel.readOutbound());
            assertNull(readChannel.readInbound());
        }

        assertFalse(writeChannel.finish());
        assertFalse(readChannel.finish());
    }

    @Test
    public void testOpCodeSurvivesEncodeAndDecode() {
        InetSocketAddress addr = SocketUtils.socketAddress("8.8.8.8", 53);
        EmbeddedChannel writeChannel = new EmbeddedChannel(new DatagramDnsQueryEncoder());
        EmbeddedChannel readChannel = new EmbeddedChannel(new DatagramDnsQueryDecoder());

        for (DnsOpCode opCode: OP_CODES) {
            DnsQuery query = new DatagramDnsQuery(null, addr, 1, opCode).setRecord(
                    DnsSection.QUESTION,
                    new DefaultDnsQuestion("example.com", DnsRecordType.A));

            assertTrue(writeChannel.writeOutbound(query));
            DatagramPacket packet = writeChannel.readOutbound();
            assertTrue(readChannel.writeInbound(packet));

            DnsQuery decodedDnsQuery = readChannel.readInbound();
            assertEquals(opCode, decodedDnsQuery.opCode());
            assertTrue(decodedDnsQuery.release());
        }

        assertFalse(writeChannel.finish());
        assertFalse(readChannel.finish());
    }

    @Test
    public void testOpCodeIsEncodedIntoBits14To11() {
        InetSocketAddress addr = SocketUtils.socketAddress("8.8.8.8", 53);
        EmbeddedChannel writeChannel = new EmbeddedChannel(new DatagramDnsQueryEncoder());

        for (DnsOpCode opCode: OP_CODES) {
            for (boolean recursionDesired: new boolean[] { false, true }) {
                DnsQuery query = new DatagramDnsQuery(null, addr, 1, opCode)
                        .setRecursionDesired(recursionDesired);
                query.setRecord(DnsSection.QUESTION,
                        new DefaultDnsQuestion("example.com", DnsRecordType.A));

                assertTrue(writeChannel.writeOutbound(query));
                DatagramPacket packet = writeChannel.readOutbound();

                // RFC 1035 section 4.1.1: the flags word is QR(1) OPCODE(4) AA TC RD RA Z(3) RCODE(4).
                // The OPCODE and RD are the only fields this encoder writes, so the whole word is known.
                int expectedFlags = opCode.byteValue() << 11;
                if (recursionDesired) {
                    expectedFlags |= 1 << 8;
                }
                assertEquals(expectedFlags, packet.content().getUnsignedShort(2),
                        "unexpected flags for opCode " + opCode + " and RD " + recursionDesired);

                assertTrue(packet.release());
            }
        }

        assertFalse(writeChannel.finish());
    }

    @Test
    public void testOutOfRangeOpCodeIsTruncatedToFourBits() {
        InetSocketAddress addr = SocketUtils.socketAddress("8.8.8.8", 53);
        EmbeddedChannel writeChannel = new EmbeddedChannel(new DatagramDnsQueryEncoder());

        // DnsOpCode does not range check, so an OPCODE that does not fit the 4 bits reserved by
        // RFC 1035 section 4.1.1 can reach the encoder. It must not spill into the neighbouring QR bit.
        DnsQuery query = new DatagramDnsQuery(null, addr, 1, DnsOpCode.valueOf(16)).setRecord(
                DnsSection.QUESTION,
                new DefaultDnsQuestion("example.com", DnsRecordType.A));

        assertTrue(writeChannel.writeOutbound(query));
        DatagramPacket packet = writeChannel.readOutbound();

        assertEquals(0, packet.content().getUnsignedShort(2));

        assertTrue(packet.release());
        assertFalse(writeChannel.finish());
    }
}
