/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.codec.dns.doh;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.dns.DefaultDnsQuery;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.NetUtil;
import org.junit.Assert;
import org.junit.Test;

import java.net.URL;

public class DoHQueryEncoderTest {

    @Test
    public void testEncodeAndDecode() throws Exception {
        byte[] dnsReq = new byte[]{0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 3, 119, 119, 119, 7, 101,
                120, 97, 109, 112, 108, 101, 3, 99, 111, 109, 0, 0, 1, 0, 1};

        byte[] dnsResp = new byte[]{0, 0, -128, -128, 0, 1, 0, 1, 0, 0, 0, 0, 3, 119, 119, 119,
                7, 101, 120, 97, 109, 112, 108, 101, 3, 99, 111, 109, 0, 0, 1, 0, 1, -64, 12, 0, 1, 0, 1, 0, 0, 2, 9,
                0, 4, 127, 0, 0, 1};

        URL url = new URL("https://localhost/dns-query");

        EmbeddedChannel embeddedChannel = new EmbeddedChannel(new DoHQueryEncoder(false, url),
                new DoHResponseDecoder());

        // Query
        DnsQuery query = new DefaultDnsQuery(0, DnsOpCode.QUERY);
        query.setRecord(DnsSection.QUESTION, new DefaultDnsQuestion("www.example.com", DnsRecordType.A));
        embeddedChannel.writeOutbound(query);
        embeddedChannel.flushOutbound();

        FullHttpRequest fullHttpRequest = embeddedChannel.readOutbound();
        Assert.assertArrayEquals(dnsReq, ByteBufUtil.getBytes(fullHttpRequest.content()));

        // Response
        ByteBuf resp = embeddedChannel.alloc().buffer().writeBytes(dnsResp);
        FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK, resp);

        fullHttpResponse.headers()
                .add(HttpHeaderNames.CONTENT_TYPE, "application/dns-message")
                .add(HttpHeaderNames.CONTENT_LENGTH, resp.readableBytes());

        embeddedChannel.writeInbound(fullHttpResponse);
        embeddedChannel.flushInbound();

        DefaultDnsResponse defaultDnsResponse = embeddedChannel.readInbound();
        DnsRecord record = defaultDnsResponse.recordAt(DnsSection.ANSWER, 0);
        DnsRawRecord raw = (DnsRawRecord) record;
        Assert.assertEquals("127.0.0.1", NetUtil.bytesToIpAddress(ByteBufUtil.getBytes(raw.content())));

        embeddedChannel.close();
    }
}
