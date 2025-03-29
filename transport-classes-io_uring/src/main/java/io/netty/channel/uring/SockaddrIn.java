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

import io.netty.util.internal.PlatformDependent;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class SockaddrIn {
    static final byte[] IPV4_MAPPED_IPV6_PREFIX = {
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0xff, (byte) 0xff };
    static final int IPV4_ADDRESS_LENGTH = 4;
    static final int IPV6_ADDRESS_LENGTH = 16;
    static final byte[] SOCKADDR_IN6_EMPTY_ARRAY = new byte[Native.SIZEOF_SOCKADDR_IN6];
    static final byte[] SOCKADDR_IN_EMPTY_ARRAY = new byte[Native.SIZEOF_SOCKADDR_IN];

    private SockaddrIn() { }

    static int set(boolean ipv6, ByteBuffer memory, InetSocketAddress address) {
        if (ipv6) {
            return setIPv6(memory, address.getAddress(), address.getPort());
        }
        return setIPv4(memory, address.getAddress(), address.getPort());
    }

    /**
     * <pre>{@code
     * struct sockaddr_in {
     *      sa_family_t    sin_family; // address family: AF_INET
     *      in_port_t      sin_port;   // port in network byte order
     *      struct in_addr sin_addr;   // internet address
     * };
     *
     * // Internet address.
     * struct in_addr {
     *     uint32_t       s_addr;     // address in network byte order
     * };
     * }</pre>
     */
    static int setIPv4(ByteBuffer memory, InetAddress address, int port) {
        int position = memory.position();
        memory.mark();
        try {
            // memset
            memory.put(SOCKADDR_IN_EMPTY_ARRAY);

            memory.putShort(position + Native.SOCKADDR_IN_OFFSETOF_SIN_FAMILY, Native.AF_INET);
            memory.putShort(position + Native.SOCKADDR_IN_OFFSETOF_SIN_PORT, handleNetworkOrder(memory.order(),
                    (short) port));

            byte[] bytes = address.getAddress();
            int offset = 0;
            if (bytes.length == IPV6_ADDRESS_LENGTH) {
                // IPV6 mapped IPV4 address, we only need the last 4 bytes.
                offset = IPV4_MAPPED_IPV6_PREFIX.length;
            }
            assert bytes.length == offset + IPV4_ADDRESS_LENGTH;
            memory.position(position + Native.SOCKADDR_IN_OFFSETOF_SIN_ADDR + Native.IN_ADDRESS_OFFSETOF_S_ADDR);
            memory.put(bytes, offset, IPV4_ADDRESS_LENGTH);
            return Native.SIZEOF_SOCKADDR_IN;
        } finally {
            // Restore position as we did change it via memory.put(byte[]...).
            memory.reset();
        }
    }

    /**
     * <pre>{@code
     * struct sockaddr_in6 {
     *     sa_family_t     sin6_family;   // AF_INET6
     *     in_port_t       sin6_port;     // port number
     *     uint32_t        sin6_flowinfo; // IPv6 flow information
     *     struct in6_addr sin6_addr;     // IPv6 address
     *     uint32_t        sin6_scope_id; /* Scope ID (new in 2.4)
     * };
     *
     * struct in6_addr {
     *     unsigned char s6_addr[16];   // IPv6 address
     * };
     * }</pre>
     */
    static int setIPv6(ByteBuffer memory, InetAddress address, int port) {
        int position = memory.position();
        memory.mark();
        try {
            // memset
            memory.put(SOCKADDR_IN6_EMPTY_ARRAY);
            memory.putShort(position + Native.SOCKADDR_IN6_OFFSETOF_SIN6_FAMILY, Native.AF_INET6);
            memory.putShort(position + Native.SOCKADDR_IN6_OFFSETOF_SIN6_PORT,
                    handleNetworkOrder(memory.order(), (short) port));
            // Skip sin6_flowinfo as we did memset before
            byte[] bytes = address.getAddress();
            int offset = Native.SOCKADDR_IN6_OFFSETOF_SIN6_ADDR + Native.IN6_ADDRESS_OFFSETOF_S6_ADDR;
            if (bytes.length == IPV4_ADDRESS_LENGTH) {
                memory.position(position + offset);
                memory.put(IPV4_MAPPED_IPV6_PREFIX);
                memory.put(bytes, 0, IPV4_ADDRESS_LENGTH);

                // Skip sin6_scope_id as we did memset before
            } else {
                memory.position(position + offset);
                memory.put(bytes, 0, IPV6_ADDRESS_LENGTH);

                memory.putInt(position + Native.SOCKADDR_IN6_OFFSETOF_SIN6_SCOPE_ID,
                        ((Inet6Address) address).getScopeId());
            }
            return Native.SIZEOF_SOCKADDR_IN6;
        } finally {
            memory.reset();
        }
    }

    static InetSocketAddress getIPv4(ByteBuffer memory, byte[] tmpArray) {
        assert tmpArray.length == IPV4_ADDRESS_LENGTH;
        int position = memory.position();
        memory.mark();
        try {
            int port = handleNetworkOrder(memory.order(),
                    memory.getShort(position + Native.SOCKADDR_IN_OFFSETOF_SIN_PORT)) & 0xFFFF;
            memory.position(position + Native.SOCKADDR_IN_OFFSETOF_SIN_ADDR + Native.IN_ADDRESS_OFFSETOF_S_ADDR);
            memory.get(tmpArray);
            try {
                return new InetSocketAddress(InetAddress.getByAddress(tmpArray), port);
            } catch (UnknownHostException ignore) {
                return null;
            }
        } finally {
            memory.reset();
        }
    }

    static InetSocketAddress getIPv6(ByteBuffer memory, byte[] ipv6Array, byte[] ipv4Array) {
        assert ipv6Array.length == IPV6_ADDRESS_LENGTH;
        assert ipv4Array.length == IPV4_ADDRESS_LENGTH;
        int position = memory.position();
        memory.mark();
        try {
            int port = handleNetworkOrder(memory.order(), memory.getShort(
                    position + Native.SOCKADDR_IN6_OFFSETOF_SIN6_PORT)) & 0xFFFF;
            memory.position(position + Native.SOCKADDR_IN6_OFFSETOF_SIN6_ADDR + Native.IN6_ADDRESS_OFFSETOF_S6_ADDR);
            memory.get(ipv6Array);
            if (PlatformDependent.equals(
                    ipv6Array, 0, IPV4_MAPPED_IPV6_PREFIX, 0, IPV4_MAPPED_IPV6_PREFIX.length)) {
                System.arraycopy(ipv6Array, IPV4_MAPPED_IPV6_PREFIX.length, ipv4Array, 0, IPV4_ADDRESS_LENGTH);
                try {
                    return new InetSocketAddress(Inet4Address.getByAddress(ipv4Array), port);
                } catch (UnknownHostException ignore) {
                    return null;
                }
            } else {
                int scopeId = memory.getInt(position + Native.SOCKADDR_IN6_OFFSETOF_SIN6_SCOPE_ID);
                try {
                    return new InetSocketAddress(Inet6Address.getByAddress(null, ipv6Array, scopeId), port);
                } catch (UnknownHostException ignore) {
                    return null;
                }
            }
        } finally {
            memory.reset();
        }
    }

    static boolean hasPortIpv4(ByteBuffer memory) {
        int port = memory.getShort(memory.position() + Native.SOCKADDR_IN_OFFSETOF_SIN_PORT) & 0xFFFF;
        return port > 0;
    }

    static boolean hasPortIpv6(ByteBuffer memory) {
        int port = memory.getShort(memory.position() + Native.SOCKADDR_IN6_OFFSETOF_SIN6_PORT) & 0xFFFF;
        return port > 0;
    }

    private static short handleNetworkOrder(ByteOrder order, short v) {
        return order != ByteOrder.nativeOrder() ? v : Short.reverseBytes(v);
    }
}
