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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.EncoderException;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TcpDnsTest {
    private static final String QUERY_DOMAIN = "www.example.com";
    private static final long TTL = 600;
    private static final byte[] QUERY_RESULT = new byte[]{(byte) 192, (byte) 168, 1, 1};

    @Test
    public void testQueryDecode() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new TcpDnsQueryDecoder());

        int randomID = new SplittableRandom().nextInt(60000 - 1000) + 1000;
        DnsQuery query = new DefaultDnsQuery(randomID, DnsOpCode.QUERY)
                .setRecord(DnsSection.QUESTION, new DefaultDnsQuestion(QUERY_DOMAIN, DnsRecordType.A));
        assertTrue(channel.writeInbound(query));

        DnsQuery readQuery = channel.readInbound();
        assertEquals(query, readQuery);
        assertEquals(query.recordAt(DnsSection.QUESTION).name(), readQuery.recordAt(DnsSection.QUESTION).name());
        readQuery.release();
        assertFalse(channel.finish());
    }

    @Test
    public void testDecoderLeak() throws Exception {
        EmbeddedChannel decoder = new EmbeddedChannel(new TcpDnsQueryDecoder());
        EmbeddedChannel encoder = new EmbeddedChannel(new TcpDnsQueryEncoder());
        int randomID = new SplittableRandom().nextInt(60000 - 1000) + 1000;
        DnsQuery query = new DefaultDnsQuery(randomID, DnsOpCode.QUERY)
                .setRecord(DnsSection.QUESTION, new DefaultDnsQuestion(QUERY_DOMAIN, DnsRecordType.A));
        assertTrue(encoder.writeOutbound(query));
        final ByteBuf encoded = encoder.readOutbound();
        assertTrue(decoder.writeInbound(encoded));
        final DnsQuery decoded = decoder.readInbound();
        assertEquals(query, decoded);

        ReferenceCountUtil.release(decoded);

        // Make sure the ByteBuf is released by TcpDnsQueryDecoder
        assertTrue(encoded.refCnt() == 0);

        assertFalse(encoder.finish());
        assertFalse(decoder.finish());
    }

    @Test
    public void testResponseEncodeFailsIfMessageTooLarge() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new TcpDnsResponseEncoder());
        DnsResponse response = new DefaultDnsResponse(1);
        addLargeRecords(response);
        try {
            assertThrows(EncoderException.class, () -> channel.writeOutbound(response));
            assertNull(channel.readOutbound());
        } finally {
            assertFalse(channel.finish());
        }
    }

    @Test
    public void testQueryEncodeFailsIfMessageTooLarge() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new TcpDnsQueryEncoder());
        DnsQuery query = new DefaultDnsQuery(1, DnsOpCode.QUERY);
        addLargeRecords(query);
        try {
            assertThrows(EncoderException.class, () -> channel.writeOutbound(query));
            assertNull(channel.readOutbound());
        } finally {
            assertFalse(channel.finish());
        }
    }

    @Test
    public void testResponseEncodeLengthPrefixOfLargeMessage() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new TcpDnsResponseEncoder());
        DnsResponse response = new DefaultDnsResponse(1);
        // Header (12 bytes) + name (1 byte) + type, class, ttl and rdlength (10 bytes) + content.
        response.addRecord(DnsSection.ANSWER, new DefaultDnsRawRecord(
                ".", DnsRecordType.TXT, 1, Unpooled.wrappedBuffer(new byte[0xFFFF - 12 - 1 - 10])));
        try {
            assertTrue(channel.writeOutbound(response));
            ByteBuf encoded = channel.readOutbound();
            try {
                assertEquals(0xFFFF, encoded.readUnsignedShort());
                assertEquals(0xFFFF, encoded.readableBytes());
            } finally {
                encoded.release();
            }
        } finally {
            assertFalse(channel.finish());
        }
    }

    private static void addLargeRecords(DnsMessage message) {
        // Each record adds more than 30000 bytes so the message is larger than what fits into the 2 byte length.
        for (int i = 0; i < 3; i++) {
            message.addRecord(DnsSection.ADDITIONAL, new DefaultDnsRawRecord(
                    "example.com.", DnsRecordType.TXT, 1, Unpooled.wrappedBuffer(new byte[30000])));
        }
    }

    @Test
    public void testResponseEncode() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new TcpDnsResponseEncoder());

        int randomID = new SplittableRandom().nextInt(60000 - 1000) + 1000;
        DnsQuery query = new DefaultDnsQuery(randomID, DnsOpCode.QUERY)
                .setRecord(DnsSection.QUESTION, new DefaultDnsQuestion(QUERY_DOMAIN, DnsRecordType.A));

        DnsQuestion question = query.recordAt(DnsSection.QUESTION);
        channel.writeInbound(newResponse(query, question, QUERY_RESULT));

        DnsResponse readResponse = channel.readInbound();
        assertEquals(question, readResponse.recordAt(DnsSection.QUESTION));
        DnsRawRecord record = new DefaultDnsRawRecord(question.name(),
                DnsRecordType.A, TTL, Unpooled.wrappedBuffer(QUERY_RESULT));
        assertEquals(record, readResponse.recordAt(DnsSection.ANSWER));
        assertEquals(record.content(), readResponse.<DnsRawRecord>recordAt(DnsSection.ANSWER).content());
        ReferenceCountUtil.release(readResponse);
        ReferenceCountUtil.release(record);
        query.release();
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
