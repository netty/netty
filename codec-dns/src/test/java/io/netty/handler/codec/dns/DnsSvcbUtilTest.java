/*
 * Copyright 2026 The Netty Project
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
import io.netty.util.AsciiString;
import org.junit.jupiter.api.Test;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DnsSvcbUtilTest {

    private static final byte[] SVC_EXAMPLE_COM = {
            3, 's', 'v', 'c', 7, 'e', 'x', 'a', 'm', 'p', 'l', 'e', 3, 'c', 'o', 'm', 0
    };

    @Test
    public void testEncodeRdataAliasMode() {
        ByteBuf expected = Unpooled.buffer();
        expected.writeShort(0); // SvcPriority
        expected.writeBytes(SVC_EXAMPLE_COM);

        assertThat(encodeRdata(0, "svc.example.com.", Collections.<SvcParam>emptyList()))
                .containsExactly(ByteBufUtil.getBytes(expected));
        expected.release();
    }

    @Test
    public void testEncodeRdataRootTargetName() {
        assertThat(encodeRdata(1, ".", Collections.<SvcParam>emptyList())).containsExactly(0, 1, 0);
    }

    @Test
    public void testEncodeRdataKeepsGivenOrder() {
        List<SvcParam> params = Arrays.<SvcParam>asList(
                new SvcParam.Port(8443),
                new SvcParam.Alpn(Collections.singletonList(new AsciiString("h3"))));

        ByteBuf expected = Unpooled.buffer();
        expected.writeShort(1); // SvcPriority
        expected.writeBytes(SVC_EXAMPLE_COM);
        expected.writeShort(SvcParam.PORT).writeShort(2).writeShort(8443);
        expected.writeShort(SvcParam.ALPN).writeShort(3).writeBytes(new byte[] { 2, 'h', '3' });

        assertThat(encodeRdata(1, "svc.example.com.", params)).containsExactly(ByteBufUtil.getBytes(expected));
        expected.release();
    }

    @Test
    public void testEncodeRdataAllowsDuplicateKeys() {
        List<SvcParam> params = Arrays.<SvcParam>asList(new SvcParam.Port(443), new SvcParam.Port(8443));

        ByteBuf expected = Unpooled.buffer();
        expected.writeShort(1); // SvcPriority
        expected.writeBytes(SVC_EXAMPLE_COM);
        expected.writeShort(SvcParam.PORT).writeShort(2).writeShort(443);
        expected.writeShort(SvcParam.PORT).writeShort(2).writeShort(8443);

        assertThat(encodeRdata(1, "svc.example.com.", params)).containsExactly(ByteBufUtil.getBytes(expected));
        expected.release();
    }

    @Test
    public void testEncodeRdataAllowsInconsistentParams() {
        List<SvcParam> params = Arrays.<SvcParam>asList(
                new SvcParam.Mandatory(Collections.singletonList(SvcParam.PORT)),
                SvcParam.NoDefaultAlpn.INSTANCE);

        ByteBuf expected = Unpooled.buffer();
        expected.writeShort(1); // SvcPriority
        expected.writeBytes(SVC_EXAMPLE_COM);
        expected.writeShort(SvcParam.MANDATORY).writeShort(2).writeShort(SvcParam.PORT);
        expected.writeShort(SvcParam.NO_DEFAULT_ALPN).writeShort(0);

        assertThat(encodeRdata(1, "svc.example.com.", params)).containsExactly(ByteBufUtil.getBytes(expected));
        expected.release();
    }

    @Test
    public void testEncodeRdataMalformedValueThroughUnknown() {
        List<SvcParam> params = Collections.<SvcParam>singletonList(
                new SvcParam.Unknown(SvcParam.ALPN, new byte[] { 5, 'h', '3' }));

        ByteBuf expected = Unpooled.buffer();
        expected.writeShort(1); // SvcPriority
        expected.writeBytes(SVC_EXAMPLE_COM);
        expected.writeShort(SvcParam.ALPN).writeShort(3).writeBytes(new byte[] { 5, 'h', '3' });

        assertThat(encodeRdata(1, "svc.example.com.", params)).containsExactly(ByteBufUtil.getBytes(expected));
        expected.release();
    }

    @Test
    public void testEncodeRdataWrappedInRawRecord() throws Exception {
        ByteBuf rdata = Unpooled.buffer();
        DnsSvcbUtil.encodeRdata(1, "svc.example.com.",
                Collections.<SvcParam>singletonList(new SvcParam.Port(8443)), rdata);
        DnsRawRecord raw = new DefaultDnsRawRecord("example.com.", DnsRecordType.SVCB, 300, rdata);
        ByteBuf out = Unpooled.buffer();
        try {
            DnsRecordEncoder.DEFAULT.encodeRecord(raw, out);

            ByteBuf expected = Unpooled.buffer();
            expected.writeBytes(new byte[] { 7, 'e', 'x', 'a', 'm', 'p', 'l', 'e', 3, 'c', 'o', 'm', 0 });
            expected.writeShort(DnsRecordType.SVCB.intValue());
            expected.writeShort(DnsRecord.CLASS_IN);
            expected.writeInt(300);
            expected.writeShort(25); // RDLENGTH: priority (2) + target name (17) + port (6)
            expected.writeShort(1);
            expected.writeBytes(SVC_EXAMPLE_COM);
            expected.writeShort(SvcParam.PORT).writeShort(2).writeShort(8443);
            assertThat(ByteBufUtil.getBytes(out)).containsExactly(ByteBufUtil.getBytes(expected));
            expected.release();
        } finally {
            out.release();
            raw.release();
        }
    }

    @Test
    public void testDecodeAliasMode() {
        DefaultDnsSvcbRecord record = decode(encodeRdata(0, "svc.example.com.", Collections.<SvcParam>emptyList()));

        assertThat(record.name()).isEqualTo("example.com.");
        assertThat(record.type()).isEqualTo(DnsRecordType.SVCB);
        assertThat(record.dnsClass()).isEqualTo(DnsRecord.CLASS_IN);
        assertThat(record.timeToLive()).isEqualTo(300);
        assertThat(record.priority()).isEqualTo(0);
        assertThat(record.targetName()).isEqualTo("svc.example.com.");
        assertThat(record.params()).isEmpty();
    }

    @Test
    public void testDecodeRootTargetName() {
        DefaultDnsSvcbRecord record = decode(new byte[] { 0, 1, 0 });

        assertThat(record.priority()).isEqualTo(1);
        assertThat(record.targetName()).isEqualTo(".");
    }

    @Test
    public void testDecodeKeepsDnsClass() {
        byte[] rdata = encodeRdata(1, ".", Collections.<SvcParam>emptyList());
        ByteBuf in = Unpooled.wrappedBuffer(rdata);
        try {
            DnsSvcbRecord record = DnsSvcbUtil.decodeRecord("example.com.", DnsRecordType.SVCB,
                    DnsRecord.CLASS_CHAOS, 300, in, 0, rdata.length);
            assertThat(record.dnsClass()).isEqualTo(DnsRecord.CLASS_CHAOS);
        } finally {
            in.release();
        }
    }

    @Test
    public void testDecodeHttpsRecord() {
        byte[] rdata = encodeRdata(1, ".", Collections.<SvcParam>singletonList(new SvcParam.Port(8443)));
        ByteBuf in = Unpooled.wrappedBuffer(rdata);
        try {
            DnsSvcbRecord record = DnsSvcbUtil.decodeRecord("example.com.", DnsRecordType.HTTPS, DnsRecord.CLASS_IN,
                    300, in, 0, rdata.length);
            assertThat(record.type()).isEqualTo(DnsRecordType.HTTPS);
            assertThat(((SvcParam.Port) record.params().get(0)).port()).isEqualTo(8443);
        } finally {
            in.release();
        }
    }

    @Test
    public void testDecodeAllParams() throws Exception {
        List<SvcParam> params = Arrays.<SvcParam>asList(
                new SvcParam.Mandatory(Arrays.asList(SvcParam.ALPN, SvcParam.PORT)),
                new SvcParam.Alpn(Arrays.asList(new AsciiString("h2"), new AsciiString("h3"))),
                SvcParam.NoDefaultAlpn.INSTANCE,
                new SvcParam.Port(8443),
                new SvcParam.Ipv4Hint(Arrays.asList(
                        (Inet4Address) InetAddress.getByName("192.0.2.1"),
                        (Inet4Address) InetAddress.getByName("192.0.2.2"))),
                new SvcParam.Ech(new byte[] { 1, 2, 3 }),
                new SvcParam.Ipv6Hint(Collections.singletonList(
                        (Inet6Address) InetAddress.getByName("2001:db8::1"))),
                new SvcParam.Unknown(667, new byte[] { 'h', 'e', 'l', 'l', 'o' }));

        DefaultDnsSvcbRecord record = decode(encodeRdata(1, "svc.example.com.", params));

        assertThat(record.priority()).isEqualTo(1);
        assertThat(record.targetName()).isEqualTo("svc.example.com.");
        List<SvcParam> decoded = record.params();
        assertThat(decoded).hasSize(8);
        assertThat(((SvcParam.Mandatory) decoded.get(0)).keys()).containsExactly(SvcParam.ALPN, SvcParam.PORT);
        assertThat(((SvcParam.Alpn) decoded.get(1)).protocols())
                .containsExactly(new AsciiString("h2"), new AsciiString("h3"));
        assertThat(decoded.get(2)).isSameAs(SvcParam.NoDefaultAlpn.INSTANCE);
        assertThat(((SvcParam.Port) decoded.get(3)).port()).isEqualTo(8443);
        assertThat(((SvcParam.Ipv4Hint) decoded.get(4)).addresses()).containsExactly(
                (Inet4Address) InetAddress.getByName("192.0.2.1"), (Inet4Address) InetAddress.getByName("192.0.2.2"));
        assertThat(((SvcParam.Ech) decoded.get(5)).echConfigList()).containsExactly(1, 2, 3);
        assertThat(((SvcParam.Ipv6Hint) decoded.get(6)).addresses())
                .containsExactly((Inet6Address) InetAddress.getByName("2001:db8::1"));
        assertThat(decoded.get(7).key()).isEqualTo(667);
        assertThat(((SvcParam.Unknown) decoded.get(7)).value()).containsExactly('h', 'e', 'l', 'l', 'o');
    }

    @Test
    public void testDecodeRfc9460MandatoryAlpnIpv4Hint() throws Exception {
        // RFC 9460 Appendix D.2, Figure 9:
        // example.com. SVCB 16 foo.example.org. (alpn=h2,h3-19 mandatory=ipv4hint,alpn ipv4hint=192.0.2.1)
        DefaultDnsSvcbRecord record = decode(bytes(
                0x00, 0x10,
                3, 'f', 'o', 'o', 7, 'e', 'x', 'a', 'm', 'p', 'l', 'e', 3, 'o', 'r', 'g', 0,
                0x00, 0x00, 0x00, 0x04, 0x00, 0x01, 0x00, 0x04,
                0x00, 0x01, 0x00, 0x09, 2, 'h', '2', 5, 'h', '3', '-', '1', '9',
                0x00, 0x04, 0x00, 0x04, 192, 0, 2, 1));

        assertThat(record.priority()).isEqualTo(16);
        assertThat(record.targetName()).isEqualTo("foo.example.org.");
        assertThat(((SvcParam.Mandatory) record.params().get(0)).keys())
                .containsExactly(SvcParam.ALPN, SvcParam.IPV4HINT);
        assertThat(((SvcParam.Alpn) record.params().get(1)).protocols())
                .containsExactly(new AsciiString("h2"), new AsciiString("h3-19"));
        assertThat(((SvcParam.Ipv4Hint) record.params().get(2)).addresses())
                .containsExactly((Inet4Address) InetAddress.getByName("192.0.2.1"));
    }

    @Test
    public void testDecodeIpv4MappedIpv6Hint() throws Exception {
        byte[] mapped = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff, (byte) 192, 0, 2, 1 };
        ByteBuf rdata = Unpooled.buffer();
        rdata.writeShort(1).writeByte(0);
        rdata.writeShort(SvcParam.IPV6HINT).writeShort(16).writeBytes(mapped);

        DefaultDnsSvcbRecord record = decode(ByteBufUtil.getBytes(rdata));
        rdata.release();

        List<Inet6Address> addresses = ((SvcParam.Ipv6Hint) record.params().get(0)).addresses();
        assertThat(addresses).hasSize(1);
        assertThat(addresses.get(0)).isInstanceOf(Inet6Address.class);
        assertThat(addresses.get(0).getAddress()).containsExactly(mapped);
    }

    @Test
    public void testDecodeAcceptsUnknownInvalidKeyEmptyEchAndInconsistentParams() {
        ByteBuf rdata = Unpooled.buffer();
        rdata.writeShort(1).writeByte(0);
        rdata.writeShort(SvcParam.MANDATORY).writeShort(2).writeShort(SvcParam.PORT); // port is absent
        rdata.writeShort(SvcParam.NO_DEFAULT_ALPN).writeShort(0); // without alpn
        rdata.writeShort(SvcParam.ECH).writeShort(0);
        rdata.writeShort(SvcParam.INVALID_KEY).writeShort(1).writeByte(7);

        DefaultDnsSvcbRecord record = decode(ByteBufUtil.getBytes(rdata));
        rdata.release();

        assertThat(record.params()).hasSize(4);
        assertThat(((SvcParam.Ech) record.params().get(2)).echConfigList()).isEmpty();
        assertThat(record.params().get(3)).isInstanceOf(SvcParam.Unknown.class);
        assertThat(record.params().get(3).key()).isEqualTo(SvcParam.INVALID_KEY);
    }

    @Test
    public void testDecodeStaysWithinRdataAndKeepsReaderIndex() {
        byte[] rdata = encodeRdata(1, ".", Collections.<SvcParam>singletonList(new SvcParam.Port(443)));
        ByteBuf in = Unpooled.buffer();
        in.writeBytes(new byte[] { 9, 9, 9 }); // bytes before the RDATA
        in.writeBytes(rdata);
        in.writeShort(SvcParam.ECH).writeShort(0); // bytes after the RDATA, which must not be read
        int readerIndex = in.readerIndex();
        try {
            DnsSvcbRecord record = DnsSvcbUtil.decodeRecord("example.com.", DnsRecordType.SVCB, DnsRecord.CLASS_IN, 300,
                    in, 3, rdata.length);
            assertThat(record.params()).hasSize(1);
            assertThat(in.readerIndex()).isEqualTo(readerIndex);
        } finally {
            in.release();
        }
    }

    @Test
    public void testDecodeRejectsMalformedRdata() {
        // too short for SvcPriority and TargetName
        assertMalformed();
        assertMalformed(0);
        assertMalformed(0, 1);
        // truncated or invalid TargetName
        assertMalformed(0, 1, 3, 'f', 'o');
        assertMalformed(0, 1, 3, 'f', 'o', 'o');
        assertMalformed(0, 1, 0xC0, 0x0C); // compression pointer
        assertMalformed(0, 1, 0x40, 0); // reserved label type
        assertMalformed(0, 1, 0x80, 0); // reserved label type
        // the end of the RDATA occurs within a SvcParam
        assertMalformed(0, 1, 0, 0, 3);
        assertMalformed(0, 1, 0, 0, 3, 0, 2, 1);
        // SvcParamKeys not in strictly increasing order, or duplicated
        assertMalformed(0, 1, 0, 0, 3, 0, 2, 1, 187, 0, 1, 0, 3, 2, 'h', '3');
        assertMalformed(0, 1, 0, 0, 3, 0, 2, 1, 187, 0, 3, 0, 2, 1, 187);
        // alpn
        assertMalformed(0, 1, 0, 0, 1, 0, 0);
        assertMalformed(0, 1, 0, 0, 1, 0, 1, 0);
        assertMalformed(0, 1, 0, 0, 1, 0, 3, 3, 'h', '3');
        assertMalformed(0, 1, 0, 0, 1, 0, 4, 2, 'h', '3', 0);
        // no-default-alpn
        assertMalformed(0, 1, 0, 0, 1, 0, 3, 2, 'h', '3', 0, 2, 0, 1, 0);
        // port
        assertMalformed(0, 1, 0, 0, 3, 0, 1, 1);
        assertMalformed(0, 1, 0, 0, 3, 0, 3, 1, 187, 0);
        // ipv4hint
        assertMalformed(0, 1, 0, 0, 4, 0, 0);
        assertMalformed(0, 1, 0, 0, 4, 0, 5, 192, 0, 2, 1, 0);
        // ipv6hint
        assertMalformed(0, 1, 0, 0, 6, 0, 0);
        assertMalformed(0, 1, 0, 0, 6, 0, 3, 0x20, 0x01, 0x0d);
        // mandatory
        assertMalformed(0, 1, 0, 0, 0, 0, 0, 3, 0, 2, 1, 187);
        assertMalformed(0, 1, 0, 0, 0, 0, 1, 3, 0, 3, 0, 2, 1, 187);
        assertMalformed(0, 1, 0, 0, 0, 0, 2, 0, 0, 0, 3, 0, 2, 1, 187);
        assertMalformed(0, 1, 0, 0, 0, 0, 4, 0, 3, 0, 1, 0, 1, 0, 3, 2, 'h', '3', 0, 3, 0, 2, 1, 187);
        assertMalformed(0, 1, 0, 0, 0, 0, 4, 0, 3, 0, 3, 0, 3, 0, 2, 1, 187);
    }

    @Test
    public void testDecodeEveryTruncation() throws Exception {
        byte[] rdata = encodeRdata(1, "svc.example.com.", Arrays.<SvcParam>asList(
                new SvcParam.Alpn(Arrays.asList(new AsciiString("h2"), new AsciiString("h3"))),
                new SvcParam.Port(8443),
                new SvcParam.Ipv6Hint(Collections.singletonList(
                        (Inet6Address) InetAddress.getByName("2001:db8::1")))));
        for (int length = 0; length <= rdata.length; length++) {
            ByteBuf in = Unpooled.wrappedBuffer(rdata);
            try {
                DnsSvcbUtil.decodeRecord("example.com.", DnsRecordType.SVCB, DnsRecord.CLASS_IN, 300, in, 0, length);
            } catch (CorruptedFrameException expected) {
                // A truncation that does not end exactly on a SvcParam boundary is malformed.
            } finally {
                in.release();
            }
        }
    }

    private static void assertMalformed(int... rdata) {
        assertThatThrownBy(() -> decode(bytes(rdata))).isInstanceOf(CorruptedFrameException.class);
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }

    private static DefaultDnsSvcbRecord decode(byte[] rdata) {
        ByteBuf in = Unpooled.wrappedBuffer(rdata);
        try {
            return DnsSvcbUtil.decodeRecord("example.com.", DnsRecordType.SVCB, DnsRecord.CLASS_IN, 300,
                    in, 0, rdata.length);
        } finally {
            in.release();
        }
    }

    private static byte[] encodeRdata(int priority, String targetName, List<SvcParam> params) {
        ByteBuf out = Unpooled.buffer();
        try {
            DnsSvcbUtil.encodeRdata(priority, targetName, params, out);
            return ByteBufUtil.getBytes(out);
        } finally {
            out.release();
        }
    }
}
