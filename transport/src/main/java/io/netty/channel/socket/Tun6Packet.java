/*
 * Copyright 2022 The Netty Project
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
package io.netty.channel.socket;

import io.netty.buffer.ByteBuf;
import io.netty.util.internal.StringUtil;

import java.net.Inet6Address;
import java.net.UnknownHostException;

import static io.netty.channel.socket.InternetProtocolFamily.IPv6;

/**
 * IPv6-based {@link TunPacket}.
 */
@SuppressWarnings("unused")
public class Tun6Packet extends TunPacket {
    public static final int INET6_HEADER_LENGTH = 40;
    // https://datatracker.ietf.org/doc/html/rfc8200#section-3
    public static final int INET6_VERSION_AND_TRAFFIC_CLASS = 0;
    public static final int INET6_FLOW_LABEL = 1;
    public static final int INET6_PAYLOAD_LENGTH = 4;
    public static final int INET6_NEXT_HEADER = 6;
    public static final int INET6_HOP_LIMIT = 7;
    public static final int INET6_SOURCE_ADDRESS = 8;
    public static final int INET6_DESTINATION_ADDRESS = 24;
    private Inet6Address sourceAddress;
    private Inet6Address destinationAddress;

    public Tun6Packet(ByteBuf data) {
        super(data);
        if (data.readableBytes() < INET6_HEADER_LENGTH) {
            throw new IllegalArgumentException("data has only " + data.readableBytes() +
                    " readable bytes. But an IPv6 packet must be at least " + INET6_HEADER_LENGTH + " bytes long.");
        }
    }

    @Override
    public InternetProtocolFamily version() {
        return IPv6;
    }

    public int trafficClass() {
        return content().getUnsignedShort(INET6_VERSION_AND_TRAFFIC_CLASS) >> 4 & 0x0f;
    }

    public long flowLabel() {
        return content().getUnsignedInt(INET6_FLOW_LABEL) >> 8 & 0x0fffff;
    }

    public long payloadLength() {
        return content().getUnsignedShort(INET6_PAYLOAD_LENGTH);
    }

    public int nextHeader() {
        return content().getUnsignedByte(INET6_NEXT_HEADER);
    }

    public int hopLimit() {
        return content().getUnsignedByte(INET6_HOP_LIMIT);
    }

    @Override
    public Inet6Address sourceAddress() {
        if (sourceAddress == null) {
            try {
                byte[] dst = new byte[16];
                content().getBytes(INET6_SOURCE_ADDRESS, dst, 0, 16);
                sourceAddress = (Inet6Address) Inet6Address.getByAddress(dst);
            } catch (UnknownHostException e) {
                // unreachable code
                throw new IllegalStateException();
            }
        }
        return sourceAddress;
    }

    @Override
    public Inet6Address destinationAddress() {
        if (destinationAddress == null) {
            try {
                byte[] dst = new byte[16];
                content().getBytes(INET6_DESTINATION_ADDRESS, dst, 0, 16);
                destinationAddress = (Inet6Address) Inet6Address.getByAddress(dst);
            } catch (UnknownHostException e) {
                // unreachable code
                throw new IllegalStateException();
            }
        }
        return destinationAddress;
    }

    /**
     * Returns the remaining data behind the IP header. Modifying the content of the returned buffer
     * or this packet's buffer affects each other's content while they maintain separate indexes and
     * marks.
     * <p>
     * Also be aware that this method will NOT call {@link #retain()} and so the reference count
     * will NOT be increased.
     */
    public ByteBuf data() {
        return content().slice(INET6_HEADER_LENGTH, content().readableBytes() - INET6_HEADER_LENGTH);
    }

    @Override
    public String toString() {
        return new StringBuilder(StringUtil.simpleClassName(this))
                .append('[')
                .append("len=").append(payloadLength())
                .append(", src=").append(sourceAddress().getHostAddress())
                .append(", dst=").append(destinationAddress().getHostAddress())
                .append(']').toString();
    }
}
