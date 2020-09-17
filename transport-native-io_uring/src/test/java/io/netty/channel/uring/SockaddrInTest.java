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
package io.netty.channel.uring;

import io.netty.channel.unix.Buffer;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class SockaddrInTest {

    @Test
    public void testIp4() throws Exception {
        Assume.assumeTrue(IOUring.isAvailable());

        ByteBuffer buffer = Buffer.allocateDirectWithNativeOrder(64);
        try {
            long memoryAddress = Buffer.memoryAddress(buffer);
            InetAddress address = InetAddress.getByAddress(new byte[] { 10, 10, 10, 10 });
            int port = 45678;
            Assert.assertEquals(Native.SIZEOF_SOCKADDR_IN, SockaddrIn.writeIPv4(memoryAddress, address, port));
            byte[] bytes = new byte[4];
            InetSocketAddress sockAddr = SockaddrIn.readIPv4(memoryAddress, bytes);
            Assert.assertArrayEquals(address.getAddress(), sockAddr.getAddress().getAddress());
            Assert.assertEquals(port, sockAddr.getPort());
        } finally {
            Buffer.free(buffer);
        }
    }

    @Test
    public void testIp6() throws Exception {
        Assume.assumeTrue(IOUring.isAvailable());

        ByteBuffer buffer = Buffer.allocateDirectWithNativeOrder(64);
        try {
            long memoryAddress = Buffer.memoryAddress(buffer);
            Inet6Address address = Inet6Address.getByAddress(
                    null, new byte[] { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 }, 12345);
            int port = 45678;
            Assert.assertEquals(Native.SIZEOF_SOCKADDR_IN6, SockaddrIn.writeIPv6(memoryAddress, address, port));
            byte[] bytes = new byte[16];
            InetSocketAddress sockAddr = SockaddrIn.readIPv6(memoryAddress, bytes);
            Inet6Address inet6Address = (Inet6Address) sockAddr.getAddress();
            Assert.assertArrayEquals(address.getAddress(), inet6Address.getAddress());
            Assert.assertEquals(address.getScopeId(), inet6Address.getScopeId());
            Assert.assertEquals(port, sockAddr.getPort());
        } finally {
            Buffer.free(buffer);
        }
    }

    @Test
    public void testWriteIp4ReadIpv6Mapped() throws Exception {
        Assume.assumeTrue(IOUring.isAvailable());

        ByteBuffer buffer = Buffer.allocateDirectWithNativeOrder(64);
        try {
            long memoryAddress = Buffer.memoryAddress(buffer);
            InetAddress address = InetAddress.getByAddress(new byte[] { 10, 10, 10, 10 });
            int port = 45678;
            Assert.assertEquals(Native.SIZEOF_SOCKADDR_IN6, SockaddrIn.writeIPv6(memoryAddress, address, port));
            byte[] bytes = new byte[16];
            InetSocketAddress sockAddr = SockaddrIn.readIPv6(memoryAddress, bytes);
            Inet6Address inet6Address = (Inet6Address) sockAddr.getAddress();

            System.arraycopy(SockaddrIn.IPV4_MAPPED_IPV6_PREFIX, 0, bytes, 0,
                    SockaddrIn.IPV4_MAPPED_IPV6_PREFIX.length);
            System.arraycopy(address.getAddress(), 0, bytes, SockaddrIn.IPV4_MAPPED_IPV6_PREFIX.length, 4);
            Assert.assertArrayEquals(bytes, inet6Address.getAddress());
            Assert.assertEquals(0, inet6Address.getScopeId());
            Assert.assertEquals(port, sockAddr.getPort());
        } finally {
            Buffer.free(buffer);
        }
    }
}
