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

import io.netty.channel.unix.DomainSocketAddress;
import io.netty.util.internal.CleanableDirectBuffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static io.netty.channel.unix.Buffer.allocateDirectBufferWithNativeOrder;
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
        CleanableDirectBuffer cleanableDirectBuffer = allocateDirectBufferWithNativeOrder(64);
        ByteBuffer buffer = cleanableDirectBuffer.buffer();
        try {
            InetAddress address = InetAddress.getByAddress(new byte[] { 10, 10, 10, 10 });
            int port = 45678;
            assertEquals(Native.SIZEOF_SOCKADDR_IN, SockaddrIn.setIPv4(buffer, address, port));
            byte[] bytes = new byte[4];
            InetSocketAddress sockAddr = SockaddrIn.getIPv4(buffer, bytes);
            assertArrayEquals(address.getAddress(), sockAddr.getAddress().getAddress());
            assertEquals(port, sockAddr.getPort());
        } finally {
            cleanableDirectBuffer.clean();
        }
    }

    @Test
    public void testIp6() throws Exception {
        CleanableDirectBuffer cleanableDirectBuffer = allocateDirectBufferWithNativeOrder(64);
        ByteBuffer buffer = cleanableDirectBuffer.buffer();
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
            cleanableDirectBuffer.clean();
        }
    }

    @Test
    public void testWriteIp4ReadIpv6Mapped() throws Exception {
        CleanableDirectBuffer cleanableDirectBuffer = allocateDirectBufferWithNativeOrder(64);
        ByteBuffer buffer = cleanableDirectBuffer.buffer();
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
            cleanableDirectBuffer.clean();
        }
    }

    @Test
    public void testUdsPathname() throws Exception {
        CleanableDirectBuffer cleanableDirectBuffer = allocateDirectBufferWithNativeOrder(
                Native.SIZEOF_SOCKADDR_UN);
        ByteBuffer buffer = cleanableDirectBuffer.buffer();
        try {
            String socketPath = "/tmp/test.sock";
            DomainSocketAddress address = new DomainSocketAddress(socketPath);
            byte[] pathBytes = socketPath.getBytes(StandardCharsets.UTF_8);

            int expectedLength = Native.SOCKADDR_UN_OFFSETOF_SUN_PATH + pathBytes.length + 1;
            int actualLength = SockaddrIn.setUds(buffer, address);

            assertEquals(expectedLength, actualLength, "Address length should include null terminator");

            // Verify sun_family is set correctly
            short family = buffer.getShort(buffer.position() + Native.SOCKADDR_UN_OFFSETOF_SUN_FAMILY);
            assertEquals(Native.AF_UNIX, family, "sun_family should be AF_UNIX");

            // Verify path is written correctly
            byte[] writtenPath = new byte[pathBytes.length];
            buffer.position(buffer.position() + Native.SOCKADDR_UN_OFFSETOF_SUN_PATH);
            buffer.get(writtenPath);
            assertArrayEquals(pathBytes, writtenPath, "Path should match");

            // Verify null terminator is present
            byte nullTerminator = buffer.get();
            assertEquals(0, nullTerminator, "Pathname socket should have null terminator");
        } finally {
            cleanableDirectBuffer.clean();
        }
    }

    @Test
    public void testUdsAbstractNamespace() throws Exception {
        CleanableDirectBuffer cleanableDirectBuffer = allocateDirectBufferWithNativeOrder(
                Native.SIZEOF_SOCKADDR_UN);
        ByteBuffer buffer = cleanableDirectBuffer.buffer();
        try {
            // Abstract namespace socket: starts with null byte
            String abstractName = "\0test-abstract-socket";
            DomainSocketAddress address = new DomainSocketAddress(abstractName);
            byte[] nameBytes = abstractName.getBytes(StandardCharsets.UTF_8);

            // For abstract sockets, length should NOT include an extra null terminator
            int expectedLength = Native.SOCKADDR_UN_OFFSETOF_SUN_PATH + nameBytes.length;
            int actualLength = SockaddrIn.setUds(buffer, address);

            assertEquals(expectedLength, actualLength,
                    "Address length should be exact for abstract socket (no extra null terminator)");

            // Verify sun_family is set correctly
            int position = buffer.position();
            short family = buffer.getShort(position + Native.SOCKADDR_UN_OFFSETOF_SUN_FAMILY);
            assertEquals(Native.AF_UNIX, family, "sun_family should be AF_UNIX");

            // Verify name is written correctly (including the leading null byte)
            byte[] writtenName = new byte[nameBytes.length];
            buffer.position(position + Native.SOCKADDR_UN_OFFSETOF_SUN_PATH);
            buffer.get(writtenName);
            assertArrayEquals(nameBytes, writtenName, "Abstract socket name should match exactly");

            // Verify first byte is null (abstract namespace indicator)
            assertEquals(0, writtenName[0], "Abstract socket should start with null byte");

            // Verify that the returned length matches exactly what we wrote
            // (no extra null terminator beyond what's in the name)
            assertEquals(Native.SOCKADDR_UN_OFFSETOF_SUN_PATH + nameBytes.length, actualLength,
                    "Returned length should be exact: offsetof(sun_path) + name_length");
        } finally {
            cleanableDirectBuffer.clean();
        }
    }
}
