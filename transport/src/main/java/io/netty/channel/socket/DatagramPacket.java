/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel.socket;

import io.netty.buffer.BufUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;

import java.net.InetSocketAddress;

/**
 * The message container that is used for {@link DatagramChannel} to communicate with the remote peer.
 */
public final class DatagramPacket extends DefaultByteBufHolder {

    private final InetSocketAddress remoteAddress;

    /**
     * Create a new instance
     *
     * @param data              the {@link ByteBuf} which holds the data of the packet
     * @param remoteAddress     the (@link InetSocketAddress} from which the packet was received or to which the
     *                          packet will be send
     */
    public DatagramPacket(ByteBuf data, InetSocketAddress remoteAddress) {
        super(data);
        if (remoteAddress == null) {
            throw new NullPointerException("remoteAddress");
        }

        this.remoteAddress = remoteAddress;
    }
    /**
     * The {@link InetSocketAddress} which this {@link DatagramPacket} will send to or was received from.
     */
    public InetSocketAddress remoteAddress() {
        return remoteAddress;
    }

    @Override
    public DatagramPacket copy() {
        return new DatagramPacket(data().copy(), remoteAddress());
    }

    @Override
    public DatagramPacket retain() {
        super.retain();
        return this;
    }

    @Override
    public DatagramPacket retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public String toString() {
        if (refCnt() == 0) {
            return "DatagramPacket{remoteAddress=" + remoteAddress().toString() +
                    ", data=(FREED)}";
        }
        return "DatagramPacket{remoteAddress=" + remoteAddress().toString() +
                ", data=" + BufUtil.hexDump(data()) + '}';
    }
}
