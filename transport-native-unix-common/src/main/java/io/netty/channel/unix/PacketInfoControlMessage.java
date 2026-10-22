/*
 * Copyright 2026 The Netty Project
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

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import static io.netty.channel.unix.ControlMessageConstants.IN6_PKTINFO_OFFSETOF_IPI6_ADDR;
import static io.netty.channel.unix.ControlMessageConstants.IN6_PKTINFO_OFFSETOF_IPI6_IFINDEX;
import static io.netty.channel.unix.ControlMessageConstants.IN_PKTINFO_OFFSETOF_IPI_ADDR;
import static io.netty.channel.unix.ControlMessageConstants.IN_PKTINFO_OFFSETOF_IPI_IFINDEX;
import static io.netty.channel.unix.ControlMessageConstants.IN_PKTINFO_OFFSETOF_IPI_SPEC_DST;

/**
 * A {@link NativeControlMessage} which carries a {@link PacketInfo}, which is either encoded as
 * {@code struct in_pktinfo} or {@code struct in6_pktinfo}.
 *
 * <pre>{@code
 * struct in_pktinfo {
 *     unsigned int   ipi_ifindex;  // Interface index
 *     struct in_addr ipi_spec_dst; // Local address
 *     struct in_addr ipi_addr;     // Header Destination address
 * };
 *
 * struct in6_pktinfo {
 *     struct in6_addr ipi6_addr;    // src/dst IPv6 address
 *     unsigned int    ipi6_ifindex; // send/recv interface index
 * };
 * }</pre>
 */
final class PacketInfoControlMessage extends NativeControlMessage<PacketInfo> {

    private static final int IPV4_ADDRESS_LENGTH = 4;
    private static final int IPV6_ADDRESS_LENGTH = 16;
    // The length of the prefix of IPv4-mapped IPv6 addresses (::ffff:0:0/96).
    private static final int IPV4_MAPPED_PREFIX_LENGTH = IPV6_ADDRESS_LENGTH - IPV4_ADDRESS_LENGTH;

    private final boolean ipv6;

    PacketInfoControlMessage(String name, boolean ipv6) {
        super(name);
        this.ipv6 = ipv6;
    }

    @Override
    public void validate(PacketInfo value) {
        super.validate(value);
        if (!ipv6 && value.address() != null && !(value.address() instanceof Inet4Address)) {
            throw new IllegalArgumentException(
                    "address: " + value.address() + " (expected: " + Inet4Address.class.getSimpleName() + ')');
        }
    }

    @Override
    int dataLength(PacketInfo value) {
        return descriptor().sendDataLength;
    }

    @Override
    void encode(PacketInfo value, ByteBuffer buffer, int offset) {
        // The data was already zeroed out, so we only need to fill in the values that are not 0.
        InetAddress address = value.address();
        if (ipv6) {
            buffer.putInt(offset + IN6_PKTINFO_OFFSETOF_IPI6_IFINDEX, value.interfaceIndex());
            if (address != null) {
                byte[] bytes = address.getAddress();
                int addressOffset = offset + IN6_PKTINFO_OFFSETOF_IPI6_ADDR;
                if (bytes.length == IPV4_ADDRESS_LENGTH) {
                    // Use an IPv4-mapped IPv6 address (::ffff:a.b.c.d), the first 10 bytes are 0 already.
                    buffer.put(addressOffset + IPV4_MAPPED_PREFIX_LENGTH - 2, (byte) 0xff);
                    buffer.put(addressOffset + IPV4_MAPPED_PREFIX_LENGTH - 1, (byte) 0xff);
                    addressOffset += IPV4_MAPPED_PREFIX_LENGTH;
                }
                putBytes(buffer, addressOffset, bytes);
            }
        } else {
            buffer.putInt(offset + IN_PKTINFO_OFFSETOF_IPI_IFINDEX, value.interfaceIndex());
            if (address != null) {
                // ipi_spec_dst is used as source address when sending, ipi_addr is ignored.
                putBytes(buffer, offset + IN_PKTINFO_OFFSETOF_IPI_SPEC_DST, address.getAddress());
            }
        }
    }

    @Override
    int maxDataLength() {
        return descriptor().sendDataLength;
    }

    @Override
    PacketInfo decode(ByteBuffer buffer, int offset, int length) {
        if (length < descriptor().sendDataLength) {
            return null;
        }
        final int interfaceIndex;
        final int addressOffset;
        final int addressLength;
        if (ipv6) {
            interfaceIndex = buffer.getInt(offset + IN6_PKTINFO_OFFSETOF_IPI6_IFINDEX);
            addressOffset = offset + IN6_PKTINFO_OFFSETOF_IPI6_ADDR;
            addressLength = IPV6_ADDRESS_LENGTH;
        } else {
            interfaceIndex = buffer.getInt(offset + IN_PKTINFO_OFFSETOF_IPI_IFINDEX);
            // ipi_addr is the destination address of the datagram.
            addressOffset = offset + IN_PKTINFO_OFFSETOF_IPI_ADDR;
            addressLength = IPV4_ADDRESS_LENGTH;
        }
        if (interfaceIndex < 0) {
            return null;
        }
        byte[] bytes = new byte[addressLength];
        for (int i = 0; i < addressLength; i++) {
            bytes[i] = buffer.get(addressOffset + i);
        }
        try {
            // This will return an Inet4Address for IPv4-mapped IPv6 addresses.
            return new PacketInfo(InetAddress.getByAddress(bytes), interfaceIndex);
        } catch (UnknownHostException e) {
            // Can't happen as we always pass in a valid length.
            return null;
        }
    }

    private static void putBytes(ByteBuffer buffer, int offset, byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            buffer.put(offset + i, bytes[i]);
        }
    }
}
