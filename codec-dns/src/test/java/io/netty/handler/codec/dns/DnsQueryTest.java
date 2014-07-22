/*
 * Copyright 2013 The Netty Project
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

import io.netty.channel.embedded.EmbeddedChannel;

import io.netty.channel.socket.DatagramPacket;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

public class DnsQueryTest {

    @Test
    public void writeQueryTest() throws Exception {
        InetSocketAddress addr = new InetSocketAddress(0);
        EmbeddedChannel embedder = new EmbeddedChannel(new DnsQueryEncoder());
        List<DnsQuery> queries = new ArrayList<DnsQuery>(5);
        queries.add(new DnsQuery(1, addr).addQuestion(new DnsQuestion("1.0.0.127.in-addr.arpa", DnsType.PTR)));
        queries.add(new DnsQuery(1, addr).addQuestion(new DnsQuestion("www.example.com", DnsType.A)));
        queries.add(new DnsQuery(1, addr).addQuestion(new DnsQuestion("example.com", DnsType.AAAA)));
        queries.add(new DnsQuery(1, addr).addQuestion(new DnsQuestion("example.com", DnsType.MX)));
        queries.add(new DnsQuery(1, addr).addQuestion(new DnsQuestion("example.com", DnsType.CNAME)));

        for (DnsQuery query: queries) {
            Assert.assertEquals("Invalid question count, expected 1.", 1, query.header().questionCount());
            Assert.assertEquals("Invalid answer count, expected 0.", 0, query.header().answerCount());
            Assert.assertEquals("Invalid authority resource record count, expected 0.", 0, query.header()
                    .authorityResourceCount());
            Assert.assertEquals("Invalid additional resource record count, expected 0.", 0, query.header()
                    .additionalResourceCount());
            Assert.assertEquals("Invalid type, should be TYPE_QUERY (0)", DnsHeader.TYPE_QUERY, query.header()
                    .type());
            embedder.writeOutbound(query);
            DatagramPacket packet = embedder.readOutbound();
            Assert.assertTrue(packet.content().isReadable());
            packet.release();
            Assert.assertNull(embedder.readOutbound());
        }
    }
}
