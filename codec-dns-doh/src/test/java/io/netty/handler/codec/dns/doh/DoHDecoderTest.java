/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.codec.dns.doh;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.dns.DefaultDnsQuery;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsRecordEncoder;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.handler.codec.http.FullHttpRequest;
import org.junit.Assert;
import org.junit.Test;

import java.net.URL;

public class DoHDecoderTest {

    private final DnsQueryEncoder dnsQueryEncoder = new DnsQueryEncoder(DnsRecordEncoder.DEFAULT);

    @Test
    public void testDecoder() throws Exception {

        EmbeddedChannel embeddedChannel = new EmbeddedChannel(
                new DoHQueryEncoder(new URL("https://localhost/dns-query")),
                new DoHDecoder()
        );

        // Create DnsQuery
        DnsQuery dnsQuerySent = new DefaultDnsQuery(0, DnsOpCode.QUERY);
        dnsQuerySent.setRecord(DnsSection.QUESTION, new DefaultDnsQuestion("www.example.com", DnsRecordType.A));
        ByteBuf dnsQuerySentBuf = Unpooled.buffer();
        dnsQueryEncoder.encode(dnsQuerySent, dnsQuerySentBuf);

        // Write and Flush DnsQuery
        Assert.assertTrue(embeddedChannel.writeOutbound(dnsQuerySent));
        embeddedChannel.flushOutbound();

        // Read FullHttpRequest from Encoder
        FullHttpRequest fullHttpRequest = embeddedChannel.readOutbound();

        // Write and Flush FullHttpRequest to Decoder
        Assert.assertTrue(embeddedChannel.writeInbound(fullHttpRequest));
        embeddedChannel.flushInbound();

        // Read DnsQuery from Decoder
        DnsQuery dnsQueryReceived = embeddedChannel.readInbound();
        ByteBuf dnsQueryReceivedBuf = Unpooled.buffer();
        dnsQueryEncoder.encode(dnsQueryReceived, dnsQueryReceivedBuf);

        // Match sent and received DnsQuery
        Assert.assertArrayEquals(ByteBufUtil.getBytes(dnsQuerySentBuf), ByteBufUtil.getBytes(dnsQueryReceivedBuf));
        dnsQuerySentBuf.release();
        dnsQueryReceivedBuf.release();

        Assert.assertTrue(embeddedChannel.close().isSuccess());
    }
}
