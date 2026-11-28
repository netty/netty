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
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.CorruptedFrameException;
import org.junit.jupiter.api.Test;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SvcbDecoderConfigTest {

    @Test
    public void testDefaults() {
        SvcbDecoderConfig config = new SvcbDecoderConfig();

        assertThat(config.maxParams()).isEqualTo(64);
        assertThat(config.maxAlpnIds()).isEqualTo(64);
        assertThat(config.maxHintAddresses()).isEqualTo(64);
    }

    @Test
    public void testLimitsMustBePositive() {
        assertThatThrownBy(() -> new SvcbDecoderConfig().maxParams(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SvcbDecoderConfig().maxAlpnIds(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SvcbDecoderConfig().maxHintAddresses(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testRecordAboveMaxParamsIsMalformed() {
        List<SvcParam> params = Arrays.<SvcParam>asList(alpn(1), new SvcParam.Port(8443),
                new SvcParam.Ech(new byte[] { 1 }));

        assertThat(decode(params, new SvcbDecoderConfig().maxParams(3)).params()).hasSize(3);
        assertThatThrownBy(() -> decode(params, new SvcbDecoderConfig().maxParams(2)))
                .isInstanceOf(CorruptedFrameException.class).hasMessage("more than 2 SvcParams");
    }

    @Test
    public void testAlpnAboveMaxAlpnIdsIsMalformed() {
        List<SvcParam> params = Collections.<SvcParam>singletonList(alpn(3));

        assertThat(decode(params, new SvcbDecoderConfig().maxAlpnIds(3)).params()).hasSize(1);
        assertThatThrownBy(() -> decode(params, new SvcbDecoderConfig().maxAlpnIds(2)))
                .isInstanceOf(CorruptedFrameException.class).hasMessage("more than 2 alpn-ids");
    }

    @Test
    public void testHintsAboveMaxHintAddressesAreMalformed() throws Exception {
        List<SvcParam> ipv4 = Collections.<SvcParam>singletonList(new SvcParam.Ipv4Hint(Arrays.asList(
                (Inet4Address) InetAddress.getByName("192.0.2.1"), (Inet4Address) InetAddress.getByName("192.0.2.2"),
                (Inet4Address) InetAddress.getByName("192.0.2.3"))));
        List<SvcParam> ipv6 = Collections.<SvcParam>singletonList(new SvcParam.Ipv6Hint(Arrays.asList(
                (Inet6Address) InetAddress.getByName("2001:db8::1"),
                (Inet6Address) InetAddress.getByName("2001:db8::2"),
                (Inet6Address) InetAddress.getByName("2001:db8::3"))));

        assertThat(decode(ipv4, new SvcbDecoderConfig().maxHintAddresses(3)).params()).hasSize(1);
        assertThatThrownBy(() -> decode(ipv4, new SvcbDecoderConfig().maxHintAddresses(2)))
                .isInstanceOf(CorruptedFrameException.class).hasMessage("more than 2 ipv4hint addresses");
        assertThat(decode(ipv6, new SvcbDecoderConfig().maxHintAddresses(3)).params()).hasSize(1);
        assertThatThrownBy(() -> decode(ipv6, new SvcbDecoderConfig().maxHintAddresses(2)))
                .isInstanceOf(CorruptedFrameException.class).hasMessage("more than 2 ipv6hint addresses");
    }

    @Test
    public void testDefaultLimitsApplyToRawRecords() {
        DnsRawRecord raw = raw(Collections.<SvcParam>singletonList(alpn(65)));
        try {
            assertThatThrownBy(() -> new DefaultDnsSvcbRecord(raw))
                    .isInstanceOf(CorruptedFrameException.class).hasMessage("more than 64 alpn-ids");
        } finally {
            raw.release();
        }
    }

    @Test
    public void testDecoderCopiesTheConfig() throws Exception {
        SvcbDecoderConfig config = new SvcbDecoderConfig().maxAlpnIds(2);
        DnsRecordDecoder decoder = new DefaultDnsRecordDecoder(config);
        config.maxAlpnIds(64);
        ByteBuf in = Unpooled.buffer();
        DnsRecordEncoder.DEFAULT.encodeRecord(new DefaultDnsSvcbRecord("example.com.", DnsRecordType.HTTPS, 300, 1,
                ".", Collections.<SvcParam>singletonList(alpn(3))), in);
        try {
            assertThatThrownBy(() -> decoder.decodeRecord(in))
                    .isInstanceOf(CorruptedFrameException.class).hasMessage("more than 2 alpn-ids");
        } finally {
            in.release();
        }
    }

    private static DefaultDnsSvcbRecord decode(List<SvcParam> params, SvcbDecoderConfig config) {
        DnsRawRecord raw = raw(params);
        try {
            return new DefaultDnsSvcbRecord(raw, config);
        } finally {
            raw.release();
        }
    }

    private static DnsRawRecord raw(List<SvcParam> params) {
        ByteBuf rdata = Unpooled.buffer();
        DnsSvcbUtil.encodeRdata(1, ".", params, rdata);
        return new DefaultDnsRawRecord("example.com.", DnsRecordType.HTTPS, DnsRecord.CLASS_IN, 300, rdata);
    }

    private static SvcParam alpn(int ids) {
        List<ProtocolId> protocols = new ArrayList<ProtocolId>(ids);
        for (int i = 0; i < ids; i++) {
            protocols.add(ProtocolId.valueOf("p" + i));
        }
        return new SvcParam.Alpn(protocols);
    }
}
