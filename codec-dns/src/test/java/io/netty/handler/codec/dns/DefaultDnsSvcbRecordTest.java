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
import org.junit.jupiter.api.Test;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DefaultDnsSvcbRecordTest {

    @Test
    public void testFromRawRecord() {
        ByteBuf rdata = Unpooled.buffer();
        DnsSvcbUtil.encodeRdata(1, "svc.example.com.", Arrays.<SvcParam>asList(
                new SvcParam.Alpn(Collections.singletonList(ProtocolId.H3)),
                new SvcParam.Port(8443)), rdata);
        DnsRawRecord raw = new DefaultDnsRawRecord("example.com.", DnsRecordType.SVCB, DnsRecord.CLASS_CHAOS,
                300, rdata);
        try {
            DefaultDnsSvcbRecord record = new DefaultDnsSvcbRecord(raw);

            assertThat(record.name()).isEqualTo("example.com.");
            assertThat(record.type()).isEqualTo(DnsRecordType.SVCB);
            assertThat(record.dnsClass()).isEqualTo(DnsRecord.CLASS_CHAOS);
            assertThat(record.timeToLive()).isEqualTo(300);
            assertThat(record.priority()).isEqualTo(1);
            assertThat(record.targetName()).isEqualTo("svc.example.com.");
            assertThat(((SvcParam.Alpn) record.params().get(0)).protocols())
                    .containsExactly(ProtocolId.H3);
            assertThat(((SvcParam.Port) record.params().get(1)).port()).isEqualTo(8443);
        } finally {
            raw.release();
        }
    }

    @Test
    public void testFromRawRecordOnlyReadsContent() {
        ByteBuf content = Unpooled.buffer();
        content.writeBytes(new byte[] { 9, 9 }); // bytes before the RDATA
        DnsSvcbUtil.encodeRdata(1, ".", Collections.<SvcParam>singletonList(new SvcParam.Port(443)), content);
        content.readerIndex(2);
        DnsRawRecord raw = new DefaultDnsRawRecord("example.com.", DnsRecordType.SVCB, 300, content);
        try {
            DefaultDnsSvcbRecord record = new DefaultDnsSvcbRecord(raw);

            assertThat(((SvcParam.Port) record.params().get(0)).port()).isEqualTo(443);
            assertThat(content.readerIndex()).isEqualTo(2);
            assertThat(raw.refCnt()).isEqualTo(1);
        } finally {
            raw.release();
        }
    }

    @Test
    public void testFromRawRecordProducedByDefaultDecoder() throws Exception {
        ByteBuf in = Unpooled.buffer();
        DnsRecordEncoder.DEFAULT.encodeRecord(new DefaultDnsSvcbRecord("example.com.", 300, 1,
                "svc.example.com.", Collections.<SvcParam>singletonList(new SvcParam.Port(8443))), in);
        DnsRawRecord raw = DnsRecordDecoder.DEFAULT.decodeRecord(in);
        try {
            DefaultDnsSvcbRecord record = new DefaultDnsSvcbRecord(raw);

            assertThat(record.targetName()).isEqualTo("svc.example.com.");
            assertThat(((SvcParam.Port) record.params().get(0)).port()).isEqualTo(8443);
        } finally {
            raw.release();
            in.release();
        }
    }

    @Test
    public void testFromMalformedRawRecord() {
        // SvcPriority 1, root TargetName, then a port SvcParam with a 1-byte value
        DnsRawRecord raw = new DefaultDnsRawRecord("example.com.", DnsRecordType.SVCB, 300,
                Unpooled.wrappedBuffer(new byte[] { 0, 1, 0, 0, 3, 0, 1, 1 }));
        try {
            assertThatThrownBy(() -> new DefaultDnsSvcbRecord(raw)).isInstanceOf(CorruptedFrameException.class);
            assertThat(raw.refCnt()).isEqualTo(1);
        } finally {
            raw.release();
        }
    }

    @Test
    public void testFromRawRecordOfWrongType() {
        DnsRawRecord raw = new DefaultDnsRawRecord("example.com.", DnsRecordType.A, 300,
                Unpooled.wrappedBuffer(new byte[] { (byte) 192, 0, 2, 1 }));
        try {
            assertThatThrownBy(() -> new DefaultDnsSvcbRecord(raw)).isInstanceOf(IllegalArgumentException.class);
        } finally {
            raw.release();
        }
    }

    @Test
    public void testFromNullRawRecord() {
        assertThatThrownBy(() -> new DefaultDnsSvcbRecord((DnsRawRecord) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testToString() throws Exception {
        DefaultDnsSvcbRecord record = new DefaultDnsSvcbRecord("example.com.", 300, 1, "svc.example.com.",
                Arrays.<SvcParam>asList(
                        new SvcParam.Mandatory(Arrays.asList(SvcParam.PORT, SvcParam.ALPN)),
                        new SvcParam.Alpn(Arrays.asList(ProtocolId.H2, ProtocolId.H3)),
                        SvcParam.NoDefaultAlpn.INSTANCE,
                        new SvcParam.Port(8443),
                        new SvcParam.Ipv4Hint(Arrays.asList(
                                (Inet4Address) InetAddress.getByName("192.0.2.1"),
                                (Inet4Address) InetAddress.getByName("192.0.2.2"))),
                        new SvcParam.Ech(new byte[] { 1, 2, 3 }),
                        new SvcParam.Ipv6Hint(Collections.singletonList(
                                (Inet6Address) InetAddress.getByName("2001:db8:0:0:0:0:0:1"))),
                        new SvcParam.Unknown(667, new byte[] { 'h', 'e', 'l', 'l', 'o' })));

        assertThat(record.toString()).isEqualTo("DefaultDnsSvcbRecord(example.com. 300 IN SVCB 1 svc.example.com."
                + " mandatory=alpn,port alpn=h2,h3 no-default-alpn port=8443 ipv4hint=192.0.2.1,192.0.2.2"
                + " ech=010203 ipv6hint=2001:db8::1 key667=68656c6c6f");
    }

    @Test
    public void testToStringAliasMode() {
        DefaultDnsSvcbRecord record = new DefaultDnsSvcbRecord("example.com.", 300, 0, "svc.example.com.",
                Collections.<SvcParam>emptyList());

        assertThat(record.toString()).isEqualTo("DefaultDnsSvcbRecord(example.com. 300 IN SVCB 0 svc.example.com.");
    }

    @Test
    public void testSvcParamToString() {
        assertThat(new SvcParam.Unknown(SvcParam.ALPN, new byte[] { 2, 'h', '3' }).toString())
                .isEqualTo("key1=026833");
        assertThat(new SvcParam.Unknown(SvcParam.INVALID_KEY, new byte[0]).toString()).isEqualTo("key65535=");
        assertThat(new SvcParam.Mandatory(Collections.singletonList(65280)).toString())
                .isEqualTo("mandatory=key65280");
        assertThat(new SvcParam(4242) { }.toString()).isEqualTo("key4242");
    }

    @Test
    public void testHttpsRecord() {
        DefaultDnsSvcbRecord record = new DefaultDnsSvcbRecord("example.com.", DnsRecordType.HTTPS, 300, 1,
                ".", Collections.<SvcParam>singletonList(new SvcParam.Port(8443)));
        DefaultDnsSvcbRecord chaos = new DefaultDnsSvcbRecord("example.com.", DnsRecordType.HTTPS,
                DnsRecord.CLASS_CHAOS, 300, 1, ".", Collections.<SvcParam>emptyList());

        assertThat(record.type()).isEqualTo(DnsRecordType.HTTPS);
        assertThat(record.dnsClass()).isEqualTo(DnsRecord.CLASS_IN);
        assertThat(record.toString()).isEqualTo("DefaultDnsSvcbRecord(example.com. 300 IN HTTPS 1 . port=8443");
        assertThat(chaos.type()).isEqualTo(DnsRecordType.HTTPS);
        assertThat(chaos.dnsClass()).isEqualTo(DnsRecord.CLASS_CHAOS);
    }

    @Test
    public void testInvalidType() {
        assertThatThrownBy(() -> new DefaultDnsSvcbRecord("example.com.", DnsRecordType.A, 300, 1, ".",
                Collections.<SvcParam>emptyList())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DefaultDnsSvcbRecord("example.com.", (DnsRecordType) null, 300, 1, ".",
                Collections.<SvcParam>emptyList())).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testFromHttpsRawRecord() {
        ByteBuf rdata = Unpooled.buffer();
        DnsSvcbUtil.encodeRdata(1, ".", Collections.<SvcParam>singletonList(new SvcParam.Port(8443)), rdata);
        DnsRawRecord raw = new DefaultDnsRawRecord("example.com.", DnsRecordType.HTTPS, 300, rdata);
        try {
            DefaultDnsSvcbRecord record = new DefaultDnsSvcbRecord(raw);

            assertThat(record.type()).isEqualTo(DnsRecordType.HTTPS);
            assertThat(((SvcParam.Port) record.params().get(0)).port()).isEqualTo(8443);
        } finally {
            raw.release();
        }
    }

    @Test
    public void testEqualsAndHashCode() throws Exception {
        DefaultDnsSvcbRecord a = new DefaultDnsSvcbRecord("example.com.", DnsRecordType.HTTPS, 300, 1,
                "svc.example.com.", allParams());
        DefaultDnsSvcbRecord b = new DefaultDnsSvcbRecord("example.com.", DnsRecordType.HTTPS, 600, 1,
                "svc.example.com.", allParams());

        assertThat(a).isEqualTo(b);
        assertThat(b).isEqualTo(a);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a).isEqualTo(new OtherSvcbRecord(allParams(), DnsRecordType.HTTPS, 1, "svc.example.com."));
    }

    @Test
    public void testEqualsAfterHashCode() throws Exception {
        // As in a HashSet, which computes the hash code before comparing.
        DefaultDnsSvcbRecord a = new DefaultDnsSvcbRecord("example.com.", DnsRecordType.HTTPS, 300, 1,
                "svc.example.com.", allParams());
        DefaultDnsSvcbRecord b = new DefaultDnsSvcbRecord("example.com.", DnsRecordType.HTTPS, 300, 1,
                "svc.example.com.", allParams());
        assertThat(a.hashCode()).isEqualTo(b.hashCode());

        assertThat(a).isEqualTo(b);
        assertThat(new HashSet<DnsSvcbRecord>(Arrays.asList(a, b))).hasSize(1);
    }

    @Test
    public void testNotEquals() throws Exception {
        DefaultDnsSvcbRecord record = new DefaultDnsSvcbRecord("example.com.", DnsRecordType.HTTPS, 300, 1,
                "svc.example.com.", allParams());

        assertThat(record).isNotEqualTo(new DefaultDnsSvcbRecord("other.com.", DnsRecordType.HTTPS, 300, 1,
                "svc.example.com.", allParams()));
        assertThat(record).isNotEqualTo(new DefaultDnsSvcbRecord("example.com.", DnsRecordType.SVCB, 300, 1,
                "svc.example.com.", allParams()));
        assertThat(record).isNotEqualTo(new DefaultDnsSvcbRecord("example.com.", DnsRecordType.HTTPS, 300, 2,
                "svc.example.com.", allParams()));
        assertThat(record).isNotEqualTo(new DefaultDnsSvcbRecord("example.com.", DnsRecordType.HTTPS, 300, 1,
                "other.example.com.", allParams()));
        assertThat(record).isNotEqualTo(new DefaultDnsSvcbRecord("example.com.", DnsRecordType.HTTPS, 300, 1,
                "svc.example.com.", Collections.<SvcParam>singletonList(new SvcParam.Port(8443))));
        assertThat(record).isNotEqualTo(new DefaultDnsRawRecord("example.com.", DnsRecordType.HTTPS, 300,
                Unpooled.EMPTY_BUFFER));
    }

    @Test
    public void testSvcParamEqualsAndHashCode() throws Exception {
        List<SvcParam> first = allParams();
        List<SvcParam> second = allParams();
        for (int i = 0; i < first.size(); i++) {
            assertThat(first.get(i)).isEqualTo(second.get(i));
            assertThat(first.get(i).hashCode()).isEqualTo(second.get(i).hashCode());
        }
        assertThat(new SvcParam.Port(443)).isNotEqualTo(new SvcParam.Port(8443));
        assertThat(alpn(ProtocolId.H2)).isNotEqualTo(alpn(ProtocolId.H3));
        assertThat(new SvcParam.Ech(new byte[] { 1 })).isNotEqualTo(new SvcParam.Ech(new byte[] { 2 }));
        assertThat(new SvcParam.Unknown(667, new byte[] { 1 }))
                .isNotEqualTo(new SvcParam.Unknown(668, new byte[] { 1 }));
        assertThat(new SvcParam.Unknown(SvcParam.PORT, new byte[] { 1, (byte) 187 }))
                .isNotEqualTo(new SvcParam.Port(443));
    }

    @Test
    public void testIpHints() throws Exception {
        Inet4Address v4 = (Inet4Address) InetAddress.getByName("192.0.2.1");
        Inet6Address v6 = (Inet6Address) InetAddress.getByName("2001:db8::1");
        SvcParam.IpHint<Inet4Address> ipv4Hint = new SvcParam.Ipv4Hint(Collections.singletonList(v4));
        SvcParam.IpHint<Inet6Address> ipv6Hint = new SvcParam.Ipv6Hint(Collections.singletonList(v6));

        assertThat(ipv4Hint.key()).isEqualTo(SvcParam.IPV4HINT);
        assertThat(ipv4Hint.addresses()).containsExactly(v4);
        assertThat(ipv4Hint).hasToString("ipv4hint=192.0.2.1");
        assertThat(ipv6Hint.key()).isEqualTo(SvcParam.IPV6HINT);
        assertThat(ipv6Hint.addresses()).containsExactly(v6);
        assertThat(ipv6Hint).hasToString("ipv6hint=2001:db8::1");
        assertThat(ipv4Hint).isNotEqualTo(ipv6Hint);
        assertThatThrownBy(() -> new SvcParam.Ipv4Hint(Collections.<Inet4Address>emptyList()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SvcParam.Ipv6Hint(Collections.<Inet6Address>singletonList(null)))
                .isInstanceOf(NullPointerException.class);
    }

    private static List<SvcParam> allParams() throws Exception {
        return Arrays.<SvcParam>asList(
                new SvcParam.Mandatory(Collections.singletonList(SvcParam.ALPN)),
                alpn(ProtocolId.H3),
                SvcParam.NoDefaultAlpn.INSTANCE,
                new SvcParam.Port(8443),
                new SvcParam.Ipv4Hint(Collections.singletonList((Inet4Address) InetAddress.getByName("192.0.2.1"))),
                new SvcParam.Ech(new byte[] { 1, 2, 3 }),
                new SvcParam.Ipv6Hint(Collections.singletonList((Inet6Address) InetAddress.getByName("2001:db8::1"))),
                new SvcParam.Unknown(667, new byte[] { 'h', 'i' }));
    }

    @Test
    public void testParamsAreCopied() {
        List<SvcParam> params = new ArrayList<SvcParam>();
        params.add(new SvcParam.Port(443));
        DefaultDnsSvcbRecord record = new DefaultDnsSvcbRecord("example.com.", 300, 1, "svc.example.com.", params);
        params.add(new SvcParam.Port(8443));

        assertThat(record.params()).hasSize(1);
        assertThatThrownBy(() -> record.params().add(alpn(ProtocolId.H3)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void testNullParam() {
        assertThatThrownBy(() -> new DefaultDnsSvcbRecord("example.com.", 300, 1, "svc.example.com.",
                Arrays.<SvcParam>asList(new SvcParam.Port(443), null)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testParamsAreSortedByKey() {
        SvcParam port = new SvcParam.Port(443);
        SvcParam otherPort = new SvcParam.Port(8443);
        SvcParam alpn = alpn(ProtocolId.H3);

        assertThat(record(port, alpn).params()).containsExactly(alpn, port);
        assertThat(record(port, alpn)).isEqualTo(record(alpn, port));
        assertThat(record(otherPort, alpn, port).params()).containsExactly(alpn, otherPort, port);
    }

    @Test
    public void testIsSelfConsistent() {
        assertThat(record(new SvcParam.Mandatory(Collections.singletonList(SvcParam.PORT)), new SvcParam.Port(443))
                .isSelfConsistent()).isTrue();
        assertThat(record(SvcParam.NoDefaultAlpn.INSTANCE, alpn(ProtocolId.H3)).isSelfConsistent()).isTrue();
        assertThat(record().isSelfConsistent()).isTrue();

        assertThat(record(new SvcParam.Mandatory(Arrays.asList(SvcParam.ALPN, SvcParam.PORT)), new SvcParam.Port(443))
                .isSelfConsistent()).isFalse();
        assertThat(record(SvcParam.NoDefaultAlpn.INSTANCE).isSelfConsistent()).isFalse();
    }

    @Test
    public void testIsSelfConsistentIgnoresMandatoryAsUnknown() {
        assertThat(record(new SvcParam.Unknown(SvcParam.MANDATORY, new byte[] { 0, 3 })).isSelfConsistent()).isTrue();
    }

    @Test
    public void testEncodeOtherDnsSvcbRecordImplementation() throws Exception {
        DnsSvcbRecord valid = new OtherSvcbRecord(
                Arrays.<SvcParam>asList(new SvcParam.Port(8443), alpn(ProtocolId.H3)));
        ByteBuf expected = Unpooled.buffer();
        ByteBuf out = Unpooled.buffer();
        try {
            DnsRecordEncoder.DEFAULT.encodeRecord(record(new SvcParam.Port(8443), alpn(ProtocolId.H3)), expected);
            DnsRecordEncoder.DEFAULT.encodeRecord(valid, out);
            assertThat(ByteBufUtil.getBytes(out)).containsExactly(ByteBufUtil.getBytes(expected));
        } finally {
            expected.release();
            out.release();
        }

        DnsSvcbRecord duplicates = new OtherSvcbRecord(
                Arrays.<SvcParam>asList(new SvcParam.Port(443), new SvcParam.Port(8443)));
        ByteBuf ignored = Unpooled.buffer();
        try {
            assertThatThrownBy(() -> DnsRecordEncoder.DEFAULT.encodeRecord(duplicates, ignored))
                    .isInstanceOf(IllegalArgumentException.class);
        } finally {
            ignored.release();
        }
    }

    private static DefaultDnsSvcbRecord record(SvcParam... params) {
        return new DefaultDnsSvcbRecord("example.com.", 300, 1, "svc.example.com.", Arrays.asList(params));
    }

    private static SvcParam alpn(ProtocolId protocol) {
        return new SvcParam.Alpn(Collections.singletonList(protocol));
    }

    private static final class OtherSvcbRecord extends AbstractDnsRecord implements DnsSvcbRecord {
        private final List<SvcParam> params;

        private final int priority;
        private final String targetName;

        OtherSvcbRecord(List<SvcParam> params) {
            this(params, DnsRecordType.SVCB, 1, "svc.example.com.");
        }

        OtherSvcbRecord(List<SvcParam> params, DnsRecordType type, int priority, String targetName) {
            super("example.com.", type, 300);
            this.params = params;
            this.priority = priority;
            this.targetName = targetName;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public String targetName() {
            return targetName;
        }

        @Override
        public List<SvcParam> params() {
            return params;
        }
    }

    @Test
    public void testRoundTripThroughRawRecord() throws Exception {
        DefaultDnsSvcbRecord original = new DefaultDnsSvcbRecord("example.com.", 300, 16, "foo.example.org.",
                Arrays.<SvcParam>asList(
                        new SvcParam.Mandatory(Collections.singletonList(SvcParam.ALPN)),
                        new SvcParam.Alpn(Arrays.asList(ProtocolId.H2, ProtocolId.H3))));
        ByteBuf encoded = Unpooled.buffer();
        DnsRecordEncoder.DEFAULT.encodeRecord(original, encoded);
        DnsRawRecord raw = DnsRecordDecoder.DEFAULT.decodeRecord(encoded);
        ByteBuf reencoded = Unpooled.buffer();
        try {
            DnsRecordEncoder.DEFAULT.encodeRecord(new DefaultDnsSvcbRecord(raw), reencoded);
            encoded.readerIndex(0);
            assertThat(ByteBufUtil.getBytes(reencoded)).containsExactly(ByteBufUtil.getBytes(encoded));
        } finally {
            raw.release();
            encoded.release();
            reencoded.release();
        }
    }
}
