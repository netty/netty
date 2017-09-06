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

    private int _protocol;
    private ByteBuf _packetData;

    public TunTapPacket() {
    }

    public TunTapPacket(int protocol, ByteBuf packetData) {
        if (packetData == null) {
            throw new NullPointerException("packetData");
        }
        _protocol = protocol;
        _packetData = packetData;
    }

    public TunTapPacket(ByteBuf packetData) {
        if (packetData == null) {
            throw new NullPointerException("packetData");
        }
        _protocol = inferProtocolFromIPPacket(packetData);
        _packetData = packetData;
    }

    public int protocol() {
        return _protocol;
    }

    public void setProtocol(int protocol) {
        _protocol = protocol;
    }

    public ByteBuf packetData() {
        return _packetData;
    }

    public void setPacketData(ByteBuf packetData) {
        _packetData = packetData;
    }

    @Override
    public int refCnt() {
        return _packetData.refCnt();
    }

    @Override
    public boolean release() {
        return _packetData.release();
    }

    @Override
    public boolean release(int decrement) {
        return _packetData.release(decrement);
    }

    @Override
    public ByteBuf content() {
        return _packetData;
    }

    @Override
    public TunTapPacket copy() {
        return new TunTapPacket(_protocol, _packetData.copy());
    }

    @Override
    public TunTapPacket duplicate() {
        return new TunTapPacket(_protocol, _packetData.duplicate());
    }

    @Override
    public TunTapPacket retain() {
        _packetData.retain();
        return this;
    }

    @Override
    public TunTapPacket retain(int increment) {
        _packetData.retain(increment);
        return this;
    }

    @Override
    public TunTapPacket touch() {
        _packetData.touch();
        return this;
    }

    @Override
    public TunTapPacket touch(Object hint) {
        _packetData.touch(hint);
        return this;
    }

    @Override
    public ByteBufHolder retainedDuplicate() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ByteBufHolder replace(ByteBuf content) {
        // TODO Auto-generated method stub
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
