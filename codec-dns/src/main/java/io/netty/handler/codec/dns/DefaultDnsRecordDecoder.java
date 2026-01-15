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
package io.netty.handler.codec.dns;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.CorruptedFrameException;

/**
 * The default {@link DnsRecordDecoder} implementation.
 *
 * @see DefaultDnsRecordEncoder
 */
public class DefaultDnsRecordDecoder implements DnsRecordDecoder {

    private final boolean legacyMode;
    static final String ROOT = ".";

    /**
     * Creates a new instance in legacy mode.
     *
     * @deprecated Use {@link #DefaultDnsRecordDecoder(boolean)} with {@code legacyMode} set to {@code false} to
     *             marshal supported DNS records into structured types; unsupported types remain
     *             {@link DefaultDnsRawRecord}.
     */
    @Deprecated
    protected DefaultDnsRecordDecoder() {
        this(true);
    }

    /**
     * Creates a new instance.
     *
     * @param legacyMode {@code true} to use legacy =<4.2.8 decode behavior which returns
     *                   {@link DefaultDnsRawRecord} for CNAME/NS (with decompressed RDATA) and
     *                   {@link DefaultDnsPtrRecord} for PTR; other types are returned as raw records.
     *                   {@code false} returns structured records (for example {@link DnsARecord},
     *                   {@link DnsMxRecord}) with parsed fields, and uses raw records only for
     *                   unsupported types.
     */
    protected DefaultDnsRecordDecoder(boolean legacyMode) {
        this.legacyMode = legacyMode;
    }

    @Override
    public final DnsQuestion decodeQuestion(ByteBuf in) throws Exception {
        String name = decodeName(in);
        DnsRecordType type = DnsRecordType.valueOf(in.readUnsignedShort());
        int qClass = in.readUnsignedShort();
        return new DefaultDnsQuestion(name, type, qClass);
    }

    @Override
    public final <T extends DnsRecord> T decodeRecord(ByteBuf in) throws Exception {
        final int startOffset = in.readerIndex();
        final String name = decodeName(in);

        final int endOffset = in.writerIndex();
        if (endOffset - in.readerIndex() < 10) {
            // Not enough data
            in.readerIndex(startOffset);
            return null;
        }

        final DnsRecordType type = DnsRecordType.valueOf(in.readUnsignedShort());
        final int aClass = in.readUnsignedShort();
        final long ttl = in.readUnsignedInt();
        final int length = in.readUnsignedShort();
        final int offset = in.readerIndex();

        if (endOffset - offset < length) {
            // Not enough data
            in.readerIndex(startOffset);
            return null;
        }

        @SuppressWarnings("unchecked")
        T record = (T) decodeRecord(name, type, aClass, ttl, in, offset, length);
        in.readerIndex(offset + length);
        return record;
    }

    /**
     * Decodes a record from the information decoded so far by {@link #decodeRecord(ByteBuf)}.
     *
     * @param name the domain name of the record
     * @param type the type of the record
     * @param dnsClass the class of the record
     * @param timeToLive the TTL of the record
     * @param in the {@link ByteBuf} that contains the RDATA
     * @param offset the start offset of the RDATA in {@code in}
     * @param length the length of the RDATA
     *
     * @return a {@link DnsRecord}. Override this method to decode RDATA and return other record implementation.
     */
    protected DnsRecord decodeRecord(
            String name, DnsRecordType type, int dnsClass, long timeToLive,
            ByteBuf in, int offset, int length) throws Exception {

        // DNS message compression means that domain names may contain "pointers" to other positions in the packet
        // to build a full message. This means the indexes are meaningful and we need the ability to reference the
        // indexes un-obstructed, and thus we cannot use a slice here.
        // See https://www.ietf.org/rfc/rfc1035 [4.1.4. Message compression]
        if (legacyMode) {
            return decodeRecordLegacy(name, type, dnsClass, timeToLive, in, offset, length);
        }

        if (type == DnsRecordType.NS) {
            return decodeNsRecord(name, dnsClass, timeToLive, in, offset, length);
        } else if (type == DnsRecordType.CNAME) {
            return decodeCnameRecord(name, dnsClass, timeToLive, in, offset, length);
        } else if (type == DnsRecordType.PTR) {
            return decodePtrRecord(name, dnsClass, timeToLive, in, offset, length);
        } else if (type == DnsRecordType.MX) {
            return decodeMxRecord(name, dnsClass, timeToLive, in, offset, length);
        }

        return new DefaultDnsRawRecord(
                name, type, dnsClass, timeToLive, in.retainedDuplicate().setIndex(offset, offset + length));
    }

    private DnsRecord decodePtrRecord(String name, int dnsClass, long timeToLive,
                                      ByteBuf in, int offset, int length) {
        return new DefaultDnsPtrRecord(
                name, dnsClass, timeToLive, decodeName(in.duplicate().setIndex(offset, offset + length)));
    }

    private DnsRecord decodeCnameRecord(String name, int dnsClass, long timeToLive,
                                        ByteBuf in, int offset, int length) {
        String canonicalName = decodeName(in.duplicate().setIndex(offset, offset + length));
        return new DefaultDnsCnameRecord(name, dnsClass, timeToLive, canonicalName);
    }

    private DnsRecord decodeNsRecord(String name, int dnsClass, long timeToLive,
                                     ByteBuf in, int offset, int length) {
        String nameServer = decodeName(in.duplicate().setIndex(offset, offset + length));
        return new DefaultDnsNsRecord(name, dnsClass, timeToLive, nameServer);
    }

    private DnsRecord decodeMxRecord(String name, int dnsClass, long timeToLive,
                                     ByteBuf in, int offset, int length) {
        if (length < 3) {
            throw new CorruptedFrameException("MX record RDATA is too short: " + length);
        }
        int pref = in.getUnsignedShort(offset);
        String exchangeName = decodeName(in.duplicate().setIndex(offset + 2, offset + length));
        return new DefaultDnsMxRecord(name, dnsClass, timeToLive, pref, exchangeName);
    }

    /**
     * Legacy decode logic matching Netty 4.2.8 behavior
     */
    DnsRecord decodeRecordLegacy(
            String name, DnsRecordType type, int dnsClass, long timeToLive,
            ByteBuf in, int offset, int length) throws Exception {
        if (type == DnsRecordType.PTR) {
            return new DefaultDnsPtrRecord(
                    name, dnsClass, timeToLive, decodeName0(in.duplicate().setIndex(offset, offset + length)));
        }
        if (type == DnsRecordType.CNAME || type == DnsRecordType.NS) {
            return new DefaultDnsRawRecord(name, type, dnsClass, timeToLive,
                                           DnsCodecUtil.decompressDomainName(
                                                   in.duplicate().setIndex(offset, offset + length)));
        }
        if (type ==  DnsRecordType.MX) {
            // MX RDATA: 16-bit preference + exchange (domain name, possibly compressed)
            if (length < 3) {
                throw new CorruptedFrameException("MX record RDATA is too short: " + length);
            }
            final int pref = in.getUnsignedShort(offset);
            ByteBuf exchange = null;
            try {
                exchange = DnsCodecUtil.decompressDomainName(
                        in.duplicate().setIndex(offset + 2, offset + length));

                // Build decompressed RDATA = [preference][expanded exchange name]
                final ByteBuf out = in.alloc().buffer(2 + exchange.readableBytes());
                out.writeShort(pref);
                out.writeBytes(exchange);

                return new DefaultDnsRawRecord(name, type, dnsClass, timeToLive, out);
            } finally {
                if (exchange != null) {
                    exchange.release();
                }
            }
        }

        return new DefaultDnsRawRecord(
                name, type, dnsClass, timeToLive, in.retainedDuplicate().setIndex(offset, offset + length));
    }

    /**
     * Retrieves a domain name given a buffer containing a DNS packet. If the
     * name contains a pointer, the position of the buffer will be set to
     * directly after the pointer's index after the name has been read.
     *
     * @param in the byte buffer containing the DNS packet
     * @return the domain name for an entry
     */
    protected String decodeName0(ByteBuf in) {
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
    public static String decodeName(ByteBuf in) {
        return DnsCodecUtil.decodeDomainName(in);
    }
}
