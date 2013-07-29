/*
 * Copyright 2013 The Netty Project
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
package io.netty.dns;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.dns.DnsQueryEncoder;
import io.netty.handler.codec.dns.DnsResponseDecoder;
import io.netty.handler.timeout.ReadTimeoutHandler;

import org.junit.Test;

public class DnsImplTest {

    // TODO: !!NOT FINISHED!!
    @Test
    public void implTest() throws Exception {
        byte[] server = null;
        while ((server = DnsExchangeFactory.getDnsServer(0)) != null) {
            DnsExchangeFactory.removeDnsServer(server);
        }
        DnsExchangeFactory.addDnsServer(new byte[] { 127, 0, 0, 1 });
        EmbeddedChannel embedded = new EmbeddedChannel(new ReadTimeoutHandler(30), new DnsResponseDecoder(),
                new DnsQueryEncoder(), new DnsInboundMessageHandler());
        DnsExchangeFactory.addChannel(new byte[] { 127, 0, 0, 1 }, embedded);
    }

}
