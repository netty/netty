/*
 * Copyright 2015 The Netty Project
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
package io.netty.channel.unix;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

/**
 * <strong>Internal usage only!</strong>
 */
public final class NativeInetAddress {
    private static final byte[] IPV4_MAPPED_IPV6_PREFIX = {
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0xff, (byte) 0xff };
    final byte[] address;
    final int scopeId;

    public static NativeInetAddress newInstance(InetAddress addr) {
        byte[] bytes = addr.getAddress();
        if (addr instanceof Inet6Address) {
            return new NativeInetAddress(bytes, ((Inet6Address) addr).getScopeId());
        } else {
            // convert to ipv4 mapped ipv6 address;
            return new NativeInetAddress(ipv4MappedIpv6Address(bytes));
        }
    }

    public NativeInetAddress(byte[] address, int scopeId) {
        this.address = address;
        this.scopeId = scopeId;
    }

    public NativeInetAddress(byte[] address) {
        this(address, 0);
    }

    public byte[] address() {
        return address;
    }

    public int scopeId() {
        return scopeId;
    }

    public static byte[] ipv4MappedIpv6Address(byte[] ipv4) {
        byte[] address = new byte[16];
        copyIpv4MappedIpv6Address(ipv4, address);
        return address;
    }

    public static void copyIpv4MappedIpv6Address(byte[] ipv4, byte[] ipv6) {
        System.arraycopy(IPV4_MAPPED_IPV6_PREFIX, 0, ipv6, 0, IPV4_MAPPED_IPV6_PREFIX.length);
        System.arraycopy(ipv4, 0, ipv6, 12, ipv4.length);
    }

    public static InetSocketAddress address(byte[] addr, int offset, int len) {
        // The last 4 bytes are always the port
        final int port = decodeInt(addr, offset + len - 4);
        final InetAddress address;

        try {
            switch (len) {
                // 8 bytes:
                // - 4  == ipaddress
                // - 4  == port
                case 8:
                    byte[] ipv4 = new byte[4];
                    System.arraycopy(addr, offset, ipv4, 0, 4);
                    address = InetAddress.getByAddress(ipv4);
                    break;

                // 24 bytes:
                // - 16  == ipaddress
                // - 4   == scopeId
                // - 4   == port
                case 24:
                    byte[] ipv6 = new byte[16];
                    System.arraycopy(addr, offset, ipv6, 0, 16);
                    int scopeId = decodeInt(addr, offset + len  - 8);
                    address = Inet6Address.getByAddress(null, ipv6, scopeId);
                    break;
                default:
                    throw new Error();
            }
            return new InetSocketAddress(address, port);
        } catch (UnknownHostException e) {
            throw new Error("Should never happen", e);
        }
    }

    static int decodeInt(byte[] addr, int index) {
        return  (addr[index]     & 0xff) << 24 |
                (addr[index + 1] & 0xff) << 16 |
                (addr[index + 2] & 0xff) <<  8 |
                addr[index + 3] & 0xff;
    }
}
