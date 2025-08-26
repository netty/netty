/*
 * Copyright 2014 The Netty Project
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

import io.netty.util.NetUtil;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import static io.netty.resolver.dns.DefaultDnsServerAddressStreamProvider.defaultAddressList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class DnsServerAddressesTest {

    private static final InetSocketAddress ADDR1 = new InetSocketAddress(NetUtil.LOCALHOST, 1);
    private static final InetSocketAddress ADDR2 = new InetSocketAddress(NetUtil.LOCALHOST, 2);
    private static final InetSocketAddress ADDR3 = new InetSocketAddress(NetUtil.LOCALHOST, 3);

    @Test
    public void testDefaultAddresses() {
        assertThat(defaultAddressList().size()).isGreaterThan(0);
    }

    @Test
    public void testSequential() {
        DnsServerAddresses seq = DnsServerAddresses.sequential(ADDR1, ADDR2, ADDR3);
        assertNotSame(seq.stream(), seq.stream());

        for (int j = 0; j < 2; j ++) {
            DnsServerAddressStream i = seq.stream();
            assertNext(i, ADDR1);
            assertNext(i, ADDR2);
            assertNext(i, ADDR3);
            assertNext(i, ADDR1);
            assertNext(i, ADDR2);
            assertNext(i, ADDR3);
        }
    }

    @Test
    public void testRotational() {
        DnsServerAddresses seq = DnsServerAddresses.rotational(ADDR1, ADDR2, ADDR3);

        DnsServerAddressStream i = seq.stream();
        assertNext(i, ADDR1);
        assertNext(i, ADDR2);
        assertNext(i, ADDR3);
        assertNext(i, ADDR1);
        assertNext(i, ADDR2);
        assertNext(i, ADDR3);

        i = seq.stream();
        assertNext(i, ADDR2);
        assertNext(i, ADDR3);
        assertNext(i, ADDR1);
        assertNext(i, ADDR2);
        assertNext(i, ADDR3);
        assertNext(i, ADDR1);

        i = seq.stream();
        assertNext(i, ADDR3);
        assertNext(i, ADDR1);
        assertNext(i, ADDR2);
        assertNext(i, ADDR3);
        assertNext(i, ADDR1);
        assertNext(i, ADDR2);

        i = seq.stream();
        assertNext(i, ADDR1);
        assertNext(i, ADDR2);
        assertNext(i, ADDR3);
        assertNext(i, ADDR1);
        assertNext(i, ADDR2);
        assertNext(i, ADDR3);
    }

    @Test
    public void testShuffled() {
        DnsServerAddresses seq = DnsServerAddresses.shuffled(ADDR1, ADDR2, ADDR3);

        // Ensure that all three addresses are returned by the iterator.
        // In theory, this test can fail at extremely low chance, but we don't really care.
        Set<InetSocketAddress> set = Collections.newSetFromMap(new IdentityHashMap<InetSocketAddress, Boolean>());
        DnsServerAddressStream i = seq.stream();
        for (int j = 0; j < 1048576; j ++) {
            set.add(i.next());
        }

        assertEquals(3, set.size());
        assertNotSame(seq.stream(), seq.stream());
    }

    @Test
    public void testSingleton() {
        DnsServerAddresses seq = DnsServerAddresses.singleton(ADDR1);

        // Should return the same iterator instance for least possible footprint.
        assertSame(seq.stream(), seq.stream());

        DnsServerAddressStream i = seq.stream();
        assertNext(i, ADDR1);
        assertNext(i, ADDR1);
        assertNext(i, ADDR1);
    }

    private static void assertNext(DnsServerAddressStream i, InetSocketAddress addr) {
        assertSame(i.next(), addr);
    }
}
