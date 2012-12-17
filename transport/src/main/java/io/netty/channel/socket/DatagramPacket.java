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

import io.netty.buffer.ByteBuf;

import java.net.InetSocketAddress;

/**
 * The message container that is used for {@link DatagramChannel} to communicate with the remote peer.
 */
public final class DatagramPacket {

    private final ByteBuf data;
    private final InetSocketAddress remoteAddress;

    public DatagramPacket(ByteBuf data, InetSocketAddress remoteAddress) {
        if (data == null) {
            throw new NullPointerException("data");
        }
        if (remoteAddress == null) {
            throw new NullPointerException("remoteAddress");
        }

        this.data = data;
        this.remoteAddress = remoteAddress;
    }

    /**
     * Return the data which is container. May return an empty {@link ByteBuf}
     */
    public ByteBuf data() {
        return data;
    }

    /**
     * The {@link InetSocketAddress} which this {@link DatagramPacket} will send to or was received from.
     * If {@code null} is used the default address will be used which the {@link DatagramChannel} was connected to.
     */
    public InetSocketAddress remoteAddress() {
        return remoteAddress;
    }

    @Override
    public String toString() {
        return "datagram(" + data.readableBytes() + "B, " + remoteAddress + ')';
    }
}
