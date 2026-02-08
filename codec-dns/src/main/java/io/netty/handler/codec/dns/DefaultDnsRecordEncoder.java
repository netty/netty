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
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.util.CharsetUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The default {@link DnsRecordEncoder} implementation.
 *
 * @see DefaultDnsRecordDecoder
 */
public class DefaultDnsRecordEncoder implements DnsRecordEncoder {
    private static final int PREFIX_MASK = Byte.SIZE - 1;

    /**
     * Creates a new instance.
     */
    protected DefaultDnsRecordEncoder() { }

    @Override
    public final void encodeQuestion(DnsQuestion question, ByteBuf out) throws Exception {
        encodeName(question.name(), out);
        out.writeShort(question.type().intValue());
        out.writeShort(question.dnsClass());
    }

    private static final Class<?>[] SUPPORTED_MESSAGES = new Class<?>[] {
            DnsQuestion.class, DnsPtrRecord.class, DnsARecord.class, DnsAaaaRecord.class,
            DnsCnameRecord.class, DnsNsRecord.class, DnsMxRecord.class, DnsSoaRecord.class, DnsTxtRecord.class,
            DnsCaaRecord.class, DnsCertRecord.class, DnsDnskeyRecord.class, DnsDsRecord.class,
            DnsHttpsRecord.class, DnsLocRecord.class, DnsNaptrRecord.class, DnsSmimeaRecord.class,
            DnsSrvRecord.class, DnsSshfpRecord.class, DnsSvcbRecord.class, DnsTlsaRecord.class,
            DnsUriRecord.class, DnsOptEcsRecord.class, DnsOptPseudoRecord.class, DnsRawRecord.class };

    @Override
    public void encodeRecord(DnsRecord record, ByteBuf out) throws Exception {
        if (record instanceof DnsQuestion) {
            encodeQuestion((DnsQuestion) record, out);
        } else if (record instanceof DnsPtrRecord) {
            encodePtrRecord((DnsPtrRecord) record, out);
        } else if (record instanceof DnsARecord) {
            encodeARecord((DnsARecord) record, out);
        } else if (record instanceof DnsAaaaRecord) {
            encodeAaaaRecord((DnsAaaaRecord) record, out);
        } else if (record instanceof DnsCnameRecord) {
            encodeCnameRecord((DnsCnameRecord) record, out);
        } else if (record instanceof DnsNsRecord) {
            encodeNsRecord((DnsNsRecord) record, out);
        } else if (record instanceof DnsMxRecord) {
            encodeMxRecord((DnsMxRecord) record, out);
        } else if (record instanceof DnsSoaRecord) {
            encodeSoaRecord((DnsSoaRecord) record, out);
        } else if (record instanceof DnsTxtRecord) {
            encodeTxtRecord((DnsTxtRecord) record, out);
        } else if (record instanceof DnsCaaRecord) {
            encodeCaaRecord((DnsCaaRecord) record, out);
        } else if (record instanceof DnsCertRecord) {
            encodeCertRecord((DnsCertRecord) record, out);
        } else if (record instanceof DnsDnskeyRecord) {
            encodeDnskeyRecord((DnsDnskeyRecord) record, out);
        } else if (record instanceof DnsDsRecord) {
            encodeDsRecord((DnsDsRecord) record, out);
        } else if (record instanceof DnsHttpsRecord) {
            encodeHttpsRecord((DnsHttpsRecord) record, out);
        } else if (record instanceof DnsLocRecord) {
            encodeLocRecord((DnsLocRecord) record, out);
        } else if (record instanceof DnsNaptrRecord) {
            encodeNaptrRecord((DnsNaptrRecord) record, out);
        } else if (record instanceof DnsSmimeaRecord) {
            encodeSmimeaRecord((DnsSmimeaRecord) record, out);
        } else if (record instanceof DnsSrvRecord) {
            encodeSrvRecord((DnsSrvRecord) record, out);
        } else if (record instanceof DnsSshfpRecord) {
            encodeSshfpRecord((DnsSshfpRecord) record, out);
        } else if (record instanceof DnsSvcbRecord) {
            encodeSvcbRecord((DnsSvcbRecord) record, out);
        } else if (record instanceof DnsTlsaRecord) {
            encodeTlsaRecord((DnsTlsaRecord) record, out);
        } else if (record instanceof DnsUriRecord) {
            encodeUriRecord((DnsUriRecord) record, out);
        } else if (record instanceof DnsOptEcsRecord) {
            encodeOptEcsRecord((DnsOptEcsRecord) record, out);
        } else if (record instanceof DnsOptPseudoRecord) {
            encodeOptPseudoRecord((DnsOptPseudoRecord) record, out);
        } else if (record instanceof DnsRawRecord) {
            encodeRawRecord((DnsRawRecord) record, out);
        } else {
            throw new UnsupportedMessageTypeException(record, SUPPORTED_MESSAGES);
        }
    }

    private void encodeRecord0(DnsRecord record, ByteBuf out) throws Exception {
        encodeName(record.name(), out);
        out.writeShort(record.type().intValue());
        out.writeShort(record.dnsClass());
        out.writeInt((int) record.timeToLive());
    }

    private void encodePtrRecord(DnsPtrRecord record, ByteBuf out) throws Exception {
        encodeRecord0(record, out);
        int writerIndex = out.writerIndex();
        // Skip 2 bytes as these will be used to encode the rdataLen after we know how many bytes were written.
        // See https://www.rfc-editor.org/rfc/rfc1035.html#section-3.2.1
        out.writerIndex(writerIndex + 2);
        encodeName(record.hostname(), out);
        int rdLength = out.writerIndex() - (writerIndex + 2);
        out.setShort(writerIndex, rdLength);
    }

    private void encodeARecord(DnsARecord record, ByteBuf out) throws Exception {
        encodeRecord0(record, out);
        byte[] address = record.address();
        if (address.length != 4) {
            throw new IllegalArgumentException("A record address length is invalid: " + address.length);
        }
        out.writeShort(4);
        out.writeBytes(address);
    }

    private void encodeAaaaRecord(DnsAaaaRecord record, ByteBuf out) throws Exception {
        encodeRecord0(record, out);
        byte[] address = record.address();
        if (address.length != 16) {
            throw new IllegalArgumentException("AAAA record address length is invalid: " + address.length);
        }
        out.writeShort(16);
        out.writeBytes(address);
    }

    private void encodeCnameRecord(DnsCnameRecord record, ByteBuf out) throws Exception {
        encodeNameRecord(record.canonicalName(), record, out);
    }

    private void encodeNsRecord(DnsNsRecord record, ByteBuf out) throws Exception {
        encodeNameRecord(record.nameServer(), record, out);
    }

    private void encodeMxRecord(DnsMxRecord record, ByteBuf out) throws Exception {
        encodeRecord0(record, out);
        int writerIndex = out.writerIndex();
        out.writerIndex(writerIndex + 2);
        out.writeShort(record.preference());
        encodeName(record.exchange(), out);
        int rdLength = out.writerIndex() - (writerIndex + 2);
        out.setShort(writerIndex, rdLength);
    }

    private void encodeSoaRecord(DnsSoaRecord record, ByteBuf out) throws Exception {
        encodeRecord0(record, out);
        int writerIndex = out.writerIndex();
        out.writerIndex(writerIndex + 2);
        encodeName(record.mname(), out);
        encodeName(record.rname(), out);
        out.writeInt((int) record.serial());
        out.writeInt((int) record.refresh());
        out.writeInt((int) record.retry());
        out.writeInt((int) record.expire());
        out.writeInt((int) record.minimum());
        int rdLength = out.writerIndex() - (writerIndex + 2);
        out.setShort(writerIndex, rdLength);
    }

    private void encodeTxtRecord(DnsTxtRecord record, ByteBuf out) throws Exception {
        encodeRecord0(record, out);
        int writerIndex = out.writerIndex();
        out.writerIndex(writerIndex + 2);
        for (byte[] entry : record.content()) {
            if (entry.length > 0xff) {
                throw new IllegalArgumentException("TXT entry is too long: " + entry.length);
            }
            out.writeByte(entry.length);
            out.writeBytes(entry);
        }
        int rdLength = out.writerIndex() - (writerIndex + 2);
        out.setShort(writerIndex, rdLength);
    }

    private void encodeCaaRecord(DnsCaaRecord record, ByteBuf out) throws Exception {
        encodeRecord0(record, out);
        int writerIndex = out.writerIndex();
        out.writerIndex(writerIndex + 2);
        byte[] tagBytes = record.tag().getBytes(CharsetUtil.US_ASCII);
        if (tagBytes.length > 0xff) {
            throw new IllegalArgumentException("CAA tag is too long: " + tagBytes.length);
        }
        out.writeByte(record.flags());
        out.writeByte(tagBytes.length);
        out.writeBytes(tagBytes);
        out.writeBytes(record.value());
        int rdLength = out.writerIndex() - (writerIndex + 2);
        out.setShort(writerIndex, rdLength);
    }

    private void encodeCertRecord(DnsCertRecord record, ByteBuf out) throws Exception {
        encodeRecord0(record, out);
        int writerIndex = out.writerIndex();
        out.writerIndex(writerIndex + 2);
        out.writeShort(record.certificateType());
        out.writeShort(record.keyTag());
        out.writeByte(record.algorithm());
        out.writeBytes(record.certificate());
        int rdLength = out.writerIndex() - (writerIndex + 2);
        out.setShort(writerIndex, rdLength);
    }

    private void encodeDnskeyRecord(DnsDnskeyRecord record, ByteBuf out) throws Exception {
        encodeRecord0(record, out);
        int writerIndex = out.writerIndex();
        out.writerIndex(writerIndex + 2);
        out.writeShort(record.flags());
        out.writeByte(record.protocol());
        out.writeByte(record.algorithm());
        out.writeBytes(record.publicKey());
        int rdLength = out.writerIndex() - (writerIndex + 2);
        out.setShort(writerIndex, rdLength);
    }

    private void encodeDsRecord(DnsDsRecord record, ByteBuf out) throws Exception {
        encodeRecord0(record, out);
        int writerIndex = out.writerIndex();
        out.writerIndex(writerIndex + 2);
        out.writeShort(record.keyTag());
        out.writeByte(record.algorithm());
        out.writeByte(record.digestType());
        out.writeBytes(record.digest());
        int rdLength = out.writerIndex() - (writerIndex + 2);
        out.setShort(writerIndex, rdLength);
    }

    private void encodeLocRecord(DnsLocRecord record, ByteBuf out) throws Exception {
        encodeRecord0(record, out);
        out.writeShort(16);
        out.writeByte(record.version());
        out.writeByte(record.size());
        out.writeByte(record.horizontalPrecision());
        out.writeByte(record.verticalPrecision());
        out.writeInt((int) record.latitude());
        out.writeInt((int) record.longitude());
        out.writeInt((int) record.altitude());
    }

    private void encodeNaptrRecord(DnsNaptrRecord record, ByteBuf out) throws Exception {
        encodeRecord0(record, out);
        int writerIndex = out.writerIndex();
        out.writerIndex(writerIndex + 2);
        out.writeShort(record.order());
        out.writeShort(record.preference());
        writeCharacterString(record.flags(), out);
        writeCharacterString(record.services(), out);
        writeCharacterString(record.regexp(), out);
        encodeName(record.replacement(), out);
        int rdLength = out.writerIndex() - (writerIndex + 2);
        out.setShort(writerIndex, rdLength);
    }

    /**
     * Writes a DNS character-string as defined in RFC 1035 Section 3.3.
     * <p>
     * A character-string is encoded as a single length octet followed by that number of bytes.
     * Per RFC 1035, the data is "treated as binary information" with no specified character
     * encoding. The maximum length is 255 bytes.
     * <p>
     * See {@link DefaultDnsRecordDecoder#decodeCharacterString} for more details on encoding
     * considerations.
     *
     * @param data the character-string data as a byte array
     * @param out the buffer to write to
     * @throws IllegalArgumentException if the data exceeds 255 bytes
     */
    private static void writeCharacterString(byte[] data, ByteBuf out) {
        if (data.length > 0xff) {
            throw new IllegalArgumentException("Character string is too long: " + data.length);
        }
        out.writeByte(data.length);
        out.writeBytes(data);
    }

    private void encodeSmimeaRecord(DnsSmimeaRecord record, ByteBuf out) throws Exception {
        encodeRecord0(record, out);
        int writerIndex = out.writerIndex();
        out.writerIndex(writerIndex + 2);
        out.writeByte(record.usage());
        out.writeByte(record.selector());
        out.writeByte(record.matchingType());
        out.writeBytes(record.associationData());
        int rdLength = out.writerIndex() - (writerIndex + 2);
        out.setShort(writerIndex, rdLength);
    }

    private void encodeSrvRecord(DnsSrvRecord record, ByteBuf out) throws Exception {
        encodeRecord0(record, out);
        int writerIndex = out.writerIndex();
        out.writerIndex(writerIndex + 2);
        out.writeShort(record.priority());
        out.writeShort(record.weight());
        out.writeShort(record.port());
        encodeName(record.target(), out);
        int rdLength = out.writerIndex() - (writerIndex + 2);
        out.setShort(writerIndex, rdLength);
    }

    private void encodeSshfpRecord(DnsSshfpRecord record, ByteBuf out) throws Exception {
        encodeRecord0(record, out);
        int writerIndex = out.writerIndex();
        out.writerIndex(writerIndex + 2);
        out.writeByte(record.algorithm());
        out.writeByte(record.fingerprintType());
        out.writeBytes(record.fingerprint());
        int rdLength = out.writerIndex() - (writerIndex + 2);
        out.setShort(writerIndex, rdLength);
    }

    private void encodeSvcbRecord(DnsSvcbRecord record, ByteBuf out) throws Exception {
        encodeSvcbRecord0(record.priority(), record.targetName(), record.parameters(), record, out);
    }

    private void encodeHttpsRecord(DnsHttpsRecord record, ByteBuf out) throws Exception {
        encodeSvcbRecord0(record.priority(), record.targetName(), record.parameters(), record, out);
    }

    private void encodeTlsaRecord(DnsTlsaRecord record, ByteBuf out) throws Exception {
        encodeRecord0(record, out);
        int writerIndex = out.writerIndex();
        out.writerIndex(writerIndex + 2);
        out.writeByte(record.usage());
        out.writeByte(record.selector());
        out.writeByte(record.matchingType());
        out.writeBytes(record.associationData());
        int rdLength = out.writerIndex() - (writerIndex + 2);
        out.setShort(writerIndex, rdLength);
    }

    private void encodeUriRecord(DnsUriRecord record, ByteBuf out) throws Exception {
        encodeRecord0(record, out);
        int writerIndex = out.writerIndex();
        out.writerIndex(writerIndex + 2);
        out.writeShort(record.priority());
        out.writeShort(record.weight());
        byte[] targetBytes = record.target().getBytes(CharsetUtil.US_ASCII);
        out.writeBytes(targetBytes);
        int rdLength = out.writerIndex() - (writerIndex + 2);
        out.setShort(writerIndex, rdLength);
    }

    private void encodeNameRecord(String name, DnsRecord record, ByteBuf out) throws Exception {
        encodeRecord0(record, out);
        int writerIndex = out.writerIndex();
        out.writerIndex(writerIndex + 2);
        encodeName(name, out);
        int rdLength = out.writerIndex() - (writerIndex + 2);
        out.setShort(writerIndex, rdLength);
    }

    private void encodeSvcbRecord0(int priority, String targetName, Map<Integer, byte[]> parameters,
                                   DnsRecord record, ByteBuf out) throws Exception {
        encodeRecord0(record, out);
        int writerIndex = out.writerIndex();
        out.writerIndex(writerIndex + 2);
        out.writeShort(priority);
        encodeName(targetName, out);
        List<Integer> keys = new ArrayList<Integer>(parameters.keySet());
        Collections.sort(keys);
        for (Integer key : keys) {
            byte[] value = parameters.get(key);
            int paramKey = key & 0xffff;
            if (value.length > 0xffff) {
                throw new IllegalArgumentException("SVCB parameter value is too long: " + value.length);
            }
            out.writeShort(paramKey);
            out.writeShort(value.length);
            out.writeBytes(value);
        }
        int rdLength = out.writerIndex() - (writerIndex + 2);
        out.setShort(writerIndex, rdLength);
    }

    private void encodeOptPseudoRecord(DnsOptPseudoRecord record, ByteBuf out) throws Exception {
        encodeRecord0(record, out);
        out.writeShort(0);
    }

    private void encodeOptEcsRecord(DnsOptEcsRecord record, ByteBuf out) throws Exception {
        encodeRecord0(record, out);

        int sourcePrefixLength = record.sourcePrefixLength();
        int scopePrefixLength = record.scopePrefixLength();
        int lowOrderBitsToPreserve = sourcePrefixLength & PREFIX_MASK;

        byte[] bytes = record.address();
        int addressBits = bytes.length << 3;
        if (addressBits < sourcePrefixLength || sourcePrefixLength < 0) {
            throw new IllegalArgumentException(sourcePrefixLength + ": " +
                    sourcePrefixLength + " (expected: 0 >= " + addressBits + ')');
        }

        // See https://www.iana.org/assignments/address-family-numbers/address-family-numbers.xhtml
        final short addressNumber = (short) (bytes.length == 4 ? 1 : 2);
        int payloadLength = calculateEcsAddressLength(sourcePrefixLength, lowOrderBitsToPreserve);

        int fullPayloadLength = 2 + // OPTION-CODE
                2 + // OPTION-LENGTH
                2 + // FAMILY
                1 + // SOURCE PREFIX-LENGTH
                1 + // SCOPE PREFIX-LENGTH
                payloadLength; //  ADDRESS...

        out.writeShort(fullPayloadLength);
        out.writeShort(8); // This is the defined type for ECS.

        out.writeShort(fullPayloadLength - 4); // Not include OPTION-CODE and OPTION-LENGTH
        out.writeShort(addressNumber);
        out.writeByte(sourcePrefixLength);
        out.writeByte(scopePrefixLength); // Must be 0 in queries.

        if (lowOrderBitsToPreserve > 0) {
            int bytesLength = payloadLength - 1;
            out.writeBytes(bytes, 0, bytesLength);

            // Pad the leftover of the last byte with zeros.
            out.writeByte(padWithZeros(bytes[bytesLength], lowOrderBitsToPreserve));
        } else {
            // The sourcePrefixLength align with Byte so just copy in the bytes directly.
            out.writeBytes(bytes, 0, payloadLength);
        }
    }

    // Package-Private for testing
    static int calculateEcsAddressLength(int sourcePrefixLength, int lowOrderBitsToPreserve) {
        return (sourcePrefixLength >>> 3) + (lowOrderBitsToPreserve != 0 ? 1 : 0);
    }

    private void encodeRawRecord(DnsRawRecord record, ByteBuf out) throws Exception {
        encodeRecord0(record, out);

        ByteBuf content = record.content();
        int contentLen = content.readableBytes();

        out.writeShort(contentLen);
        out.writeBytes(content, content.readerIndex(), contentLen);
    }

    protected void encodeName(String name, ByteBuf buf) throws Exception {
        DnsCodecUtil.encodeDomainName(name, buf);
    }

    private static byte padWithZeros(byte b, int lowOrderBitsToPreserve) {
        switch (lowOrderBitsToPreserve) {
        case 0:
            return 0;
        case 1:
            return (byte) (0x80 & b);
        case 2:
            return (byte) (0xC0 & b);
        case 3:
            return (byte) (0xE0 & b);
        case 4:
            return (byte) (0xF0 & b);
        case 5:
            return (byte) (0xF8 & b);
        case 6:
            return (byte) (0xFC & b);
        case 7:
            return (byte) (0xFE & b);
        case 8:
            return b;
        default:
            throw new IllegalArgumentException("lowOrderBitsToPreserve: " + lowOrderBitsToPreserve);
        }
    }
}
