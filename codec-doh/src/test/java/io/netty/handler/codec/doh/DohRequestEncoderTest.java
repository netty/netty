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
package io.netty.handler.codec.doh;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.dns.DefaultDnsQuery;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DohRequestEncoderTest {

    private EmbeddedChannel channel;

    @BeforeEach
    public void setUp() {
        channel = new EmbeddedChannel(new DohRequestEncoder("127.0.0.1"));
    }

    @AfterEach
    public void tearDown() {
        assertFalse(channel.finish());
    }

    @Test
    public void testEncode() {
        DefaultDnsQuestion defaultDnsQuestion = new DefaultDnsQuestion("example.com.", DnsRecordType.AAAA);
        DnsQuery query = new DefaultDnsQuery(1, DnsOpCode.QUERY).setRecord(DnsSection.QUESTION,
                defaultDnsQuestion);

        assertTrue(channel.writeOutbound(query));
        DefaultFullHttpRequest packet = channel.readOutbound();
        try {
            assertTrue(packet.content().toString(CharsetUtil.UTF_8).contains("example"));
        } finally {
            packet.release();
        }
    }

}
