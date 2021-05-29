/*
 * Copyright 2020 The Netty Project
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
package io.netty.incubator.codec.quic;

import io.netty.util.internal.PlatformDependent;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import static io.netty.util.internal.PlatformDependent.BIG_ENDIAN_NATIVE_ORDER;

final class SockaddrIn {
    static final byte[] IPV4_MAPPED_IPV6_PREFIX = {
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0xff, (byte) 0xff };
    static final int IPV4_ADDRESS_LENGTH = 4;
    static final int IPV6_ADDRESS_LENGTH = 16;

    private SockaddrIn() { }

    static int cmp(long memory, long memory2) {
        return Quiche.sockaddr_cmp(memory, memory2);
    }

    static int write(long memory, InetSocketAddress address) {
        InetAddress addr = address.getAddress();
        return write(addr instanceof Inet6Address, memory, address);
    }

    static int write(boolean ipv6, long memory, InetSocketAddress address) {
        if (ipv6) {
            return SockaddrIn.writeIPv6(memory, address.getAddress(), address.getPort());
        } else {
            return SockaddrIn.writeIPv4(memory, address.getAddress(), address.getPort());
        }
    }

    /**
     *
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
     *
     */
    static int writeIPv4(long memory, InetAddress address, int port) {
        PlatformDependent.setMemory(memory, Quiche.SIZEOF_SOCKADDR_IN, (byte) 0);

        PlatformDependent.putShort(memory + Quiche.SOCKADDR_IN_OFFSETOF_SIN_FAMILY, Quiche.AF_INET);
        PlatformDependent.putShort(memory + Quiche.SOCKADDR_IN_OFFSETOF_SIN_PORT, handleNetworkOrder((short) port));
        byte[] bytes = address.getAddress();
        int offset = 0;
        if (bytes.length == IPV6_ADDRESS_LENGTH) {
            // IPV6 mapped IPV4 address, we only need the last 4 bytes.
            offset = IPV4_MAPPED_IPV6_PREFIX.length;
        }
        assert bytes.length == offset + 4;
        PlatformDependent.copyMemory(bytes, offset,
                memory + Quiche.SOCKADDR_IN_OFFSETOF_SIN_ADDR + Quiche.IN_ADDRESS_OFFSETOF_S_ADDR, 4);
        return Quiche.SIZEOF_SOCKADDR_IN;
    }

    /**
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
     */
    static int writeIPv6(long memory, InetAddress address, int port) {
        PlatformDependent.setMemory(memory, Quiche.SIZEOF_SOCKADDR_IN6, (byte) 0);
        PlatformDependent.putShort(memory + Quiche.SOCKADDR_IN6_OFFSETOF_SIN6_FAMILY, Quiche.AF_INET6);
        PlatformDependent.putShort(memory + Quiche.SOCKADDR_IN6_OFFSETOF_SIN6_PORT, handleNetworkOrder((short) port));
        // Skip sin6_flowinfo as we did memset before
        byte[] bytes = address.getAddress();
        if  (bytes.length == IPV4_ADDRESS_LENGTH) {
            int offset = Quiche.SOCKADDR_IN6_OFFSETOF_SIN6_ADDR + Quiche.IN6_ADDRESS_OFFSETOF_S6_ADDR;
            PlatformDependent.copyMemory(IPV4_MAPPED_IPV6_PREFIX, 0, memory + offset, IPV4_MAPPED_IPV6_PREFIX.length);
            PlatformDependent.copyMemory(bytes, 0,
                    memory + offset + IPV4_MAPPED_IPV6_PREFIX.length, IPV4_ADDRESS_LENGTH);
            // Skip sin6_scope_id as we did memset before
        } else {
            PlatformDependent.copyMemory(
                    bytes, 0, memory + Quiche.SOCKADDR_IN6_OFFSETOF_SIN6_ADDR + Quiche.IN6_ADDRESS_OFFSETOF_S6_ADDR,
                    IPV6_ADDRESS_LENGTH);
            PlatformDependent.putInt(
                    memory + Quiche.SOCKADDR_IN6_OFFSETOF_SIN6_SCOPE_ID, ((Inet6Address) address).getScopeId());
        }
        return Quiche.SIZEOF_SOCKADDR_IN6;
    }

    static InetSocketAddress readIPv4(long memory, byte[] tmpArray) {
        assert tmpArray.length == IPV4_ADDRESS_LENGTH;
        int port = handleNetworkOrder(PlatformDependent.getShort(
                memory + Quiche.SOCKADDR_IN_OFFSETOF_SIN_PORT)) & 0xFFFF;
        PlatformDependent.copyMemory(memory + Quiche.SOCKADDR_IN_OFFSETOF_SIN_ADDR + Quiche.IN_ADDRESS_OFFSETOF_S_ADDR,
                tmpArray, 0, IPV4_ADDRESS_LENGTH);
        try {
            return new InetSocketAddress(InetAddress.getByAddress(tmpArray), port);
        } catch (UnknownHostException ignore) {
            return null;
        }
    }

    static InetSocketAddress readIPv6(long memory, byte[] ipv6Array, byte[] ipv4Array) {
        assert ipv6Array.length == IPV6_ADDRESS_LENGTH;
        assert ipv4Array.length == IPV4_ADDRESS_LENGTH;

        int port = handleNetworkOrder(PlatformDependent.getShort(
                memory + Quiche.SOCKADDR_IN6_OFFSETOF_SIN6_PORT)) & 0xFFFF;
        PlatformDependent.copyMemory(
                memory + Quiche.SOCKADDR_IN6_OFFSETOF_SIN6_ADDR + Quiche.IN6_ADDRESS_OFFSETOF_S6_ADDR,
                ipv6Array, 0, IPV6_ADDRESS_LENGTH);
        if (PlatformDependent.equals(
                ipv6Array, 0, IPV4_MAPPED_IPV6_PREFIX, 0, IPV4_MAPPED_IPV6_PREFIX.length)) {
            System.arraycopy(ipv6Array, IPV4_MAPPED_IPV6_PREFIX.length, ipv4Array, 0, IPV4_ADDRESS_LENGTH);
            try {
                return new InetSocketAddress(Inet4Address.getByAddress(ipv4Array), port);
            } catch (UnknownHostException ignore) {
                return null;
            }
        } else {
            int scopeId = PlatformDependent.getInt(memory + Quiche.SOCKADDR_IN6_OFFSETOF_SIN6_SCOPE_ID);
            try {
                return new InetSocketAddress(Inet6Address.getByAddress(null, ipv6Array, scopeId), port);
            } catch (UnknownHostException ignore) {
                return null;
            }
        }
    }

    private static short handleNetworkOrder(short v) {
        return BIG_ENDIAN_NATIVE_ORDER ? v : Short.reverseBytes(v);
    }
}
