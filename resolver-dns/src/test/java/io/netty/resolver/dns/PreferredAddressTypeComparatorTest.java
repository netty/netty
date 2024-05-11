/*
 * Copyright 2019 The Netty Project
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
package io.netty.resolver.dns;

import io.netty.channel.socket.SocketProtocolFamily;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PreferredAddressTypeComparatorTest {

    @Test
    public void testIpv4() throws UnknownHostException {
        InetAddress ipv4Address1 = InetAddress.getByName("10.0.0.1");
        InetAddress ipv4Address2 = InetAddress.getByName("10.0.0.2");
        InetAddress ipv4Address3 = InetAddress.getByName("10.0.0.3");
        InetAddress ipv6Address1 = InetAddress.getByName("::1");
        InetAddress ipv6Address2 = InetAddress.getByName("::2");
        InetAddress ipv6Address3 = InetAddress.getByName("::3");

        PreferredAddressTypeComparator ipv4 = PreferredAddressTypeComparator.comparator(SocketProtocolFamily.INET);

        List<InetAddress> addressList = new ArrayList<InetAddress>();
        Collections.addAll(addressList, ipv4Address1, ipv4Address2, ipv6Address1,
                ipv6Address2, ipv4Address3, ipv6Address3);
        Collections.sort(addressList, ipv4);

        assertEquals(Arrays.asList(ipv4Address1, ipv4Address2, ipv4Address3, ipv6Address1,
                ipv6Address2, ipv6Address3), addressList);
    }

    @Test
    public void testIpv6() throws UnknownHostException {
        InetAddress ipv4Address1 = InetAddress.getByName("10.0.0.1");
        InetAddress ipv4Address2 = InetAddress.getByName("10.0.0.2");
        InetAddress ipv4Address3 = InetAddress.getByName("10.0.0.3");
        InetAddress ipv6Address1 = InetAddress.getByName("::1");
        InetAddress ipv6Address2 = InetAddress.getByName("::2");
        InetAddress ipv6Address3 = InetAddress.getByName("::3");

        PreferredAddressTypeComparator ipv4 = PreferredAddressTypeComparator.comparator(SocketProtocolFamily.INET6);

        List<InetAddress> addressList = new ArrayList<InetAddress>();
        Collections.addAll(addressList, ipv4Address1, ipv4Address2, ipv6Address1,
                ipv6Address2, ipv4Address3, ipv6Address3);
        Collections.sort(addressList, ipv4);

        assertEquals(Arrays.asList(ipv6Address1,
                ipv6Address2, ipv6Address3, ipv4Address1, ipv4Address2, ipv4Address3), addressList);
    }
}
