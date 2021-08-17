/*
 * Copyright 2021 The Netty Project
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

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TcpDnsTest {
    private static final String QUERY_DOMAIN = "www.example.com";
    private static final long TTL = 600;
    private static final byte[] QUERY_RESULT = new byte[]{(byte) 192, (byte) 168, 1, 1};

    @Test
    public void testQueryDecode() {
        EmbeddedChannel channel = new EmbeddedChannel(new TcpDnsQueryDecoder());

        int randomID = new Random().nextInt(60000 - 1000) + 1000;
        DnsQuery query = new DefaultDnsQuery(randomID, DnsOpCode.QUERY)
                .setRecord(DnsSection.QUESTION, new DefaultDnsQuestion(QUERY_DOMAIN, DnsRecordType.A));
        assertTrue(channel.writeInbound(query));

        DnsQuery readQuery = channel.readInbound();
        assertThat(readQuery, is(query));
        assertThat(readQuery.recordAt(DnsSection.QUESTION).name(), is(query.recordAt(DnsSection.QUESTION).name()));
        assertFalse(channel.finish());
    }

    @Test
    public void testResponseEncode() {
        EmbeddedChannel channel = new EmbeddedChannel(new TcpDnsResponseEncoder());

        int randomID = new Random().nextInt(60000 - 1000) + 1000;
        DnsQuery query = new DefaultDnsQuery(randomID, DnsOpCode.QUERY)
                .setRecord(DnsSection.QUESTION, new DefaultDnsQuestion(QUERY_DOMAIN, DnsRecordType.A));

        DnsQuestion question = query.recordAt(DnsSection.QUESTION);
        channel.writeInbound(newResponse(query, question, QUERY_RESULT));

        DnsResponse readResponse = channel.readInbound();
        assertThat(readResponse.recordAt(DnsSection.QUESTION), is((DnsRecord) question));
        DnsRawRecord record = new DefaultDnsRawRecord(question.name(),
                DnsRecordType.A, TTL, Unpooled.wrappedBuffer(QUERY_RESULT));
        assertThat(readResponse.recordAt(DnsSection.ANSWER), is((DnsRecord) record));
        assertThat(readResponse.<DnsRawRecord>recordAt(DnsSection.ANSWER).content(), is(record.content()));
        ReferenceCountUtil.release(readResponse);
        ReferenceCountUtil.release(record);
        assertFalse(channel.finish());
    }

    private static DefaultDnsResponse newResponse(DnsQuery query, DnsQuestion question, byte[]... addresses) {
        DefaultDnsResponse response = new DefaultDnsResponse(query.id());
        response.addRecord(DnsSection.QUESTION, question);

        for (byte[] address : addresses) {
            DefaultDnsRawRecord queryAnswer = new DefaultDnsRawRecord(question.name(),
                    DnsRecordType.A, TTL, Unpooled.wrappedBuffer(address));
            response.addRecord(DnsSection.ANSWER, queryAnswer);
        }
        return response;
    }
}
