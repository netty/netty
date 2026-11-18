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
import io.netty.util.AsciiString;
import org.junit.jupiter.api.Test;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The wire format test vectors of
 * <a href="https://www.rfc-editor.org/rfc/rfc9460.html#appendix-D">RFC 9460, Appendix D</a>, checked in both
 * directions: the RDATA decodes to the expected record, and the record encodes to the same RDATA.
 */
public class Rfc9460TestVectorsTest {

    private static final String FOO_EXAMPLE_COM = "03 66 6f 6f 07 65 78 61 6d 70 6c 65 03 63 6f 6d 00";
    private static final String FOO_EXAMPLE_ORG = "03 66 6f 6f 07 65 78 61 6d 70 6c 65 03 6f 72 67 00";

    // Figure 2: example.com. HTTPS 0 foo.example.com.
    @Test
    public void testAliasMode() {
        assertVector(DnsRecordType.HTTPS, 0, "foo.example.com.", Collections.<SvcParam>emptyList(),
                "00 00 " + FOO_EXAMPLE_COM);
    }

    // Figure 3: example.com. SVCB 1 .
    @Test
    public void testTargetNameIsRoot() {
        assertVector(DnsRecordType.SVCB, 1, ".", Collections.<SvcParam>emptyList(), "00 01 00");
    }

    // Figure 4: example.com. SVCB 16 foo.example.com. port=53
    @Test
    public void testPort() {
        assertVector(DnsRecordType.SVCB, 16, "foo.example.com.",
                Collections.<SvcParam>singletonList(new SvcParam.Port(53)),
                "00 10 " + FOO_EXAMPLE_COM + " 00 03 00 02 00 35");
    }

    // Figure 5: example.com. SVCB 1 foo.example.com. key667=hello
    @Test
    public void testGenericKeyUnquotedValue() {
        assertVector(DnsRecordType.SVCB, 1, "foo.example.com.",
                Collections.<SvcParam>singletonList(new SvcParam.Unknown(667, bytes("68 65 6c 6c 6f"))),
                "00 01 " + FOO_EXAMPLE_COM + " 02 9b 00 05 68 65 6c 6c 6f");
    }

    // Figure 6: example.com. SVCB 1 foo.example.com. key667="hello\210qoo"
    @Test
    public void testGenericKeyQuotedValueWithDecimalEscape() {
        assertVector(DnsRecordType.SVCB, 1, "foo.example.com.",
                Collections.<SvcParam>singletonList(new SvcParam.Unknown(667, bytes("68 65 6c 6c 6f d2 71 6f 6f"))),
                "00 01 " + FOO_EXAMPLE_COM + " 02 9b 00 09 68 65 6c 6c 6f d2 71 6f 6f");
    }

    // Figure 7: example.com. SVCB 1 foo.example.com. ipv6hint="2001:db8::1,2001:db8::53:1"
    @Test
    public void testTwoIpv6Hints() throws Exception {
        assertVector(DnsRecordType.SVCB, 1, "foo.example.com.",
                Collections.<SvcParam>singletonList(new SvcParam.Ipv6Hint(Arrays.asList(
                        ipv6("20 01 0d b8 00 00 00 00 00 00 00 00 00 00 00 01"),
                        ipv6("20 01 0d b8 00 00 00 00 00 00 00 00 00 53 00 01")))),
                "00 01 " + FOO_EXAMPLE_COM + " 00 06 00 20" +
                        " 20 01 0d b8 00 00 00 00 00 00 00 00 00 00 00 01" +
                        " 20 01 0d b8 00 00 00 00 00 00 00 00 00 53 00 01");
    }

    // Figure 8: example.com. SVCB 1 example.com. ipv6hint="2001:db8:122:344::192.0.2.33"
    @Test
    public void testIpv6HintWithEmbeddedIpv4() throws Exception {
        assertVector(DnsRecordType.SVCB, 1, "example.com.",
                Collections.<SvcParam>singletonList(new SvcParam.Ipv6Hint(Collections.singletonList(
                        ipv6("20 01 0d b8 01 22 03 44 00 00 00 00 c0 00 02 21")))),
                "00 01 07 65 78 61 6d 70 6c 65 03 63 6f 6d 00 00 06 00 10" +
                        " 20 01 0d b8 01 22 03 44 00 00 00 00 c0 00 02 21");
    }

    // Figure 9: example.com. SVCB 16 foo.example.org. alpn=h2,h3-19 mandatory=ipv4hint,alpn ipv4hint=192.0.2.1
    @Test
    public void testKeysSortedInWireFormat() throws Exception {
        assertVector(DnsRecordType.SVCB, 16, "foo.example.org.",
                Arrays.<SvcParam>asList(
                        new SvcParam.Mandatory(Arrays.asList(SvcParam.ALPN, SvcParam.IPV4HINT)),
                        new SvcParam.Alpn(Arrays.asList(new AsciiString("h2"), new AsciiString("h3-19"))),
                        new SvcParam.Ipv4Hint(Collections.singletonList(
                                (Inet4Address) InetAddress.getByAddress(bytes("c0 00 02 01"))))),
                "00 10 " + FOO_EXAMPLE_ORG +
                        " 00 00 00 04 00 01 00 04" +
                        " 00 01 00 09 02 68 32 05 68 33 2d 31 39" +
                        " 00 04 00 04 c0 00 02 01");
    }

    // Figure 10: example.com. SVCB 16 foo.example.org. alpn="f\\\\oo\\,bar,h2"
    @Test
    public void testAlpnWithEscapedCommaAndBackslash() {
        assertVector(DnsRecordType.SVCB, 16, "foo.example.org.",
                Collections.<SvcParam>singletonList(new SvcParam.Alpn(Arrays.asList(
                        new AsciiString(bytes("66 5c 6f 6f 2c 62 61 72")), new AsciiString("h2")))),
                "00 10 " + FOO_EXAMPLE_ORG + " 00 01 00 0c 08 66 5c 6f 6f 2c 62 61 72 02 68 32");
    }

    private static void assertVector(DnsRecordType type, int priority, String targetName, List<SvcParam> params,
                                     String rdataHex) {
        byte[] rdata = bytes(rdataHex);
        DefaultDnsSvcbRecord expected = new DefaultDnsSvcbRecord("example.com.", type, 300, priority, targetName,
                params);

        ByteBuf in = Unpooled.wrappedBuffer(rdata);
        try {
            DefaultDnsSvcbRecord decoded = DnsSvcbUtil.decodeRecord("example.com.", type, DnsRecord.CLASS_IN, 300,
                    in, 0, rdata.length);
            assertThat(decoded).isEqualTo(expected);
        } finally {
            in.release();
        }

        ByteBuf out = Unpooled.buffer();
        try {
            DnsRecordEncoder.DEFAULT.encodeRecord(expected, out);
            // owner name "example.com." (13) + type (2) + class (2) + ttl (4) + rdlength (2)
            assertThat(out.getUnsignedShort(21)).isEqualTo(rdata.length);
            assertThat(ByteBufUtil.getBytes(out, 23, out.readableBytes() - 23)).containsExactly(rdata);
        } catch (Exception e) {
            throw new AssertionError(e);
        } finally {
            out.release();
        }
    }

    private static Inet6Address ipv6(String hex) throws Exception {
        return Inet6Address.getByAddress(null, bytes(hex), -1);
    }

    private static byte[] bytes(String hex) {
        String[] parts = hex.trim().split("\\s+");
        byte[] bytes = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            bytes[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return bytes;
    }
}
