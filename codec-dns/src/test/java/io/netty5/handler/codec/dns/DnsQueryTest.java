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
package io.netty5.handler.codec.dns;

import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.channel.socket.BufferDatagramPacket;
import io.netty5.util.internal.SocketUtils;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DnsQueryTest {

    @Test
    public void testEncodeAndDecodeQuery() {
        InetSocketAddress addr = SocketUtils.socketAddress("8.8.8.8", 53);
        EmbeddedChannel writeChannel = new EmbeddedChannel(new DatagramDnsQueryEncoder());
        EmbeddedChannel readChannel = new EmbeddedChannel(new DatagramDnsQueryDecoder());

        verifyEncodeAndDecode(writeChannel, readChannel, new DatagramDnsQuery(null, addr, 1).setRecord(
                DnsSection.QUESTION,
                new DefaultDnsQuestion("1.0.0.127.in-addr.arpa", DnsRecordType.PTR)));
        verifyEncodeAndDecode(writeChannel, readChannel, new DatagramDnsQuery(null, addr, 1).setRecord(
                DnsSection.QUESTION,
                new DefaultDnsQuestion("www.example.com", DnsRecordType.A)));
        verifyEncodeAndDecode(writeChannel, readChannel, new DatagramDnsQuery(null, addr, 1).setRecord(
                DnsSection.QUESTION,
                new DefaultDnsQuestion("example.com", DnsRecordType.AAAA)));
        verifyEncodeAndDecode(writeChannel, readChannel, new DatagramDnsQuery(null, addr, 1).setRecord(
                DnsSection.QUESTION,
                new DefaultDnsQuestion("example.com", DnsRecordType.MX)));
        verifyEncodeAndDecode(writeChannel, readChannel, new DatagramDnsQuery(null, addr, 1).setRecord(
                DnsSection.QUESTION,
                new DefaultDnsQuestion("example.com", DnsRecordType.CNAME)));

        assertFalse(writeChannel.finish());
        assertFalse(readChannel.finish());
    }

    private static void verifyEncodeAndDecode(EmbeddedChannel writeChan, EmbeddedChannel readChan, DnsQuery query) {
        assertThat(query.count(DnsSection.QUESTION), is(1));
        assertThat(query.count(DnsSection.ANSWER), is(0));
        assertThat(query.count(DnsSection.AUTHORITY), is(0));
        assertThat(query.count(DnsSection.ADDITIONAL), is(0));

        assertTrue(writeChan.writeOutbound(query));

        BufferDatagramPacket packet = writeChan.readOutbound();
        assertTrue(packet.content().readableBytes() > 0);
        assertTrue(readChan.writeInbound(packet));

        DnsQuery decodedDnsQuery = readChan.readInbound();
        assertEquals(query, decodedDnsQuery);
        assertTrue(decodedDnsQuery.release());

        assertNull(writeChan.readOutbound());
        assertNull(readChan.readInbound());
    }
}
