/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.dns;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.CharsetUtil;
import org.junit.Test;
import static org.junit.Assert.*;

public class Ipv4AddressRecordTest {

    @Test
    public void test() {
        Ipv4AddressRecord rec = new Ipv4AddressRecord("foo.com", DnsType.A, DnsClass.IN, 300, "192.168.2.1");
        int val = rec.address();
        assertEquals("192.168.2.1", rec.stringValue());
        Ipv4AddressRecord rec2 = new Ipv4AddressRecord("foo.com", DnsType.A, DnsClass.IN, 300, val);
        assertEquals(rec, rec2);
        assertEquals("192.168.2.1", rec.stringValue());
        Ipv4AddressRecord rec3 = new Ipv4AddressRecord("foo.com", DnsType.A, DnsClass.IN, 500, val);
        assertNotEquals(rec, rec3);
        assertEquals("192.168.2.1", rec.stringValue());
        Ipv4AddressRecord rec4 = new Ipv4AddressRecord("bar.com", DnsType.A, DnsClass.IN, 300, val);
        assertNotEquals(rec, rec4);
        assertEquals("192.168.2.1", rec.stringValue());

        ByteBufAllocator alloc = ByteBufAllocator.DEFAULT;
        ByteBuf buf = alloc.buffer();

        rec.writePayload(NameWriter.DEFAULT, buf, CharsetUtil.UTF_8);

        Ipv4AddressRecord decoded = DnsType.A.decode("foo.com", DnsClass.IN, 300, buf, buf.readableBytes());
        assertEquals(rec, decoded);

        CNameDnsRecord cname = new CNameDnsRecord("foo.com", DnsType.CNAME, DnsClass.IN, 300, "bar.com");
        buf = alloc.buffer();
        cname.writePayload(NameWriter.DEFAULT, buf, CharsetUtil.UTF_8);
        CNameDnsRecord cnameDecoded = DnsType.CNAME.decode("foo.com", DnsClass.IN, 300, buf, buf.readableBytes());
        assertEquals(cname, cnameDecoded);

        MailExchangerDnsRecord mx = new MailExchangerDnsRecord("foo.com", DnsType.MX, DnsClass.IN, 300, 5, "bar.com");
        buf = alloc.buffer();
        mx.writePayload(NameWriter.DEFAULT, buf, CharsetUtil.UTF_8);
        MailExchangerDnsRecord mxDecoded = DnsType.MX.decode("foo.com", DnsClass.IN, 300, buf, buf.readableBytes());
        assertEquals(mx, mxDecoded);

        StartOfAuthorityDnsRecord soa = new StartOfAuthorityDnsRecord("foo.com", DnsType.SOA, DnsClass.IN,
                "ns.com", "bo@ns.com", 1L, 2L, 3L, 4L, 5L, 6L);
        buf = alloc.buffer();
        soa.writePayload(NameWriter.DEFAULT, buf, CharsetUtil.UTF_8);
        StartOfAuthorityDnsRecord soaDecoded = DnsType.SOA.decode("foo.com", DnsClass.IN, 6L, buf, buf.readableBytes());
        assertEquals(soa, soaDecoded);

        PointerDnsRecord ptr = new PointerDnsRecord("foo.com", DnsType.PTR, DnsClass.IN, "bar.com", 300);
        buf = alloc.buffer();
        ptr.writePayload(NameWriter.DEFAULT, buf, CharsetUtil.UTF_8);
        PointerDnsRecord ptrDecoded = DnsType.PTR.decode("foo.com", DnsClass.IN, 300, buf, buf.readableBytes());
        assertEquals(ptr, ptrDecoded);
    }
}
