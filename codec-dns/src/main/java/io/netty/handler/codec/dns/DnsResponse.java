/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.dns;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;

/**
 * A DNS response packet which is sent to a client after a server receives a
 * query.
 */
public class DnsResponse extends DnsMessage<DnsResponseHeader> implements ByteBufHolder {

    private final ByteBuf rawPacket;
    private final int originalIndex;

    public DnsResponse(ByteBuf rawPacket) {
        this.rawPacket = rawPacket;
        this.originalIndex = rawPacket.readerIndex();
    }

    /**
     * Returns the non-decoded DNS response packet.
     */
    @Override
    public ByteBuf content() {
        return rawPacket;
    }

    @Override
    public int refCnt() {
        return rawPacket.refCnt();
    }

    @Override
    public boolean release() {
        return rawPacket.release();
    }

    /**
     * Returns a deep copy of this DNS response.
     */
    @Override
    public DnsResponse copy() {
        return DnsResponseDecoder.decodeResponse(rawPacket.copy(), rawPacket.alloc());
    }

    /**
     * Returns a duplicate of this DNS response.
     */
    @Override
    public ByteBufHolder duplicate() {
        return DnsResponseDecoder.decodeResponse(rawPacket.duplicate(), rawPacket.alloc());
    }

    @Override
    public DnsResponse retain() {
        rawPacket.retain();
        return this;
    }

    @Override
    public DnsResponse retain(int increment) {
        rawPacket.retain(increment);
        return this;
    }

    @Override
    public boolean release(int decrement) {
        return rawPacket.release(decrement);
    }

    /**
     * Returns the original index at which the DNS response packet starts for the {@link ByteBuf}
     * stored in this {@link ByteBufHolder}.
     */
    public int originalIndex() {
        return originalIndex;
    }

}
