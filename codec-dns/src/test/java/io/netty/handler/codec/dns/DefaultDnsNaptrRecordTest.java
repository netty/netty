/*
 * Copyright 2016 The Netty Project
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

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;


public class DefaultDnsNaptrRecordTest {

    private EmbeddedChannel channel;

    /**
     * Raw hex datagram response from BIND9 with the following master records
     * sip IN NAPTR 100 10 "S" "SIP+D2U" "!^.*$!sip:customer-service@dummy.se!" _sip._udp.dummy.se.
     * sip IN NAPTR 102 10 "S" "SIP+D2T" "!^.*$!sip:customer-service@dummy.se!" _sip._tcp.dummy.se.
     *
     * _sip._tcp.dummy.se. 86400 IN SRV 0 5 5060 sipserver.dummy.se.
     *
     */
    private static String NAPTR_RESPONSE =
            "626785800001000200010002037369700564756d6d790273650000230001c00c002300010000960000470064000a01530" +
                    "75349502b44325524215e2e2a24217369703a637573746f6d65722d736572766963654064756d6d792e736521" +
                    "045f736970045f7564700564756d6d7902736500c00c002300010000960000470066000a0153075349502b443" +
                    "25424215e2e2a24217369703a637573746f6d65722d736572766963654064756d6d792e736521045f73697004" +
                    "5f7463700564756d6d7902736500c0ba0002000100009600000e0c34396434356437346532633600097369707" +
                    "36572766572c0ba000100010000960000040a000002c0b00021000100015180001a0000000513c40973697073" +
                    "65727665720564756d6d7902736500";

    @Before
    public void before() {
        channel = new EmbeddedChannel(new DatagramDnsResponseDecoder());
    }

    @Test
    public void testDecodeNaptrRecord() throws Exception {
        byte[] raw = hexStringToByteArray(NAPTR_RESPONSE);
        ByteBuf buf = Unpooled.wrappedBuffer(raw);

        DatagramPacket packet = new DatagramPacket(buf,
                new InetSocketAddress("127.0.0.1", 4711), new InetSocketAddress("127.0.0.1", 4712));

        channel.writeInbound(packet);

        Object o = channel.readInbound();
        assertNotNull(o);
        DnsResponse response = (DnsResponse) o;
        assertThat(response.count(DnsSection.ANSWER), is(2));
        DnsRecord a1 = response.recordAt(DnsSection.ANSWER, 0);
        assertThat(a1.type(), is(DnsRecordType.NAPTR));
        DefaultDnsNaptrRecord naptr1 = (DefaultDnsNaptrRecord) a1;
        assertThat(naptr1.order(), is(100));
        assertThat(naptr1.preference(), is(10));
        assertThat(naptr1.flags(), is("S"));
        assertThat(naptr1.services(), is("SIP+D2U"));
        assertThat(naptr1.replacement(), is("_sip._udp.dummy.se."));

        /*
         * BIND also sends back SRV and A record
         */
        for (int i = 0; i < response.count(DnsSection.ADDITIONAL); ++i) {
            DnsRecord r = response.recordAt(DnsSection.ADDITIONAL, i);
            if (r.type() == DnsRecordType.SRV) {
                DefaultDnsSrvRecord srv = (DefaultDnsSrvRecord) r;
                assertThat(srv.priority(), is(0));
                assertThat(srv.weight(), is(5));
                assertThat(srv.port(), is(5060));
                assertThat(srv.target(), is("sipserver.dummy.se."));
            }
        }
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}
