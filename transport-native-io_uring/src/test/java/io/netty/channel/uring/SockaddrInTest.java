/*
 * Copyright 2024 The Netty Project
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
package io.netty.channel.uring;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import static io.netty.channel.unix.Buffer.allocateDirectWithNativeOrder;
import static io.netty.channel.unix.Buffer.free;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class SockaddrInTest {

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    @Test
    public void testIp4() throws Exception {
        ByteBuffer buffer = allocateDirectWithNativeOrder(64);
        try {
            InetAddress address = InetAddress.getByAddress(new byte[] { 10, 10, 10, 10 });
            int port = 45678;
            assertEquals(Native.SIZEOF_SOCKADDR_IN, SockaddrIn.setIPv4(buffer, address, port));
            byte[] bytes = new byte[4];
            InetSocketAddress sockAddr = SockaddrIn.getIPv4(buffer, bytes);
            assertArrayEquals(address.getAddress(), sockAddr.getAddress().getAddress());
            assertEquals(port, sockAddr.getPort());
        } finally {
            free(buffer);
        }
    }

    @Test
    public void testIp6() throws Exception {
        ByteBuffer buffer = allocateDirectWithNativeOrder(64);
        try {
            Inet6Address address = Inet6Address.getByAddress(
                    null, new byte[] { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 }, 12345);
            int port = 45678;
            assertEquals(Native.SIZEOF_SOCKADDR_IN6, SockaddrIn.setIPv6(buffer, address, port));
            byte[] ipv6Bytes = new byte[16];
            byte[] ipv4Bytes = new byte[4];

            InetSocketAddress sockAddr = SockaddrIn.getIPv6(buffer, ipv6Bytes, ipv4Bytes);
            Inet6Address inet6Address = (Inet6Address) sockAddr.getAddress();
            assertArrayEquals(address.getAddress(), inet6Address.getAddress());
            assertEquals(address.getScopeId(), inet6Address.getScopeId());
            assertEquals(port, sockAddr.getPort());
        } finally {
            free(buffer);
        }
    }

    @Test
    public void testWriteIp4ReadIpv6Mapped() throws Exception {
        ByteBuffer buffer = allocateDirectWithNativeOrder(64);
        try {
            InetAddress address = InetAddress.getByAddress(new byte[] { 10, 10, 10, 10 });
            int port = 45678;
            assertEquals(Native.SIZEOF_SOCKADDR_IN6, SockaddrIn.setIPv6(buffer, address, port));
            byte[] ipv6Bytes = new byte[16];
            byte[] ipv4Bytes = new byte[4];

            InetSocketAddress sockAddr = SockaddrIn.getIPv6(buffer, ipv6Bytes, ipv4Bytes);
            Inet4Address ipv4Address = (Inet4Address) sockAddr.getAddress();

            System.arraycopy(SockaddrIn.IPV4_MAPPED_IPV6_PREFIX, 0, ipv6Bytes, 0,
                    SockaddrIn.IPV4_MAPPED_IPV6_PREFIX.length);
            assertArrayEquals(ipv4Bytes, ipv4Address.getAddress());
            assertEquals(port, sockAddr.getPort());
        } finally {
            free(buffer);
        }
    }
}
