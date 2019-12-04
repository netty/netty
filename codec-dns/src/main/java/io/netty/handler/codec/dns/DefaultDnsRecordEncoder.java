/*
 * Copyright 2015 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.handler.codec.dns.rdata.DnsRDataCodecs;
import io.netty.handler.codec.dns.rdata.DnsRDataEncoder;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.UnstableApi;

import static io.netty.handler.codec.dns.util.DnsEncodeUtil.*;

/**
 * The default {@link DnsRecordEncoder} implementation.
 *
 * @see DefaultDnsRecordDecoder
 */
@UnstableApi
public class DefaultDnsRecordEncoder implements DnsRecordEncoder {
    /**
     * Creates a new instance.
     */
    protected DefaultDnsRecordEncoder() {
    }

    @Override
    public final void encodeQuestion(DnsQuestion question, ByteBuf out) throws Exception {
        encodeDomainName(question.name(), out);
        out.writeShort(question.type().intValue());
        out.writeShort(question.dnsClass());
    }

    @Override
    public void encodeRecord(DnsRecord record, ByteBuf out) throws Exception {
        if (record instanceof DnsQuestion) {
            encodeQuestion((DnsQuestion) record, out);
            return;
        }

        // Encode the part of header
        encodeRecordHeader(record, out);
        int rDataOffset = out.writerIndex();

        // Encode the rdata
        DnsRDataEncoder encoder = DnsRDataCodecs.rDataEncoder(record.type());
        if (encoder != null) {
            encoder.encodeRData(record, out);
        } else if (record instanceof DnsRawRecord) {
            DnsRawRecord rawRecord = (DnsRawRecord) record;
            out.writeBytes(rawRecord.content());
        } else {
            throw new UnsupportedMessageTypeException(StringUtil.simpleClassName(record));
        }

        // Write rdata length
        int rDataContentLength = out.writerIndex() - rDataOffset;
        int rDataEnd = out.writerIndex();
        out.writerIndex(rDataOffset - Short.BYTES)
           .writeShort(rDataContentLength)
           .writerIndex(rDataEnd);
    }

    private void encodeRecordHeader(DnsRecord record, ByteBuf out) throws Exception {
        encodeDomainName(record.name(), out);
        out.writeShort(record.type().intValue());
        out.writeShort(record.dnsClass());
        out.writeInt((int) record.timeToLive());
        out.writeShort(0);
    }
}
