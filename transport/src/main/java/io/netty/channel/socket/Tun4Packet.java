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

import java.net.Inet4Address;
import java.net.UnknownHostException;

import static io.netty.channel.socket.InternetProtocolFamily.IPv4;

/**
 * IPv4-based {@link TunPacket}.
 */
@SuppressWarnings("unused")
public class Tun4Packet extends TunPacket {
    public static final int INET4_HEADER_LENGTH = 20;
    // https://datatracker.ietf.org/doc/html/rfc791#section-3.1
    public static final int INET4_VERSION_AND_INTERNET_HEADER_LENGTH = 0;
    public static final int INET4_TYPE_OF_SERVICE = 1;
    public static final int INET4_TYPE_OF_SERVICE_PRECEDENCE_ROUTINE = 0;
    public static final int INET4_TYPE_OF_SERVICE_PRECEDENCE_PRIORITY = 1;
    public static final int INET4_TYPE_OF_SERVICE_PRECEDENCE_IMMEDIATE = 2;
    public static final int INET4_TYPE_OF_SERVICE_PRECEDENCE_FLASH = 3;
    public static final int INET4_TYPE_OF_SERVICE_PRECEDENCE_FLASH_OVERRIDE = 4;
    public static final int INET4_TYPE_OF_SERVICE_PRECEDENCE_CRITIC_ECP = 5;
    public static final int INET4_TYPE_OF_SERVICE_PRECEDENCE_INTERNETWORK_CONTROL = 6;
    public static final int INET4_TYPE_OF_SERVICE_PRECEDENCE_NETWORK_CONTROL = 7;
    public static final int INET4_TYPE_OF_SERVICE_DELAY_MASK = 1 << 3;
    public static final int INET4_TYPE_OF_SERVICE_THROUGHPUT_MASK = 1 << 4;
    public static final int INET4_TYPE_OF_SERVICE_RELIBILITY_MASK = 1 << 5;
    public static final int INET4_TOTAL_LENGTH = 2;
    public static final int INET4_IDENTIFICATION = 4;
    public static final int INET4_FLAGS_AND_FRAGMENT_OFFSET = 6;
    public static final int INET4_FLAGS_DONT_FRAGMENT_MASK = 1 << 1;
    public static final int INET4_FLAGS_MORE_FRAGMENTS_MASK = 1 << 2;
    public static final int INET4_TIME_TO_LIVE = 8;
    public static final int INET4_PROTOCOL = 9;
    public static final int INET4_HEADER_CHECKSUM = 10;
    public static final int INET4_SOURCE_ADDRESS = 12;
    public static final int INET4_DESTINATION_ADDRESS = 16;
    private Inet4Address sourceAddress;
    private Inet4Address destinationAddress;

    public Tun4Packet(ByteBuf data) {
        super(data);
        if (data.readableBytes() < INET4_HEADER_LENGTH) {
            throw new IllegalArgumentException("data has only " + data.readableBytes() +
                    " readable bytes. But an IPv4 packet must be at least " + INET4_HEADER_LENGTH + " bytes long.");
        }
    }

    @Override
    public InternetProtocolFamily version() {
        return IPv4;
    }

    public int internetHeaderLength() {
        return content().getUnsignedByte(INET4_VERSION_AND_INTERNET_HEADER_LENGTH) & 0x0f;
    }

    public int typeOfService() {
        return content().getUnsignedShort(INET4_TYPE_OF_SERVICE);
    }

    public int totalLength() {
        return content().getUnsignedShort(INET4_TOTAL_LENGTH);
    }

    public int identification() {
        return content().getUnsignedShort(INET4_IDENTIFICATION);
    }

    public int flags() {
        return content().getUnsignedByte(INET4_FLAGS_AND_FRAGMENT_OFFSET) >> 5;
    }

    public int fragmentOffset() {
        return content().getUnsignedShort(INET4_FLAGS_AND_FRAGMENT_OFFSET) & 0x01fff;
    }

    public int timeToLive() {
        return content().getUnsignedByte(INET4_TIME_TO_LIVE);
    }

    public int protocol() {
        return content().getUnsignedByte(INET4_PROTOCOL);
    }

    public int headerChecksum() {
        return content().getUnsignedShort(INET4_HEADER_CHECKSUM);
    }

    @Override
    public Inet4Address sourceAddress() {
        if (sourceAddress == null) {
            try {
                byte[] dst = new byte[4];
                content().getBytes(INET4_SOURCE_ADDRESS, dst, 0, 4);
                sourceAddress = (Inet4Address) Inet4Address.getByAddress(dst);
            } catch (UnknownHostException e) {
                // unreachable code
                throw new IllegalStateException();
            }
        }
        return sourceAddress;
    }

    @Override
    public Inet4Address destinationAddress() {
        if (destinationAddress == null) {
            try {
                byte[] dst = new byte[4];
                content().getBytes(INET4_DESTINATION_ADDRESS, dst, 0, 4);
                destinationAddress = (Inet4Address) Inet4Address.getByAddress(dst);
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
        return content().slice(INET4_HEADER_LENGTH, content().readableBytes() - INET4_HEADER_LENGTH);
    }

    @Override
    public String toString() {
        return new StringBuilder(StringUtil.simpleClassName(this))
                .append('[')
                .append("id=").append(identification())
                .append(", len=").append(totalLength())
                .append(", src=").append(sourceAddress().getHostAddress())
                .append(", dst=").append(destinationAddress().getHostAddress())
                .append(']').toString();
    }

    public boolean verifyChecksum() {
        return calculateChecksum(content()) == 0;
    }

    public static int calculateChecksum(ByteBuf buf) {
        int sum = 0;
        for (int i = 0; i < INET4_HEADER_LENGTH; i += 2) {
            sum += buf.getUnsignedShort(i);
        }
        return (~((sum & 0xffff) + (sum >> 16))) & 0xffff;
    }
}
