/*
 * Copyright 2018 The Netty Project
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

import org.junit.BeforeClass;
import org.junit.Test;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;


public class NameServerComparatorTest {

    private static InetSocketAddress IPV4ADDRESS1;
    private static InetSocketAddress IPV4ADDRESS2;
    private static InetSocketAddress IPV4ADDRESS3;

    private static InetSocketAddress IPV6ADDRESS1;
    private static InetSocketAddress IPV6ADDRESS2;

    private static InetSocketAddress UNRESOLVED1;
    private static InetSocketAddress UNRESOLVED2;
    private static InetSocketAddress UNRESOLVED3;

    @BeforeClass
    public static void before() throws UnknownHostException {
        IPV4ADDRESS1 = new InetSocketAddress(InetAddress.getByAddress("ns1", new byte[] { 10, 0, 0, 1 }), 53);
        IPV4ADDRESS2 = new InetSocketAddress(InetAddress.getByAddress("ns2", new byte[] { 10, 0, 0, 2 }), 53);
        IPV4ADDRESS3 = new InetSocketAddress(InetAddress.getByAddress("ns3", new byte[] { 10, 0, 0, 3 }), 53);

        IPV6ADDRESS1 = new InetSocketAddress(InetAddress.getByAddress(
                "ns1", new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 }), 53);
        IPV6ADDRESS2 = new InetSocketAddress(InetAddress.getByAddress(
                "ns2", new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2 }), 53);

        UNRESOLVED1 = InetSocketAddress.createUnresolved("ns3", 53);
        UNRESOLVED2 = InetSocketAddress.createUnresolved("ns4", 53);
        UNRESOLVED3 = InetSocketAddress.createUnresolved("ns5", 53);
    }

    @Test
    public void testCompareResolvedOnly() {
        NameServerComparator comparator = new NameServerComparator(Inet4Address.class);
        int x = comparator.compare(IPV4ADDRESS1, IPV6ADDRESS1);
        int y = comparator.compare(IPV6ADDRESS1, IPV4ADDRESS1);

        assertEquals(-1, x);
        assertEquals(x, -y);

        assertEquals(0, comparator.compare(IPV4ADDRESS1, IPV4ADDRESS1));
        assertEquals(0, comparator.compare(IPV6ADDRESS1, IPV6ADDRESS1));
    }

    @Test
    public void testCompareUnresolvedSimple() {
        NameServerComparator comparator = new NameServerComparator(Inet4Address.class);
        int x = comparator.compare(IPV4ADDRESS1, UNRESOLVED1);
        int y = comparator.compare(UNRESOLVED1, IPV4ADDRESS1);

        assertEquals(-1, x);
        assertEquals(x, -y);
        assertEquals(0, comparator.compare(IPV4ADDRESS1, IPV4ADDRESS1));
        assertEquals(0, comparator.compare(UNRESOLVED1, UNRESOLVED1));
    }

    @Test
    public void testCompareUnresolvedOnly() {
        NameServerComparator comparator = new NameServerComparator(Inet4Address.class);
        int x = comparator.compare(UNRESOLVED1, UNRESOLVED2);
        int y = comparator.compare(UNRESOLVED2, UNRESOLVED1);

        assertEquals(0, x);
        assertEquals(x, -y);

        assertEquals(0, comparator.compare(UNRESOLVED1, UNRESOLVED1));
        assertEquals(0, comparator.compare(UNRESOLVED2, UNRESOLVED2));
    }

    @Test
    public void testSortAlreadySortedPreferred() {
        List<InetSocketAddress> expected = Arrays.asList(IPV4ADDRESS1, IPV4ADDRESS2, IPV4ADDRESS3);
        List<InetSocketAddress> addresses = new ArrayList<InetSocketAddress>(expected);
        NameServerComparator comparator = new NameServerComparator(Inet4Address.class);

        Collections.sort(addresses, comparator);

        assertEquals(expected, addresses);
    }

    @Test
    public void testSortAlreadySortedNotPreferred() {
        List<InetSocketAddress> expected = Arrays.asList(IPV4ADDRESS1, IPV4ADDRESS2, IPV4ADDRESS3);
        List<InetSocketAddress> addresses = new ArrayList<InetSocketAddress>(expected);
        NameServerComparator comparator = new NameServerComparator(Inet6Address.class);

        Collections.sort(addresses, comparator);

        assertEquals(expected, addresses);
    }

    @Test
    public void testSortAlreadySortedUnresolved() {
        List<InetSocketAddress> expected = Arrays.asList(UNRESOLVED1, UNRESOLVED2, UNRESOLVED3);
        List<InetSocketAddress> addresses = new ArrayList<InetSocketAddress>(expected);
        NameServerComparator comparator = new NameServerComparator(Inet6Address.class);

        Collections.sort(addresses, comparator);

        assertEquals(expected, addresses);
    }

    @Test
    public void testSortAlreadySortedMixed() {
        List<InetSocketAddress> expected = Arrays.asList(
                IPV4ADDRESS1, IPV4ADDRESS2, IPV6ADDRESS1, IPV6ADDRESS2, UNRESOLVED1, UNRESOLVED2);

        List<InetSocketAddress> addresses = new ArrayList<InetSocketAddress>(expected);
        NameServerComparator comparator = new NameServerComparator(Inet4Address.class);

        Collections.sort(addresses, comparator);

        assertEquals(expected, addresses);
    }

    @Test
    public void testSort1() {
        List<InetSocketAddress> expected = Arrays.asList(
                IPV4ADDRESS1, IPV4ADDRESS2, IPV6ADDRESS1, IPV6ADDRESS2, UNRESOLVED1, UNRESOLVED2);
        List<InetSocketAddress> addresses = new ArrayList<InetSocketAddress>(
                Arrays.asList(IPV6ADDRESS1, IPV4ADDRESS1, IPV6ADDRESS2, UNRESOLVED1, UNRESOLVED2, IPV4ADDRESS2));
        NameServerComparator comparator = new NameServerComparator(Inet4Address.class);

        Collections.sort(addresses, comparator);

        assertEquals(expected, addresses);
    }

    @Test
    public void testSort2() {
        List<InetSocketAddress> expected = Arrays.asList(
                IPV4ADDRESS1, IPV4ADDRESS2, IPV6ADDRESS1, IPV6ADDRESS2, UNRESOLVED1, UNRESOLVED2);
        List<InetSocketAddress> addresses = new ArrayList<InetSocketAddress>(
                Arrays.asList(IPV4ADDRESS1, IPV6ADDRESS1, IPV6ADDRESS2, UNRESOLVED1, IPV4ADDRESS2, UNRESOLVED2));
        NameServerComparator comparator = new NameServerComparator(Inet4Address.class);

        Collections.sort(addresses, comparator);

        assertEquals(expected, addresses);
    }
}
