/*
 * Copyright 2021 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.DefaultAddressedEnvelope;

/**
 * The message container that is used for {@link DomainDatagramChannel} to communicate with the remote peer.
 */
public final class DomainDatagramPacket
        extends DefaultAddressedEnvelope<ByteBuf, DomainSocketAddress> implements ByteBufHolder {

    /**
     * Create a new instance with the specified packet {@code data} and {@code recipient} address.
     */
    public DomainDatagramPacket(ByteBuf data, DomainSocketAddress recipient) {
        super(data, recipient);
    }

    /**
     * Create a new instance with the specified packet {@code data}, {@code recipient} address, and {@code sender}
     * address.
     */
    public DomainDatagramPacket(ByteBuf data, DomainSocketAddress recipient, DomainSocketAddress sender) {
        super(data, recipient, sender);
    }

    @Override
    public DomainDatagramPacket copy() {
        return replace(content().copy());
    }

    @Override
    public DomainDatagramPacket duplicate() {
        return replace(content().duplicate());
    }

    @Override
    public DomainDatagramPacket replace(ByteBuf content) {
        return new DomainDatagramPacket(content, recipient(), sender());
    }

    @Override
    public DomainDatagramPacket retain() {
        super.retain();
        return this;
    }

    @Override
    public DomainDatagramPacket retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public DomainDatagramPacket retainedDuplicate() {
        return replace(content().retainedDuplicate());
    }

    @Override
    public DomainDatagramPacket touch() {
        super.touch();
        return this;
    }

    @Override
    public DomainDatagramPacket touch(Object hint) {
        super.touch(hint);
        return this;
    }
}
