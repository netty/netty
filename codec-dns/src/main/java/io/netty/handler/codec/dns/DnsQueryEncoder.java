/*
 * Copyright 2019 The Netty Project
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

import static io.netty.util.internal.ObjectUtil.checkNotNull;

final class DnsQueryEncoder {

    private final DnsRecordEncoder recordEncoder;

    /**
     * Creates a new encoder with the specified {@code recordEncoder}.
     */
    DnsQueryEncoder(DnsRecordEncoder recordEncoder) {
        this.recordEncoder = checkNotNull(recordEncoder, "recordEncoder");
    }

    /**
     * Encodes the given {@link DnsQuery} into a {@link ByteBuf}.
     */
    void encode(DnsQuery query, ByteBuf out) throws Exception {
        encodeHeader(query, out);
        encodeQuestions(query, out);
        encodeRecords(query, DnsSection.ADDITIONAL, out);
    }

    /**
     * Encodes the header that is always 12 bytes long.
     *
     * @param query the query header being encoded
     * @param buf   the buffer the encoded data should be written to
     */
    private static void encodeHeader(DnsQuery query, ByteBuf buf) {
        buf.writeShort(query.id());
        int flags = 0;
        flags |= (query.opCode().byteValue() & 0xFF) << 14;
        if (query.isRecursionDesired()) {
            flags |= 1 << 8;
        }
        buf.writeShort(flags);
        buf.writeShort(query.count(DnsSection.QUESTION));
        buf.writeShort(0); // answerCount
        buf.writeShort(0); // authorityResourceCount
        buf.writeShort(query.count(DnsSection.ADDITIONAL));
    }

    private void encodeQuestions(DnsQuery query, ByteBuf buf) throws Exception {
        final int count = query.count(DnsSection.QUESTION);
        for (int i = 0; i < count; i++) {
            recordEncoder.encodeQuestion((DnsQuestion) query.recordAt(DnsSection.QUESTION, i), buf);
        }
    }

    private void encodeRecords(DnsQuery query, DnsSection section, ByteBuf buf) throws Exception {
        final int count = query.count(section);
        for (int i = 0; i < count; i++) {
            recordEncoder.encodeRecord(query.recordAt(section, i), buf);
        }
    }
}
