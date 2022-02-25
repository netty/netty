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
package io.netty5.channel.socket;

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.BufferAddressedEnvelope;

import java.net.InetSocketAddress;

/**
 * The message container that is used for {@link DatagramChannel} to communicate with the remote peer.
 */
public class BufferDatagramPacket extends BufferAddressedEnvelope<InetSocketAddress, BufferDatagramPacket> {
    /**
     * Create a new instance with the specified packet {@code data}, {@code recipient} address, and {@code sender}
     * address.
     */
    public BufferDatagramPacket(Buffer message, InetSocketAddress recipient, InetSocketAddress sender) {
        super(message, recipient, sender);
    }

    /**
     * Create a new instance with the specified packet {@code data} and {@code recipient} address.
     */
    public BufferDatagramPacket(Buffer message, InetSocketAddress recipient) {
        super(message, recipient);
    }

    @Override
    public BufferDatagramPacket replace(Buffer content) {
        return new BufferDatagramPacket(content, recipient(), sender());
    }

    @Override
    public BufferDatagramPacket touch(Object hint) {
        super.touch(hint);
        return this;
    }
}
