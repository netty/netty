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
import io.netty.util.CharsetUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        }
        if (type == DnsRecordType.CNAME) {
            return decodeCnameRecord(name, dnsClass, timeToLive, in, offset, length);
        }
        if (type == DnsRecordType.SOA) {
            return decodeSoaRecord(name, dnsClass, timeToLive, in, offset, length);
        }
        if (type == DnsRecordType.PTR) {
            return decodePtrRecord(name, dnsClass, timeToLive, in, offset, length);
        }
        if (type == DnsRecordType.MX) {
            return decodeMxRecord(name, dnsClass, timeToLive, in, offset, length);
        }
        if (type == DnsRecordType.A) {
            return decodeARecord(name, dnsClass, timeToLive, in, offset, length);
        }
        if (type == DnsRecordType.AAAA) {
            return decodeAaaaRecord(name, dnsClass, timeToLive, in, offset, length);
        }
        if (type == DnsRecordType.TXT) {
            return decodeTxtRecord(name, dnsClass, timeToLive, in, offset, length);
        }
        if (type == DnsRecordType.CAA) {
            return decodeCaaRecord(name, dnsClass, timeToLive, in, offset, length);
        }
        if (type == DnsRecordType.CERT) {
            return decodeCertRecord(name, dnsClass, timeToLive, in, offset, length);
        }
        if (type == DnsRecordType.DNSKEY) {
            return decodeDnskeyRecord(name, dnsClass, timeToLive, in, offset, length);
        }
        if (type == DnsRecordType.DS) {
            return decodeDsRecord(name, dnsClass, timeToLive, in, offset, length);
        }
        if (type == DnsRecordType.HTTPS) {
            return decodeHttpsRecord(name, dnsClass, timeToLive, in, offset, length);
        }
        if (type == DnsRecordType.LOC) {
            return decodeLocRecord(name, dnsClass, timeToLive, in, offset, length);
        }
        if (type == DnsRecordType.NAPTR) {
            return decodeNaptrRecord(name, dnsClass, timeToLive, in, offset, length);
        }
        if (type == DnsRecordType.SMIMEA) {
            return decodeSmimeaRecord(name, dnsClass, timeToLive, in, offset, length);
        }
        if (type == DnsRecordType.SRV) {
            return decodeSrvRecord(name, dnsClass, timeToLive, in, offset, length);
        }
        if (type == DnsRecordType.SSHFP) {
            return decodeSshfpRecord(name, dnsClass, timeToLive, in, offset, length);
        }
        if (type == DnsRecordType.SVCB) {
            return decodeSvcbRecord(name, dnsClass, timeToLive, in, offset, length);
        }
        if (type == DnsRecordType.TLSA) {
            return decodeTlsaRecord(name, dnsClass, timeToLive, in, offset, length);
        }
        if (type == DnsRecordType.URI) {
            return decodeUriRecord(name, dnsClass, timeToLive, in, offset, length);
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

    private DnsRecord decodeSoaRecord(String name, int dnsClass, long timeToLive,
                                      ByteBuf in, int offset, int length) {
        if (length < 22) {
            throw new CorruptedFrameException("SOA record RDATA is too short: " + length);
        }
        ByteBuf data = in.duplicate().setIndex(offset, offset + length);
        String mname = decodeName(data);
        String rname = decodeName(data);
        if (data.readableBytes() < 20) {
            throw new CorruptedFrameException("SOA record RDATA is truncated");
        }
        long serial = data.readUnsignedInt();
        long refresh = data.readUnsignedInt();
        long retry = data.readUnsignedInt();
        long expire = data.readUnsignedInt();
        long minimum = data.readUnsignedInt();
        return new DefaultDnsSoaRecord(name, dnsClass, timeToLive,
                mname, rname, serial, refresh, retry, expire, minimum);
    }

    //TODO come back to this and see if byte array is the right structure for ip
    //same for v6
    private DnsRecord decodeARecord(String name, int dnsClass, long timeToLive,
                                    ByteBuf in, int offset, int length) {
        if (length != 4) {
            throw new CorruptedFrameException("A record RDATA length is invalid: " + length);
        }
        byte[] address = new byte[4];
        in.getBytes(offset, address);
        return new DefaultDnsARecord(name, dnsClass, timeToLive, address);
    }

    private DnsRecord decodeAaaaRecord(String name, int dnsClass, long timeToLive,
                                       ByteBuf in, int offset, int length) {
        if (length != 16) {
            throw new CorruptedFrameException("AAAA record RDATA length is invalid: " + length);
        }
        byte[] address = new byte[16];
        in.getBytes(offset, address);
        return new DefaultDnsAaaaRecord(name, dnsClass, timeToLive, address);
    }

    private DnsRecord decodeTxtRecord(String name, int dnsClass, long timeToLive,
                                      ByteBuf in, int offset, int length) {
        ByteBuf data = in.duplicate().setIndex(offset, offset + length);
        List<byte[]> content = new ArrayList<byte[]>();
        while (data.isReadable()) {
            int len = data.readUnsignedByte();
            if (!data.isReadable(len)) {
                throw new CorruptedFrameException("TXT record RDATA is too short");
            }
            byte[] entry = new byte[len];
            data.readBytes(entry);
            content.add(entry);
        }
        return new DefaultDnsTxtRecord(name, dnsClass, timeToLive, content);
    }

    private DnsRecord decodeCaaRecord(String name, int dnsClass, long timeToLive,
                                      ByteBuf in, int offset, int length) {
        if (length < 2) {
            throw new CorruptedFrameException("CAA record RDATA is too short: " + length);
        }
        int flags = in.getUnsignedByte(offset);
        int tagLength = in.getUnsignedByte(offset + 1);
        if (length < 2 + tagLength) {
            throw new CorruptedFrameException("CAA record tag is truncated");
        }
        int tagOffset = offset + 2;
        //RFC 6844 specifies US ASCII as does the update in RFC 8659
        String tag = in.toString(tagOffset, tagLength, CharsetUtil.US_ASCII);
        int valueOffset = tagOffset + tagLength;
        int valueLength = length - 2 - tagLength;
        byte[] value = new byte[valueLength];
        in.getBytes(valueOffset, value);
        return new DefaultDnsCaaRecord(name, dnsClass, timeToLive, flags, tag, value);
    }

    private DnsRecord decodeCertRecord(String name, int dnsClass, long timeToLive,
                                       ByteBuf in, int offset, int length) {
        if (length < 5) {
            throw new CorruptedFrameException("CERT record RDATA is too short: " + length);
        }
        int type = in.getUnsignedShort(offset);
        int keyTag = in.getUnsignedShort(offset + 2);
        int algorithm = in.getUnsignedByte(offset + 4);
        int certLength = length - 5;
        byte[] cert = new byte[certLength];
        in.getBytes(offset + 5, cert);
        return new DefaultDnsCertRecord(name, dnsClass, timeToLive, type, keyTag, algorithm, cert);
    }

    private DnsRecord decodeDnskeyRecord(String name, int dnsClass, long timeToLive,
                                         ByteBuf in, int offset, int length) {
        if (length < 4) {
            throw new CorruptedFrameException("DNSKEY record RDATA is too short: " + length);
        }
        int flags = in.getUnsignedShort(offset);
        int protocol = in.getUnsignedByte(offset + 2);
        int algorithm = in.getUnsignedByte(offset + 3);
        int keyLength = length - 4;
        byte[] key = new byte[keyLength];
        in.getBytes(offset + 4, key);
        return new DefaultDnsDnskeyRecord(name, dnsClass, timeToLive, flags, protocol, algorithm, key);
    }

    private DnsRecord decodeDsRecord(String name, int dnsClass, long timeToLive,
                                     ByteBuf in, int offset, int length) {
        if (length < 4) {
            throw new CorruptedFrameException("DS record RDATA is too short: " + length);
        }
        int keyTag = in.getUnsignedShort(offset);
        int algorithm = in.getUnsignedByte(offset + 2);
        int digestType = in.getUnsignedByte(offset + 3);
        int digestLength = length - 4;
        byte[] digest = new byte[digestLength];
        in.getBytes(offset + 4, digest);
        return new DefaultDnsDsRecord(name, dnsClass, timeToLive, keyTag, algorithm, digestType, digest);
    }

    private DnsRecord decodeLocRecord(String name, int dnsClass, long timeToLive,
                                      ByteBuf in, int offset, int length) {
        if (length != 16) {
            throw new CorruptedFrameException("LOC record RDATA length is invalid: " + length);
        }
        int version = in.getUnsignedByte(offset);
        int size = in.getUnsignedByte(offset + 1);
        int horizontalPrecision = in.getUnsignedByte(offset + 2);
        int verticalPrecision = in.getUnsignedByte(offset + 3);
        long latitude = in.getUnsignedInt(offset + 4);
        long longitude = in.getUnsignedInt(offset + 8);
        long altitude = in.getUnsignedInt(offset + 12);
        return new DefaultDnsLocRecord(name, dnsClass, timeToLive,
                version, size, horizontalPrecision, verticalPrecision, latitude, longitude, altitude);
    }

    private DnsRecord decodeNaptrRecord(String name, int dnsClass, long timeToLive,
                                        ByteBuf in, int offset, int length) {
        if (length < 5) {
            throw new CorruptedFrameException("NAPTR record RDATA is too short: " + length);
        }
        ByteBuf data = in.duplicate().setIndex(offset, offset + length);
        int order = data.readUnsignedShort();
        int preference = data.readUnsignedShort();
        byte[] flags = decodeCharacterString(data);
        byte[] services = decodeCharacterString(data);
        byte[] regexp = decodeCharacterString(data);
        String replacement = decodeName(data);
        return new DefaultDnsNaptrRecord(name, dnsClass, timeToLive,
                order, preference, flags, services, regexp, replacement);
    }

    private DnsRecord decodeSmimeaRecord(String name, int dnsClass, long timeToLive,
                                         ByteBuf in, int offset, int length) {
        if (length < 3) {
            throw new CorruptedFrameException("SMIMEA record RDATA is too short: " + length);
        }
        int usage = in.getUnsignedByte(offset);
        int selector = in.getUnsignedByte(offset + 1);
        int matchingType = in.getUnsignedByte(offset + 2);
        int dataLength = length - 3;
        byte[] data = new byte[dataLength];
        in.getBytes(offset + 3, data);
        return new DefaultDnsSmimeaRecord(name, dnsClass, timeToLive, usage, selector, matchingType, data);
    }

    private DnsRecord decodeSrvRecord(String name, int dnsClass, long timeToLive,
                                      ByteBuf in, int offset, int length) {
        if (length < 7) {
            throw new CorruptedFrameException("SRV record RDATA is too short: " + length);
        }
        int priority = in.getUnsignedShort(offset);
        int weight = in.getUnsignedShort(offset + 2);
        int port = in.getUnsignedShort(offset + 4);
        String target = decodeName(in.duplicate().setIndex(offset + 6, offset + length));
        return new DefaultDnsSrvRecord(name, dnsClass, timeToLive, priority, weight, port, target);
    }

    private DnsRecord decodeSshfpRecord(String name, int dnsClass, long timeToLive,
                                        ByteBuf in, int offset, int length) {
        if (length < 2) {
            throw new CorruptedFrameException("SSHFP record RDATA is too short: " + length);
        }
        int algorithm = in.getUnsignedByte(offset);
        int fingerprintType = in.getUnsignedByte(offset + 1);
        int dataLength = length - 2;
        byte[] fingerprint = new byte[dataLength];
        in.getBytes(offset + 2, fingerprint);
        return new DefaultDnsSshfpRecord(name, dnsClass, timeToLive, algorithm, fingerprintType, fingerprint);
    }

    private DnsRecord decodeSvcbRecord(String name, int dnsClass, long timeToLive,
                                       ByteBuf in, int offset, int length) {
        return decodeSvcbRecord0(name, dnsClass, timeToLive, in, offset, length, true);
    }

    private DnsRecord decodeHttpsRecord(String name, int dnsClass, long timeToLive,
                                        ByteBuf in, int offset, int length) {
        return decodeSvcbRecord0(name, dnsClass, timeToLive, in, offset, length, false);
    }

    private DnsRecord decodeTlsaRecord(String name, int dnsClass, long timeToLive,
                                       ByteBuf in, int offset, int length) {
        if (length < 3) {
            throw new CorruptedFrameException("TLSA record RDATA is too short: " + length);
        }
        int usage = in.getUnsignedByte(offset);
        int selector = in.getUnsignedByte(offset + 1);
        int matchingType = in.getUnsignedByte(offset + 2);
        int dataLength = length - 3;
        byte[] data = new byte[dataLength];
        in.getBytes(offset + 3, data);
        return new DefaultDnsTlsaRecord(name, dnsClass, timeToLive, usage, selector, matchingType, data);
    }

    private DnsRecord decodeUriRecord(String name, int dnsClass, long timeToLive,
                                      ByteBuf in, int offset, int length) {
        if (length < 4) {
            throw new CorruptedFrameException("URI record RDATA is too short: " + length);
        }
        int priority = in.getUnsignedShort(offset);
        int weight = in.getUnsignedShort(offset + 2);
        int targetLength = length - 4;
        String target = in.toString(offset + 4, targetLength, CharsetUtil.US_ASCII);
        return new DefaultDnsUriRecord(name, dnsClass, timeToLive, priority, weight, target);
    }

    private DnsRecord decodeSvcbRecord0(String name, int dnsClass, long timeToLive,
                                        ByteBuf in, int offset, int length, boolean svcb) {
        if (length < 3) {
            throw new CorruptedFrameException((svcb ? "SVCB" : "HTTPS") + " record RDATA is too short: " + length);
        }
        ByteBuf data = in.duplicate().setIndex(offset, offset + length);
        int priority = data.readUnsignedShort();
        String targetName = decodeName(data);
        Map<Integer, byte[]> params = new LinkedHashMap<Integer, byte[]>();
        while (data.isReadable()) {
            if (data.readableBytes() < 4) {
                throw new CorruptedFrameException((svcb ? "SVCB" : "HTTPS") + " parameter is truncated");
            }
            int key = data.readUnsignedShort();
            int valueLength = data.readUnsignedShort();
            if (data.readableBytes() < valueLength) {
                throw new CorruptedFrameException((svcb ? "SVCB" : "HTTPS") + " parameter value is truncated");
            }
            byte[] value = new byte[valueLength];
            data.readBytes(value);
            params.put(key, value);
        }
        if (svcb) {
            return new DefaultDnsSvcbRecord(name, dnsClass, timeToLive, priority, targetName, params);
        }
        return new DefaultDnsHttpsRecord(name, dnsClass, timeToLive, priority, targetName, params);
    }

    /**
     * Decodes a DNS character-string as defined in RFC 1035 Section 3.3.
     * <p>
     * Per RFC 1035, a character-string is "a single length octet followed by that number
     * of characters" and is "treated as binary information". The RFC does not specify a
     * character encoding; it predates the widespread adoption of Unicode and was designed
     * for 8-bit byte sequences.
     * <p>
     * Different DNS record types that use character-strings may have different expectations:
     * <ul>
     *   <li>TXT records (RFC 1035) - explicitly binary, no encoding specified</li>
     *   <li>NAPTR records (RFC 3403) - fields like flags and services are defined with
     *       ASCII-compatible values, but the regexp field could theoretically contain
     *       any bytes</li>
     *   <li>Other records may define their own conventions</li>
     * </ul>
     * <p>
     * This method returns the raw bytes, leaving the choice of character encoding (if any)
     * to the caller. For convenience, record interfaces may provide methods that interpret
     * the bytes as US-ASCII strings where appropriate.
     *
     * @param in the buffer to read from
     * @return the character-string data as a byte array
     * @throws CorruptedFrameException if the data is truncated
     */
    private static byte[] decodeCharacterString(ByteBuf in) {
        if (!in.isReadable()) {
            throw new CorruptedFrameException("Character string is truncated");
        }
        int length = in.readUnsignedByte();
        if (!in.isReadable(length)) {
            throw new CorruptedFrameException("Character string is truncated");
        }
        byte[] data = new byte[length];
        in.readBytes(data);
        return data;
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
            ByteBuf decompressed = DnsCodecUtil.decompressDomainName(
                    in.duplicate().setIndex(offset, offset + length));
            try {
                DnsRecord record = new DefaultDnsRawRecord(name, type, dnsClass, timeToLive, decompressed);
                decompressed = null;
                return record;
            } finally {
                if (decompressed != null) {
                    decompressed.release();
                }
            }
        }
        if (type ==  DnsRecordType.MX) {
            // MX RDATA: 16-bit preference + exchange (domain name, possibly compressed)
            if (length < 3) {
                throw new CorruptedFrameException("MX record RDATA is too short: " + length);
            }
            final int pref = in.getUnsignedShort(offset);
            ByteBuf exchange = null;
            ByteBuf out = null;
            try {
                exchange = DnsCodecUtil.decompressDomainName(
                        in.duplicate().setIndex(offset + 2, offset + length));

                // Build decompressed RDATA = [preference][expanded exchange name]
                out = in.alloc().buffer(2 + exchange.readableBytes());
                out.writeShort(pref);
                out.writeBytes(exchange);

                DnsRecord record = new DefaultDnsRawRecord(name, type, dnsClass, timeToLive, out);
                out = null;
                return record;
            } finally {
                if (exchange != null) {
                    exchange.release();
                }
                if (out != null) {
                    out.release();
                }
            }
        }

        ByteBuf content = in.retainedDuplicate();
        try {
            content.setIndex(offset, offset + length);
            DnsRecord record = new DefaultDnsRawRecord(name, type, dnsClass, timeToLive, content);
            content = null;
            return record;
        } finally {
            if (content != null) {
                content.release();
            }
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
