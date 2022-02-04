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

import io.netty.buffer.api.Buffer;
import io.netty.channel.BufferAddressedEnvelope;

import java.net.InetSocketAddress;

public class BufferDatagramPacket extends BufferAddressedEnvelope<InetSocketAddress, BufferDatagramPacket> {
    public BufferDatagramPacket(Buffer message, InetSocketAddress recipient,
                                InetSocketAddress sender) {
        super(message, recipient, sender);
    }

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
