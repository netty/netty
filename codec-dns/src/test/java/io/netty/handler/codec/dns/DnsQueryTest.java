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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DnsQueryTest {

    @Test
    public void writeQueryTest() throws Exception {
        InetSocketAddress addr = SocketUtils.socketAddress("8.8.8.8", 53);
        EmbeddedChannel embedder = new EmbeddedChannel(new DatagramDnsQueryEncoder());
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
            assertThat(query.count(DnsSection.QUESTION), is(1));
            assertThat(query.count(DnsSection.ANSWER), is(0));
            assertThat(query.count(DnsSection.AUTHORITY), is(0));
            assertThat(query.count(DnsSection.ADDITIONAL), is(0));

            embedder.writeOutbound(query);

            DatagramPacket packet = embedder.readOutbound();
            assertTrue(packet.content().isReadable());
            packet.release();
            assertNull(embedder.readOutbound());
        }
    }
}
