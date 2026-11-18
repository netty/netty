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
import io.netty.util.AsciiString;
import io.netty.util.internal.SocketUtils;
import io.netty.util.internal.StringUtil;
import org.junit.jupiter.api.Test;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DefaultDnsRecordEncoderTest {

    @Test
    public void testEncodePtr() throws Exception {
        DefaultDnsRecordEncoder encoder = new DefaultDnsRecordEncoder();
        DnsPtrRecord ptrRecord = new DefaultDnsPtrRecord("131.186.250.142.in-addr.arpa.",
                DnsRecord.CLASS_IN, 80, "fra24s07-in-f3.1e100.net.");
        ByteBuf out = Unpooled.buffer();
        ByteBuf expectedBuf = Unpooled.buffer();
        expectedBuf.writeBytes(
                new byte[] {
                        3, '1', '3', '1',
                        3, '1', '8', '6',
                        3, '2', '5', '0',
                        3, '1', '4', '2',
                        7, 'i', 'n', '-', 'a', 'd', 'd', 'r',
                        4, 'a', 'r', 'p', 'a',
                        0
        });
        expectedBuf.writeShort(DnsRecordType.PTR.intValue());
        expectedBuf.writeShort(DnsRecord.CLASS_IN);
        expectedBuf.writeInt(80);
        byte[] hostname = new byte[] {
                14, 'f', 'r', 'a', '2', '4', 's', '0', '7', '-', 'i', 'n', '-', 'f', '3',
                5, '1', 'e', '1', '0', '0', 3, 'n', 'e', 't',
                0
        };
        expectedBuf.writeShort(hostname.length);
        expectedBuf.writeBytes(hostname);
        try {
            encoder.encodeRecord(ptrRecord, out);
            assertEquals(expectedBuf, out);
        } finally {
            out.release();
            expectedBuf.release();
        }
    }

    @Test
    public void testEncodeOptCookie() throws Exception {
        DefaultDnsRecordEncoder encoder = new DefaultDnsRecordEncoder();
        byte[] cookie = { 1, 2, 3, 4, 5, 6, 7, 8 };
        DnsOptCookieRecord record = new DefaultDnsOptCookieRecord(1232, cookie);
        ByteBuf out = Unpooled.buffer();
        ByteBuf expectedBuf = Unpooled.buffer();
        try {
            expectedBuf.writeByte(0); // root name
            expectedBuf.writeShort(DnsRecordType.OPT.intValue());
            expectedBuf.writeShort(1232);
            expectedBuf.writeInt(0);
            expectedBuf.writeShort(12); // RDLENGTH
            expectedBuf.writeShort(10); // OPTION-CODE
            expectedBuf.writeShort(8); // OPTION-LENGTH
            expectedBuf.writeBytes(cookie);

            encoder.encodeRecord(record, out);
            assertEquals(expectedBuf, out);
        } finally {
            out.release();
            expectedBuf.release();
        }
    }

    @Test
    public void testOptCookieRequiresEightBytes() {
        assertThrows(IllegalArgumentException.class, () -> new DefaultDnsOptCookieRecord(1232, new byte[7]));
        assertThrows(IllegalArgumentException.class, () -> new DefaultDnsOptCookieRecord(1232, new byte[16]));
    }

    @Test
    public void testEncodeSvcbAliasMode() throws Exception {
        DnsSvcbRecord record = new DefaultDnsSvcbRecord("example.com.", 300, 0, "svc.example.com.",
                Collections.<SvcParam>emptyList());

        ByteBuf expected = Unpooled.buffer();
        expected.writeBytes(new byte[] { 7, 'e', 'x', 'a', 'm', 'p', 'l', 'e', 3, 'c', 'o', 'm', 0 });
        expected.writeShort(DnsRecordType.SVCB.intValue());
        expected.writeShort(DnsRecord.CLASS_IN);
        expected.writeInt(300);
        expected.writeShort(19); // RDLENGTH: priority (2) + target name (17)
        expected.writeShort(0); // SvcPriority
        expected.writeBytes(SVC_EXAMPLE_COM);

        assertThat(encodeSvcb(record)).containsExactly(ByteBufUtil.getBytes(expected));
        expected.release();
    }

    @Test
    public void testEncodeSvcbServiceModeWithAlpn() throws Exception {
        DnsSvcbRecord record = new DefaultDnsSvcbRecord("example.com.", 300, 1, "svc.example.com.",
                Collections.<SvcParam>singletonList(
                        new SvcParam.Alpn(Arrays.asList(new AsciiString("h2"), new AsciiString("h3")))));

        ByteBuf expected = Unpooled.buffer();
        expected.writeBytes(new byte[] { 7, 'e', 'x', 'a', 'm', 'p', 'l', 'e', 3, 'c', 'o', 'm', 0 });
        expected.writeShort(DnsRecordType.SVCB.intValue());
        expected.writeShort(DnsRecord.CLASS_IN);
        expected.writeInt(300);
        expected.writeShort(29); // RDLENGTH: priority (2) + target name (17) + alpn (4 + 6)
        expected.writeShort(1); // SvcPriority
        expected.writeBytes(SVC_EXAMPLE_COM);
        expected.writeShort(1); // SvcParamKey alpn
        expected.writeShort(6); // SvcParamValue length
        expected.writeBytes(new byte[] { 2, 'h', '2', 2, 'h', '3' });

        assertThat(encodeSvcb(record)).containsExactly(ByteBufUtil.getBytes(expected));
        expected.release();
    }

    @Test
    public void testEncodeSvcbSortsParamsWithoutMutatingInput() throws Exception {
        SvcParam port = new SvcParam.Port(8443);
        SvcParam alpn = new SvcParam.Alpn(Collections.singletonList(new AsciiString("h3")));
        List<SvcParam> params = Collections.unmodifiableList(Arrays.asList(port, alpn));
        DnsSvcbRecord record = new DefaultDnsSvcbRecord("example.com.", 300, 1, "svc.example.com.", params);

        byte[] encoded = encodeSvcb(record);

        ByteBuf expectedRdata = Unpooled.buffer();
        expectedRdata.writeShort(1); // SvcPriority
        expectedRdata.writeBytes(SVC_EXAMPLE_COM);
        expectedRdata.writeShort(1); // SvcParamKey alpn
        expectedRdata.writeShort(3);
        expectedRdata.writeBytes(new byte[] { 2, 'h', '3' });
        expectedRdata.writeShort(3); // SvcParamKey port
        expectedRdata.writeShort(2);
        expectedRdata.writeShort(8443);
        byte[] rdata = ByteBufUtil.getBytes(expectedRdata);
        expectedRdata.release();

        // owner name (13) + type (2) + class (2) + ttl (4) + rdlength (2)
        assertThat(Arrays.copyOfRange(encoded, 21, 23)).containsExactly(0, rdata.length);
        assertThat(Arrays.copyOfRange(encoded, 23, encoded.length)).containsExactly(rdata);
        assertThat(params).containsExactly(port, alpn);
    }

    @Test
    public void testEncodeHttps() throws Exception {
        DnsSvcbRecord record = new DefaultDnsSvcbRecord("example.com.", DnsRecordType.HTTPS, 300, 1,
                "svc.example.com.", Collections.<SvcParam>singletonList(new SvcParam.Port(8443)));

        ByteBuf expected = Unpooled.buffer();
        expected.writeBytes(new byte[] { 7, 'e', 'x', 'a', 'm', 'p', 'l', 'e', 3, 'c', 'o', 'm', 0 });
        expected.writeShort(DnsRecordType.HTTPS.intValue());
        expected.writeShort(DnsRecord.CLASS_IN);
        expected.writeInt(300);
        expected.writeShort(25); // RDLENGTH: priority (2) + target name (17) + port (6)
        expected.writeShort(1); // SvcPriority
        expected.writeBytes(SVC_EXAMPLE_COM);
        expected.writeShort(SvcParam.PORT).writeShort(2).writeShort(8443);

        assertThat(encodeSvcb(record)).containsExactly(ByteBufUtil.getBytes(expected));
        expected.release();
    }

    @Test
    public void testEncodeSvcbRejectsDuplicateKeys() {
        List<SvcParam> params = Arrays.asList(
                new SvcParam.Alpn(Collections.singletonList(new AsciiString("h3"))),
                new SvcParam.Port(443),
                new SvcParam.Port(8443));
        DnsSvcbRecord record = new DefaultDnsSvcbRecord("example.com.", 300, 1, "svc.example.com.", params);

        assertThatThrownBy(() -> encodeSvcb(record)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testEncodeSvcbAlpnHonorsArrayOffset() throws Exception {
        byte[] backing = { 'x', 'h', '2', 'y' };
        AsciiString h2 = new AsciiString(backing, 1, 2, false);
        DnsSvcbRecord record = new DefaultDnsSvcbRecord("example.com.", 300, 1, "svc.example.com.",
                Collections.<SvcParam>singletonList(new SvcParam.Alpn(Collections.singletonList(h2))));

        byte[] encoded = encodeSvcb(record);

        assertThat(Arrays.copyOfRange(encoded, encoded.length - 7, encoded.length))
                .containsExactly(0, 1, 0, 3, 2, 'h', '2');
    }

    @Test
    public void testAlpnRejectsTooLongProtocol() {
        AsciiString tooLong = new AsciiString(new byte[256], false);
        assertThatThrownBy(() -> new SvcParam.Alpn(Collections.singletonList(tooLong)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testAlpnCopiesProtocols() throws Exception {
        List<AsciiString> protocols = new ArrayList<AsciiString>();
        protocols.add(new AsciiString("h2"));
        SvcParam alpn = new SvcParam.Alpn(protocols);
        protocols.add(new AsciiString("h3"));
        DnsSvcbRecord record = new DefaultDnsSvcbRecord("example.com.", 300, 1, "svc.example.com.",
                Collections.singletonList(alpn));

        byte[] encoded = encodeSvcb(record);

        assertThat(Arrays.copyOfRange(encoded, encoded.length - 7, encoded.length))
                .containsExactly(0, 1, 0, 3, 2, 'h', '2');
    }

    private static final byte[] SVC_EXAMPLE_COM = {
            3, 's', 'v', 'c', 7, 'e', 'x', 'a', 'm', 'p', 'l', 'e', 3, 'c', 'o', 'm', 0
    };

    private static byte[] encodeSvcb(DnsSvcbRecord record) throws Exception {
        ByteBuf out = Unpooled.buffer();
        try {
            new DefaultDnsRecordEncoder().encodeRecord(record, out);
            return ByteBufUtil.getBytes(out);
        } finally {
            out.release();
        }
    }

    @Test
    public void testEncodeSvcbMandatoryAlpnIpv4Hint() throws Exception {
        // RFC 9460 Appendix D.2, Figure 9:
        // example.com. SVCB 16 foo.example.org. (alpn=h2,h3-19 mandatory=ipv4hint,alpn ipv4hint=192.0.2.1)
        List<SvcParam> params = Arrays.asList(
                new SvcParam.Ipv4Hint(Collections.singletonList(
                        (Inet4Address) InetAddress.getByAddress(new byte[] { (byte) 192, 0, 2, 1 }))),
                new SvcParam.Alpn(Arrays.asList(new AsciiString("h2"), new AsciiString("h3-19"))),
                new SvcParam.Mandatory(Arrays.asList(SvcParam.IPV4HINT, SvcParam.ALPN)));
        DnsSvcbRecord record = new DefaultDnsSvcbRecord("example.com.", 300, 16, "foo.example.org.", params);

        assertThat(svcbRdata(record)).containsExactly(
                0x00, 0x10, // SvcPriority 16
                3, 'f', 'o', 'o', 7, 'e', 'x', 'a', 'm', 'p', 'l', 'e', 3, 'o', 'r', 'g', 0,
                0x00, 0x00, 0x00, 0x04, 0x00, 0x01, 0x00, 0x04, // mandatory=alpn,ipv4hint
                0x00, 0x01, 0x00, 0x09, 2, 'h', '2', 5, 'h', '3', '-', '1', '9', // alpn=h2,h3-19
                0x00, 0x04, 0x00, 0x04, 192, 0, 2, 1); // ipv4hint=192.0.2.1
    }

    @Test
    public void testEncodeSvcbNoDefaultAlpnPortEchIpv6HintUnknown() throws Exception {
        byte[] ech = { 1, 2, 3 };
        List<SvcParam> params = Arrays.<SvcParam>asList(
                new SvcParam.Unknown(667, new byte[] { 'h', 'e', 'l', 'l', 'o' }),
                new SvcParam.Ipv6Hint(Arrays.asList(
                        (Inet6Address) InetAddress.getByName("2001:db8::1"),
                        (Inet6Address) InetAddress.getByName("2001:db8::53:1"))),
                new SvcParam.Ech(ech),
                new SvcParam.Port(53),
                SvcParam.NoDefaultAlpn.INSTANCE,
                new SvcParam.Alpn(Collections.singletonList(new AsciiString("h3"))));
        DnsSvcbRecord record = new DefaultDnsSvcbRecord("example.com.", 300, 1, "svc.example.com.", params);
        ech[0] = 42;

        ByteBuf expected = Unpooled.buffer();
        expected.writeShort(1); // SvcPriority
        expected.writeBytes(SVC_EXAMPLE_COM);
        expected.writeShort(SvcParam.ALPN).writeShort(3).writeBytes(new byte[] { 2, 'h', '3' });
        expected.writeShort(SvcParam.NO_DEFAULT_ALPN).writeShort(0);
        expected.writeShort(SvcParam.PORT).writeShort(2).writeShort(53);
        expected.writeShort(SvcParam.ECH).writeShort(3).writeBytes(new byte[] { 1, 2, 3 });
        expected.writeShort(SvcParam.IPV6HINT).writeShort(32);
        expected.writeBytes(new byte[] { 0x20, 0x01, 0x0d, (byte) 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x01 });
        expected.writeBytes(new byte[] { 0x20, 0x01, 0x0d, (byte) 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x53, 0, 0x01 });
        expected.writeShort(667).writeShort(5).writeBytes(new byte[] { 'h', 'e', 'l', 'l', 'o' });

        assertThat(svcbRdata(record)).containsExactly(ByteBufUtil.getBytes(expected));
        expected.release();
    }

    @Test
    public void testEncodeSvcbRejectsMissingMandatoryKey() {
        List<SvcParam> params = Arrays.<SvcParam>asList(
                new SvcParam.Mandatory(Collections.singletonList(SvcParam.PORT)),
                new SvcParam.Alpn(Collections.singletonList(new AsciiString("h3"))));
        DnsSvcbRecord record = new DefaultDnsSvcbRecord("example.com.", 300, 1, "svc.example.com.", params);

        assertThatThrownBy(() -> encodeSvcb(record)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testEncodeSvcbRejectsNoDefaultAlpnWithoutAlpn() {
        DnsSvcbRecord record = new DefaultDnsSvcbRecord("example.com.", 300, 1, "svc.example.com.",
                Collections.<SvcParam>singletonList(SvcParam.NoDefaultAlpn.INSTANCE));

        assertThatThrownBy(() -> encodeSvcb(record)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testEncodeSvcbTargetNameIgnoresEncodeNameOverride() throws Exception {
        DefaultDnsRecordEncoder compressingEncoder = new DefaultDnsRecordEncoder() {
            @Override
            protected void encodeName(String name, ByteBuf buf) {
                // Writes a compression pointer for every name.
                buf.writeShort(0xC00C);
            }
        };
        DnsSvcbRecord record = new DefaultDnsSvcbRecord("example.com.", 300, 1, "svc.example.com.",
                Collections.<SvcParam>emptyList());
        ByteBuf out = Unpooled.buffer();
        try {
            compressingEncoder.encodeRecord(record, out);
            byte[] encoded = ByteBufUtil.getBytes(out);

            // owner name (2, compressed by the override) + type (2) + class (2) + ttl (4) + rdlength (2)
            ByteBuf expectedRdata = Unpooled.buffer();
            expectedRdata.writeShort(1); // SvcPriority
            expectedRdata.writeBytes(SVC_EXAMPLE_COM);
            assertThat(Arrays.copyOfRange(encoded, 12, encoded.length))
                    .containsExactly(ByteBufUtil.getBytes(expectedRdata));
            expectedRdata.release();
        } finally {
            out.release();
        }
    }

    @Test
    public void testSvcParamValidation() {
        assertThatThrownBy(() -> new SvcParam.Unknown(-1, new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SvcParam.Mandatory(Collections.<Integer>emptyList()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SvcParam.Mandatory(Collections.singletonList(SvcParam.MANDATORY)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SvcParam.Mandatory(Arrays.asList(SvcParam.PORT, SvcParam.PORT)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SvcParam.Unknown(65536, new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SvcParam.Mandatory(Collections.singletonList(65536)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SvcParam.Alpn(Collections.<AsciiString>emptyList()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SvcParam.Alpn(Collections.singletonList(AsciiString.EMPTY_STRING)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SvcParam.Port(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SvcParam.Port(65536)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SvcParam.Ipv4Hint(Collections.<Inet4Address>emptyList()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SvcParam.Ipv6Hint(Collections.<Inet6Address>emptyList()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testMandatoryKeysAreSorted() {
        SvcParam.Mandatory mandatory = new SvcParam.Mandatory(Arrays.asList(SvcParam.IPV4HINT, SvcParam.ALPN));
        assertThat(mandatory.keys()).containsExactly(SvcParam.ALPN, SvcParam.IPV4HINT);
    }

    private static byte[] svcbRdata(DnsSvcbRecord record) throws Exception {
        byte[] encoded = encodeSvcb(record);
        // owner name "example.com." (13) + type (2) + class (2) + ttl (4) + rdlength (2)
        int rdLength = (encoded[21] & 0xff) << 8 | encoded[22] & 0xff;
        assertThat(encoded.length - 23).isEqualTo(rdLength);
        return Arrays.copyOfRange(encoded, 23, encoded.length);
    }

    @Test
    public void testEncodeName() throws Exception {
        testEncodeName(new byte[] { 5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0 }, "netty.io.");
    }

    @Test
    public void testEncodeNameWithoutTerminator() throws Exception {
        testEncodeName(new byte[] { 5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0 }, "netty.io");
    }

    @Test
    public void testEncodeNameWithExtraTerminator() throws Exception {
        testEncodeName(new byte[] { 5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0 }, "netty.io..");
    }

    // Test for https://github.com/netty/netty/issues/5014
    @Test
    public void testEncodeEmptyName() throws Exception {
        testEncodeName(new byte[] { 0 }, StringUtil.EMPTY_STRING);
    }

    @Test
    public void testEncodeRootName() throws Exception {
        testEncodeName(new byte[] { 0 }, ".");
    }

    private static void testEncodeName(byte[] expected, String name) throws Exception {
        DefaultDnsRecordEncoder encoder = new DefaultDnsRecordEncoder();
        ByteBuf out = Unpooled.buffer();
        ByteBuf expectedBuf = Unpooled.wrappedBuffer(expected);
        try {
            encoder.encodeName(name, out);
            assertEquals(expectedBuf, out);
        } finally {
            out.release();
            expectedBuf.release();
        }
    }

    @Test
    public void testOptEcsRecordIpv4() throws Exception {
        testOptEcsRecordIp(SocketUtils.addressByName("1.2.3.4"));
        testOptEcsRecordIp(SocketUtils.addressByName("1.2.3.255"));
    }

    @Test
    public void testOptEcsRecordIpv6() throws Exception {
        testOptEcsRecordIp(SocketUtils.addressByName("::0"));
        testOptEcsRecordIp(SocketUtils.addressByName("::FF"));
    }

    private static void testOptEcsRecordIp(InetAddress address) throws Exception {
        int addressBits = address.getAddress().length * Byte.SIZE;
        for (int i = 0; i <= addressBits; ++i) {
            testIp(address, i);
        }
    }

    private static void testIp(InetAddress address, int prefix) throws Exception {
        int lowOrderBitsToPreserve = prefix % Byte.SIZE;

        ByteBuf addressPart = Unpooled.wrappedBuffer(address.getAddress(), 0,
                DefaultDnsRecordEncoder.calculateEcsAddressLength(prefix, lowOrderBitsToPreserve));

        if (lowOrderBitsToPreserve > 0) {
            // Pad the leftover of the last byte with zeros.
            int idx = addressPart.writerIndex() - 1;
            byte lastByte = addressPart.getByte(idx);
            int paddingMask = -1 << 8 - lowOrderBitsToPreserve;
            addressPart.setByte(idx, lastByte & paddingMask);
        }

        int payloadSize = nextInt(Short.MAX_VALUE);
        int extendedRcode = nextInt(Byte.MAX_VALUE * 2); // Unsigned
        int version = nextInt(Byte.MAX_VALUE * 2); // Unsigned

        DefaultDnsRecordEncoder encoder = new DefaultDnsRecordEncoder();
        ByteBuf out = Unpooled.buffer();
        try {
            DnsOptEcsRecord record = new DefaultDnsOptEcsRecord(
                    payloadSize, extendedRcode, version, prefix, address.getAddress());
            encoder.encodeRecord(record, out);

            assertEquals(0, out.readByte()); // Name
            assertEquals(DnsRecordType.OPT.intValue(), out.readUnsignedShort()); // Opt
            assertEquals(payloadSize, out.readUnsignedShort()); // payload
            assertEquals(record.timeToLive(), out.getUnsignedInt(out.readerIndex()));

            // Read unpacked TTL.
            assertEquals(extendedRcode, out.readUnsignedByte());
            assertEquals(version, out.readUnsignedByte());
            assertEquals(extendedRcode, record.extendedRcode());
            assertEquals(version, record.version());
            assertEquals(0, record.flags());

            assertEquals(0, out.readShort());

            int payloadLength = out.readUnsignedShort();
            assertEquals(payloadLength, out.readableBytes());

            assertEquals(8, out.readShort()); // As defined by RFC.

            int rdataLength = out.readUnsignedShort();
            assertEquals(rdataLength, out.readableBytes());

            assertEquals((short) (address instanceof Inet4Address ? 1 : 2), out.readShort());

            assertEquals(prefix, out.readUnsignedByte());
            assertEquals(0, out.readUnsignedByte()); // This must be 0 for requests.
            assertEquals(addressPart, out);
        } finally {
            addressPart.release();
            out.release();
        }
    }

    private static int nextInt(int max) {
        return ThreadLocalRandom.current().nextInt(max);
    }
}
