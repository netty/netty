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
import java.util.Arrays;
import java.util.List;

public class DnsQueryTest {

    @Test
    public void writeQueryTest() throws Exception {
        InetSocketAddress addr = new InetSocketAddress(0);
        EmbeddedChannel embedder = new EmbeddedChannel(new DnsQueryEncoder());

        DnsQuery q1 = (DnsQuery)new DnsQuery(1, addr).addQuestion(new DnsQuestion("1.0.0.127.in-addr.arpa", DnsEntry.TYPE_PTR));
        DnsQuery q2 = (DnsQuery)new DnsQuery(1, addr).addQuestion(new DnsQuestion("www.example.com", DnsEntry.TYPE_A));
        DnsQuery q3 = (DnsQuery)new DnsQuery(1, addr).addQuestion(new DnsQuestion("example.com", DnsEntry.TYPE_AAAA));
        DnsQuery q4 = (DnsQuery)new DnsQuery(1, addr).addQuestion(new DnsQuestion("example.com", DnsEntry.TYPE_MX));
        DnsQuery q5 = (DnsQuery)new DnsQuery(1, addr).addQuestion(new DnsQuestion("example.com", DnsEntry.TYPE_CNAME));

        for (DnsQuery query : Arrays.asList(q1, q2, q3, q4, q5)) {

            Assert.assertEquals("Invalid question count, expected 1.", 1, query.getHeader().questionCount());
            Assert.assertEquals("Invalid answer count, expected 0.", 0, query.getHeader().answerCount());
            Assert.assertEquals("Invalid authority resource record count, expected 0.", 0, query.getHeader()
                    .authorityResourceCount());
            Assert.assertEquals("Invalid additional resource record count, expected 0.", 0, query.getHeader()
                    .additionalResourceCount());
            Assert.assertEquals("Invalid type, should be TYPE_QUERY (0)", DnsHeader.TYPE_QUERY, query.getHeader()
                    .getType());
            embedder.writeOutbound(query);
            DatagramPacket packet = embedder.readOutbound();
            List<Object> out = new ArrayList<Object>();
            DnsQueryEncoder.encodeQuery(embedder.alloc(), query, out);
            DatagramPacket p = (DatagramPacket) out.get(0);
            Assert.assertEquals("Malformed packet", packet.recipient(), p.recipient());
            Assert.assertEquals("Malformed packet", packet.content(), p.content());
        }
    }
}
