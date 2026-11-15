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
package io.netty.handler.codec.dns;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DefaultDnsRecordDecoderTest {

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void testDecodeSoaRecord(boolean compressed) throws Exception {
        ByteBuf packet = Unpooled.buffer();
        ByteBuf encoded = Unpooled.buffer();
        DnsRawRecord record = null;
        DnsRawRecord decoded = null;
        try {
            // The original message contains a name that both SOA fields refer to.
            DnsCodecUtil.encodeDomainName("example.org.", packet);
            int recordOffset = packet.writerIndex();
            DnsCodecUtil.encodeDomainName("netty.io.", packet);
            packet.writeShort(DnsRecordType.SOA.intValue()).writeShort(DnsRecord.CLASS_IN).writeInt(60);
            int lengthOffset = packet.writerIndex();
            packet.writeShort(0);
            if (compressed) {
                packet.writeByte(2).writeByte('n').writeByte('s').writeShort(0xc000);
                packet.writeByte(10).writeBytes(new byte[] { 'h', 'o', 's', 't', 'm', 'a', 's', 't', 'e', 'r' });
                packet.writeShort(0xc000);
            } else {
                DnsCodecUtil.encodeDomainName("ns.example.org.", packet);
                DnsCodecUtil.encodeDomainName("hostmaster.example.org.", packet);
            }
            packet.writeInt(-1).writeInt(3600).writeInt(600).writeInt(86400).writeInt(60);
            packet.setShort(lengthOffset, packet.writerIndex() - lengthOffset - 2);
            packet.readerIndex(recordOffset);
            record = DnsRecordDecoder.DEFAULT.decodeRecord(packet);
            assertThat(record.name()).isEqualTo("netty.io.");
            assertThat(record.type()).isEqualTo(DnsRecordType.SOA);
            assertThat(record.dnsClass()).isEqualTo(DnsRecord.CLASS_IN);
            assertThat(record.timeToLive()).isEqualTo(60);
            assertThat(packet.isReadable()).isFalse();

            // Re-encoding the record at a different offset must not retain pointers into the old message.
            DnsRecordEncoder.DEFAULT.encodeRecord(record, encoded);
            decoded = DnsRecordDecoder.DEFAULT.decodeRecord(encoded);
            ByteBuf data = decoded.content().copy();
            try {
                assertThat(DnsCodecUtil.decodeDomainName(data)).isEqualTo("ns.example.org.");
                assertThat(DnsCodecUtil.decodeDomainName(data)).isEqualTo("hostmaster.example.org.");
                assertThat(data.readUnsignedInt()).isEqualTo(0xffffffffL);
                assertThat(data.readUnsignedInt()).isEqualTo(3600);
                assertThat(data.readUnsignedInt()).isEqualTo(600);
                assertThat(data.readUnsignedInt()).isEqualTo(86400);
                assertThat(data.readUnsignedInt()).isEqualTo(60);
                assertThat(data.isReadable()).isFalse();
            } finally {
                data.release();
            }
        } finally {
            if (decoded != null) {
                decoded.release();
            }
            if (record != null) {
                record.release();
            }
            encoded.release();
            packet.release();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { 21, 23 })
    public void testDecodeSoaRecordWithInvalidIntegerFields(int length) {
        ByteBuf packet = Unpooled.buffer().writeZero(length);
        try {
            assertThatThrownBy(() -> {
                DnsRawRecord record = (DnsRawRecord) new DefaultDnsRecordDecoder().decodeRecord(
                        "netty.io.", DnsRecordType.SOA, DnsRecord.CLASS_IN, 60, packet, 0, length);
                record.release();
            }).isInstanceOf(CorruptedFrameException.class);
            assertThat(packet.readerIndex()).isZero();
            assertThat(packet.writerIndex()).isEqualTo(length);
            assertThat(packet.refCnt()).isEqualTo(1);
        } finally {
            packet.release();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 22 })
    public void testDecodeSoaRecordWithEmptyDataOrRootNames(int length) throws Exception {
        ByteBuf packet = Unpooled.buffer().writeZero(length);
        DnsRawRecord record = null;
        try {
            record = (DnsRawRecord) new DefaultDnsRecordDecoder().decodeRecord(
                    "netty.io.", DnsRecordType.SOA, length == 0 ? DnsRecord.CLASS_ANY : DnsRecord.CLASS_IN,
                    0, packet, 0, length);
            assertThat(ByteBufUtil.hexDump(record.content())).isEqualTo(ByteBufUtil.hexDump(packet));
            assertThat(packet.readerIndex()).isZero();
            assertThat(packet.writerIndex()).isEqualTo(length);
        } finally {
            if (record != null) {
                record.release();
            }
            packet.release();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void testDecodeSoaRecordPreservesMailboxLabel(boolean compressed) throws Exception {
        ByteBuf packet = Unpooled.buffer();
        ByteBuf expected = Unpooled.buffer();
        DnsRawRecord record = null;
        try {
            DnsCodecUtil.encodeDomainName("example.org.", packet);
            int offset = packet.writerIndex();
            packet.writeByte(0);
            packet.writeByte(10).writeBytes(new byte[] { 'J', 'o', 'h', 'n', '.', 'S', 'm', 'i', 't', 'h' });
            if (compressed) {
                packet.writeShort(0xc000);
            } else {
                DnsCodecUtil.encodeDomainName("example.org.", packet);
            }
            packet.writeZero(20);
            expected.writeByte(0);
            expected.writeByte(10).writeBytes(new byte[] { 'J', 'o', 'h', 'n', '.', 'S', 'm', 'i', 't', 'h' });
            DnsCodecUtil.encodeDomainName("example.org.", expected);
            expected.writeZero(20);
            record = (DnsRawRecord) new DefaultDnsRecordDecoder().decodeRecord(
                    "netty.io.", DnsRecordType.SOA, DnsRecord.CLASS_IN, 60,
                    packet, offset, packet.writerIndex() - offset);
            assertThat(ByteBufUtil.hexDump(record.content())).isEqualTo(ByteBufUtil.hexDump(expected));
        } finally {
            if (record != null) {
                record.release();
            }
            expected.release();
            packet.release();
        }
    }

    @Test
    public void testDecodeName() {
        testDecodeName("netty.io.", Unpooled.wrappedBuffer(new byte[] {
                5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0
        }));
    }

    @Test
    public void testDecodeNameWithoutTerminator() {
        testDecodeName("netty.io.", Unpooled.wrappedBuffer(new byte[] {
                5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o'
        }));
    }

    @Test
    public void testDecodeNameWithExtraTerminator() {
        // Should not be decoded as 'netty.io..'
        testDecodeName("netty.io.", Unpooled.wrappedBuffer(new byte[] {
                5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0, 0
        }));
    }

    @Test
    public void testDecodeEmptyName() {
        testDecodeName(".", Unpooled.buffer().writeByte(0));
    }

    @Test
    public void testDecodeEmptyNameFromEmptyBuffer() {
        testDecodeName(".", Unpooled.EMPTY_BUFFER);
    }

    @Test
    public void testDecodeEmptyNameFromExtraZeroes() {
        testDecodeName(".", Unpooled.wrappedBuffer(new byte[] { 0, 0 }));
    }

    private static void testDecodeName(String expected, ByteBuf buffer) {
        try {
            DefaultDnsRecordDecoder decoder = new DefaultDnsRecordDecoder();
            assertEquals(expected, decoder.decodeName0(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testDecodeSvcbRecordRawByDefault() throws Exception {
        assertSvcbDecodedAsRaw(DnsRecordDecoder.DEFAULT, svcbRecordBytes(new SvcParam.Port(8443)));
    }

    @Test
    public void testDecodeSvcbRecordRawWhenDisabled() throws Exception {
        assertSvcbDecodedAsRaw(new DefaultDnsRecordDecoder(null), svcbRecordBytes(new SvcParam.Port(8443)));
    }

    @Test
    public void testDecodeMalformedSvcbRecordRawWhenDisabled() throws Exception {
        assertSvcbDecodedAsRaw(DnsRecordDecoder.DEFAULT, malformedSvcbRecordBytes());
        assertSvcbDecodedAsRaw(new DefaultDnsRecordDecoder(null), malformedSvcbRecordBytes());
    }

    @Test
    public void testDecodeSvcbRecordWhenEnabled() throws Exception {
        DnsRecordDecoder decoder = new DefaultDnsRecordDecoder(new SvcbDecoderConfig());
        ByteBuf in = Unpooled.buffer();
        in.writeBytes(svcbRecordBytes(new SvcParam.Port(8443)));
        in.writeBytes(svcbRecordBytes(new SvcParam.Port(443)));
        try {
            DnsRecord first = decoder.decodeRecord(in);
            DnsRecord second = decoder.decodeRecord(in);

            assertThat(first).isInstanceOf(DefaultDnsSvcbRecord.class);
            DnsSvcbRecord record = (DnsSvcbRecord) first;
            assertThat(record.name()).isEqualTo("example.com.");
            assertThat(record.type()).isEqualTo(DnsRecordType.SVCB);
            assertThat(record.dnsClass()).isEqualTo(DnsRecord.CLASS_IN);
            assertThat(record.timeToLive()).isEqualTo(300);
            assertThat(record.priority()).isEqualTo(1);
            assertThat(record.targetName()).isEqualTo("svc.example.com.");
            assertThat(((SvcParam.Port) record.params().get(0)).port()).isEqualTo(8443);

            assertThat(second).isInstanceOf(DefaultDnsSvcbRecord.class);
            assertThat(((SvcParam.Port) ((DnsSvcbRecord) second).params().get(0)).port()).isEqualTo(443);
            assertThat(in.isReadable()).isFalse();
        } finally {
            in.release();
        }
    }

    @Test
    public void testDecodeMalformedSvcbRecordWhenEnabled() {
        DnsRecordDecoder decoder = new DefaultDnsRecordDecoder(new SvcbDecoderConfig());
        ByteBuf in = Unpooled.wrappedBuffer(malformedSvcbRecordBytes());
        try {
            assertThatThrownBy(() -> decoder.decodeRecord(in)).isInstanceOf(CorruptedFrameException.class);
        } finally {
            in.release();
        }
    }

    @Test
    public void testDecodeHttpsRecordRawByDefaultOrWhenDisabled() throws Exception {
        assertSvcbDecodedAsRaw(DnsRecordDecoder.DEFAULT, httpsRecordBytes(), DnsRecordType.HTTPS);
        assertSvcbDecodedAsRaw(new DefaultDnsRecordDecoder(null), httpsRecordBytes(), DnsRecordType.HTTPS);
    }

    @Test
    public void testDecodeHttpsRecordWhenEnabled() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(httpsRecordBytes());
        try {
            DnsRecord record = new DefaultDnsRecordDecoder(new SvcbDecoderConfig()).decodeRecord(in);

            assertThat(record).isInstanceOf(DefaultDnsSvcbRecord.class);
            assertThat(record.type()).isEqualTo(DnsRecordType.HTTPS);
            assertThat(((SvcParam.Port) ((DnsSvcbRecord) record).params().get(0)).port()).isEqualTo(8443);
        } finally {
            in.release();
        }
    }

    private static byte[] httpsRecordBytes() throws Exception {
        ByteBuf out = Unpooled.buffer();
        try {
            DnsRecordEncoder.DEFAULT.encodeRecord(new DefaultDnsSvcbRecord("example.com.", DnsRecordType.HTTPS, 300,
                    1, "svc.example.com.", Collections.<SvcParam>singletonList(new SvcParam.Port(8443))), out);
            return ByteBufUtil.getBytes(out);
        } finally {
            out.release();
        }
    }

    private static void assertSvcbDecodedAsRaw(DnsRecordDecoder decoder, byte[] recordBytes) throws Exception {
        assertSvcbDecodedAsRaw(decoder, recordBytes, DnsRecordType.SVCB);
    }

    private static void assertSvcbDecodedAsRaw(DnsRecordDecoder decoder, byte[] recordBytes,
                                               DnsRecordType type) throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(recordBytes);
        DnsRecord record = null;
        try {
            record = decoder.decodeRecord(in);
            assertThat(record).isInstanceOf(DefaultDnsRawRecord.class);
            assertThat(record.type()).isEqualTo(type);
            // owner name "example.com." (13) + type (2) + class (2) + ttl (4) + rdlength (2)
            assertThat(ByteBufUtil.getBytes(((DnsRawRecord) record).content()))
                    .containsExactly(Arrays.copyOfRange(recordBytes, 23, recordBytes.length));
            assertThat(in.isReadable()).isFalse();
        } finally {
            ReferenceCountUtil.release(record);
            in.release();
        }
    }

    private static byte[] svcbRecordBytes(SvcParam param) throws Exception {
        ByteBuf out = Unpooled.buffer();
        try {
            DnsRecordEncoder.DEFAULT.encodeRecord(new DefaultDnsSvcbRecord("example.com.", 300, 1,
                    "svc.example.com.", Collections.singletonList(param)), out);
            return ByteBufUtil.getBytes(out);
        } finally {
            out.release();
        }
    }

    private static byte[] malformedSvcbRecordBytes() {
        ByteBuf out = Unpooled.buffer();
        try {
            out.writeBytes(new byte[] { 7, 'e', 'x', 'a', 'm', 'p', 'l', 'e', 3, 'c', 'o', 'm', 0 });
            out.writeShort(DnsRecordType.SVCB.intValue());
            out.writeShort(DnsRecord.CLASS_IN);
            out.writeInt(300);
            out.writeShort(7);
            // SvcPriority 1, root TargetName, then a port SvcParam with a 1-byte value
            out.writeBytes(new byte[] { 0, 1, 0, 0, 3, 0, 1 });
            return ByteBufUtil.getBytes(out);
        } finally {
            out.release();
        }
    }

    @Test
    public void testDecodePtrRecord() throws Exception {
        DefaultDnsRecordDecoder decoder = new DefaultDnsRecordDecoder();
        ByteBuf buffer = Unpooled.buffer().writeByte(0);
        int readerIndex = buffer.readerIndex();
        int writerIndex = buffer.writerIndex();
        try {
            DnsPtrRecord record = (DnsPtrRecord) decoder.decodeRecord(
                    "netty.io", DnsRecordType.PTR, DnsRecord.CLASS_IN, 60, buffer, 0, 1);
            assertEquals("netty.io.", record.name());
            assertEquals(DnsRecord.CLASS_IN, record.dnsClass());
            assertEquals(60, record.timeToLive());
            assertEquals(DnsRecordType.PTR, record.type());
            assertEquals(readerIndex, buffer.readerIndex());
            assertEquals(writerIndex, buffer.writerIndex());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testdecompressCompressPointer() {
        byte[] compressionPointer = {
                5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0,
                (byte) 0xC0, 0
        };
        ByteBuf buffer = Unpooled.wrappedBuffer(compressionPointer);
        ByteBuf uncompressed = null;
        try {
            uncompressed = DnsCodecUtil.decompressDomainName(buffer.duplicate().setIndex(10, 12));
            assertEquals(0, ByteBufUtil.compare(buffer.duplicate().setIndex(0, 10), uncompressed));
        } finally {
            buffer.release();
            if (uncompressed != null) {
                uncompressed.release();
            }
        }
    }

    @Test
    public void testdecompressNestedCompressionPointer() {
        byte[] nestedCompressionPointer = {
                6, 'g', 'i', 't', 'h', 'u', 'b', 2, 'i', 'o', 0, // github.io
                5, 'n', 'e', 't', 't', 'y', (byte) 0xC0, 0, // netty.github.io
                (byte) 0xC0, 11, // netty.github.io
        };
        ByteBuf buffer = Unpooled.wrappedBuffer(nestedCompressionPointer);
        ByteBuf uncompressed = null;
        try {
            uncompressed = DnsCodecUtil.decompressDomainName(buffer.duplicate().setIndex(19, 21));
            assertEquals(0, ByteBufUtil.compare(
                    Unpooled.wrappedBuffer(new byte[] {
                            5, 'n', 'e', 't', 't', 'y', 6, 'g', 'i', 't', 'h', 'u', 'b', 2, 'i', 'o', 0
                    }), uncompressed));
        } finally {
            buffer.release();
            if (uncompressed != null) {
                uncompressed.release();
            }
        }
    }

    @Test
    public void testDecodeCompressionRDataPointer() throws Exception {
        DefaultDnsRecordDecoder decoder = new DefaultDnsRecordDecoder();
        byte[] compressionPointer = {
                5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0,
                (byte) 0xC0, 0
        };
        ByteBuf buffer = Unpooled.wrappedBuffer(compressionPointer);
        DefaultDnsRawRecord cnameRecord = null;
        DefaultDnsRawRecord nsRecord = null;
        try {
            cnameRecord = (DefaultDnsRawRecord) decoder.decodeRecord(
                    "netty.github.io", DnsRecordType.CNAME, DnsRecord.CLASS_IN, 60, buffer, 10, 2);
            assertEquals(0, ByteBufUtil.compare(buffer.duplicate().setIndex(0, 10), cnameRecord.content()),
                "The rdata of CNAME-type record should be decompressed in advance");
            assertEquals("netty.io.", DnsCodecUtil.decodeDomainName(cnameRecord.content()));
            nsRecord = (DefaultDnsRawRecord) decoder.decodeRecord(
                    "netty.github.io", DnsRecordType.NS, DnsRecord.CLASS_IN, 60, buffer, 10, 2);
            assertEquals(0, ByteBufUtil.compare(buffer.duplicate().setIndex(0, 10), nsRecord.content()),
                        "The rdata of NS-type record should be decompressed in advance");
            assertEquals("netty.io.", DnsCodecUtil.decodeDomainName(nsRecord.content()));
        } finally {
            buffer.release();
            if (cnameRecord != null) {
                cnameRecord.release();
            }

            if (nsRecord != null) {
                nsRecord.release();
            }
        }
    }

    @Test
    public void testDecodeCompressionRDataPointerMX() throws Exception {
        DefaultDnsRecordDecoder decoder = new DefaultDnsRecordDecoder();
        byte[] compressionPointer = {
                5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0,
                0, 10,          // preference = 10
                (byte) 0xC0, 0  // record is a pointer to netty.io
        };

        byte[] expected = {
                0, 10, // pref = 10
                5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0
        };
        ByteBuf buffer = Unpooled.wrappedBuffer(compressionPointer);
        DefaultDnsRawRecord mxRecord = null;
        ByteBuf expectedBuf = null;
        try {
            mxRecord = (DefaultDnsRawRecord) decoder.decodeRecord(
                    "mail.example.com",
                    DnsRecordType.MX,
                    DnsRecord.CLASS_IN,
                    60,
                    buffer,
                    10,
                    4);

            expectedBuf = Unpooled.wrappedBuffer(expected);

            assertEquals(0, ByteBufUtil.compare(expectedBuf, mxRecord.content()),
                         "The rdata of MX-type record should be decompressed in advance");
            assertEquals(10, mxRecord.content().getUnsignedShort(0));

            ByteBuf exchangerName = mxRecord.content().duplicate().setIndex(2, mxRecord.content().writerIndex());
            assertEquals("netty.io.", DnsCodecUtil.decodeDomainName(exchangerName));
        } finally {
            buffer.release();
            if (expectedBuf != null) {
                expectedBuf.release();
            }
            if (mxRecord != null) {
                mxRecord.release();
            }
        }
    }

    @Test
    public void testDecodeMessageCompression() throws Exception {
        // See https://www.ietf.org/rfc/rfc1035 [4.1.4. Message compression]
        DefaultDnsRecordDecoder decoder = new DefaultDnsRecordDecoder();
        byte[] rfcExample = { 1, 'F', 3, 'I', 'S', 'I', 4, 'A', 'R', 'P', 'A',
                0, 3, 'F', 'O', 'O',
                (byte) 0xC0, 0, // this is 20 in the example
                (byte) 0xC0, 6, // this is 26 in the example
        };
        DefaultDnsRawRecord rawPlainRecord = null;
        DefaultDnsRawRecord rawUncompressedRecord = null;
        DefaultDnsRawRecord rawUncompressedIndexedRecord = null;
        ByteBuf buffer = Unpooled.wrappedBuffer(rfcExample);
        try {
            // First lets test that our utility function can correctly handle index references and decompression.
            String plainName = DefaultDnsRecordDecoder.decodeName(buffer.duplicate());
            assertEquals("F.ISI.ARPA.", plainName);
            String uncompressedPlainName = DefaultDnsRecordDecoder.decodeName(buffer.duplicate().setIndex(16, 20));
            assertEquals(plainName, uncompressedPlainName);
            String uncompressedIndexedName = DefaultDnsRecordDecoder.decodeName(buffer.duplicate().setIndex(12, 20));
            assertEquals("FOO." + plainName, uncompressedIndexedName);

            // Now lets make sure out object parsing produces the same results for non PTR type (just use CNAME).
            rawPlainRecord = (DefaultDnsRawRecord) decoder.decodeRecord(
                    plainName, DnsRecordType.CNAME, DnsRecord.CLASS_IN, 60, buffer, 0, 11);
            assertEquals(plainName, rawPlainRecord.name());
            assertEquals(plainName, DefaultDnsRecordDecoder.decodeName(rawPlainRecord.content()));

            rawUncompressedRecord = (DefaultDnsRawRecord) decoder.decodeRecord(
                    uncompressedPlainName, DnsRecordType.CNAME, DnsRecord.CLASS_IN, 60, buffer, 16, 4);
            assertEquals(uncompressedPlainName, rawUncompressedRecord.name());
            assertEquals(uncompressedPlainName, DefaultDnsRecordDecoder.decodeName(rawUncompressedRecord.content()));

            rawUncompressedIndexedRecord = (DefaultDnsRawRecord) decoder.decodeRecord(
                    uncompressedIndexedName, DnsRecordType.CNAME, DnsRecord.CLASS_IN, 60, buffer, 12, 8);
            assertEquals(uncompressedIndexedName, rawUncompressedIndexedRecord.name());
            assertEquals(uncompressedIndexedName,
                         DefaultDnsRecordDecoder.decodeName(rawUncompressedIndexedRecord.content()));

            // Now lets make sure out object parsing produces the same results for PTR type.
            DnsPtrRecord ptrRecord = (DnsPtrRecord) decoder.decodeRecord(
                    plainName, DnsRecordType.PTR, DnsRecord.CLASS_IN, 60, buffer, 0, 11);
            assertEquals(plainName, ptrRecord.name());
            assertEquals(plainName, ptrRecord.hostname());

            ptrRecord = (DnsPtrRecord) decoder.decodeRecord(
                    uncompressedPlainName, DnsRecordType.PTR, DnsRecord.CLASS_IN, 60, buffer, 16, 4);
            assertEquals(uncompressedPlainName, ptrRecord.name());
            assertEquals(uncompressedPlainName, ptrRecord.hostname());

            ptrRecord = (DnsPtrRecord) decoder.decodeRecord(
                    uncompressedIndexedName, DnsRecordType.PTR, DnsRecord.CLASS_IN, 60, buffer, 12, 8);
            assertEquals(uncompressedIndexedName, ptrRecord.name());
            assertEquals(uncompressedIndexedName, ptrRecord.hostname());
        } finally {
            if (rawPlainRecord != null) {
                rawPlainRecord.release();
            }
            if (rawUncompressedRecord != null) {
                rawUncompressedRecord.release();
            }
            if (rawUncompressedIndexedRecord != null) {
                rawUncompressedIndexedRecord.release();
            }
            buffer.release();
        }
    }

    @Test
    public void testTruncatedPacket() throws Exception {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(0);
        buffer.writeShort(DnsRecordType.A.intValue());
        buffer.writeShort(1);
        buffer.writeInt(32);

        // Write a truncated last value.
        buffer.writeByte(0);
        DefaultDnsRecordDecoder decoder = new DefaultDnsRecordDecoder();
        try {
            int readerIndex = buffer.readerIndex();
            assertNull(decoder.decodeRecord(buffer));
            assertEquals(readerIndex, buffer.readerIndex());
        } finally {
            buffer.release();
        }
    }
}
