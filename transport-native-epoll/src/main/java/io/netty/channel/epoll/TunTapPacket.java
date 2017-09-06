/*
 * Copyright 2014 The Netty Project
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

package io.netty.channel.epoll;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;

/**
 * Represents a packet being received or sent via a {@link TunTapChannel}.
 */
public final class TunTapPacket implements ByteBufHolder {

    private int protocol;
    private ByteBuf packetData;

    public TunTapPacket() {
        // Nothing to do.
    }

    public TunTapPacket(int protocol, ByteBuf packetData) {
        if (packetData == null) {
            throw new NullPointerException("packetData");
        }
        this.protocol = protocol;
        this.packetData = packetData;
    }

    public TunTapPacket(ByteBuf packetData) {
        if (packetData == null) {
            throw new NullPointerException("packetData");
        }
        protocol = inferProtocolFromIPPacket(packetData);
        this.packetData = packetData;
    }

    public int protocol() {
        return protocol;
    }

    public void setProtocol(int protocol) {
        this.protocol = protocol;
    }

    public ByteBuf packetData() {
        return packetData;
    }

    public void setPacketData(ByteBuf packetData) {
        this.packetData = packetData;
    }

    @Override
    public int refCnt() {
        return packetData.refCnt();
    }

    @Override
    public boolean release() {
        return packetData.release();
    }

    @Override
    public boolean release(int decrement) {
        return packetData.release(decrement);
    }

    @Override
    public ByteBuf content() {
        return packetData;
    }

    @Override
    public TunTapPacket copy() {
        return new TunTapPacket(protocol, packetData.copy());
    }

    @Override
    public TunTapPacket duplicate() {
        return new TunTapPacket(protocol, packetData.duplicate());
    }

    @Override
    public TunTapPacket retain() {
        packetData.retain();
        return this;
    }

    @Override
    public TunTapPacket retain(int increment) {
        packetData.retain(increment);
        return this;
    }

    @Override
    public TunTapPacket touch() {
        packetData.touch();
        return this;
    }

    @Override
    public TunTapPacket touch(Object hint) {
        packetData.touch(hint);
        return this;
    }

    @Override
    public ByteBufHolder retainedDuplicate() {
        return null;
    }

    @Override
    public ByteBufHolder replace(ByteBuf content) {
        return null;
    }

    public static final int PROTOCOL_IPV4 = 0x0800;
    public static final int PROTOCOL_IPV6 = 0x86DD;
    public static final int PROTOCOL_ARP = 0x0806;
    public static final int PROTOCOL_NOT_SPECIFIED = 0;

    public static int inferProtocolFromIPPacket(ByteBuf packetData) {
        int ipVersionField = packetData.getUnsignedByte(packetData.readerIndex()) & 0xF0;
        switch (ipVersionField) {
        case 0x60:
            return PROTOCOL_IPV6;
        case 0x40:
            return PROTOCOL_IPV4;
        default:
            return PROTOCOL_NOT_SPECIFIED;
        }
    }

    public static String getProtocolName(int protocol) {
        switch (protocol) {
        case TunTapPacket.PROTOCOL_IPV4:
            return "IPv4";
        case TunTapPacket.PROTOCOL_IPV6:
            return "IPv6";
        case TunTapPacket.PROTOCOL_ARP:
            return "ARP";
        default:
            return Integer.toHexString(protocol).toUpperCase();
        }
    }
}
