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

import io.netty.util.internal.PlatformDependent;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import static io.netty.util.internal.PlatformDependent.BIG_ENDIAN_NATIVE_ORDER;

final class SockaddrIn {
    static final byte[] IPV4_MAPPED_IPV6_PREFIX = {
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0xff, (byte) 0xff };
    private SockaddrIn() { }

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
        int written = 0;
        PlatformDependent.putShort(memory, Native.AF_INET);
        written += 2;
        PlatformDependent.putShort(memory + written, handleNetworkOrder((short) port));
        written += 2;
        byte[] bytes = address.getAddress();
        int offset = 0;
        if (bytes.length == 16) {
            // IPV6 mapped IPV4 address
            offset = 12;
        }
        assert bytes.length == offset + 4;
        PlatformDependent.copyMemory(bytes, offset, memory + written, 4);
        written += 4;

        int padding = Native.SIZEOF_SOCKADDR_IN - written;
        PlatformDependent.setMemory(memory + written, padding, (byte) 0);
        written += padding;
        assert written == Native.SIZEOF_SOCKADDR_IN;
        return written;
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
     * struct in6_addr{
     *     unsigned char s6_addr[16];   // IPv6 address
     * };
     */
    static int writeIPv6(long memory, InetAddress address, int port) {
        int written = 0;
        // AF_INET6
        PlatformDependent.putShort(memory, Native.AF_INET6);
        written += 2;
        PlatformDependent.putShort(memory + written, handleNetworkOrder((short) port));
        written += 2;
        PlatformDependent.putInt(memory + written, 0);
        written += 4;
        byte[] bytes = address.getAddress();
        if  (bytes.length == 4) {
            PlatformDependent.copyMemory(IPV4_MAPPED_IPV6_PREFIX, 0, memory + written, IPV4_MAPPED_IPV6_PREFIX.length);
            written += IPV4_MAPPED_IPV6_PREFIX.length;
            PlatformDependent.copyMemory(bytes, 0, memory + written, 4);
            written += 4;
            PlatformDependent.putInt(memory + written, 0);
            written += 4;
        } else {
            PlatformDependent.copyMemory(bytes, 0, memory + written, 16);
            written += 16;
            PlatformDependent.putInt(memory + written, ((Inet6Address) address).getScopeId());
            written += 4;
        }
        int padding = Native.SIZEOF_SOCKADDR_IN6 - written;
        PlatformDependent.setMemory(memory + written, padding, (byte) 0);
        written += padding;
        assert written == Native.SIZEOF_SOCKADDR_IN6;
        return written;
    }

    static InetSocketAddress readIPv4(long memory, byte[] tmpArray) {
        assert tmpArray.length == 4;
        int port = handleNetworkOrder(PlatformDependent.getShort(memory + 2)) & 0xFFFF;
        PlatformDependent.copyMemory(memory + 4, tmpArray, 0, 4);
        try {
            return new InetSocketAddress(InetAddress.getByAddress(tmpArray), port);
        } catch (UnknownHostException ignore) {
            return null;
        }
    }

    static InetSocketAddress readIPv6(long memory, byte[] tmpArray) {
        assert tmpArray.length == 16;
        int port = handleNetworkOrder(PlatformDependent.getShort(memory + 2)) & 0xFFFF;
        PlatformDependent.copyMemory(memory + 8, tmpArray, 0, 16);
        int scopeId = PlatformDependent.getInt(memory + 24);
        try {
            return new InetSocketAddress(Inet6Address.getByAddress(null, tmpArray, scopeId), port);
        } catch (UnknownHostException ignore) {
            return null;
        }
    }

    private static short handleNetworkOrder(short v) {
        return BIG_ENDIAN_NATIVE_ORDER ? v : Short.reverseBytes(v);
    }
}
