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
import io.netty.util.internal.SocketUtils;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DefaultDnsRecordTypesTest {

    @Test
    public void testEncodeDecodeARecord() throws Exception {
        byte[] address = new byte[] { 1, 2, 3, 4 };
        DnsARecord decoded = encodeDecode(new DefaultDnsARecord("a.example.com.",
                DnsRecord.CLASS_IN, 60, address), DnsARecord.class);
        assertArrayEquals(address, decoded.address());
    }

    @Test
    public void testEncodeDecodeAaaaRecord() throws Exception {
        InetAddress address = SocketUtils.addressByName("2001:db8::1");
        DnsAaaaRecord decoded = encodeDecode(new DefaultDnsAaaaRecord("aaaa.example.com.",
                DnsRecord.CLASS_IN, 60, address.getAddress()), DnsAaaaRecord.class);
        assertArrayEquals(address.getAddress(), decoded.address());
    }

    @Test
    public void testEncodeDecodeTxtRecord() throws Exception {
        byte[] entry1 = "v=spf1".getBytes(CharsetUtil.UTF_8);
        byte[] entry2 = "-all".getBytes(CharsetUtil.UTF_8);
        List<byte[]> content = Arrays.asList(entry1, entry2);
        DnsTxtRecord decoded = encodeDecode(new DefaultDnsTxtRecord("txt.example.com.",
                DnsRecord.CLASS_IN, 60, content), DnsTxtRecord.class);
        assertEquals(content.size(), decoded.content().size());
        assertArrayEquals(entry1, decoded.content().get(0));
        assertArrayEquals(entry2, decoded.content().get(1));
    }

    @Test
    public void testEncodeDecodeSoaRecord() throws Exception {
        DnsSoaRecord decoded = encodeDecode(new DefaultDnsSoaRecord("example.com.",
                DnsRecord.CLASS_IN, 60,
                "ns1.example.com.", "hostmaster.example.com.",
                2024010101L, 7200, 3600, 1209600, 86400), DnsSoaRecord.class);
        assertEquals("ns1.example.com.", decoded.mname());
        assertEquals("hostmaster.example.com.", decoded.rname());
        assertEquals(2024010101L, decoded.serial());
        assertEquals(7200, decoded.refresh());
        assertEquals(3600, decoded.retry());
        assertEquals(1209600, decoded.expire());
        assertEquals(86400, decoded.minimum());
    }

    @Test
    public void testEncodeDecodeCaaRecord() throws Exception {
        byte[] value = "letsencrypt.org".getBytes(CharsetUtil.US_ASCII);
        DnsCaaRecord decoded = encodeDecode(new DefaultDnsCaaRecord("caa.example.com.",
                DnsRecord.CLASS_IN, 60, 0, "issue", value), DnsCaaRecord.class);
        assertEquals(0, decoded.flags());
        assertEquals("issue", decoded.tag());
        assertArrayEquals(value, decoded.value());
    }

    @Test
    public void testEncodeDecodeCertRecord() throws Exception {
        byte[] cert = new byte[] { 1, 2, 3, 4 };
        DnsCertRecord decoded = encodeDecode(new DefaultDnsCertRecord("cert.example.com.",
                DnsRecord.CLASS_IN, 60, 1, 1234, 3, cert), DnsCertRecord.class);
        assertEquals(1, decoded.certificateType());
        assertEquals(1234, decoded.keyTag());
        assertEquals(3, decoded.algorithm());
        assertArrayEquals(cert, decoded.certificate());
    }

    @Test
    public void testEncodeDecodeDnskeyRecord() throws Exception {
        byte[] key = new byte[] { 9, 8, 7 };
        DnsDnskeyRecord decoded = encodeDecode(new DefaultDnsDnskeyRecord("dnskey.example.com.",
                DnsRecord.CLASS_IN, 60, 256, 3, 8, key), DnsDnskeyRecord.class);
        assertEquals(256, decoded.flags());
        assertEquals(3, decoded.protocol());
        assertEquals(8, decoded.algorithm());
        assertArrayEquals(key, decoded.publicKey());
    }

    @Test
    public void testEncodeDecodeDsRecord() throws Exception {
        byte[] digest = new byte[] { 10, 11, 12 };
        DnsDsRecord decoded = encodeDecode(new DefaultDnsDsRecord("ds.example.com.",
                DnsRecord.CLASS_IN, 60, 42, 8, 2, digest), DnsDsRecord.class);
        assertEquals(42, decoded.keyTag());
        assertEquals(8, decoded.algorithm());
        assertEquals(2, decoded.digestType());
        assertArrayEquals(digest, decoded.digest());
    }

    @Test
    public void testEncodeDecodeHttpsRecord() throws Exception {
        Map<Integer, byte[]> params = new LinkedHashMap<Integer, byte[]>();
        params.put(1, new byte[] { 1, 2 });
        params.put(3, new byte[] { 4 });
        DnsHttpsRecord decoded = encodeDecode(new DefaultDnsHttpsRecord("https.example.com.",
                DnsRecord.CLASS_IN, 60, 1, "svc.example.com.", params), DnsHttpsRecord.class);
        assertEquals(1, decoded.priority());
        assertEquals("svc.example.com.", decoded.targetName());
        assertParameters(params, decoded.parameters());
    }

    @Test
    public void testEncodeDecodeLocRecord() throws Exception {
        DnsLocRecord decoded = encodeDecode(new DefaultDnsLocRecord("loc.example.com.",
                DnsRecord.CLASS_IN, 60, 0, 1, 2, 3, 1000, 2000, 3000), DnsLocRecord.class);
        assertEquals(0, decoded.version());
        assertEquals(1, decoded.size());
        assertEquals(2, decoded.horizontalPrecision());
        assertEquals(3, decoded.verticalPrecision());
        assertEquals(1000, decoded.latitude());
        assertEquals(2000, decoded.longitude());
        assertEquals(3000, decoded.altitude());
    }

    @Test
    public void testEncodeDecodeNaptrRecord() throws Exception {
        DnsNaptrRecord decoded = encodeDecode(new DefaultDnsNaptrRecord("naptr.example.com.",
                DnsRecord.CLASS_IN, 60, 100, 10, "U", "E2U+sip",
                "!^.*$!sip:info@example.com!", "example.com."), DnsNaptrRecord.class);
        assertEquals(100, decoded.order());
        assertEquals(10, decoded.preference());
        assertEquals("U", decoded.flagsAsString());
        assertEquals("E2U+sip", decoded.servicesAsString());
        assertEquals("!^.*$!sip:info@example.com!", decoded.regexpAsString());
        assertEquals("example.com.", decoded.replacement());
    }

    @Test
    public void testEncodeDecodeSmimeaRecord() throws Exception {
        byte[] data = new byte[] { 5, 6, 7 };
        DnsSmimeaRecord decoded = encodeDecode(new DefaultDnsSmimeaRecord("smimea.example.com.",
                DnsRecord.CLASS_IN, 60, 0, 1, 2, data), DnsSmimeaRecord.class);
        assertEquals(0, decoded.usage());
        assertEquals(1, decoded.selector());
        assertEquals(2, decoded.matchingType());
        assertArrayEquals(data, decoded.associationData());
    }

    @Test
    public void testEncodeDecodeSrvRecord() throws Exception {
        DnsSrvRecord decoded = encodeDecode(new DefaultDnsSrvRecord("_sip._tcp.example.com.",
                DnsRecord.CLASS_IN, 60, 10, 5, 5060, "sip.example.com."), DnsSrvRecord.class);
        assertEquals(10, decoded.priority());
        assertEquals(5, decoded.weight());
        assertEquals(5060, decoded.port());
        assertEquals("sip.example.com.", decoded.target());
    }

    @Test
    public void testEncodeDecodeSshfpRecord() throws Exception {
        byte[] fingerprint = new byte[] { 1, 2, 3, 4, 5 };
        DnsSshfpRecord decoded = encodeDecode(new DefaultDnsSshfpRecord("ssh.example.com.",
                DnsRecord.CLASS_IN, 60, 1, 1, fingerprint), DnsSshfpRecord.class);
        assertEquals(1, decoded.algorithm());
        assertEquals(1, decoded.fingerprintType());
        assertArrayEquals(fingerprint, decoded.fingerprint());
    }

    @Test
    public void testEncodeDecodeSvcbRecord() throws Exception {
        Map<Integer, byte[]> params = new LinkedHashMap<Integer, byte[]>();
        params.put(3, new byte[] { 1, 0 });
        DnsSvcbRecord decoded = encodeDecode(new DefaultDnsSvcbRecord("svc.example.com.",
                DnsRecord.CLASS_IN, 60, 0, ".", params), DnsSvcbRecord.class);
        assertEquals(0, decoded.priority());
        assertEquals(".", decoded.targetName());
        assertParameters(params, decoded.parameters());
    }

    @Test
    public void testEncodeDecodeTlsaRecord() throws Exception {
        byte[] data = new byte[] { 9, 8, 7 };
        DnsTlsaRecord decoded = encodeDecode(new DefaultDnsTlsaRecord("_443._tcp.example.com.",
                DnsRecord.CLASS_IN, 60, 3, 1, 1, data), DnsTlsaRecord.class);
        assertEquals(3, decoded.usage());
        assertEquals(1, decoded.selector());
        assertEquals(1, decoded.matchingType());
        assertArrayEquals(data, decoded.associationData());
    }

    @Test
    public void testEncodeDecodeUriRecord() throws Exception {
        DnsUriRecord decoded = encodeDecode(new DefaultDnsUriRecord("uri.example.com.",
                DnsRecord.CLASS_IN, 60, 10, 1, "https://example.com/service"), DnsUriRecord.class);
        assertEquals(10, decoded.priority());
        assertEquals(1, decoded.weight());
        assertEquals("https://example.com/service", decoded.target());
    }

    private static <T extends DnsRecord> T encodeDecode(DnsRecord record, Class<T> type) throws Exception {
        DefaultDnsRecordEncoder encoder = new DefaultDnsRecordEncoder();
        DefaultDnsRecordDecoder decoder = new DefaultDnsRecordDecoder(false);
        ByteBuf out = Unpooled.buffer();
        try {
            encoder.encodeRecord(record, out);
            return type.cast(decoder.decodeRecord(out.duplicate()));
        } finally {
            out.release();
        }
    }

    private static void assertParameters(Map<Integer, byte[]> expected, Map<Integer, byte[]> actual) {
        assertEquals(expected.keySet(), actual.keySet());
        for (Map.Entry<Integer, byte[]> entry : expected.entrySet()) {
            assertArrayEquals(entry.getValue(), actual.get(entry.getKey()));
        }
    }
}
