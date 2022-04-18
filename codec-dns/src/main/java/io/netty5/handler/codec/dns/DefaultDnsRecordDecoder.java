/*
 * Copyright 2015 The Netty Project
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

import io.netty5.buffer.api.Buffer;
import io.netty5.util.internal.UnstableApi;

/**
 * The default {@link DnsRecordDecoder} implementation.
 *
 * @see DefaultDnsRecordEncoder
 */
@UnstableApi
public class DefaultDnsRecordDecoder implements DnsRecordDecoder {

    static final String ROOT = ".";

    /**
     * Creates a new instance.
     */
    protected DefaultDnsRecordDecoder() { }

    @Override
    public final DnsQuestion decodeQuestion(Buffer in) throws Exception {
        String name = decodeName(in);
        DnsRecordType type = DnsRecordType.valueOf(in.readUnsignedShort());
        int qClass = in.readUnsignedShort();
        return new DefaultDnsQuestion(name, type, qClass);
    }

    @Override
    public final <T extends DnsRecord> T decodeRecord(Buffer in) throws Exception {
        final int startOffset = in.readerOffset();
        final String name = decodeName(in);

        final int endOffset = in.writerOffset();
        if (endOffset - in.readerOffset() < 10) {
            // Not enough data
            in.readerOffset(startOffset);
            return null;
        }

        final DnsRecordType type = DnsRecordType.valueOf(in.readUnsignedShort());
        final int aClass = in.readUnsignedShort();
        final long ttl = in.readUnsignedInt();
        final int length = in.readUnsignedShort();
        final int offset = in.readerOffset();

        if (endOffset - offset < length) {
            // Not enough data
            in.readerOffset(startOffset);
            return null;
        }

        @SuppressWarnings("unchecked")
        T record = (T) decodeRecord(name, type, aClass, ttl, in, offset, length);
        in.readerOffset(offset + length);
        return record;
    }

    /**
     * Decodes a record from the information decoded so far by {@link #decodeRecord(Buffer)}.
     *
     * @param name the domain name of the record
     * @param type the type of the record
     * @param dnsClass the class of the record
     * @param timeToLive the TTL of the record
     * @param in the {@link Buffer} that contains the RDATA
     * @param offset the start offset of the RDATA in {@code in}
     * @param length the length of the RDATA
     *
     * @return a {@link DnsRawRecord}. Override this method to decode RDATA and return other record implementation.
     */
    protected DnsRecord decodeRecord(
            String name, DnsRecordType type, int dnsClass, long timeToLive,
            Buffer in, int offset, int length) throws Exception {

        // DNS message compression means that domain names may contain "pointers" to other positions in the packet
        // to build a full message. This means the indexes are meaningful and we need the ability to reference the
        // indexes un-obstructed, and thus we cannot use a slice here.
        // See https://www.ietf.org/rfc/rfc1035 [4.1.4. Message compression]
        int roff = in.readerOffset();
        int woff = in.writerOffset();
        in.resetOffsets().writerOffset(offset + length).readerOffset(offset);
        try {
            if (type == DnsRecordType.PTR) {
                return new DefaultDnsPtrRecord(
                        name, dnsClass, timeToLive, decodeName0(in));
            }
            if (type == DnsRecordType.CNAME || type == DnsRecordType.NS) {
                return new DefaultDnsRawRecord(name, type, dnsClass, timeToLive,
                                               DnsCodecUtil.decompressDomainName(in));
            }
            return new DefaultDnsRawRecord(
                    name, type, dnsClass, timeToLive, in.copy());
        } finally {
            in.resetOffsets().writerOffset(woff).readerOffset(roff);
        }
    }

    /**
     * Retrieves a domain name given a buffer containing a DNS packet. If the
     * name contains a pointer, the position of the buffer will be set to
     * directly after the pointer's index after the name has been read.
     *
     * @param in the byte buffer containing the DNS packet
     * @return the domain name for an entry
     */
    protected String decodeName0(Buffer in) {
        return decodeName(in);
    }

    /**
     * Retrieves a domain name given a buffer containing a DNS packet. If the
     * name contains a pointer, the position of the buffer will be set to
     * directly after the pointer's index after the name has been read.
     *
     * @param in the byte buffer containing the DNS packet
     * @return the domain name for an entry
     */
    public static String decodeName(Buffer in) {
        return DnsCodecUtil.decodeDomainName(in);
    }
}
