/*
 * Copyright 2019 The Netty Project
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
package io.netty.resolver.dns;

import io.netty.util.CharsetUtil;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;

public class MacOSDnsServerAddressStreamProviderTest {

    @Test
    public void test() throws IOException {
        String testInput = "DNS configuration\n" +
                "\n" +
                "resolver #1\n" +
                "  search domain[0] : netty.io\n" +
                "  search domain[1] : foo.bar\n" +
                "  search domain[2] : lan\n" +
                "  nameserver[0] : 192.168.86.1\n" +
                "  if_index : 23 (en7)\n" +
                "  flags    : Request A records\n" +
                "  reach    : 0x00020002 (Reachable,Directly Reachable Address)\n" +
                "\n" +
                "resolver #2\n" +
                "  domain   : netty.io\n" +
                "  nameserver[0] : 10.0.0.1\n" +
                "  nameserver[1] : 10.0.0.2\n" +
                "  if_index : 20 (utun4)\n" +
                "  flags    : Supplemental, Request A records, Request AAAA records\n" +
                "  reach    : 0x00000002 (Reachable)\n" +
                "  order    : 101600\n" +
                "\n" +
                "resolver #3\n" +
                "  domain   : foo.bar\n" +
                "  nameserver[0] : 10.0.0.1\n" +
                "  nameserver[1] : 10.0.0.2\n" +
                "  if_index : 20 (utun4)\n" +
                "  flags    : Supplemental, Request A records, Request AAAA records\n" +
                "  reach    : 0x00000002 (Reachable)\n" +
                "  order    : 101602\n" +
                "\n" +
                "resolver #4\n" +
                "  domain   : local\n" +
                "  options  : mdns\n" +
                "  timeout  : 5\n" +
                "  flags    : Request A records\n" +
                "  reach    : 0x00000000 (Not Reachable)\n" +
                "  order    : 300000\n" +
                "\n" +
                "DNS configuration (for scoped queries)\n" +
                "\n" +
                "resolver #1\n" +
                "  search domain[0] : lan\n" +
                "  nameserver[0] : 192.168.86.1\n" +
                "  if_index : 23 (en7)\n" +
                "  flags    : Scoped, Request A records\n" +
                "  reach    : 0x00020002 (Reachable,Directly Reachable Address)\n" +
                "\n" +
                "resolver #2\n" +
                "  search domain[0] : lan\n" +
                "  nameserver[0] : 192.168.86.1\n" +
                "  if_index : 8 (en0)\n" +
                "  flags    : Scoped, Request A records\n" +
                "  reach    : 0x00020002 (Reachable,Directly Reachable Address)\n" +
                "\n" +
                "resolver #3\n" +
                "  search domain[0] : netty.io\n" +
                "  search domain[1] : foo.bar\n" +
                "  nameserver[0] : 10.0.0.1\n" +
                "  nameserver[1] : 10.0.0.2\n" +
                "  if_index : 20 (utun4)\n" +
                "  flags    : Scoped, Request A records, Request AAAA records\n" +
                "  reach    : 0x00000002 (Reachable)";
        Map<String, DnsServerAddresses> result = MacOSDnsServerAddressStreamProvider.parse(
                new ByteArrayInputStream(testInput.getBytes(CharsetUtil.US_ASCII)));

        Assert.assertEquals(2, result.size());
        DnsServerAddressStream addresses = result.get("netty.io").stream();
        Assert.assertEquals(2, addresses.size());
        Assert.assertEquals(new InetSocketAddress("10.0.0.1", 53), addresses.next());
        Assert.assertEquals(new InetSocketAddress("10.0.0.2", 53), addresses.next());

        addresses = result.get("foo.bar").stream();
        Assert.assertEquals(2, addresses.size());
        Assert.assertEquals(new InetSocketAddress("10.0.0.1", 53), addresses.next());
        Assert.assertEquals(new InetSocketAddress("10.0.0.2", 53), addresses.next());
    }
}
